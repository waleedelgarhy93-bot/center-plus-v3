# routes/auth.py - كل الـ auth endpoints + Single Device Login
from flask import Blueprint, jsonify, request
import random
import string
from datetime import datetime

from db import supabase
from helpers import (
    hash_password,
    verify_password,
    create_token,
    token_required,
    get_current_tenant_id,
)

auth_bp = Blueprint('auth', __name__)


# ============ Helpers ============

def _get_device_limit(tenant_id, role):
    """يرجّع الحد الأقصى لعدد الأجهزة حسب دور المستخدم"""
    default_limits = {
        'student': 1,
        'parent': 2,
        'teacher': 3,
        'assistant': 1,
    }

    if not tenant_id:
        return default_limits.get(role, 1)

    settings_res = supabase.table('device_settings')\
        .select('*')\
        .eq('tenant_id', tenant_id)\
        .execute()

    if not settings_res.data:
        return default_limits.get(role, 1)

    settings = settings_res.data[0]
    role_key = f"{role}_limit"

    return settings.get(role_key, default_limits.get(role, 1))


def _check_and_save_session(user_id, user_type, user_role, tenant_id, device_id):
    """يتحقق من عدد الأجهزة المسموح"""
    limit = _get_device_limit(tenant_id, user_role)

    existing = supabase.table('active_sessions')\
        .select('*')\
        .eq('user_id', user_id)\
        .eq('user_type', user_type)\
        .eq('is_active', True)\
        .execute()

    sessions = existing.data or []

    # لو نفس الجهاز موجود بالفعل → تحديث last_active
    same_device = next((s for s in sessions if s['device_id'] == device_id), None)
    if same_device:
        supabase.table('active_sessions').update({
            'last_active': datetime.utcnow().isoformat(),
        }).eq('id', same_device['id']).execute()
        return True, None

    current_count = len(sessions)

    if current_count >= limit:
        if limit == 1:
            # حالة جهاز واحد: نطرد القديم
            for s in sessions:
                supabase.table('active_sessions').update({
                    'is_active': False,
                }).eq('id', s['id']).execute()
        else:
            return False, {
                'error': f'وصلت للحد الأقصى ({limit} أجهزة). اقفل جلسة من جهاز آخر أولاً.',
                'active_devices': len(sessions),
                'max_devices': limit,
            }

    # إضافة الجلسة الجديدة
    supabase.table('active_sessions').insert({
        'user_id': user_id,
        'user_type': user_type,
        'user_role': user_role,
        'tenant_id': tenant_id,
        'device_id': device_id,
        'ip_address': request.remote_addr or 'unknown',
        'user_agent': request.headers.get('User-Agent', 'unknown')[:500],
        'is_active': True,
    }).execute()

    return True, None


# ============ Login / Register ============

