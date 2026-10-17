# routes/groups.py - CRUD المجموعات
from flask import Blueprint, jsonify, request

from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    check_tenant_ownership,
)

groups_bp = Blueprint('groups', __name__)


@groups_bp.route('/api/groups', methods=['GET'])
@token_required
def get_groups():
    try:
        query = supabase.table('groups').select('*')
        query = apply_tenant_filter(query)
        response = query.execute()

        return jsonify({
            'success': True,
            'count': len(response.data),
            'groups': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@groups_bp.route('/api/groups/<int:group_id>', methods=['GET'])
@token_required
def get_group(group_id):
    try:
        response = supabase.table('groups').select('*').eq('id', group_id).execute()
        if not response.data:
            return jsonify({'success': False, 'error': 'Group not found'}), 404

        group = response.data[0]
        if not check_tenant_ownership(group):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        return jsonify({'success': True, 'group': group})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@groups_bp.route('/api/groups', methods=['POST'])
@token_required
def create_group():
    try:
        data = request.get_json() or {}
        grade = data.get('grade')
        center = data.get('center')
        subject_id = data.get('subject_id')

        if not grade or not center or not subject_id:
            return jsonify({
                'success': False,
                'error': 'grade, center, subject_id مطلوبين'
            }), 400

        tenant_id = get_tenant_for_insert()

        new_group = supabase.table('groups').insert({
            'grade': grade,
            'center': center,
            'subject_id': subject_id,
            'days': data.get('days'),
            'time': data.get('time'),
            'price': data.get('price'),
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم إنشاء المجموعة',
            'group': new_group.data[0] if new_group.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@groups_bp.route('/api/groups/<int:group_id>', methods=['PUT'])
@token_required
def update_group(group_id):
    try:
        existing = supabase.table('groups').select('tenant_id').eq('id', group_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'Group not found'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        update_data = {}
        for field in ['grade', 'center', 'subject_id', 'days', 'time', 'price']:
            if field in data:
                update_data[field] = data[field]

        if not update_data:
            return jsonify({'success': False, 'error': 'مفيش بيانات للتعديل'}), 400

        result = supabase.table('groups').update(update_data).eq('id', group_id).execute()

        return jsonify({
            'success': True,
            'message': 'تم التعديل',
            'group': result.data[0] if result.data else None
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@groups_bp.route('/api/groups/<int:group_id>', methods=['DELETE'])
@token_required
def delete_group(group_id):
    try:
        existing = supabase.table('groups').select('tenant_id').eq('id', group_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'Group not found'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('groups').delete().eq('id', group_id).execute()

        return jsonify({'success': True, 'message': 'تم الحذف'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500