# routes/grades.py - الدرجات + تقارير الطلاب
from flask import Blueprint, jsonify, request
from datetime import datetime

from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    check_tenant_ownership,
)

grades_bp = Blueprint('grades', __name__)


# ============ CRUD ============

@grades_bp.route('/api/grades', methods=['GET'])
@token_required
def get_grades():
    try:
        query = supabase.table('grades').select('*')
        query = apply_tenant_filter(query)
        response = query.execute()

        return jsonify({
            'success': True,
            'count': len(response.data),
            'grades': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@grades_bp.route('/api/grades/student/<int:student_id>', methods=['GET'])
@token_required
def get_student_grades(student_id):
    try:
        # التحقق من الطالب
        student_res = supabase.table('students').select('tenant_id').eq('id', student_id).execute()
        if student_res.data and not check_tenant_ownership(student_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        query = supabase.table('grades').select('*').eq('student_id', student_id)
        query = apply_tenant_filter(query)
        response = query.execute()

        return jsonify({
            'success': True,
            'count': len(response.data),
            'grades': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@grades_bp.route('/api/grades/exam/<int:exam_id>', methods=['GET'])
@token_required
def get_exam_grades(exam_id):
    try:
        query = supabase.table('grades').select('*').eq('exam_id', exam_id)
        query = apply_tenant_filter(query)
        response = query.execute()

        return jsonify({
            'success': True,
            'count': len(response.data),
            'grades': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@grades_bp.route('/api/grades', methods=['POST'])
@token_required
def create_grade():
    try:
        data = request.get_json() or {}
        student_id = data.get('student_id')
        exam_id = data.get('exam_id')
        grade = data.get('grade')

        if not student_id or not exam_id or grade is None:
            return jsonify({'success': False, 'error': 'student_id, exam_id, grade مطلوبين'}), 400

        tenant_id = get_tenant_for_insert()

        new_grade = supabase.table('grades').insert({
            'student_id': student_id,
            'exam_id': exam_id,
            'grade': grade,
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم إضافة الدرجة',
            'grade': new_grade.data[0] if new_grade.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@grades_bp.route('/api/grades/<int:grade_id>', methods=['PUT'])
@token_required
def update_grade(grade_id):
    try:
        existing = supabase.table('grades').select('tenant_id').eq('id', grade_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'Grade not found'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        update_data = {}
        for field in ['student_id', 'exam_id', 'grade']:
            if field in data:
                update_data[field] = data[field]

        if not update_data:
            return jsonify({'success': False, 'error': 'مفيش بيانات للتعديل'}), 400

        result = supabase.table('grades').update(update_data).eq('id', grade_id).execute()

        return jsonify({
            'success': True,
            'message': 'تم التعديل',
            'grade': result.data[0] if result.data else None
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@grades_bp.route('/api/grades/<int:grade_id>', methods=['DELETE'])
@token_required
def delete_grade(grade_id):
    try:
        existing = supabase.table('grades').select('tenant_id').eq('id', grade_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'Grade not found'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('grades').delete().eq('id', grade_id).execute()

        return jsonify({'success': True, 'message': 'تم الحذف'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Grades Overview ============

@grades_bp.route('/api/grades/all', methods=['GET'])
@token_required
def get_all_grades():
    """كل الدرجات مع الفلاتر + إحصائيات"""
    try:
        subject_id = request.args.get('subject_id', type=int)
        group_id = request.args.get('group_id', type=int)
        exam_id = request.args.get('exam_id', type=int)
        month = request.args.get('month', type=str)

        # 1) الاختبارات
        exams_query = supabase.table('exams').select('*')
        exams_query = apply_tenant_filter(exams_query)

        if subject_id:
            exams_query = exams_query.eq('subject_id', subject_id)
        if group_id:
            exams_query = exams_query.eq('group_id', group_id)
        if exam_id:
            exams_query = exams_query.eq('id', exam_id)

        exams_res = exams_query.execute()
        exams = {e['id']: e for e in (exams_res.data or [])}

        if not exams:
            return jsonify({
                'success': True,
                'count': 0,
                'grades': [],
                'stats': {'average': 0, 'highest': 0, 'lowest': 0, 'total': 0}
            })

        # 2) التسليمات
        subs_query = supabase.table('student_exams').select('*').in_('exam_id', list(exams.keys()))
        if month:
            subs_query = subs_query.gte('submitted_at', f'{month}-01').lt('submitted_at', f'{month}-32')

        subs_res = subs_query.execute()
        submissions = subs_res.data or []

        # 3) الطلاب
        student_ids = list({s['student_id'] for s in submissions})
        if not student_ids:
            return jsonify({
                'success': True,
                'count': 0,
                'grades': [],
                'stats': {'average': 0, 'highest': 0, 'lowest': 0, 'total': 0}
            })

        students_res = supabase.table('students')\
            .select('id, name, barcode, group_id')\
            .in_('id', student_ids)\
            .execute()
        students = {s['id']: s for s in (students_res.data or [])}

        # 4) المجموعات والمواد
        group_ids = list({e.get('group_id') for e in exams.values() if e.get('group_id')})
        groups_map = {}
        if group_ids:
            groups_res = supabase.table('groups').select('id, grade, center').in_('id', group_ids).execute()
            groups_map = {g['id']: f"{g['grade']} - {g['center']}" for g in (groups_res.data or [])}

        subject_ids = list({e.get('subject_id') for e in exams.values() if e.get('subject_id')})
        subjects_map = {}
        if subject_ids:
            subjects_res = supabase.table('subjects').select('id, name').in_('id', subject_ids).execute()
            subjects_map = {s['id']: s['name'] for s in (subjects_res.data or [])}

        # 5) تجميع
        grades_list = []
        total_grade = 0
        total_max = 0
        highest = 0
        lowest = 999999
        count = 0

        for sub in submissions:
            exam = exams.get(sub['exam_id'])
            student = students.get(sub['student_id'])
            if not exam or not student:
                continue

            if subject_id and exam.get('subject_id') != subject_id:
                continue

            g = float(sub.get('total_grade', 0))
            m = float(sub.get('max_grade', exam.get('max_grade', 100)))
            pct = (g / m * 100) if m > 0 else 0

            grades_list.append({
                'submission_id': sub['id'],
                'student_id': student['id'],
                'student_name': student['name'],
                'student_barcode': student.get('barcode'),
                'group_id': exam.get('group_id'),
                'group_name': groups_map.get(exam.get('group_id')),
                'exam_id': exam['id'],
                'exam_title': exam['title'],
                'subject_id': exam.get('subject_id'),
                'subject_name': subjects_map.get(exam.get('subject_id')),
                'grade': g,
                'max_grade': m,
                'percentage': round(pct, 1),
                'submitted_at': sub.get('submitted_at'),
                'status': sub.get('status', 'submitted'),
            })

            total_grade += g
            total_max += m
            highest = max(highest, pct)
            lowest = min(lowest, pct)
            count += 1

        grades_list.sort(key=lambda x: x.get('submitted_at', ''), reverse=True)

        average = (total_grade / total_max * 100) if total_max > 0 else 0
        lowest = lowest if lowest != 999999 else 0

        return jsonify({
            'success': True,
            'count': count,
            'grades': grades_list,
            'stats': {
                'average': round(average, 1),
                'highest': round(highest, 1),
                'lowest': round(lowest, 1),
                'total': count,
            }
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500