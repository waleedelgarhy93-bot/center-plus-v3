# routes/branches.py - إدارة الفروع والوحدات الدراسية (Grade → Subject → Branch)
from flask import Blueprint, jsonify, request
from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    check_tenant_ownership,
)

branches_bp = Blueprint('branches', __name__)


# ============ GET: قائمة الفروع ============

@branches_bp.route('/api/branches', methods=['GET'])
@token_required
def get_branches():
    """جلب الفروع مع فلترة اختيارية بالصف والمادة"""
    try:
        grade = request.args.get('grade')
        subject_id = request.args.get('subject_id', type=int)

        query = supabase.table('branches').select('*').order('sort_order').order('created_at')
        query = apply_tenant_filter(query)

        if grade:
            query = query.eq('grade', grade)
        if subject_id:
            query = query.eq('subject_id', subject_id)

        res = query.execute()
        branches = res.data or []

        # ربط أسماء المواد
        if branches:
            sub_ids = list({b['subject_id'] for b in branches if b.get('subject_id')})
            if sub_ids:
                subs_res = supabase.table('subjects').select('id, name').in_('id', sub_ids).execute()
                subs_map = {s['id']: s['name'] for s in (subs_res.data or [])}
                for b in branches:
                    b['subject_name'] = subs_map.get(b.get('subject_id'))

        return jsonify({'success': True, 'count': len(branches), 'branches': branches})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ GET: الصفوف المتاحة مع موادها ============

@branches_bp.route('/api/branches/grades-overview', methods=['GET'])
@token_required
def get_grades_overview():
    """
    يرجع قائمة الصفوف مع المواد والفروع لكل صف
    الهيكل: { grade, subjects: [{ subject_id, subject_name, branches_count }] }
    """
    try:
        # جلب كل الفروع للـ tenant
        query = supabase.table('branches').select('*').order('grade').order('sort_order')
        query = apply_tenant_filter(query)
        res = query.execute()
        all_branches = res.data or []

        # جلب المواد
        subs_res = supabase.table('subjects').select('id, name, color, icon')
        subs_res = apply_tenant_filter(subs_res)
        subs_map = {s['id']: s for s in (subs_res.execute().data or [])}

        # تجميع حسب الصف → المادة
        grade_map = {}
        for b in all_branches:
            grade = b.get('grade', '')
            sid = b.get('subject_id')
            if grade not in grade_map:
                grade_map[grade] = {}
            if sid not in grade_map[grade]:
                grade_map[grade][sid] = {
                    'subject_id': sid,
                    'subject_name': subs_map.get(sid, {}).get('name', ''),
                    'subject_color': subs_map.get(sid, {}).get('color', 'blue'),
                    'branches_count': 0,
                    'branches': []
                }
            grade_map[grade][sid]['branches_count'] += 1
            grade_map[grade][sid]['branches'].append({
                'id': b['id'],
                'name': b['name'],
                'sort_order': b.get('sort_order', 0)
            })

        # ترتيب الصفوف بالترتيب المنطقي
        GRADE_ORDER = [
            'أولى إعدادي', 'تانية إعدادي', 'تالتة إعدادي',
            'أولى ثانوي', 'تانية ثانوي', 'تالتة ثانوي'
        ]
        result = []
        for grade in GRADE_ORDER:
            if grade in grade_map:
                subjects = list(grade_map[grade].values())
                subjects.sort(key=lambda x: x['subject_name'])
                result.append({'grade': grade, 'subjects': subjects})

        # إضافة أي صفوف غير موجودة في الترتيب المبدئي
        for grade, subjects_dict in grade_map.items():
            if grade not in GRADE_ORDER:
                subjects = list(subjects_dict.values())
                result.append({'grade': grade, 'subjects': subjects})

        return jsonify({'success': True, 'grades': result})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ GET: مواد صف معين ============

@branches_bp.route('/api/branches/subjects-by-grade', methods=['GET'])
@token_required
def get_subjects_by_grade():
    """جلب المواد المتاحة لصف معين (من الفروع الموجودة)"""
    try:
        grade = request.args.get('grade')
        if not grade:
            return jsonify({'success': False, 'error': 'grade مطلوب'}), 400

        query = supabase.table('branches').select('subject_id').eq('grade', grade)
        query = apply_tenant_filter(query)
        res = query.execute()

        sub_ids = list({b['subject_id'] for b in (res.data or []) if b.get('subject_id')})

        subjects = []
        if sub_ids:
            subs_res = supabase.table('subjects').select('*').in_('id', sub_ids).execute()
            subjects = subs_res.data or []

        return jsonify({'success': True, 'grade': grade, 'subjects': subjects})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ POST: إضافة فرع جديد ============

