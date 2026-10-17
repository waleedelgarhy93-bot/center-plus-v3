# routes/exams.py - الامتحانات + الأسئلة + التسليمات + AI Grading
from flask import Blueprint, jsonify, request
import json
import re
import urllib.request

from db import supabase, get_gemini_model
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    get_current_tenant_id,
    check_tenant_ownership,
)

exams_bp = Blueprint('exams', __name__)


# ============ Exams CRUD (Teacher) ============

@exams_bp.route('/api/exams', methods=['GET'])
@token_required
def get_exams():
    try:
        query = supabase.table('exams').select('*').order('created_at', desc=True)
        query = apply_tenant_filter(query)
        response = query.execute()
        exams = response.data or []

        if exams:
            group_ids = list({e['group_id'] for e in exams if e.get('group_id')})
            subject_ids = list({e['subject_id'] for e in exams if e.get('subject_id')})

            groups_map = {}
            if group_ids:
                grps = supabase.table('groups').select('id, grade, center').in_('id', group_ids).execute()
                groups_map = {g['id']: f"{g['grade']} - {g['center']}" for g in (grps.data or [])}

            subjects_map = {}
            if subject_ids:
                subs = supabase.table('subjects').select('id, name').in_('id', subject_ids).execute()
                subjects_map = {s['id']: s['name'] for s in (subs.data or [])}

            for e in exams:
                e['group_name'] = groups_map.get(e.get('group_id'))
                e['subject_name'] = subjects_map.get(e.get('subject_id'))

        return jsonify({'success': True, 'count': len(exams), 'exams': exams})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@exams_bp.route('/api/exams', methods=['POST'])
