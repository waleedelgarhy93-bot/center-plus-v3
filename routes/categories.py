# routes/categories.py - إدارة الأقسام والفروع والوحدات
from flask import Blueprint, jsonify, request
from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    check_tenant_ownership,
)

categories_bp = Blueprint('categories', __name__)


@categories_bp.route('/api/categories', methods=['GET'])
@token_required
def get_categories():
    try:
        subject_id = request.args.get('subject_id')
        query = supabase.table('categories').select('*').order('created_at')
        query = apply_tenant_filter(query)

        if subject_id:
            query = query.eq('subject_id', int(subject_id))

        res = query.execute()
        categories = res.data or []

        # ربط أسماء المواد إذا وجدت
        if categories:
            sub_ids = list({c['subject_id'] for c in categories if c.get('subject_id')})
            if sub_ids:
                subs_res = supabase.table('subjects').select('id, name').in_('id', sub_ids).execute()
                subs_map = {s['id']: s['name'] for s in (subs_res.data or [])}
                for c in categories:
                    c['subject_name'] = subs_map.get(c.get('subject_id'))

        return jsonify({'success': True, 'count': len(categories), 'categories': categories})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@categories_bp.route('/api/categories', methods=['POST'])
@token_required
def create_category():
    try:
        data = request.get_json() or {}
        name = (data.get('name') or '').strip()
        subject_id = data.get('subject_id')

        if not name:
            return jsonify({'success': False, 'error': 'اسم القسم مطلوب'}), 400

        tenant_id = get_tenant_for_insert()

        new_cat = supabase.table('categories').insert({
            'name': name,
            'subject_id': int(subject_id) if subject_id else None,
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم إضافة القسم بنجاح',
            'category': new_cat.data[0] if new_cat.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@categories_bp.route('/api/categories/<int:cat_id>', methods=['DELETE'])
@token_required
def delete_category(cat_id):
    try:
        existing = supabase.table('categories').select('tenant_id').eq('id', cat_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'القسم غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('categories').delete().eq('id', cat_id).execute()
        return jsonify({'success': True, 'message': 'تم الحذف'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500
