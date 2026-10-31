# -*- coding: utf-8 -*-
# routes/documents.py - المذكرات والكتب + تصنيف الفروع + حماية/تحميل الملفات
from flask import Blueprint, jsonify, request
import os
import uuid
from werkzeug.utils import secure_filename
from supabase import create_client

from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    get_current_tenant_id,
    check_tenant_ownership,
)

documents_bp = Blueprint('documents', __name__)

UPLOAD_FOLDER = os.path.join(os.getcwd(), 'uploads', 'documents')
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
ALLOWED_EXTENSIONS = {'pdf'}

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def _save_document_groups(document_id, group_ids, tenant_id):
    supabase.table('document_groups').delete().eq('document_id', document_id).execute()
    if group_ids:
        rows = [{'document_id': document_id, 'group_id': gid, 'tenant_id': tenant_id} for gid in group_ids]
        supabase.table('document_groups').insert(rows).execute()

def _get_document_group_ids(document_id):
    res = supabase.table('document_groups').select('group_id').eq('document_id', document_id).execute()
    return [r['group_id'] for r in (res.data or [])]

def _enrich_categories(items):
    if not items:
        return items
    cat_ids = list({it.get('category_id') for it in items if it.get('category_id')})
    if cat_ids:
        cats_res = supabase.table('categories').select('id, name').in_('id', cat_ids).execute()
        cats_map = {c['id']: c['name'] for c in (cats_res.data or [])}
        for it in items:
            it['category_name'] = cats_map.get(it.get('category_id'))
    else:
        for it in items:
            it['category_name'] = None
    return items

def _enrich_documents_with_groups(documents):
    if not documents:
        return documents
    doc_ids = [d['id'] for d in documents]
    dg_res = supabase.table('document_groups').select('document_id, group_id').in_('document_id', doc_ids).execute()
    dg_data = dg_res.data or []
    all_group_ids = list({r['group_id'] for r in dg_data})
    groups_map = {}
    if all_group_ids:
        grps = supabase.table('groups').select('id, grade, center, subject_id').in_('id', all_group_ids).execute()
        grps_data = grps.data or []
        sub_ids = list({g['subject_id'] for g in grps_data if g.get('subject_id')})
        subs_map = {}
        if sub_ids:
            s_res = supabase.table('subjects').select('id, name').in_('id', sub_ids).execute()
            subs_map = {s['id']: s['name'] for s in (s_res.data or [])}
        groups_map = {
            g['id']: f"{g['grade']} - {g['center']}" + (f" ({subs_map[g['subject_id']]})" if g.get('subject_id') in subs_map else "")
            for g in grps_data
        }
    doc_groups_map = {}
    for r in dg_data:
        did = r['document_id']
        gid = r['group_id']
        if did not in doc_groups_map:
            doc_groups_map[did] = []
        doc_groups_map[did].append(gid)
    for d in documents:
        gids = doc_groups_map.get(d['id'], [])
        d['group_ids'] = gids
        d['group_names'] = [groups_map.get(gid, '') for gid in gids]
        d['group_id'] = gids[0] if gids else d.get('group_id')
        d['group_name'] = groups_map.get(d['group_id']) if d.get('group_id') else None
    return documents

def _parse_doc_download_flag(doc):
    if not doc:
        return doc
    desc = doc.get('description') or ''
    doc['is_downloadable'] = '[ALLOW_DOWNLOAD]' in desc
    doc['description'] = desc.replace('[ALLOW_DOWNLOAD]', '').strip()
    return doc


# ============ Documents CRUD (Teacher) ============

@documents_bp.route('/api/documents', methods=['GET'])
@token_required
def get_documents():
    try:
        query = supabase.table('documents').select('*').order('created_at', desc=True)
        query = apply_tenant_filter(query)
        response = query.execute()
        documents = response.data or []

        if documents:
            subject_ids = list({d['subject_id'] for d in documents if d.get('subject_id')})
            subjects_map = {}
            if subject_ids:
                subs = supabase.table('subjects').select('id, name').in_('id', subject_ids).execute()
                subjects_map = {s['id']: s['name'] for s in (subs.data or [])}

            for d in documents:
                d['subject_name'] = subjects_map.get(d.get('subject_id'))
                _parse_doc_download_flag(d)

            documents = _enrich_documents_with_groups(documents)
            documents = _enrich_categories(documents)

        documents = _enrich_categories(documents)
        return jsonify({'success': True, 'count': len(documents), 'documents': documents})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@documents_bp.route('/api/documents', methods=['POST'])
@token_required
def create_document():
    try:
        data = request.get_json() or {}
        title = data.get('title')
        file_url = data.get('file_url')

        if not title or not file_url:
            return jsonify({'success': False, 'error': 'العنوان ورابط الملف مطلوبين'}), 400

        tenant_id = get_tenant_for_insert()
        group_ids = data.get('group_ids', [])

        if not group_ids and data.get('group_id'):
            group_ids = [data['group_id']]

        # معالجة خيار السماح بالتحميل
        raw_desc = (data.get('description') or '').strip()
        if data.get('allow_download') or data.get('is_downloadable'):
            raw_desc = (raw_desc + ' [ALLOW_DOWNLOAD]').strip()

        new_doc = supabase.table('documents').insert({
            'title': title,
            'description': raw_desc if raw_desc else None,
            'file_url': file_url,
            'subject_id': data.get('subject_id'),
            'category_id': int(data['category_id']) if data.get('category_id') else None,
            'group_id': group_ids[0] if group_ids else None,
            'teacher_id': request.user_id,
            'tenant_id': tenant_id,
        }).execute()

        doc = new_doc.data[0] if new_doc.data else None
        if doc and group_ids:
            _save_document_groups(doc['id'], group_ids, tenant_id)
            doc['group_ids'] = group_ids

        _parse_doc_download_flag(doc)
        return jsonify({'success': True, 'message': 'تم إضافة المذكرة', 'document': doc}), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@documents_bp.route('/api/documents/<int:doc_id>', methods=['GET'])