@token_required
def create_exam():
    try:
        data = request.get_json() or {}
        title = data.get('title')
        if not title:
            return jsonify({'success': False, 'error': 'title مطلوب'}), 400

        tenant_id = get_tenant_for_insert()

        new_exam = supabase.table('exams').insert({
            'title': title,
            'description': data.get('description'),
            'group_id': data.get('group_id'),
            'subject_id': data.get('subject_id'),
            'teacher_id': request.user_id,
            'max_grade': data.get('max_grade', 100),
            'duration_minutes': data.get('duration_minutes', 0),
            'show_result': data.get('show_result', True),
            'has_essay': data.get('has_essay', False),
            'essay_prompt': data.get('essay_prompt'),
            'essay_max_grade': data.get('essay_max_grade', 0),
            'is_published': data.get('is_published', False),
            'exam_type': data.get('exam_type', 'internal'),
            'external_link': data.get('external_link'),
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم إنشاء الاختبار',
            'exam': new_exam.data[0] if new_exam.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@exams_bp.route('/api/exams/<int:exam_id>', methods=['GET'])
@token_required
def get_exam(exam_id):
    try:
        exam_res = supabase.table('exams').select('*').eq('id', exam_id).execute()
        if not exam_res.data:
            return jsonify({'success': False, 'error': 'الاختبار غير موجود'}), 404
        if not check_tenant_ownership(exam_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        questions_res = supabase.table('questions')\
            .select('*')\
            .eq('exam_id', exam_id)\
            .order('order_index')\
            .execute()

        return jsonify({
            'success': True,
            'exam': exam_res.data[0],
            'questions': questions_res.data or []
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@exams_bp.route('/api/exams/<int:exam_id>', methods=['PUT'])
@token_required
def update_exam(exam_id):
    try:
        existing = supabase.table('exams').select('tenant_id').eq('id', exam_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'الاختبار غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        update_data = {}
        for field in ['title', 'description', 'group_id', 'subject_id', 'max_grade',
                      'duration_minutes', 'show_result', 'has_essay', 'essay_prompt',
                      'essay_max_grade', 'is_published', 'exam_type', 'external_link']:
            if field in data:
                update_data[field] = data[field]

        if not update_data:
            return jsonify({'success': False, 'error': 'مفيش بيانات للتعديل'}), 400

        result = supabase.table('exams').update(update_data).eq('id', exam_id).execute()

        return jsonify({'success': True, 'exam': result.data[0] if result.data else None})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@exams_bp.route('/api/exams/<int:exam_id>', methods=['DELETE'])
@token_required
def delete_exam(exam_id):
    try:
        existing = supabase.table('exams').select('tenant_id').eq('id', exam_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'الاختبار غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('exams').delete().eq('id', exam_id).execute()
        return jsonify({'success': True, 'message': 'تم الحذف'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Questions ============

@exams_bp.route('/api/exams/<int:exam_id>/questions', methods=['POST'])
@token_required
def add_question(exam_id):
    try:
        exam_res = supabase.table('exams').select('tenant_id').eq('id', exam_id).execute()
        if not exam_res.data:
            return jsonify({'success': False, 'error': 'الاختبار غير موجود'}), 404
        if not check_tenant_ownership(exam_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        question_text = data.get('question_text')
        if not question_text:
            return jsonify({'success': False, 'error': 'question_text مطلوب'}), 400

        new_q = supabase.table('questions').insert({
            'exam_id': exam_id,
            'question_text': question_text,
            'question_type': data.get('question_type', 'mcq'),
            'options': data.get('options'),
            'correct_answer': data.get('correct_answer'),
            'points': data.get('points', 1),
            'order_index': data.get('order_index', 0),
            'question_image_url': data.get('question_image_url'),
            'correct_answer_image_url': data.get('correct_answer_image_url'),
            'tenant_id': exam_res.data[0].get('tenant_id'),
        }).execute()

        return jsonify({'success': True, 'question': new_q.data[0] if new_q.data else None}), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@exams_bp.route('/api/questions/<int:question_id>', methods=['PUT'])
@token_required
def update_question(question_id):
    try:
        existing = supabase.table('questions').select('tenant_id').eq('id', question_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'السؤال غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        update_data = {}
        for field in ['question_text', 'question_type', 'options', 'correct_answer',
                      'points', 'order_index', 'question_image_url', 'correct_answer_image_url']:
            if field in data:
                update_data[field] = data[field]

        if not update_data:
            return jsonify({'success': False, 'error': 'مفيش بيانات للتعديل'}), 400

        result = supabase.table('questions').update(update_data).eq('id', question_id).execute()
        return jsonify({'success': True, 'question': result.data[0] if result.data else None})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@exams_bp.route('/api/questions/<int:question_id>', methods=['DELETE'])
@token_required
def delete_question(question_id):
    try:
        existing = supabase.table('questions').select('tenant_id').eq('id', question_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'السؤال غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('questions').delete().eq('id', question_id).execute()
        return jsonify({'success': True, 'message': 'تم الحذف'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Student Exams ============

@exams_bp.route('/api/student/exams', methods=['GET'])
@token_required
def get_student_exams():
    """الاختبارات المتاحة للطالب"""
    try:
        student_res = supabase.table('students').select('*').eq('id', request.user_id).execute()
        if not student_res.data:
            return jsonify({'success': False, 'error': 'الطالب غير موجود'}), 404

        student = student_res.data[0]
        group_id = student.get('group_id')
        subject_id = None

        if group_id:
            group_res = supabase.table('groups').select('subject_id').eq('id', group_id).execute()
            if group_res.data:
                subject_id = group_res.data[0].get('subject_id')

        query = supabase.table('exams').select('*').eq('is_published', True)
        if student.get('tenant_id'):
            query = query.eq('tenant_id', student['tenant_id'])

        if group_id and subject_id:
            query = query.or_(f'group_id.eq.{group_id},subject_id.eq.{subject_id}')
        elif group_id:
            query = query.eq('group_id', group_id)
        elif subject_id:
            query = query.eq('subject_id', subject_id)

        exams_res = query.order('created_at', desc=True).execute()
        exams = exams_res.data or []

        submissions_res = supabase.table('student_exams')\
            .select('*')\
            .eq('student_id', request.user_id)\
            .execute()
        submissions_map = {s['exam_id']: s for s in (submissions_res.data or [])}

        for exam in exams:
            submission = submissions_map.get(exam['id'])
            if submission:
                exam['my_status'] = 'submitted'
                exam['my_submission_id'] = submission['id']
                exam['my_grade'] = submission.get('total_grade')
                exam['my_max_grade'] = submission.get('max_grade')
                exam['submitted_at'] = submission.get('submitted_at')
            else:
                exam['my_status'] = 'not_submitted'

        return jsonify({
            'success': True,
            'count': len(exams),
            'exams': exams
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@exams_bp.route('/api/exams/<int:exam_id>/take', methods=['GET'])
@token_required
def take_exam(exam_id):
    """الطالب يفتح اختبار (بدون إجابات)"""
    try:
        exam_res = supabase.table('exams').select('*').eq('id', exam_id).execute()
        if not exam_res.data:
            return jsonify({'success': False, 'error': 'الاختبار غير موجود'}), 404

        exam = exam_res.data[0]
        if not exam.get('is_published'):
            return jsonify({'success': False, 'error': 'الاختبار غير منشور'}), 403

        existing = supabase.table('student_exams')\
            .select('*')\
            .eq('exam_id', exam_id)\
            .eq('student_id', request.user_id)\
            .execute()

        if existing.data:
            return jsonify({
                'success': False,
                'error': 'لقد سلّمت هذا الاختبار بالفعل',
                'submission_id': existing.data[0]['id']
            }), 409

        questions_res = supabase.table('questions')\
            .select('*')\
            .eq('exam_id', exam_id)\
            .order('order_index')\
            .execute()

        questions = questions_res.data or []
        for q in questions:
            q.pop('correct_answer', None)
            q.pop('correct_answer_image_url', None)

        student_res = supabase.table('students').select('name, barcode').eq('id', request.user_id).execute()
        student = student_res.data[0] if student_res.data else {}

        return jsonify({
            'success': True,
            'exam': exam,
            'questions': questions,
            'student': student
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@exams_bp.route('/api/exams/<int:exam_id>/submit', methods=['POST'])
@token_required
def submit_exam(exam_id):
    """الطالب يسلّم الاختبار"""
    try:
        existing = supabase.table('student_exams')\
            .select('id')\
            .eq('exam_id', exam_id)\
            .eq('student_id', request.user_id)\
            .execute()

        if existing.data:
            return jsonify({'success': False, 'error': 'لقد سلّمت هذا الاختبار بالفعل'}), 409

        exam_res = supabase.table('exams').select('*').eq('id', exam_id).execute()
        if not exam_res.data:
            return jsonify({'success': False, 'error': 'الاختبار غير موجود'}), 404

        exam = exam_res.data[0]

        questions_res = supabase.table('questions').select('*').eq('exam_id', exam_id).execute()
        questions_map = {q['id']: q for q in (questions_res.data or [])}

        new_submission = supabase.table('student_exams').insert({
            'exam_id': exam_id,
            'student_id': request.user_id,
            'total_grade': 0,
            'max_grade': exam.get('max_grade', 100),
            'status': 'submitted',
            'tenant_id': exam.get('tenant_id'),
        }).execute()

        if not new_submission.data:
            return jsonify({'success': False, 'error': 'فشل إنشاء التسليم'}), 500

        submission_id = new_submission.data[0]['id']

        data = request.get_json() or {}
        answers = data.get('answers', [])

        auto_grade = 0
        has_essay = False

        for ans in answers:
            q_id = ans.get('question_id')
            q = questions_map.get(q_id)
            if not q:
                continue

            student_answer = ans.get('answer', '')
            answer_image_url = ans.get('answer_image_url', '')

            ai_grade = None
            final_grade = None
            graded_by = 'pending'

            if q['question_type'] == 'mcq':
                correct = q.get('correct_answer', '')
                is_correct = str(student_answer) == str(correct)
                final_grade = q['points'] if is_correct else 0
                auto_grade += final_grade
                graded_by = 'auto'
            elif q['question_type'] == 'fill':
                correct = (q.get('correct_answer') or '').strip().lower()
                given = (student_answer or '').strip().lower()
                is_correct = correct == given
                final_grade = q['points'] if is_correct else 0
                auto_grade += final_grade
                graded_by = 'auto'
            elif q['question_type'] == 'essay':
                has_essay = True
                graded_by = 'pending'

            supabase.table('student_answers').insert({
                'student_exam_id': submission_id,
                'question_id': q_id,
                'answer': student_answer or None,
                'answer_image_url': answer_image_url or None,
                'final_grade': final_grade,
                'graded_by': graded_by,
                'tenant_id': exam.get('tenant_id'),
            }).execute()

        supabase.table('student_exams').update({
            'total_grade': auto_grade
        }).eq('id', submission_id).execute()

        return jsonify({
            'success': True,
            'message': 'تم تسليم الاختبار',
            'submission_id': submission_id,
            'auto_grade': auto_grade,
            'max_grade': exam.get('max_grade', 100),
            'has_essay': has_essay
        }), 201
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Exam Results (Student) ============

@exams_bp.route('/api/exams/<int:exam_id>/my-result', methods=['GET'])
@token_required
def get_my_exam_result(exam_id):
    try:
        submission_res = supabase.table('student_exams')\
            .select('*')\
            .eq('exam_id', exam_id)\
            .eq('student_id', request.user_id)\
            .execute()

        if not submission_res.data:
            return jsonify({'success': False, 'error': 'لم تسلّم هذا الاختبار'}), 404

        submission = submission_res.data[0]

        exam_res = supabase.table('exams').select('*').eq('id', exam_id).execute()
        exam = exam_res.data[0] if exam_res.data else {}

        questions_res = supabase.table('questions')\
            .select('*')\
            .eq('exam_id', exam_id)\
            .order('order_index')\
            .execute()
        questions = questions_res.data or []

        answers_res = supabase.table('student_answers')\
            .select('*')\
            .eq('student_exam_id', submission['id'])\
            .execute()
        answers_map = {a['question_id']: a for a in (answers_res.data or [])}

        result_questions = []
        for q in questions:
            ans = answers_map.get(q['id'], {})
            show_correct = exam.get('show_result', True)

            result_questions.append({
                'id': q['id'],
                'question_text': q['question_text'],
                'question_type': q['question_type'],
                'options': q['options'],
                'points': q['points'],
                'question_image_url': q.get('question_image_url'),
                'student_answer': ans.get('answer'),
                'student_answer_image_url': ans.get('answer_image_url'),
                'final_grade': ans.get('final_grade'),
                'ai_grade': ans.get('ai_grade'),
                'ai_feedback': ans.get('ai_feedback'),
                'graded_by': ans.get('graded_by', 'pending'),
                'correct_answer': q.get('correct_answer') if show_correct else None,
            })

        return jsonify({
            'success': True,
            'exam': {
                'id': exam.get('id'),
                'title': exam.get('title'),
                'max_grade': exam.get('max_grade'),
                'show_result': exam.get('show_result', True),
            },
            'submission': {
                'id': submission['id'],
                'total_grade': submission.get('total_grade'),
                'max_grade': submission.get('max_grade'),
                'status': submission.get('status'),
                'submitted_at': submission.get('submitted_at'),
            },
            'questions': result_questions
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Exam Submissions (Teacher) ============

@exams_bp.route('/api/exams/<int:exam_id>/submissions', methods=['GET'])
@token_required
def get_exam_submissions(exam_id):
    try:
        exam_res = supabase.table('exams').select('*').eq('id', exam_id).execute()
        if not exam_res.data:
            return jsonify({'success': False, 'error': 'الاختبار غير موجود'}), 404
        if not check_tenant_ownership(exam_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        exam = exam_res.data[0]

        submissions_res = supabase.table('student_exams')\
            .select('*')\
            .eq('exam_id', exam_id)\
            .order('submitted_at', desc=True)\
            .execute()
        submissions = submissions_res.data or []

        if submissions:
            student_ids = [s['student_id'] for s in submissions]
            students_res = supabase.table('students')\
                .select('id, name, barcode')\
                .in_('id', student_ids)\
                .execute()
            students_map = {s['id']: s for s in (students_res.data or [])}

            for s in submissions:
                student = students_map.get(s['student_id'], {})
                s['student_name'] = student.get('name', 'غير معروف')
                s['student_barcode'] = student.get('barcode')

        essay_count_res = supabase.table('questions')\
            .select('id')\
            .eq('exam_id', exam_id)\
            .eq('question_type', 'essay')\
            .execute()
        essay_count = len(essay_count_res.data or [])

        return jsonify({
            'success': True,
            'exam': exam,
            'submissions': submissions,
            'count': len(submissions),
            'essay_count': essay_count
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@exams_bp.route('/api/submissions/<int:submission_id>', methods=['GET'])
@token_required
def get_submission_detail(submission_id):
    try:
        sub_res = supabase.table('student_exams').select('*').eq('id', submission_id).execute()
        if not sub_res.data:
            return jsonify({'success': False, 'error': 'التسليم غير موجود'}), 404

        submission = sub_res.data[0]

        if not check_tenant_ownership(submission):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        student_res = supabase.table('students').select('name, barcode').eq('id', submission['student_id']).execute()
        student = student_res.data[0] if student_res.data else {}

        questions_res = supabase.table('questions')\
            .select('*')\
            .eq('exam_id', submission['exam_id'])\
            .order('order_index')\
            .execute()
        questions = questions_res.data or []

        answers_res = supabase.table('student_answers')\
            .select('*')\
            .eq('student_exam_id', submission_id)\
            .execute()
        answers_map = {a['question_id']: a for a in (answers_res.data or [])}

        detailed = []
        for q in questions:
            ans = answers_map.get(q['id'], {})
            detailed.append({
                'question_id': q['id'],
                'question_text': q['question_text'],
                'question_type': q['question_type'],
                'options': q['options'],
                'points': q['points'],
                'correct_answer': q.get('correct_answer'),
                'question_image_url': q.get('question_image_url'),
                'correct_answer_image_url': q.get('correct_answer_image_url'),
                'answer_id': ans.get('id'),
                'student_answer': ans.get('answer'),
                'student_answer_image_url': ans.get('answer_image_url'),
                'ai_grade': ans.get('ai_grade'),
                'ai_feedback': ans.get('ai_feedback'),
                'final_grade': ans.get('final_grade'),
                'graded_by': ans.get('graded_by', 'pending'),
            })

        return jsonify({
            'success': True,
            'submission': submission,
            'student': student,
            'answers': detailed
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ AI Grading ============

@exams_bp.route('/api/exams/grade-essay', methods=['POST'])
@token_required
def grade_essay_with_ai():
    """تصحيح سؤال مقالي بالذكاء الاصطناعي"""
    try:
        data = request.get_json() or {}
        question_text = data.get('question_text', '')
        question_image_url = data.get('question_image_url')
        student_answer = data.get('student_answer', '')
        student_answer_image_url = data.get('student_answer_image_url')
        correct_answer = data.get('correct_answer', '')
        correct_answer_image_url = data.get('correct_answer_image_url')
        max_points = data.get('max_points', 10)

        if not question_text and not question_image_url:
            return jsonify({'success': False, 'error': 'السؤال مطلوب'}), 400
        if not student_answer and not student_answer_image_url:
            return jsonify({'success': False, 'error': 'إجابة الطالب مطلوبة'}), 400

        result = _run_ai_grading(
            question_text, question_image_url,
            student_answer, student_answer_image_url,
            correct_answer, correct_answer_image_url,
            max_points
        )

        return jsonify(result)
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({'success': False, 'error': str(e)}), 500


@exams_bp.route('/api/student-answers/<int:answer_id>/grade-essay', methods=['POST'])
@token_required
def grade_essay_answer(answer_id):
    """تصحيح إجابة مقالية موجودة"""
    try:
        answer_res = supabase.table('student_answers').select('*').eq('id', answer_id).execute()
        if not answer_res.data:
            return jsonify({'success': False, 'error': 'الإجابة غير موجودة'}), 404

        answer = answer_res.data[0]
        if not check_tenant_ownership(answer):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        question_res = supabase.table('questions').select('*').eq('id', answer['question_id']).execute()
        if not question_res.data:
            return jsonify({'success': False, 'error': 'السؤال غير موجود'}), 404

        question = question_res.data[0]
        if question['question_type'] != 'essay':
            return jsonify({'success': False, 'error': 'هذا السؤال ليس مقالي'}), 400

        max_points = question.get('points', 10)

        result = _run_ai_grading(
            question.get('question_text', ''),
            question.get('question_image_url'),
            answer.get('answer', ''),
            answer.get('answer_image_url'),
            question.get('correct_answer', ''),
            question.get('correct_answer_image_url'),
            max_points
        )

        if not result.get('success'):
            return jsonify(result), 500

        ai_grade = result['grade']
        ai_feedback = result['feedback']

        supabase.table('student_answers').update({
            'ai_grade': ai_grade,
            'ai_feedback': ai_feedback,
            'final_grade': ai_grade,
            'graded_by': 'ai'
        }).eq('id', answer_id).execute()

        _recalculate_submission_grade(answer['student_exam_id'])

        return jsonify({
            'success': True,
            'ai_grade': ai_grade,
            'ai_feedback': ai_feedback,
            'max_points': max_points
        })
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({'success': False, 'error': str(e)}), 500


@exams_bp.route('/api/student-answers/<int:answer_id>/grade-manual', methods=['PUT'])
@token_required
def grade_manual_answer(answer_id):
    """المدرس يعدّل الدرجة يدوياً"""
    try:
        data = request.get_json() or {}
        new_grade = data.get('grade')

        if new_grade is None:
            return jsonify({'success': False, 'error': 'grade مطلوب'}), 400

        answer_res = supabase.table('student_answers').select('*').eq('id', answer_id).execute()
        if not answer_res.data:
            return jsonify({'success': False, 'error': 'الإجابة غير موجودة'}), 404

        answer = answer_res.data[0]
        if not check_tenant_ownership(answer):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('student_answers').update({
            'final_grade': float(new_grade),
            'graded_by': 'manual'
        }).eq('id', answer_id).execute()

        _recalculate_submission_grade(answer['student_exam_id'])

        return jsonify({
            'success': True,
            'message': 'تم التحديث',
            'new_grade': float(new_grade)
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Helpers ============

def _run_ai_grading(question_text, question_image_url, student_answer, student_answer_image_url,
                    correct_answer, correct_answer_image_url, max_points):
    """الدالة المساعدة للتصحيح بالـ AI"""
    prompt_parts = [
        "أنت مصحح امتحانات خبير. صحّح إجابة الطالب التالية بدقة واحترافية.",
        "",
        "**تعليمات مهمة:**",
        "1. اقرأ السؤال بعناية",
        "2. اقرأ إجابة الطالب",
        "3. قارن بالإجابة النموذجية (إن وُجدت)",
        f"4. أعط درجة من {max_points}",
        "5. اكتب تعليق مختصر ومفيد",
        "",
        f"**الدرجة القصوى:** {max_points}",
        "",
        "**السؤال:**",
        question_text if question_text else "(انظر الصورة)",
        "",
    ]

    if correct_answer:
        prompt_parts.extend(["**الإجابة النموذجية:**", correct_answer, ""])
    if student_answer:
        prompt_parts.extend(["**إجابة الطالب:**", student_answer, ""])

    prompt_parts.extend([
        "**المطلوب منك:**",
        "أجب بهذا الشكل بالظبط (JSON فقط):",
        '{"grade": رقم_الدرجة, "feedback": "التعليق", "reasoning": "السبب"}',
        "",
        f"تذكر: الدرجة رقم من 0 إلى {max_points}."
    ])

    prompt = "\n".join(prompt_parts)
    contents = [prompt]

    # إضافة الصور
    for img_url in [question_image_url, student_answer_image_url, correct_answer_image_url]:
        if img_url:
            try:
                with urllib.request.urlopen(img_url, timeout=10) as response:
                    img_data = response.read()
                img_type = 'image/png' if img_url.lower().endswith('.png') else 'image/jpeg'
                contents.append({'mime_type': img_type, 'data': img_data})
            except Exception as e:
                print(f"⚠️ فشل تحميل الصورة: {e}")

    model = get_gemini_model()
    response = model.generate_content(contents)

    result_text = response.text.strip()

    if result_text.startswith('```'):
        result_text = result_text.split('```')[1]
        if result_text.startswith('json'):
            result_text = result_text[4:]
        result_text = result_text.strip()

    json_match = re.search(r'\{.*\}', result_text, re.DOTALL)
    if json_match:
        parsed = json.loads(json_match.group())
        grade = float(parsed.get('grade', 0))
        feedback = parsed.get('feedback', '')
        reasoning = parsed.get('reasoning', '')
    else:
        grade_match = re.search(r'\d+(?:\.\d+)?', result_text)
        grade = float(grade_match.group()) if grade_match else 0
        feedback = result_text
        reasoning = ''

    grade = max(0, min(grade, max_points))

    return {
        'success': True,
        'grade': grade,
        'max_points': max_points,
        'feedback': feedback,
        'reasoning': reasoning,
        'raw_response': result_text
    }


def _recalculate_submission_grade(submission_id):
    """إعادة حساب الدرجة الإجمالية"""
    try:
        answers_res = supabase.table('student_answers')\
            .select('final_grade')\
            .eq('student_exam_id', submission_id)\
            .execute()

        total = sum((a.get('final_grade') or 0) for a in (answers_res.data or []))

        supabase.table('student_exams').update({
            'total_grade': total
        }).eq('id', submission_id).execute()

        return total
    except Exception as e:
        print(f"⚠️ فشل حساب الدرجة: {e}")
        return 0