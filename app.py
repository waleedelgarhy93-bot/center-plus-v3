# app.py - Center Plus SaaS Backend
import os
import bcrypt
import jwt
from datetime import datetime, timedelta
from dotenv import load_dotenv
from flask import Flask, jsonify, request
from supabase import create_client, Client

load_dotenv()

app = Flask(__name__)
app.config['SECRET_KEY'] = os.getenv('FLASK_SECRET_KEY')

SUPABASE_URL = os.getenv('SUPABASE_URL')
SUPABASE_KEY = os.getenv('SUPABASE_SECRET_KEY')

supabase = create_client(SUPABASE_URL, SUPABASE_KEY)

# ============ Helpers ============

def hash_password(password):
    """تشفير كلمة السر"""
    salt = bcrypt.gensalt()
    hashed = bcrypt.hashpw(password.encode('utf-8'), salt)
    return hashed.decode('utf-8')

def verify_password(password, hashed):
    """التحقق من كلمة السر"""
    return bcrypt.checkpw(password.encode('utf-8'), hashed.encode('utf-8'))

def create_token(user_id, role):
    """توليد JWT Token"""
    payload = {
        'user_id': user_id,
        'role': role,
        'exp': datetime.utcnow() + timedelta(days=30)
    }
    token = jwt.encode(payload, app.config['SECRET_KEY'], algorithm='HS256')
    return token

from functools import wraps

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
            payload = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
            request.user_id = payload['user_id']
            request.user_role = payload['role']
        except jwt.ExpiredSignatureError:
            return jsonify({'success': False, 'error': 'Token expired'}), 401
        except jwt.InvalidTokenError:
            return jsonify({'success': False, 'error': 'Invalid token'}), 401
        
        return f(*args, **kwargs)
    
    return decorated

# ============ Routes ============

@app.route('/')
def home():
    return jsonify({
        'app': 'Center Plus',
        'version': '1.0.0',
        'status': 'running'
    })

@app.route('/api/health')
def health():
    return jsonify({
        'status': 'healthy',
        'supabase_url': SUPABASE_URL
    })

@app.route('/api/test')
def test():
    return jsonify({'message': 'API is working!'})