@token_required
def get_document(doc_id):
    try:
        res = supabase.table('documents').select('*').eq('id', doc_id).execute()
        if not res.data:
            return jsonify({'success': False, 'error': 'المذكرة غير موجودة'}), 404

        doc = res.data[0]
        if doc.get('subject_id'):
            sub_res = supabase.table('subjects').select('name').eq('id', doc['subject_id']).execute()
            if sub_res.data:
                doc['subject_name'] = sub_res.data[0]['name']

        _parse_doc_download_flag(doc)
        return jsonify({'success': True, 'document': doc})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@documents_bp.route('/api/documents/<int:doc_id>', methods=['DELETE'])
@token_required
def delete_document(doc_id):
    try:
        existing = supabase.table('documents').select('tenant_id').eq('id', doc_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'المذكرة غير موجودة'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('documents').delete().eq('id', doc_id).execute()
        return jsonify({'success': True, 'message': 'تم الحذف'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ رفع ملف PDF مباشر ============

@documents_bp.route('/api/upload/pdf', methods=['POST'])
@token_required
def upload_pdf():
    try:
        if 'file' not in request.files:
            return jsonify({'success': False, 'error': 'لم يتم اختيار ملف'}), 400

        file = request.files['file']
        if file.filename == '':
            return jsonify({'success': False, 'error': 'اسم الملف فارغ'}), 400

        if not allowed_file(file.filename):
            return jsonify({'success': False, 'error': 'مسموح فقط بملفات PDF'}), 400

        ext = file.filename.rsplit('.', 1)[1].lower()
        unique_name = f"doc_{uuid.uuid4().hex[:12]}.{ext}"

        file_bytes = file.read()
        if len(file_bytes) > 25 * 1024 * 1024:
            return jsonify({'success': False, 'error': 'حجم الملف أكبر من 25MB'}), 400

        storage_path = f"documents/{unique_name}"
        res = supabase.storage.from_('documents').upload(
            path=storage_path,
            file=file_bytes,
            file_options={"content-type": "application/pdf"}
        )

        public_url = supabase.storage.from_('documents').get_public_url(storage_path)

        return jsonify({
            'success': True,
            'file_url': public_url,
            'filename': file.filename,
        })
    except Exception as e:
        print(f"Upload PDF Error: {e}")
        return jsonify({'success': False, 'error': f'فشل رفع الملف: {str(e)}'}), 500


# ============ Documents (Student) ============

@documents_bp.route('/api/student/documents', methods=['GET'])
@token_required
def get_student_documents():
    try:
        student_res = supabase.table('students').select('group_id, tenant_id').eq('id', request.user_id).execute()
        if not student_res.data:
            return jsonify({'success': False, 'error': 'الطالب غير موجود'}), 404

        student_info = student_res.data[0]
        student_tenant = student_info.get('tenant_id')
        primary_group_id = student_info.get('group_id')

        sg_res = supabase.table('student_groups').select('group_id').eq('student_id', request.user_id).execute()
        student_group_ids = [r['group_id'] for r in (sg_res.data or [])]

        if primary_group_id and primary_group_id not in student_group_ids:
            student_group_ids.append(primary_group_id)

        dg_doc_ids = []
        if student_group_ids:
            dg_res = supabase.table('document_groups').select('document_id').in_('group_id', student_group_ids).execute()
            dg_doc_ids = [r['document_id'] for r in (dg_res.data or [])]

        query = supabase.table('documents').select('*').eq('tenant_id', student_tenant)

        if student_group_ids:
            query = query.or_(f"group_id.is.null,group_id.in.({','.join(map(str, student_group_ids))})")
        else:
            query = query.or_('group_id.is.null')

        docs_res = query.order('created_at', desc=True).execute()
        documents = docs_res.data or []

        existing_ids = {d['id'] for d in documents}
        remaining_ids = [did for did in dg_doc_ids if did not in existing_ids]
        if remaining_ids:
            extra_res = supabase.table('documents').select('*').in_('id', remaining_ids).execute()
            documents += (extra_res.data or [])

        if documents:
            subject_ids = list({d['subject_id'] for d in documents if d.get('subject_id')})
            subjects_map = {}
            if subject_ids:
                subs = supabase.table('subjects').select('id, name').in_('id', subject_ids).execute()
                subjects_map = {s['id']: s['name'] for s in (subs.data or [])}

            for d in documents:
                d['subject_name'] = subjects_map.get(d.get('subject_id'))
                _parse_doc_download_flag(d)

        documents = _enrich_categories(documents)
        return jsonify({'success': True, 'count': len(documents), 'documents': documents})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500
