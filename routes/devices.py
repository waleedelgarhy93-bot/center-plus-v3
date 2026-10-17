# routes/devices.py - إدارة إعدادات الأجهزة لكل سنتر
from flask import Blueprint, jsonify, request
from datetime import datetime

from db import supabase
from helpers import token_required, get_current_tenant_id, check_tenant_ownership

devices_bp = Blueprint('devices', __name__)


@devices_bp.route('/api/devices/settings', methods=['GET'])
@token_required
def get_device_settings():
    """جلب إعدادات الأجهزة للسنتر الحالي"""
    try:
        tenant_id, role = get_current_tenant_id()

        if not tenant_id:
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        # الـ defaults
        defaults = {
            'tenant_id': tenant_id,
            'student_limit': 1,
            'parent_limit': 2,
            'teacher_limit': 3,
            'assistant_limit': 1,
        }

        res = supabase.table('device_settings')\
            .select('*')\
            .eq('tenant_id', tenant_id)\
            .execute()

        if res.data:
            return jsonify({'success': True, 'settings': res.data[0]})

        return jsonify({'success': True, 'settings': defaults})

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@devices_bp.route('/api/devices/settings', methods=['PUT'])
@token_required
def update_device_settings():
    """تعديل إعدادات الأجهزة (لصاحب السنتر فقط)"""
    try:
        tenant_id, role = get_current_tenant_id()

        if not tenant_id:
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        if role not in ('teacher', 'super_admin'):
            return jsonify({'success': False, 'error': 'لصاحب السنتر فقط'}), 403

        data = request.get_json() or {}

        settings = {
            'student_limit': int(data.get('student_limit', 1)),
            'parent_limit': int(data.get('parent_limit', 2)),
            'teacher_limit': int(data.get('teacher_limit', 3)),
            'assistant_limit': int(data.get('assistant_limit', 1)),
        }

        # التحقق من الحد الأدنى
        for k, v in settings.items():
            if v < 1:
                return jsonify({
                    'success': False,
                    'error': f'{k} لازم يكون 1 على الأقل'
                }), 400

        existing = supabase.table('device_settings')\
            .select('tenant_id')\
            .eq('tenant_id', tenant_id)\
            .execute()

        if existing.data:
            result = supabase.table('device_settings')\
                .update({**settings, 'updated_at': datetime.utcnow().isoformat()})\
                .eq('tenant_id', tenant_id)\
                .execute()
        else:
            result = supabase.table('device_settings').insert({
                'tenant_id': tenant_id,
                **settings,
            }).execute()

        return jsonify({
            'success': True,
            'message': 'تم التحديث',
            'settings': result.data[0] if result.data else None,
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@devices_bp.route('/api/devices/active', methods=['GET'])
@token_required
def get_active_devices():
    """جلب الأجهزة النشطة للمستخدم الحالي"""
    try:
        sessions_res = supabase.table('active_sessions')\
            .select('*')\
            .eq('user_id', request.user_id)\
            .eq('user_type', 'user')\
            .eq('is_active', True)\
            .order('last_active', desc=True)\
            .execute()

        return jsonify({
            'success': True,
            'count': len(sessions_res.data or []),
            'devices': sessions_res.data or [],
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@devices_bp.route('/api/devices/<int:session_id>', methods=['DELETE'])
@token_required
def close_device_session(session_id):
    """إغلاق جلسة معينة"""
    try:
        res = supabase.table('active_sessions')\
            .select('user_id')\
            .eq('id', session_id)\
            .execute()

        if not res.data:
            return jsonify({'success': False, 'error': 'الجلسة غير موجودة'}), 404

        # لازم تكون جلستك
        if res.data[0]['user_id'] != request.user_id:
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('active_sessions').update({
            'is_active': False,
        }).eq('id', session_id).execute()

        return jsonify({'success': True, 'message': 'تم إغلاق الجلسة'})

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500