@auth_bp.route('/api/auth/register', methods=['POST'])
def register():
    try:
        data = request.get_json() or {}
        username = data.get('username')
        password = data.get('password')
        role = data.get('role', 'student')
        full_name = data.get('full_name', '')
        phone = data.get('phone', '')
        tenant_id = data.get('tenant_id')

        if not username or not password:
            return jsonify({'success': False, 'error': 'username و password مطلوبين'}), 400

        existing = supabase.table('users').select('id').eq('username', username).execute()
        if existing.data:
            return jsonify({'success': False, 'error': 'username موجود بالفعل'}), 400

        new_user = supabase.table('users').insert({
            'username': username,
            'password': hash_password(password),
            'role': role,
            'full_name': full_name,
            'phone': phone,
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'User registered successfully',
            'user': new_user.data[0] if new_user.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@auth_bp.route('/api/auth/login', methods=['POST'])
def login():
    try:
        data = request.get_json() or {}
        username = data.get('username')
        password = data.get('password')
        device_id = data.get('device_id', 'unknown')

        if not username or not password:
            return jsonify({'success': False, 'error': 'username و password مطلوبين'}), 400

        response = supabase.table('users').select('*').eq('username', username).execute()
        if not response.data:
            return jsonify({'success': False, 'error': 'Invalid credentials'}), 401

        user = response.data[0]

        if not verify_password(password, user['password']):
            return jsonify({'success': False, 'error': 'Invalid credentials'}), 401

        # التحقق من حالة السنتر
        if user.get('tenant_id') and user.get('role') != 'super_admin':
            tenant_res = supabase.table('tenants')\
                .select('status')\
                .eq('id', user['tenant_id'])\
                .execute()

            if tenant_res.data and tenant_res.data[0].get('status') != 'active':
                return jsonify({
                    'success': False,
                    'error': 'السنتر موقوف. تواصل مع الإدارة'
                }), 403

        # Single Device
        ok, err = _check_and_save_session(
            user_id=user['id'],
            user_type='user',
            user_role=user['role'],
            tenant_id=user.get('tenant_id'),
            device_id=device_id,
        )

        if not ok:
            return jsonify({'success': False, **err}), 403

        token = create_token(user['id'], user['role'])

        return jsonify({
            'success': True,
            'message': 'Login successful',
            'token': token,
            'user': {
                'id': user['id'],
                'username': user['username'],
                'role': user['role'],
                'full_name': user.get('full_name'),
                'phone': user.get('phone'),
                'tenant_id': user.get('tenant_id'),
            }
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@auth_bp.route('/api/auth/logout', methods=['POST'])
@token_required
def logout():
    try:
        data = request.get_json() or {}
        device_id = data.get('device_id', 'unknown')

        supabase.table('active_sessions')\
            .update({'is_active': False})\
            .eq('user_id', request.user_id)\
            .eq('user_type', 'user')\
            .eq('device_id', device_id)\
            .execute()

        return jsonify({'success': True, 'message': 'تم تسجيل الخروج'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@auth_bp.route('/api/auth/me', methods=['GET'])
@token_required
def me():
    try:
        user_res = supabase.table('users').select('*').eq('id', request.user_id).execute()
        if not user_res.data:
            return jsonify({'success': False, 'error': 'User not found'}), 404

        user = user_res.data[0]

        return jsonify({
            'success': True,
            'user': {
                'id': user['id'],
                'username': user['username'],
                'role': user['role'],
                'full_name': user.get('full_name'),
                'phone': user.get('phone'),
                'tenant_id': user.get('tenant_id'),
            }
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Student Login ============

@auth_bp.route('/api/auth/login-student', methods=['POST'])
def login_student():
    try:
        data = request.get_json() or {}
        barcode = data.get('barcode')
        username = data.get('username')
        password = data.get('password')
        device_id = data.get('device_id', 'unknown')

        if not barcode or not username or not password:
            return jsonify({'success': False, 'error': 'barcode + username + password مطلوبين'}), 400

        card_res = supabase.table('cards').select('*').eq('barcode', barcode).execute()
        if not card_res.data:
            return jsonify({'success': False, 'error': 'الكارت غير موجود'}), 401

        card = card_res.data[0]

        if card.get('username') != username:
            return jsonify({'success': False, 'error': 'اليوزر غير صحيح'}), 401

        if card.get('password') != password:
            return jsonify({'success': False, 'error': 'الباسورد غير صحيح'}), 401

        if not card.get('student_id'):
            return jsonify({'success': False, 'error': 'الكارت غير مربوط بأي طالب'}), 401

        student_res = supabase.table('students').select('*').eq('id', card['student_id']).execute()
        if not student_res.data:
            return jsonify({'success': False, 'error': 'الطالب غير موجود'}), 404

        student = student_res.data[0]

        # Single Device
        ok, err = _check_and_save_session(
            user_id=student['id'],
            user_type='student',
            user_role='student',
            tenant_id=student.get('tenant_id'),
            device_id=device_id,
        )

        if not ok:
            return jsonify({'success': False, **err}), 403

        token = create_token(student['id'], 'student')

        return jsonify({
            'success': True,
            'message': f"أهلاً {student['name']}",
            'token': token,
            'user': {
                'id': student['id'],
                'name': student['name'],
                'role': 'student',
                'barcode': card['barcode'],
                'group_id': student.get('group_id'),
                'tenant_id': student.get('tenant_id'),
            }
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Parent ============

@auth_bp.route('/api/auth/register-parent', methods=['POST'])
@token_required
def register_parent():
    try:
        data = request.get_json() or {}
        student_id = data.get('student_id')

        if not student_id:
            return jsonify({'success': False, 'error': 'student_id مطلوب'}), 400

        student_res = supabase.table('students').select('*').eq('id', student_id).execute()
        if not student_res.data:
            return jsonify({'success': False, 'error': 'الطالب غير موجود'}), 404

        student = student_res.data[0]

        existing = supabase.table('users')\
            .select('*')\
            .eq('student_id', student_id)\
            .eq('role', 'parent')\
            .execute()

        if existing.data:
            parent = existing.data[0]
            return jsonify({
                'success': True,
                'already_exists': True,
                'message': 'يوجد حساب ولي أمر بالفعل',
                'parent': {
                    'id': parent['id'],
                    'username': parent['username'],
                    'full_name': parent.get('full_name'),
                    'student_name': student['name']
                }
            })

        max_attempts = 30
        parent_username = None
        for _ in range(max_attempts):
            candidate = 'par_' + ''.join(random.choices(string.digits, k=6))
            check = supabase.table('users').select('id').eq('username', candidate).execute()
            if not check.data:
                parent_username = candidate
                break

        if not parent_username:
            return jsonify({'success': False, 'error': 'فشل توليد username فريد'}), 500

        parent_password = ''.join(random.choices(string.digits, k=8))

        new_user = supabase.table('users').insert({
            'username': parent_username,
            'password': hash_password(parent_password),
            'role': 'parent',
            'full_name': f"ولي أمر {student['name']}",
            'phone': student.get('parent_phone'),
            'student_id': student_id,
            'tenant_id': student.get('tenant_id'),
        }).execute()

        if not new_user.data:
            return jsonify({'success': False, 'error': 'فشل إنشاء الحساب'}), 500

        return jsonify({
            'success': True,
            'message': f"تم إنشاء حساب ولي الأمر لـ {student['name']}",
            'parent': {
                'id': new_user.data[0]['id'],
                'username': parent_username,
                'password': parent_password,
                'full_name': new_user.data[0].get('full_name'),
                'student_name': student['name'],
                'student_id': student_id
            }
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@auth_bp.route('/api/auth/parent/child', methods=['GET'])
@token_required
def get_parent_child():
    try:
        user_res = supabase.table('users').select('*').eq('id', request.user_id).execute()
        if not user_res.data:
            return jsonify({'success': False, 'error': 'المستخدم غير موجود'}), 404

        user = user_res.data[0]

        if user['role'] != 'parent':
            return jsonify({'success': False, 'error': 'هذا الحساب ليس ولي أمر'}), 403

        if not user.get('student_id'):
            return jsonify({'success': False, 'error': 'حسابك غير مربوط بطالب'}), 404

        student_res = supabase.table('students').select('*').eq('id', user['student_id']).execute()
        if not student_res.data:
            return jsonify({'success': False, 'error': 'الطالب غير موجود'}), 404

        student = student_res.data[0]

        attendance_res = supabase.table('attendance')\
            .select('*')\
            .eq('student_id', student['id'])\
            .order('date', desc=True)\
            .limit(30)\
            .execute()

        grades_res = supabase.table('grades')\
            .select('*')\
            .eq('student_id', student['id'])\
            .execute()

        return jsonify({
            'success': True,
            'student': student,
            'attendance': attendance_res.data or [],
            'grades': grades_res.data or []
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Assistant ============

@auth_bp.route('/api/auth/register-assistant', methods=['POST'])
@token_required
def register_assistant():
    try:
        data = request.get_json() or {}
        username = data.get('username')
        password = data.get('password')
        full_name = data.get('full_name', 'مساعد')
        phone = data.get('phone', '')

        if not username or not password:
            return jsonify({'success': False, 'error': 'username و password مطلوبين'}), 400

        existing = supabase.table('users').select('id').eq('username', username).execute()
        if existing.data:
            return jsonify({'success': False, 'error': 'username موجود بالفعل'}), 409

        tenant_id, _ = get_current_tenant_id()

        new_user = supabase.table('users').insert({
            'username': username,
            'password': hash_password(password),
            'role': 'assistant',
            'full_name': full_name,
            'phone': phone,
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم إنشاء حساب المساعد',
            'user': {
                'id': new_user.data[0]['id'],
                'username': new_user.data[0]['username'],
                'role': new_user.data[0]['role'],
                'full_name': new_user.data[0]['full_name'],
            }
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ TEMPORARY: Reset Password (للتطوير فقط) ============

@auth_bp.route('/api/auth/dev-reset-password', methods=['POST'])
def dev_reset_password():
    """⚠️ للتطوير فقط — نصفر الباسورد لأي مستخدم"""
    try:
        data = request.get_json() or {}
        username = data.get('username')
        new_password = data.get('new_password', 'admin123')

        if not username:
            return jsonify({'success': False, 'error': 'username مطلوب'}), 400

        user_res = supabase.table('users').select('id, username').eq('username', username).execute()
        if not user_res.data:
            return jsonify({'success': False, 'error': 'المستخدم غير موجود'}), 404

        user = user_res.data[0]

        supabase.table('users').update({
            'password': hash_password(new_password)
        }).eq('id', user['id']).execute()

        return jsonify({
            'success': True,
            'message': f'تم تحديث الباسورد لـ {username}',
            'username': username,
            'new_password': new_password
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500