# routes/subjects.py - CRUD المواد
from flask import Blueprint, jsonify, request

from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    check_tenant_ownership,
)

subjects_bp = Blueprint('subjects', __name__)


@subjects_bp.route('/api/subjects', methods=['GET'])
@token_required
def get_subjects():
    try:
        query = supabase.table('subjects').select('*')
        query = apply_tenant_filter(query)
        response = query.execute()

        return jsonify({
            'success': True,
            'count': len(response.data),
            'subjects': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@subjects_bp.route('/api/subjects/<int:subject_id>', methods=['GET'])
@token_required
def get_subject(subject_id):
    try:
        response = supabase.table('subjects').select('*').eq('id', subject_id).execute()
        if not response.data:
            return jsonify({'success': False, 'error': 'Subject not found'}), 404

        subject = response.data[0]
        if not check_tenant_ownership(subject):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        return jsonify({'success': True, 'subject': subject})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@subjects_bp.route('/api/subjects', methods=['POST'])
@token_required
def create_subject():
    try:
        data = request.get_json() or {}
        name = data.get('name')
        if not name:
            return jsonify({'success': False, 'error': 'name مطلوب'}), 400

        tenant_id = get_tenant_for_insert()

        new_subject = supabase.table('subjects').insert({
            'name': name,
            'description': data.get('description'),
            'color': data.get('color', 'blue'),
            'icon': data.get('icon'),
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم إنشاء المادة',
            'subject': new_subject.data[0] if new_subject.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@subjects_bp.route('/api/subjects/<int:subject_id>', methods=['PUT'])
@token_required
def update_subject(subject_id):
    try:
        existing = supabase.table('subjects').select('tenant_id').eq('id', subject_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'Subject not found'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        update_data = {}
        for field in ['name', 'description', 'color', 'icon']:
            if field in data:
                update_data[field] = data[field]

        if not update_data:
            return jsonify({'success': False, 'error': 'مفيش بيانات للتعديل'}), 400

        result = supabase.table('subjects').update(update_data).eq('id', subject_id).execute()

        return jsonify({
            'success': True,
            'message': 'تم التعديل',
            'subject': result.data[0] if result.data else None
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@subjects_bp.route('/api/subjects/<int:subject_id>', methods=['DELETE'])
@token_required
def delete_subject(subject_id):
    try:
        existing = supabase.table('subjects').select('tenant_id').eq('id', subject_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'Subject not found'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('subjects').delete().eq('id', subject_id).execute()

        return jsonify({'success': True, 'message': 'تم الحذف'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500