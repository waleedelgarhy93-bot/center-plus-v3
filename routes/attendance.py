# routes/attendance.py - الحضور + check-in الذكي
from flask import Blueprint, jsonify, request
from datetime import datetime

from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    check_tenant_ownership,
)

attendance_bp = Blueprint('attendance', __name__)


# ============ CRUD أساسي ============

@attendance_bp.route('/api/attendance', methods=['GET'])
@token_required
def get_attendance():
    try:
        query = supabase.table('attendance').select('*')
        query = apply_tenant_filter(query)
        response = query.execute()

        return jsonify({
            'success': True,
            'count': len(response.data),
            'attendance': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@attendance_bp.route('/api/attendance/student/<int:student_id>', methods=['GET'])
@token_required
def get_student_attendance(student_id):
    try:
        query = supabase.table('attendance').select('*').eq('student_id', student_id)
        query = apply_tenant_filter(query)
        response = query.execute()

        return jsonify({
            'success': True,
            'count': len(response.data),
            'attendance': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@attendance_bp.route('/api/attendance', methods=['POST'])
@token_required
def create_attendance():
    try:
        data = request.get_json() or {}
        student_id = data.get('student_id')
        date = data.get('date')

        if not student_id or not date:
            return jsonify({'success': False, 'error': 'student_id و date مطلوبين'}), 400

        tenant_id = get_tenant_for_insert()

        new_attendance = supabase.table('attendance').insert({
            'student_id': student_id,
            'group_id': data.get('group_id'),
            'date': date,
            'time': data.get('time'),
            'status': data.get('status', 'present'),
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم تسجيل الحضور',
            'attendance': new_attendance.data[0] if new_attendance.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@attendance_bp.route('/api/attendance/<int:attendance_id>', methods=['PUT'])
@token_required
def update_attendance(attendance_id):
    try:
        existing = supabase.table('attendance').select('tenant_id').eq('id', attendance_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'Attendance not found'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        update_data = {}
        for field in ['student_id', 'group_id', 'date', 'time', 'status']:
            if field in data:
                update_data[field] = data[field]

        if not update_data:
            return jsonify({'success': False, 'error': 'مفيش بيانات للتعديل'}), 400

        result = supabase.table('attendance').update(update_data).eq('id', attendance_id).execute()

        return jsonify({
            'success': True,
            'message': 'تم التعديل',
            'attendance': result.data[0] if result.data else None
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@attendance_bp.route('/api/attendance/<int:attendance_id>', methods=['DELETE'])
@token_required
def delete_attendance(attendance_id):
    try:
        existing = supabase.table('attendance').select('tenant_id').eq('id', attendance_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'Attendance not found'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('attendance').delete().eq('id', attendance_id).execute()

        return jsonify({'success': True, 'message': 'تم الحذف'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Smart Attendance ============

@attendance_bp.route('/api/attendance/group/<int:group_id>/today', methods=['GET'])
@token_required
def get_group_attendance_today(group_id):
    """حضور مجموعة اليوم"""
    try:
        today = datetime.now().strftime('%Y-%m-%d')

        # التحقق من المجموعة
        group_res = supabase.table('groups').select('tenant_id').eq('id', group_id).execute()
        if not group_res.data:
            return jsonify({'success': False, 'error': 'المجموعة غير موجودة'}), 404
        if not check_tenant_ownership(group_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        students_res = supabase.table('students').select('*').eq('group_id', group_id).execute()
        students = students_res.data or []

        attendance_res = supabase.table('attendance')\
            .select('*')\
            .eq('group_id', group_id)\
            .eq('date', today)\
            .execute()
        attendances = attendance_res.data or []

        present_ids = {a['student_id'] for a in attendances}
        present = [s for s in students if s['id'] in present_ids]
        absent = [s for s in students if s['id'] not in present_ids]

        return jsonify({
            'success': True,
            'date': today,
            'total': len(students),
            'present_count': len(present),
            'absent_count': len(absent),
            'present': present,
            'absent': absent,
            'attendances': attendances
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@attendance_bp.route('/api/attendance/check-in', methods=['POST'])
@token_required
def smart_check_in():
    """تسجيل الحضور الذكي (barcode أو student_id)"""
    try:
        data = request.get_json() or {}
        barcode = data.get('barcode')
        student_id = data.get('student_id')
        group_id = data.get('group_id')

        if not barcode and not student_id:
            return jsonify({'success': False, 'error': 'barcode أو student_id مطلوب'}), 400

        if not group_id:
            return jsonify({'success': False, 'error': 'group_id مطلوب'}), 400

        # التحقق من المجموعة
        group_res = supabase.table('groups').select('tenant_id').eq('id', group_id).execute()
        if not group_res.data:
            return jsonify({'success': False, 'error': 'المجموعة غير موجودة'}), 404
        if not check_tenant_ownership(group_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        if barcode:
            student_res = supabase.table('students').select('*').eq('barcode', barcode).execute()
        else:
            student_res = supabase.table('students').select('*').eq('id', student_id).execute()

        if not student_res.data:
            return jsonify({'success': False, 'error': 'الطالب غير موجود'}), 404

        student = student_res.data[0]

        if not check_tenant_ownership(student):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        if student.get('group_id') != group_id:
            return jsonify({
                'success': False,
                'error': f"الطالب {student['name']} ليس في هذه المجموعة"
            }), 400

        today = datetime.now().strftime('%Y-%m-%d')
        existing = supabase.table('attendance')\
            .select('*')\
            .eq('student_id', student['id'])\
            .eq('group_id', group_id)\
            .eq('date', today)\
            .execute()

        if existing.data:
            return jsonify({
                'success': False,
                'error': 'تم تسجيل حضور الطالب بالفعل اليوم',
                'student': student,
                'attendance': existing.data[0]
            }), 409

        now = datetime.now()
        tenant_id = student.get('tenant_id')

        new_attendance = supabase.table('attendance').insert({
            'student_id': student['id'],
            'group_id': group_id,
            'date': today,
            'time': now.strftime('%H:%M:%S'),
            'status': 'present',
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': f"تم تسجيل حضور {student['name']}",
            'student': student,
            'attendance': new_attendance.data[0] if new_attendance.data else None,
            'timestamp': now.strftime('%H:%M:%S')
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500