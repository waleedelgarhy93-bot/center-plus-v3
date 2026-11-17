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
    """Decorator للتحقق من الـ JWT Token مع استثناء طلبات OPTIONS"""
    @wraps(f)
    def decorated(*args, **kwargs):
        # ⚠️ الحل السحري: السماح لطلبات Preflight بالمرور فوراً بحالة 200 OK
        if request.method == 'OPTIONS':
            return '', 200

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

        if not _role_allowed(request.user_role):
            return jsonify({'success': False, 'error': 'غير مصرح لهذا الدور'}), 403

        return f(*args, **kwargs)
    return decorated


# ============ Role Policy ============
# المدرس والسوبر أدمن: كل الـ endpoints (والـ admin routes ليها فحص إضافي خاص بيها).
# باقي الأدوار: بس الـ endpoints اللي صفحاتهم في الفرونت إند بتستخدمها فعلاً.
# لو أضفت صفحة جديدة لطالب/ولي أمر/مساعد، ضيف الـ endpoint بتاعها هنا.

FULL_ACCESS_ROLES = {'teacher', 'super_admin', 'superadmin'}

COMMON_ENDPOINTS = {
    ('POST', '/api/auth/logout'),
    ('GET', '/api/auth/me'),
    ('GET', '/api/honor/leaderboard'),
    ('GET', '/api/honor/notifications'),
    ('POST', '/api/honor/notifications/mark-read'),
}

ROLE_ENDPOINTS = {
    'student': {
        ('GET', '/api/attendance'),
        ('GET', '/api/grades'),
        ('GET', '/api/groups'),
        ('GET', '/api/subjects'),
        ('GET', '/api/subjects/<int:subject_id>'),
        ('GET', '/api/branches'),
        ('GET', '/api/categories'),
        ('GET', '/api/documents/<int:doc_id>'),
        ('GET', '/api/student/documents'),
        ('GET', '/api/student/exams'),
        ('GET', '/api/student/my-subjects'),
        ('GET', '/api/student/videos'),
        ('GET', '/api/exams/<int:exam_id>/take'),
        ('POST', '/api/exams/<int:exam_id>/submit'),
        ('GET', '/api/exams/<int:exam_id>/receipt'),
        ('GET', '/api/exams/<int:exam_id>/my-result'),
        ('POST', '/api/videos/<int:video_id>/view'),
        ('POST', '/api/videos/<int:video_id>/unlock'),
        ('POST', '/api/upload/image'),
        ('GET', '/api/honor/my-rank'),
        ('GET', '/api/live/session'),
        ('GET', '/api/live/messages'),
        ('POST', '/api/live/messages'),
        ('POST', '/api/live/attendance'),
        ('GET', '/api/live/quiz'),
        ('POST', '/api/live/quiz/answer'),
    },
    'parent': {
        ('GET', '/api/auth/parent/child'),
        ('GET', '/api/honor/my-rank'),
    },
    'assistant': {
        ('GET', '/api/students'),
        ('GET', '/api/students/activity-tracker'),
        ('GET', '/api/groups'),
        ('GET', '/api/groups/<int:group_id>/students'),
        ('GET', '/api/subjects'),
        ('GET', '/api/attendance/group/<int:group_id>/today'),
        ('POST', '/api/attendance/check-in'),
        ('POST', '/api/honor/points/add'),
        ('GET', '/api/live/session'),
        ('GET', '/api/live/messages'),
        ('POST', '/api/live/messages'),
        ('POST', '/api/live/attendance'),
        ('GET', '/api/live/quiz'),
        ('POST', '/api/live/quiz/answer'),
    },
}


def _role_allowed(role) -> bool:
    if role in FULL_ACCESS_ROLES:
        return True
    rule = request.url_rule.rule if request.url_rule else None
    key = (request.method, rule)
    return key in COMMON_ENDPOINTS or key in ROLE_ENDPOINTS.get(role, set())


# ============ Multi-tenancy Helpers ============

def is_student_token() -> bool:
    """توكن الطالب فيه id من جدول students مش users — لازم ما يتدورش عليه في users"""
    return getattr(request, 'user_role', None) == 'student'


def get_current_tenant_id():
    try:
        if is_student_token():
            student_res = supabase.table('students')\
                .select('tenant_id')\
                .eq('id', request.user_id)\
                .execute()
            if not student_res.data:
                return None, None
            return student_res.data[0].get('tenant_id'), 'student'

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
    tenant_id, role = get_current_tenant_id()

    if role == 'super_admin':
        return query

    if tenant_id:
        return query.eq('tenant_id', tenant_id)

    return query.eq('tenant_id', -1)


def get_tenant_for_insert():
    tenant_id, role = get_current_tenant_id()
    return tenant_id


def is_super_admin():
    _, role = get_current_tenant_id()
    return role in ('super_admin', 'superadmin')


def get_staff_user():
    """يرجّع صف المستخدم (users) لصاحب التوكن، أو None لو التوكن لطالب أو المستخدم مش موجود"""
    if is_student_token():
        return None
    try:
        res = supabase.table('users').select('*').eq('id', request.user_id).execute()
        return res.data[0] if res.data else None
    except Exception:
        return None


def check_tenant_ownership(record: dict) -> bool:
    if is_super_admin():
        return True

    tenant_id, _ = get_current_tenant_id()
    if not tenant_id:
        return False

    return record.get('tenant_id') == tenant_id
