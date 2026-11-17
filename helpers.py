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


# ============ Student Content Visibility ============
# الطالب يشوف المحتوى (فيديو/امتحان/واجب/مذكرة) بس لو:
#   1) من نفس السنتر، و
#   2) المحتوى متوجّه لمجموعة من مجموعاته (group_id أو جدول الربط)، أو
#   3) المحتوى "عام" (من غير مجموعات) وهو في مادة الطالب مشترك فيها بمجموعة من نفس الصف.

CONTENT_LINKS = {
    'videos': ('video_groups', 'video_id'),
    'exams': ('exam_groups', 'exam_id'),
    'documents': ('document_groups', 'document_id'),
}


def get_student_access(student_id, only_group_id=None):
    """بيانات اشتراك الطالب: السنتر + المجموعات + المواد + الصفوف
    only_group_id: لو الطالب فاتح مادة من مجموعة معينة، نحصر المحتوى في المجموعة دي بس"""
    st = supabase.table('students').select('id, tenant_id, group_id').eq('id', student_id).execute()
    if not st.data:
        return None
    student = st.data[0]
    sg = supabase.table('student_groups').select('group_id').eq('student_id', student_id).execute()
    group_ids = {r['group_id'] for r in (sg.data or []) if r.get('group_id')}
    if student.get('group_id'):
        group_ids.add(student['group_id'])
    if only_group_id and only_group_id in group_ids:
        group_ids = {only_group_id}
    groups = []
    if group_ids:
        groups = supabase.table('groups').select('id, subject_id, grade').in_('id', list(group_ids)).execute().data or []
    return {
        'student_id': student_id,
        'tenant_id': student.get('tenant_id'),
        'group_ids': group_ids,
        'subject_ids': {g['subject_id'] for g in groups if g.get('subject_id')},
        'grades': {g['grade'] for g in groups if g.get('grade')},
    }


def _content_group_map(table, item_ids):
    link_table, fk = CONTENT_LINKS[table]
    if not item_ids:
        return {}
    rows = supabase.table(link_table).select(f'{fk}, group_id').in_(fk, list(item_ids)).execute().data or []
    m = {}
    for r in rows:
        m.setdefault(r[fk], set()).add(r['group_id'])
    return m


def _student_sees(item, linked_groups, access):
    if not access or item.get('tenant_id') != access['tenant_id']:
        return False
    target = set(linked_groups or set())
    if item.get('group_id'):
        target.add(item['group_id'])
    if target:
        return bool(target & access['group_ids'])
    # محتوى عام: لازم الطالب يكون مشترك في نفس المادة
    subject_id = item.get('subject_id')
    return subject_id is None or subject_id in access['subject_ids']


def filter_content_for_student(table, items, access):
    """يرجّع بس العناصر اللي الطالب مسموح له يشوفها"""
    gm = _content_group_map(table, [i['id'] for i in items])
    return [i for i in items if _student_sees(i, gm.get(i['id']), access)]


def student_can_access_content(table, item, student_id=None):
    """فحص عنصر واحد (فيديو/امتحان/مذكرة) لطالب"""
    access = get_student_access(student_id or request.user_id)
    gm = _content_group_map(table, [item['id']])
    return _student_sees(item, gm.get(item['id']), access)


# ============ Grade normalization ============
# أسماء الصفوف مكتوبة بأكتر من شكل ("أولى ثانوي" / "الأول الثانوي" / "الأول الثانوي (الصف العاشر)")

_STAGES = (('ثانوي', 'sec'), ('اعدادي', 'prep'), ('إعدادي', 'prep'), ('ابتدائي', 'pri'))
_LEVELS = (('أول', 1), ('اول', 1), ('أولى', 1), ('اولى', 1), ('ثاني', 2), ('تاني', 2), ('تانية', 2), ('ثانية', 2),
           ('ثالث', 3), ('تالت', 3), ('تالتة', 3), ('ثالثة', 3), ('رابع', 4), ('خامس', 5), ('سادس', 6),
           ('1', 1), ('2', 2), ('3', 3), ('١', 1), ('٢', 2), ('٣', 3))


def normalize_grade(grade):
    """يرجّع (المرحلة, السنة) أو None"""
    if not grade:
        return None
    g = str(grade).split('(')[0]
    stage = next((code for word, code in _STAGES if word in g), None)
    level = None
    for w in g.split():
        w = w[2:] if w.startswith('ال') else w
        if any(w.startswith(st) for st, _ in _STAGES):
            continue
        level = next((num for word, num in _LEVELS if w.startswith(word)), None)
        if level:
            break
    return (stage, level) if stage and level else None


def student_matches_grade_subject(record, access):
    """للفروع/التصنيفات: نفس مادة الطالب، ونفس صفه (لو الصف متحدد)"""
    if not access:
        return False
    if record.get('subject_id') and record['subject_id'] not in access['subject_ids']:
        return False
    if record.get('grade'):
        student_grades = {normalize_grade(g) for g in access['grades']} - {None}
        rg = normalize_grade(record['grade'])
        if rg is None:
            return record['grade'] in access['grades']
        return rg in student_grades
    return True

