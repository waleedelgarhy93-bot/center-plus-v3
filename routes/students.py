# routes/students.py - CRUD الطلاب + Multi-tenancy
from flask import Blueprint, jsonify, request

from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    get_current_tenant_id,
    check_tenant_ownership,
)

students_bp = Blueprint('students', __name__)


# ============ Get All ============

@students_bp.route('/api/students', methods=['GET'])
@token_required
def get_students():
    """جلب كل الطلاب (مفلتر حسب السنتر)"""
    try:
        query = supabase.table('students').select('*')
        query = apply_tenant_filter(query)  # ← Multi-tenancy
        response = query.execute()

        return jsonify({
            'success': True,
            'count': len(response.data),
            'students': response.data
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Get One ============

@students_bp.route('/api/students/<int:student_id>', methods=['GET'])
@token_required
def get_student(student_id):
    """جلب طالب واحد"""
    try:
        response = supabase.table('students')\
            .select('*')\
            .eq('id', student_id)\
            .execute()

        if not response.data:
            return jsonify({'success': False, 'error': 'Student not found'}), 404

        student = response.data[0]

        # تحقق من ملكية السنتر
        if not check_tenant_ownership(student):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        return jsonify({'success': True, 'student': student})

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Create ============

@students_bp.route('/api/students', methods=['POST'])
@token_required
def create_student():
    """إضافة طالب جديد"""
    try:
        data = request.get_json() or {}

        name = data.get('name')
        if not name:
            return jsonify({'success': False, 'error': 'name مطلوب'}), 400

        tenant_id = get_tenant_for_insert()

        new_student = supabase.table('students').insert({
            'name': name,
            'barcode': data.get('barcode'),
            'phone': data.get('phone'),
            'parent_phone': data.get('parent_phone'),
            'type': data.get('type', 'center'),
            'group_id': data.get('group_id'),
            'tenant_id': tenant_id,  # ← Multi-tenancy
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم إنشاء الطالب',
            'student': new_student.data[0] if new_student.data else None
        }), 201

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Update ============

@students_bp.route('/api/students/<int:student_id>', methods=['PUT'])
@token_required
def update_student(student_id):
    """تعديل طالب"""
    try:
        # تحقق من الملكية
        existing = supabase.table('students')\
            .select('tenant_id')\
            .eq('id', student_id)\
            .execute()

        if not existing.data:
            return jsonify({'success': False, 'error': 'Student not found'}), 404

        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}

        update_data = {}
        for field in ['name', 'barcode', 'phone', 'parent_phone', 'type', 'group_id']:
            if field in data:
                update_data[field] = data[field]

        if not update_data:
            return jsonify({'success': False, 'error': 'لا يوجد بيانات للتعديل'}), 400

        result = supabase.table('students')\
            .update(update_data)\
            .eq('id', student_id)\
            .execute()

        return jsonify({
            'success': True,
            'message': 'تم التعديل',
            'student': result.data[0] if result.data else None
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Delete ============

@students_bp.route('/api/students/<int:student_id>', methods=['DELETE'])
@token_required
def delete_student(student_id):
    """حذف طالب"""
    try:
        existing = supabase.table('students')\
            .select('tenant_id')\
            .eq('id', student_id)\
            .execute()

        if not existing.data:
            return jsonify({'success': False, 'error': 'Student not found'}), 404

        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('students').delete().eq('id', student_id).execute()

        return jsonify({'success': True, 'message': 'تم الحذف'})

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Get by Barcode ============

@students_bp.route('/api/students/barcode/<string:barcode>', methods=['GET'])
@token_required
def get_student_by_barcode(barcode):
    """جلب طالب بالباركود"""
    try:
        response = supabase.table('students')\
            .select('*')\
            .eq('barcode', barcode)\
            .execute()

        if not response.data:
            return jsonify({'success': False, 'error': 'لا يوجد طالب بهذا الكارت'}), 404

        student = response.data[0]

        if not check_tenant_ownership(student):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        return jsonify({'success': True, 'student': student})

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Create Parent Account ============

@students_bp.route('/api/students/<int:student_id>/create-parent', methods=['POST'])
@token_required
def create_parent_account(student_id):
    """إنشاء حساب ولي أمر لطالب"""
    try:
        import random
        import string

        student_res = supabase.table('students')\
            .select('*')\
            .eq('id', student_id)\
            .execute()

        if not student_res.data:
            return jsonify({'success': False, 'error': 'الطالب غير موجود'}), 404

        student = student_res.data[0]

        if not check_tenant_ownership(student):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

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

        from helpers import hash_password

        parent_username = None
        for _ in range(30):
            candidate = 'par_' + ''.join(random.choices(string.digits, k=6))
            check = supabase.table('users').select('id').eq('username', candidate).execute()
            if not check.data:
                parent_username = candidate
                break

        if not parent_username:
            return jsonify({'success': False, 'error': 'فشل توليد username'}), 500

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

        return jsonify({
            'success': True,
            'message': f"تم إنشاء حساب ولي الأمر لـ {student['name']}",
            'parent': {
                'id': new_user.data[0]['id'],
                'username': parent_username,
                'password': parent_password,
                'full_name': new_user.data[0]['full_name'],
                'student_name': student['name'],
                'student_id': student_id
            }
        }), 201

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Group Students ============

@students_bp.route('/api/groups/<int:group_id>/students', methods=['GET'])
@token_required
def get_group_students(group_id):
    """جلب طلاب مجموعة معينة"""
    try:
        query = supabase.table('students')\
            .select('*')\
            .eq('group_id', group_id)
        query = apply_tenant_filter(query)
        response = query.execute()

        return jsonify({
            'success': True,
            'count': len(response.data),
            'students': response.data
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500