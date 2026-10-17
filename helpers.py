# helpers.py - كل الـ helpers + JWT + Multi-tenancy
import os
import bcrypt
import jwt
from datetime import datetime, timedelta
from functools import wraps
from flask import jsonify, request

from db import supabase

SECRET_KEY = os.getenv('FLASK_SECRET_KEY')


# ============ Password Helpers ============

def hash_password(password: str) -> str:
    """تشفير كلمة السر"""
    salt = bcrypt.gensalt()
    hashed = bcrypt.hashpw(password.encode('utf-8'), salt)
    return hashed.decode('utf-8')


def verify_password(password: str, hashed: str) -> bool:
    """التحقق من كلمة السر"""
    try:
        return bcrypt.checkpw(password.encode('utf-8'), hashed.encode('utf-8'))
    except Exception:
        return False


# ============ JWT Helpers ============

def create_token(user_id: int, role: str) -> str:
    """توليد JWT Token"""
    payload = {
        'user_id': user_id,
        'role': role,
        'exp': datetime.utcnow() + timedelta(days=30)
    }
    return jwt.encode(payload, SECRET_KEY, algorithm='HS256')


def token_required(f):
    """Decorator للتحقق من الـ JWT Token"""
    @wraps(f)
    def decorated(*args, **kwargs):
        token = None
        auth_header = request.headers.get('Authorization')

        if auth_header and auth_header.startswith('Bearer '):
            token = auth_header.replace('Bearer ', '')

        if not token:
            return jsonify({'success': False, 'error': 'Token is missing'}), 401

        try:
            payload = jwt.decode(token, SECRET_KEY, algorithms=['HS256'])
            request.user_id = payload['user_id']
            request.user_role = payload['role']
        except jwt.ExpiredSignatureError:
            return jsonify({'success': False, 'error': 'Token expired'}), 401
        except jwt.InvalidTokenError:
            return jsonify({'success': False, 'error': 'Invalid token'}), 401

        return f(*args, **kwargs)
    return decorated


# ============ Multi-tenancy Helpers ============

def get_current_tenant_id():
    """
    يرجّع tenant_id بتاع المستخدم الحالي + دوره
    - super_admin → (None, 'super_admin')
    - باقي الأدوار → (tenant_id, role)
    """
    try:
        user_res = supabase.table('users')\
            .select('tenant_id, role')\
            .eq('id', request.user_id)\
            .execute()

        if not user_res.data:
            return None, None

        user = user_res.data[0]
        return user.get('tenant_id'), user.get('role')

    except Exception:
        return None, None


def apply_tenant_filter(query):
    """
    يضيف فلتر tenant_id على query
    - super_admin: يشوف كل حاجة (بدون فلتر)
    - باقي الأدوار: مفلتر على tenant_id بتاعه
    """
    tenant_id, role = get_current_tenant_id()

    # super_admin يشوف كل حاجة
    if role == 'super_admin':
        return query

    # باقي الأدوار: فلتر على سنتره
    if tenant_id:
        return query.eq('tenant_id', tenant_id)

    # لو مفيش tenant_id → نرجّع query فاضي
    return query.eq('tenant_id', -1)


def get_tenant_for_insert():
    """
    يرجّع tenant_id اللي المفروض يتحفظ مع البيانات الجديدة
    """
    tenant_id, role = get_current_tenant_id()
    return tenant_id


def is_super_admin():
    """هل المستخدم الحالي super_admin؟"""
    _, role = get_current_tenant_id()
    return role == 'super_admin'


def check_tenant_ownership(record: dict) -> bool:
    """
    يتحقق إن السجل بتاع نفس سنتر المستخدم
    - super_admin → True دايماً
    - باقي الأدوار → يقارن tenant_id
    """
    if is_super_admin():
        return True

    tenant_id, _ = get_current_tenant_id()
    if not tenant_id:
        return False

    return record.get('tenant_id') == tenant_id