@app.route('/api/students')
@token_required
def get_students():
    try:
        response = supabase.table('students').select('*').execute()
        return jsonify({
            'success': True,
            'count': len(response.data),
            'students': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/students/<int:student_id>', methods=['GET'])
@token_required
def get_student(student_id):
    """جلب طالب واحد"""
    try:
        response = supabase.table('students').select('*').eq('id', student_id).execute()
        if not response.data:
            return jsonify({'success': False, 'error': 'Student not found'}), 404
        return jsonify({'success': True, 'student': response.data[0]})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/students', methods=['POST'])
@token_required
def create_student():
    """إضافة طالب جديد"""
    try:
        data = request.get_json()
        
        name = data.get('name')
        if not name:
            return jsonify({'success': False, 'error': 'name is required'}), 400
        
        new_student = supabase.table('students').insert({
            'name': name,
            'barcode': data.get('barcode'),
            'phone': data.get('phone'),
            'parent_phone': data.get('parent_phone'),
            'type': data.get('type', 'center')
        }).execute()
        
        return jsonify({
            'success': True,
            'message': 'Student created successfully',
            'student': new_student.data[0] if new_student.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/students/<int:student_id>', methods=['PUT'])
@token_required
def update_student(student_id):
    """تعديل طالب"""
    try:
        data = request.get_json()
        
        update_data = {}
        for field in ['name', 'barcode', 'phone', 'parent_phone', 'type']:
            if field in data:
                update_data[field] = data[field]
        
        if not update_data:
            return jsonify({'success': False, 'error': 'No fields to update'}), 400
        
        result = supabase.table('students').update(update_data).eq('id', student_id).execute()
        
        if not result.data:
            return jsonify({'success': False, 'error': 'Student not found'}), 404
        
        return jsonify({
            'success': True,
            'message': 'Student updated successfully',
            'student': result.data[0]
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/students/<int:student_id>', methods=['DELETE'])
@token_required
def delete_student(student_id):
    """حذف طالب"""
    try:
        result = supabase.table('students').delete().eq('id', student_id).execute()
        
        if not result.data:
            return jsonify({'success': False, 'error': 'Student not found'}), 404
        
        return jsonify({'success': True, 'message': 'Student deleted successfully'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@app.route('/api/users')
def get_users():
    try:
        response = supabase.table('users').select('*').execute()
        return jsonify({
            'success': True,
            'count': len(response.data),
            'users': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

# ============ Auth ============

@app.route('/api/auth/register', methods=['POST'])
def register():
    """تسجيل مستخدم جديد"""
    try:
        data = request.get_json()
        
        username = data.get('username')
        password = data.get('password')
        role = data.get('role', 'student')
        full_name = data.get('full_name', '')
        phone = data.get('phone', '')
        
        if not username or not password:
            return jsonify({
                'success': False,
                'error': 'username and password are required'
            }), 400
        
        # التحقق إن المستخدم مش موجود
        existing = supabase.table('users').select('*').eq('username', username).execute()
        if existing.data:
            return jsonify({
                'success': False,
                'error': 'username already exists'
            }), 400
        
        # تشفير كلمة السر
        hashed_pw = hash_password(password)
        
        # إضافة المستخدم
        new_user = supabase.table('users').insert({
            'username': username,
            'password': hashed_pw,
            'role': role,
            'full_name': full_name,
            'phone': phone
        }).execute()
        
        return jsonify({
            'success': True,
            'message': 'User registered successfully',
            'user': new_user.data[0] if new_user.data else None
        }), 201
        
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/auth/login', methods=['POST'])
def login():
    """تسجيل دخول"""
    try:
        data = request.get_json()
        
        username = data.get('username')
        password = data.get('password')
        
        if not username or not password:
            return jsonify({
                'success': False,
                'error': 'username and password are required'
            }), 400
        
        # البحث عن المستخدم
        response = supabase.table('users').select('*').eq('username', username).execute()
        
        if not response.data:
            return jsonify({
                'success': False,
                'error': 'Invalid credentials'
            }), 401
        
        user = response.data[0]
        
        # التحقق من كلمة السر
        if not verify_password(password, user['password']):
            return jsonify({
                'success': False,
                'error': 'Invalid credentials'
            }), 401
        
        # توليد Token
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
                'phone': user.get('phone')
            }
        })
        
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/auth/me')
def me():
    """معلومات المستخدم الحالي"""
    try:
        auth_header = request.headers.get('Authorization')
        
        if not auth_header or not auth_header.startswith('Bearer '):
            return jsonify({
                'success': False,
                'error': 'No token provided'
            }), 401
        
        token = auth_header.replace('Bearer ', '')
        
        try:
            payload = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
        except jwt.ExpiredSignatureError:
            return jsonify({'success': False, 'error': 'Token expired'}), 401
        except jwt.InvalidTokenError:
            return jsonify({'success': False, 'error': 'Invalid token'}), 401
        
        user_id = payload['user_id']
        
        response = supabase.table('users').select('*').eq('id', user_id).execute()
        
        if not response.data:
            return jsonify({'success': False, 'error': 'User not found'}), 404
        
        user = response.data[0]
        
        return jsonify({
            'success': True,
            'user': {
                'id': user['id'],
                'username': user['username'],
                'role': user['role'],
                'full_name': user.get('full_name'),
                'phone': user.get('phone')
            }
        })
        
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

# ============ Subjects CRUD ============

@app.route('/api/subjects', methods=['GET'])
@token_required
def get_subjects():
    """جلب كل المواد"""
    try:
        response = supabase.table('subjects').select('*').execute()
        return jsonify({
            'success': True,
            'count': len(response.data),
            'subjects': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/subjects/<int:subject_id>', methods=['GET'])
@token_required
def get_subject(subject_id):
    """جلب مادة واحدة"""
    try:
        response = supabase.table('subjects').select('*').eq('id', subject_id).execute()
        if not response.data:
            return jsonify({'success': False, 'error': 'Subject not found'}), 404
        return jsonify({'success': True, 'subject': response.data[0]})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/subjects', methods=['POST'])
@token_required
def create_subject():
    """إضافة مادة جديدة"""
    try:
        data = request.get_json()
        
        name = data.get('name')
        if not name:
            return jsonify({'success': False, 'error': 'name is required'}), 400
        
        new_subject = supabase.table('subjects').insert({
            'name': name,
            'description': data.get('description'),
            'color': data.get('color', 'blue'),
            'icon': data.get('icon')
        }).execute()
        
        return jsonify({
            'success': True,
            'message': 'Subject created successfully',
            'subject': new_subject.data[0] if new_subject.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/subjects/<int:subject_id>', methods=['PUT'])
@token_required
def update_subject(subject_id):
    """تعديل مادة"""
    try:
        data = request.get_json()
        
        update_data = {}
        for field in ['name', 'description', 'color', 'icon']:
            if field in data:
                update_data[field] = data[field]
        
        if not update_data:
            return jsonify({'success': False, 'error': 'No fields to update'}), 400
        
        result = supabase.table('subjects').update(update_data).eq('id', subject_id).execute()
        
        if not result.data:
            return jsonify({'success': False, 'error': 'Subject not found'}), 404
        
        return jsonify({
            'success': True,
            'message': 'Subject updated successfully',
            'subject': result.data[0]
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/subjects/<int:subject_id>', methods=['DELETE'])
@token_required
def delete_subject(subject_id):
    """حذف مادة"""
    try:
        result = supabase.table('subjects').delete().eq('id', subject_id).execute()
        
        if not result.data:
            return jsonify({'success': False, 'error': 'Subject not found'}), 404
        
        return jsonify({'success': True, 'message': 'Subject deleted successfully'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

# ============ Groups CRUD ============

@app.route('/api/groups', methods=['GET'])
@token_required
def get_groups():
    """جلب كل المجموعات"""
    try:
        response = supabase.table('groups').select('*').execute()
        return jsonify({
            'success': True,
            'count': len(response.data),
            'groups': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/groups/<int:group_id>', methods=['GET'])
@token_required
def get_group(group_id):
    """جلب مجموعة واحدة"""
    try:
        response = supabase.table('groups').select('*').eq('id', group_id).execute()
        if not response.data:
            return jsonify({'success': False, 'error': 'Group not found'}), 404
        return jsonify({'success': True, 'group': response.data[0]})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/groups', methods=['POST'])
@token_required
def create_group():
    """إضافة مجموعة جديدة"""
    try:
        data = request.get_json()
        
        grade = data.get('grade')
        center = data.get('center')
        subject_id = data.get('subject_id')
        
        if not grade or not center or not subject_id:
            return jsonify({
                'success': False,
                'error': 'grade, center, and subject_id are required'
            }), 400
        
        new_group = supabase.table('groups').insert({
            'grade': grade,
            'center': center,
            'subject_id': subject_id,
            'days': data.get('days'),
            'time': data.get('time'),
            'price': data.get('price')
        }).execute()
        
        return jsonify({
            'success': True,
            'message': 'Group created successfully',
            'group': new_group.data[0] if new_group.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/groups/<int:group_id>', methods=['PUT'])
@token_required
def update_group(group_id):
    """تعديل مجموعة"""
    try:
        data = request.get_json()
        
        update_data = {}
        for field in ['grade', 'center', 'subject_id', 'days', 'time', 'price']:
            if field in data:
                update_data[field] = data[field]
        
        if not update_data:
            return jsonify({'success': False, 'error': 'No fields to update'}), 400
        
        result = supabase.table('groups').update(update_data).eq('id', group_id).execute()
        
        if not result.data:
            return jsonify({'success': False, 'error': 'Group not found'}), 404
        
        return jsonify({
            'success': True,
            'message': 'Group updated successfully',
            'group': result.data[0]
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/groups/<int:group_id>', methods=['DELETE'])
@token_required
def delete_group(group_id):
    """حذف مجموعة"""
    try:
        result = supabase.table('groups').delete().eq('id', group_id).execute()
        
        if not result.data:
            return jsonify({'success': False, 'error': 'Group not found'}), 404
        
        return jsonify({'success': True, 'message': 'Group deleted successfully'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

# ============ Attendance CRUD ============

@app.route('/api/attendance', methods=['GET'])
@token_required
def get_attendance():
    """جلب كل الحضور"""
    try:
        response = supabase.table('attendance').select('*').execute()
        return jsonify({
            'success': True,
            'count': len(response.data),
            'attendance': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/attendance/student/<int:student_id>', methods=['GET'])
@token_required
def get_student_attendance(student_id):
    """جلب حضور طالب معين"""
    try:
        response = supabase.table('attendance').select('*').eq('student_id', student_id).execute()
        return jsonify({
            'success': True,
            'count': len(response.data),
            'attendance': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/attendance', methods=['POST'])
@token_required
def create_attendance():
    """تسجيل حضور"""
    try:
        data = request.get_json()
        
        student_id = data.get('student_id')
        date = data.get('date')
        
        if not student_id or not date:
            return jsonify({
                'success': False,
                'error': 'student_id and date are required'
            }), 400
        
        new_attendance = supabase.table('attendance').insert({
            'student_id': student_id,
            'group_id': data.get('group_id'),
            'date': date,
            'time': data.get('time'),
            'status': data.get('status', 'present')
        }).execute()
        
        return jsonify({
            'success': True,
            'message': 'Attendance recorded successfully',
            'attendance': new_attendance.data[0] if new_attendance.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/attendance/<int:attendance_id>', methods=['PUT'])
@token_required
def update_attendance(attendance_id):
    """تعديل حضور"""
    try:
        data = request.get_json()
        
        update_data = {}
        for field in ['student_id', 'group_id', 'date', 'time', 'status']:
            if field in data:
                update_data[field] = data[field]
        
        if not update_data:
            return jsonify({'success': False, 'error': 'No fields to update'}), 400
        
        result = supabase.table('attendance').update(update_data).eq('id', attendance_id).execute()
        
        if not result.data:
            return jsonify({'success': False, 'error': 'Attendance not found'}), 404
        
        return jsonify({
            'success': True,
            'message': 'Attendance updated successfully',
            'attendance': result.data[0]
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/attendance/<int:attendance_id>', methods=['DELETE'])
@token_required
def delete_attendance(attendance_id):
    """حذف حضور"""
    try:
        result = supabase.table('attendance').delete().eq('id', attendance_id).execute()
        
        if not result.data:
            return jsonify({'success': False, 'error': 'Attendance not found'}), 404
        
        return jsonify({'success': True, 'message': 'Attendance deleted successfully'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

# ============ Exams CRUD ============

@app.route('/api/exams', methods=['GET'])
@token_required
def get_exams():
    """جلب كل الاختبارات"""
    try:
        response = supabase.table('exams').select('*').execute()
        return jsonify({
            'success': True,
            'count': len(response.data),
            'exams': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/exams/<int:exam_id>', methods=['GET'])
@token_required
def get_exam(exam_id):
    """جلب اختبار واحد"""
    try:
        response = supabase.table('exams').select('*').eq('id', exam_id).execute()
        if not response.data:
            return jsonify({'success': False, 'error': 'Exam not found'}), 404
        return jsonify({'success': True, 'exam': response.data[0]})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/exams', methods=['POST'])
@token_required
def create_exam():
    """إضافة اختبار جديد"""
    try:
        data = request.get_json()
        
        title = data.get('title')
        subject_id = data.get('subject_id')
        max_grade = data.get('max_grade')
        
        if not title or not subject_id or max_grade is None:
            return jsonify({
                'success': False,
                'error': 'title, subject_id, and max_grade are required'
            }), 400
        
        new_exam = supabase.table('exams').insert({
            'title': title,
            'subject_id': subject_id,
            'date': data.get('date'),
            'max_grade': max_grade,
            'notes': data.get('notes')
        }).execute()
        
        return jsonify({
            'success': True,
            'message': 'Exam created successfully',
            'exam': new_exam.data[0] if new_exam.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/exams/<int:exam_id>', methods=['PUT'])
@token_required
def update_exam(exam_id):
    """تعديل اختبار"""
    try:
        data = request.get_json()
        
        update_data = {}
        for field in ['title', 'subject_id', 'date', 'max_grade', 'notes']:
            if field in data:
                update_data[field] = data[field]
        
        if not update_data:
            return jsonify({'success': False, 'error': 'No fields to update'}), 400
        
        result = supabase.table('exams').update(update_data).eq('id', exam_id).execute()
        
        if not result.data:
            return jsonify({'success': False, 'error': 'Exam not found'}), 404
        
        return jsonify({
            'success': True,
            'message': 'Exam updated successfully',
            'exam': result.data[0]
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/exams/<int:exam_id>', methods=['DELETE'])
@token_required
def delete_exam(exam_id):
    """حذف اختبار"""
    try:
        result = supabase.table('exams').delete().eq('id', exam_id).execute()
        
        if not result.data:
            return jsonify({'success': False, 'error': 'Exam not found'}), 404
        
        return jsonify({'success': True, 'message': 'Exam deleted successfully'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

# ============ Grades CRUD ============

@app.route('/api/grades', methods=['GET'])
@token_required
def get_grades():
    """جلب كل الدرجات"""
    try:
        response = supabase.table('grades').select('*').execute()
        return jsonify({
            'success': True,
            'count': len(response.data),
            'grades': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/grades/student/<int:student_id>', methods=['GET'])
@token_required
def get_student_grades(student_id):
    """جلب درجات طالب معين"""
    try:
        response = supabase.table('grades').select('*').eq('student_id', student_id).execute()
        return jsonify({
            'success': True,
            'count': len(response.data),
            'grades': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/grades/exam/<int:exam_id>', methods=['GET'])
@token_required
def get_exam_grades(exam_id):
    """جلب درجات اختبار معين"""
    try:
        response = supabase.table('grades').select('*').eq('exam_id', exam_id).execute()
        return jsonify({
            'success': True,
            'count': len(response.data),
            'grades': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/grades', methods=['POST'])
@token_required
def create_grade():
    """إضافة درجة جديدة"""
    try:
        data = request.get_json()
        
        student_id = data.get('student_id')
        exam_id = data.get('exam_id')
        grade = data.get('grade')
        
        if not student_id or not exam_id or grade is None:
            return jsonify({
                'success': False,
                'error': 'student_id, exam_id, and grade are required'
            }), 400
        
        new_grade = supabase.table('grades').insert({
            'student_id': student_id,
            'exam_id': exam_id,
            'grade': grade
        }).execute()
        
        return jsonify({
            'success': True,
            'message': 'Grade created successfully',
            'grade': new_grade.data[0] if new_grade.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/grades/<int:grade_id>', methods=['PUT'])
@token_required
def update_grade(grade_id):
    """تعديل درجة"""
    try:
        data = request.get_json()
        
        update_data = {}
        for field in ['student_id', 'exam_id', 'grade']:
            if field in data:
                update_data[field] = data[field]
        
        if not update_data:
            return jsonify({'success': False, 'error': 'No fields to update'}), 400
        
        result = supabase.table('grades').update(update_data).eq('id', grade_id).execute()
        
        if not result.data:
            return jsonify({'success': False, 'error': 'Grade not found'}), 404
        
        return jsonify({
            'success': True,
            'message': 'Grade updated successfully',
            'grade': result.data[0]
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/grades/<int:grade_id>', methods=['DELETE'])
@token_required
def delete_grade(grade_id):
    """حذف درجة"""
    try:
        result = supabase.table('grades').delete().eq('id', grade_id).execute()
        
        if not result.data:
            return jsonify({'success': False, 'error': 'Grade not found'}), 404
        
        return jsonify({'success': True, 'message': 'Grade deleted successfully'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

# ============ Payments CRUD ============

@app.route('/api/payments', methods=['GET'])
@token_required
def get_payments():
    """جلب كل المدفوعات"""
    try:
        response = supabase.table('payments').select('*').execute()
        return jsonify({
            'success': True,
            'count': len(response.data),
            'payments': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/payments/student/<int:student_id>', methods=['GET'])
@token_required
def get_student_payments(student_id):
    """جلب مدفوعات طالب معين"""
    try:
        response = supabase.table('payments').select('*').eq('student_id', student_id).execute()
        return jsonify({
            'success': True,
            'count': len(response.data),
            'payments': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/payments', methods=['POST'])
@token_required
def create_payment():
    """إضافة دفعة جديدة"""
    try:
        data = request.get_json()
        
        student_id = data.get('student_id')
        subject_id = data.get('subject_id')
        amount = data.get('amount')
        month = data.get('month')
        
        if not student_id or not subject_id or amount is None or not month:
            return jsonify({
                'success': False,
                'error': 'student_id, subject_id, amount, and month are required'
            }), 400
        
        new_payment = supabase.table('payments').insert({
            'student_id': student_id,
            'subject_id': subject_id,
            'amount': amount,
            'month': month,
            'status': data.get('status', 'unpaid'),
            'paid_at': data.get('paid_at')
        }).execute()
        
        return jsonify({
            'success': True,
            'message': 'Payment created successfully',
            'payment': new_payment.data[0] if new_payment.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/payments/<int:payment_id>', methods=['PUT'])
@token_required
def update_payment(payment_id):
    """تعديل دفعة"""
    try:
        data = request.get_json()
        
        update_data = {}
        for field in ['student_id', 'subject_id', 'amount', 'month', 'status', 'paid_at']:
            if field in data:
                update_data[field] = data[field]
        
        if not update_data:
            return jsonify({'success': False, 'error': 'No fields to update'}), 400
        
        result = supabase.table('payments').update(update_data).eq('id', payment_id).execute()
        
        if not result.data:
            return jsonify({'success': False, 'error': 'Payment not found'}), 404
        
        return jsonify({
            'success': True,
            'message': 'Payment updated successfully',
            'payment': result.data[0]
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/payments/<int:payment_id>', methods=['DELETE'])
@token_required
def delete_payment(payment_id):
    """حذف دفعة"""
    try:
        result = supabase.table('payments').delete().eq('id', payment_id).execute()
        
        if not result.data:
            return jsonify({'success': False, 'error': 'Payment not found'}), 404
        
        return jsonify({'success': True, 'message': 'Payment deleted successfully'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500



if __name__ == '__main__':
    port = int(os.getenv('PORT', 5000))
    print("=" * 50)
    print("Center Plus API")
    print("Server: http://localhost:" + str(port))
    print("=" * 50)
    app.run(host='0.0.0.0', port=port, debug=True)