@branches_bp.route('/api/branches', methods=['POST'])
@token_required
def create_branch():
    """إضافة فرع/وحدة جديدة"""
    try:
        data = request.get_json() or {}
        name = (data.get('name') or '').strip()
        subject_id = data.get('subject_id')
        grade = (data.get('grade') or '').strip()

        if not name:
            return jsonify({'success': False, 'error': 'اسم الفرع مطلوب'}), 400
        if not grade:
            return jsonify({'success': False, 'error': 'الصف مطلوب'}), 400
        if not subject_id:
            return jsonify({'success': False, 'error': 'المادة مطلوبة'}), 400

        tenant_id = get_tenant_for_insert()

        new_branch = supabase.table('branches').insert({
            'name': name,
            'subject_id': int(subject_id),
            'grade': grade,
            'sort_order': int(data.get('sort_order', 0)),
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم إضافة الفرع بنجاح',
            'branch': new_branch.data[0] if new_branch.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ PUT: تعديل فرع ============

@branches_bp.route('/api/branches/<int:branch_id>', methods=['PUT'])
@token_required
def update_branch(branch_id):
    """تعديل اسم أو ترتيب فرع موجود"""
    try:
        existing = supabase.table('branches').select('tenant_id').eq('id', branch_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'الفرع غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        update_data = {}
        for field in ['name', 'sort_order', 'grade', 'subject_id']:
            if field in data:
                update_data[field] = data[field]

        if not update_data:
            return jsonify({'success': False, 'error': 'لا توجد بيانات للتعديل'}), 400

        result = supabase.table('branches').update(update_data).eq('id', branch_id).execute()

        return jsonify({
            'success': True,
            'message': 'تم التعديل',
            'branch': result.data[0] if result.data else None
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ DELETE: حذف فرع ============

@branches_bp.route('/api/branches/<int:branch_id>', methods=['DELETE'])
@token_required
def delete_branch(branch_id):
    """حذف فرع (يُعيّن branch_id=null في الفيديوهات والاختبارات المرتبطة)"""
    try:
        existing = supabase.table('branches').select('tenant_id').eq('id', branch_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'الفرع غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('branches').delete().eq('id', branch_id).execute()
        return jsonify({'success': True, 'message': 'تم حذف الفرع'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ GET: محتوى فرع معين ============

@branches_bp.route('/api/branches/<int:branch_id>/content', methods=['GET'])
@token_required
def get_branch_content(branch_id):
    """جلب كل المحتوى (فيديوهات + اختبارات + واجبات + مستندات) لفرع معين"""
    try:
        # التحقق من وجود الفرع
        branch_res = supabase.table('branches').select('*').eq('id', branch_id).execute()
        if not branch_res.data:
            return jsonify({'success': False, 'error': 'الفرع غير موجود'}), 404

        branch = branch_res.data[0]

        # جلب المحتوى بالتوازي
        videos_res = supabase.table('videos').select('id, title, description, video_url, created_at').eq('branch_id', branch_id).execute()
        exams_res = supabase.table('exams').select('id, title, description, created_at, max_grade').eq('branch_id', branch_id).execute()

        # Homework
        try:
            hw_res = supabase.table('homework').select('id, title, description, created_at, due_date').eq('branch_id', branch_id).execute()
            homework = hw_res.data or []
        except Exception:
            homework = []

        # Documents
        try:
            docs_res = supabase.table('documents').select('id, title, description, file_url, created_at').eq('branch_id', branch_id).execute()
            documents = docs_res.data or []
        except Exception:
            documents = []

        return jsonify({
            'success': True,
            'branch': branch,
            'content': {
                'videos': videos_res.data or [],
                'exams': exams_res.data or [],
                'homework': homework,
                'documents': documents,
            },
            'counts': {
                'videos': len(videos_res.data or []),
                'exams': len(exams_res.data or []),
                'homework': len(homework),
                'documents': len(documents),
            }
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500
