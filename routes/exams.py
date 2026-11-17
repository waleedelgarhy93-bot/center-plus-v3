# routes/exams.py - الامتحانات + الأسئلة + التسليمات + AI Grading
from flask import Blueprint, jsonify, request
import json
import re
import urllib.request

from db import supabase, get_gemini_model, generate_with_fallback
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    get_current_tenant_id,
    check_tenant_ownership,
    get_student_access,
    filter_content_for_student,
    student_can_access_content,
)

exams_bp = Blueprint('exams', __name__)


# ============ تنظيف وتظبيط رسومات الـ SVG اللي بيولدها الذكاء الاصطناعي ============

_WESTERN_TO_INDIC = str.maketrans('0123456789', '٠١٢٣٤٥٦٧٨٩')
_INDIC_TO_WESTERN = str.maketrans('٠١٢٣٤٥٦٧٨٩', '0123456789')


def clean_ai_svg(svg, math_symbols='english'):
    """يرجّع SVG نضيف وآمن ومتظبط، أو None لو الرسم بايظ (مقطوع أو مش XML سليم).
    - يشيل أي script / foreignObject / on*= / javascript: (أمان)
    - يخلي الرسم responsive (width=100%) مع viewBox
    - يحط خلفية بيضا وخط بيدعم العربي
    - يوحّد الأرقام اللي على الرسم مع نوع الرموز (هندي للعربي / إنجليزي للإنجليزي)
    """
    if not svg or not isinstance(svg, str):
        return None
    s = svg.strip()
    s = re.sub(r'^```(?:svg|xml|html)?\s*|\s*```$', '', s)
    start, end = s.find('<svg'), s.rfind('</svg>')
    if start == -1 or end == -1:
        return None  # الرسم مقطوع
    s = s[start:end + len('</svg>')]

    # أمان
    s = re.sub(r'<script\b[\s\S]*?</script\s*>', '', s, flags=re.I)
    s = re.sub(r'<foreignObject\b[\s\S]*?</foreignObject\s*>', '', s, flags=re.I)
    s = re.sub(r'\son[a-z]+\s*=\s*("[^"]*"|\'[^\']*\')', '', s, flags=re.I)
    s = re.sub(r'(href\s*=\s*["\'])\s*javascript:[^"\']*', r'\1#', s, flags=re.I)

    m = re.match(r'<svg\b([^>]*)>', s)
    if not m:
        return None
    attrs = m.group(1)
    if 'xmlns=' not in attrs:
        attrs = ' xmlns="http://www.w3.org/2000/svg"' + attrs
    if not re.search(r'\bviewBox\s*=', attrs):
        w = re.search(r'\bwidth\s*=\s*["\']?([\d.]+)', attrs)
        h = re.search(r'\bheight\s*=\s*["\']?([\d.]+)', attrs)
        vb = f'0 0 {w.group(1)} {h.group(1)}' if (w and h) else '0 0 380 240'
        attrs += f' viewBox="{vb}"'
    # responsive: نشيل المقاسات الثابتة من الوسم الرئيسي بس
    attrs = re.sub(r'\s(width|height)\s*=\s*("[^"]*"|\'[^\']*\'|[\d.]+%?)', '', attrs)
    attrs += ' width="100%" preserveAspectRatio="xMidYMid meet"'
    if 'font-family' not in attrs:
        attrs += ' font-family="Tahoma, \'Segoe UI\', Arial, sans-serif"'
    s = '<svg' + attrs + '>' + s[m.end():]

    # خلفية بيضا لو مفيش (عشان الرسم ما يبقاش شفاف على خلفيات غامقة)
    if not re.search(r'<rect\b[^>]*fill\s*=\s*["\'](#fff\b|#ffffff|white|#f8fafc)', s[:600], flags=re.I):
        s = re.sub(r'(<svg\b[^>]*>)', r'\1<rect x="0" y="0" width="100%" height="100%" fill="#ffffff"/>', s, count=1)

    # توحيد الأرقام في النصوص اللي على الرسم
    table = _WESTERN_TO_INDIC if math_symbols == 'arabic' else _INDIC_TO_WESTERN
    s = re.sub(r'(<text\b[^>]*>)([^<]*)(</text>)', lambda mm: mm.group(1) + mm.group(2).translate(table) + mm.group(3), s)
    s = re.sub(r'(<tspan\b[^>]*>)([^<]*)(</tspan>)', lambda mm: mm.group(1) + mm.group(2).translate(table) + mm.group(3), s)

    # التأكد إن الناتج XML سليم، وإلا نرمي الرسم بدل ما يظهر صورة بايظة
    try:
        import xml.etree.ElementTree as ET
        ET.fromstring(s)
    except Exception:
        return None
    return s

# ============ Helper: حفظ/تحديث مجموعات الاختبار ============

def _save_exam_groups(exam_id, group_ids, tenant_id):
    supabase.table('exam_groups').delete().eq('exam_id', exam_id).execute()
    if group_ids:
        rows = [{'exam_id': exam_id, 'group_id': gid, 'tenant_id': tenant_id} for gid in group_ids]
        supabase.table('exam_groups').insert(rows).execute()


def _get_exam_group_ids(exam_id):
    res = supabase.table('exam_groups').select('group_id').eq('exam_id', exam_id).execute()
    return [r['group_id'] for r in (res.data or [])]



def _enrich_categories(items):
    if not items:
        return items
    cat_ids = list({it.get('category_id') for it in items if it.get('category_id')})
    if cat_ids:
        cats_res = supabase.table('categories').select('id, name').in_('id', cat_ids).execute()
        cats_map = {c['id']: c['name'] for c in (cats_res.data or [])}
        for it in items:
            it['category_name'] = cats_map.get(it.get('category_id'))
    else:
        for it in items:
            it['category_name'] = None
    return items

def _enrich_exams_with_groups(exams):
    if not exams:
        return exams
    exam_ids = [e['id'] for e in exams]
    eg_res = supabase.table('exam_groups').select('exam_id, group_id').in_('exam_id', exam_ids).execute()
    eg_data = eg_res.data or []
    all_group_ids = list({r['group_id'] for r in eg_data})
    groups_map = {}
    if all_group_ids:
        grps = supabase.table('groups').select('id, grade, center, subject_id').in_('id', all_group_ids).execute()
        grps_data = grps.data or []
        sub_ids = list({g['subject_id'] for g in grps_data if g.get('subject_id')})
        subs_map = {}
        if sub_ids:
            s_res = supabase.table('subjects').select('id, name').in_('id', sub_ids).execute()
            subs_map = {s['id']: s['name'] for s in (s_res.data or [])}
        groups_map = {
            g['id']: f"{g['grade']} - {g['center']}" + (f" ({subs_map[g['subject_id']]})" if g.get('subject_id') in subs_map else "")
            for g in grps_data
        }
    exam_groups_map = {}
    for r in eg_data:
        eid = r['exam_id']
        gid = r['group_id']
        if eid not in exam_groups_map:
            exam_groups_map[eid] = []
        exam_groups_map[eid].append(gid)
    for e in exams:
        gids = exam_groups_map.get(e['id'], [])
        e['group_ids'] = gids
        e['group_names'] = [groups_map.get(gid, '') for gid in gids]
        e['group_id'] = gids[0] if gids else e.get('group_id')
        e['group_name'] = groups_map.get(e['group_id']) if e.get('group_id') else None
    return exams



# ============ Exams CRUD (Teacher) ============

@exams_bp.route('/api/exams', methods=['GET'])
@token_required
def get_exams():
    try:
        exam_type = request.args.get('exam_type') or request.args.get('type')
        query = supabase.table('exams').select('*').order('created_at', desc=True)
        query = apply_tenant_filter(query)
        if exam_type == 'homework':
            query = query.eq('exam_type', 'homework')
        elif exam_type == 'exam':
            query = query.neq('exam_type', 'homework')
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

            exams = _enrich_exams_with_groups(exams)
            exams = _enrich_categories(exams)

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

        group_ids = data.get('group_ids', [])
        if not group_ids and data.get('group_id'):
            group_ids = [data['group_id']]

        new_exam = supabase.table('exams').insert({
            'title': title,
            'description': data.get('description'),
            'group_id': group_ids[0] if group_ids else None,
            'subject_id': data.get('subject_id'),
            'category_id': int(data['category_id']) if data.get('category_id') else None,
            'branch_id': int(data['branch_id']) if data.get('branch_id') else None,
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

        exam = new_exam.data[0] if new_exam.data else None
        if exam and group_ids:
            _save_exam_groups(exam['id'], group_ids, tenant_id)
            exam['group_ids'] = group_ids

        # إشعار تلقائي للطلاب
        try:
            if data.get('is_published', True):
                is_hw = data.get('exam_type') == 'homework'
                n_type = 'homework' if is_hw else 'exam'
                n_title = f'واجب جديد: {title}' if is_hw else f'امتحان جديد: {title}'
                n_msg = f'تم نشر واجب منزلي جديد بعنوان "{title}"، يمكنك الحل والتسليم بأريحية.' if is_hw else f'تم نشر اختبار جديد بعنوان "{title}"، ابدأ بحل الاختبار الآن وتحدّ نفسك!'
                n_icon = '📚' if is_hw else '📝'
                supabase.table('honor_notifications').insert({
                    'tenant_id': tenant_id,
                    'type': n_type,
                    'title': n_title,
                    'message': n_msg,
                    'icon': n_icon,
                    'is_read': False
                }).execute()
        except Exception as _ne:
            pass

        return jsonify({'success': True, 'message': 'تم إنشاء الاختبار', 'exam': exam}), 201
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

        exam_data = exam_res.data[0]
        group_ids = _get_exam_group_ids(exam_id)
        if not group_ids and exam_data.get('group_id'):
            group_ids = [exam_data['group_id']]
        exam_data['group_ids'] = group_ids

        questions_res = supabase.table('questions')\
            .select('*')\
            .eq('exam_id', exam_id)\
            .order('order_index')\
            .execute()

        return jsonify({
            'success': True,
            'exam': exam_data,
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
        for field in ['title', 'description', 'subject_id', 'category_id', 'branch_id', 'max_grade',
                      'duration_minutes', 'show_result', 'has_essay', 'essay_prompt',
                      'essay_max_grade', 'is_published', 'exam_type', 'external_link']:
            if field in data:
                update_data[field] = data[field]

        group_ids = data.get('group_ids')
        if group_ids is None and 'group_id' in data:
            group_ids = [data['group_id']] if data['group_id'] else []

        if group_ids is not None:
            update_data['group_id'] = group_ids[0] if group_ids else None
            tenant_id = existing.data[0].get('tenant_id')
            _save_exam_groups(exam_id, group_ids, tenant_id)

        if not update_data and group_ids is None:
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

        tenant_id = exam_res.data[0].get('tenant_id')
        topic = data.get('topic')
        difficulty = data.get('difficulty', 'medium')
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
            'topic': topic,
            'difficulty': difficulty,
            'tenant_id': tenant_id,
        }).execute()

        # إذا طلب حفظ السؤال في بنك الأسئلة
        if data.get('save_to_bank'):
            try:
                e_data = supabase.table('exams').select('subject_id, category_id').eq('id', exam_id).execute()
                sub_id = e_data.data[0].get('subject_id') if e_data.data else None
                cat_id = e_data.data[0].get('category_id') if e_data.data else None
                supabase.table('question_bank').insert({
                    'tenant_id': tenant_id,
                    'subject_id': sub_id,
                    'category_id': cat_id,
                    'topic': topic,
                    'difficulty': difficulty,
                    'question_text': question_text,
                    'question_type': data.get('question_type', 'mcq'),
                    'options': data.get('options'),
                    'correct_answer': data.get('correct_answer'),
                    'points': data.get('points', 1),
                    'question_image_url': data.get('question_image_url'),
                    'correct_answer_image_url': data.get('correct_answer_image_url'),
                }).execute()
            except Exception as bank_err:
                print('Error auto-saving to bank:', bank_err)

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
        primary_group_id = student.get('group_id')
        student_tenant = student.get('tenant_id')

        # جلب كل مجموعات الطالب من student_groups
        sg_res = supabase.table('student_groups').select('group_id').eq('student_id', request.user_id).execute()
        student_group_ids = [r['group_id'] for r in (sg_res.data or []) if r.get('group_id')]
        if primary_group_id and primary_group_id not in student_group_ids:
            student_group_ids.append(primary_group_id)

        exam_type = request.args.get('exam_type') or request.args.get('type')
        query = supabase.table('exams').select('*').eq('is_published', True)
        if student_tenant:
            query = query.eq('tenant_id', student_tenant)
        if exam_type == 'homework':
            query = query.eq('exam_type', 'homework')
        elif exam_type == 'exam':
            query = query.neq('exam_type', 'homework')

        if student_group_ids:
            query = query.or_(f"group_id.is.null,group_id.in.({','.join(map(str, student_group_ids))})")
        else:
            query = query.or_('group_id.is.null')

        # جلب الاختبارات عبر exam_groups لأي من مجموعات الطالب
        eg_exam_ids = []
        if student_group_ids:
            eg_res = supabase.table('exam_groups').select('exam_id').in_('group_id', student_group_ids).execute()
            eg_exam_ids = [r['exam_id'] for r in (eg_res.data or [])]

        exams_res = query.order('created_at', desc=True).execute()
        exams = exams_res.data or []

        existing_ids = {e['id'] for e in exams}
        remaining_ids = [eid for eid in eg_exam_ids if eid not in existing_ids]
        if remaining_ids:
            extra_q = supabase.table('exams').select('*').in_('id', remaining_ids).eq('is_published', True)
            if exam_type == 'homework':
                extra_q = extra_q.eq('exam_type', 'homework')
            elif exam_type == 'exam':
                extra_q = extra_q.neq('exam_type', 'homework')
            extra_res = extra_q.execute()
            exams += (extra_res.data or [])

        # فلترة نهائية: بس الامتحانات الموجّهة لمجموعات الطالب
        exams = filter_content_for_student('exams', exams, get_student_access(request.user_id, request.args.get('group_id', type=int)))

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

        exams = _enrich_categories(exams)
        return jsonify({'success': True, 'count': len(exams), 'exams': exams})
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
        if getattr(request, 'user_role', None) == 'student' and not student_can_access_content('exams', exam):
            return jsonify({'success': False, 'error': 'هذا المحتوى غير متاح لمجموعتك'}), 403

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
        if getattr(request, 'user_role', None) == 'student' and (not exam.get('is_published') or not student_can_access_content('exams', exam)):
            return jsonify({'success': False, 'error': 'هذا المحتوى غير متاح لمجموعتك'}), 403

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
                # "١" و "1" نفس الإجابة (عشان تحويل الرموز للعربي ما يبوظش التصحيح)
                correct = str(q.get('correct_answer', '') or '').translate(_INDIC_TO_WESTERN).strip()
                is_correct = str(student_answer or '').translate(_INDIC_TO_WESTERN).strip() == correct
                final_grade = q['points'] if is_correct else 0
                auto_grade += final_grade
                graded_by = 'auto'
            elif q['question_type'] == 'fill':
                correct = (q.get('correct_answer') or '').translate(_INDIC_TO_WESTERN).strip().lower()
                given = (student_answer or '').translate(_INDIC_TO_WESTERN).strip().lower()
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

        # جلب بيانات الطالب للرد بالإيصال
        student_info = {}
        try:
            st_res = supabase.table('students').select('id, name, barcode, phone, group_id, groups(grade, center)').eq('id', request.user_id).execute()
            if st_res.data:
                student_info = st_res.data[0]
        except Exception:
            pass

        return jsonify({
            'success': True,
            'message': 'تم تسليم الاختبار بنجاح',
            'submission_id': submission_id,
            'auto_grade': auto_grade,
            'max_grade': exam.get('max_grade', 100),
            'has_essay': has_essay,
            'show_result': exam.get('show_result', True),
            'receipt': {
                'submission_id': submission_id,
                'receipt_code': f"REC-{exam_id}-{submission_id}",
                'exam_id': exam_id,
                'exam_title': exam.get('title'),
                'exam_type': exam.get('exam_type', 'exam'),
                'student_name': student_info.get('name', ''),
                'student_barcode': student_info.get('barcode', ''),
                'grade_level': (student_info.get('groups') or {}).get('grade', '') if isinstance(student_info.get('groups'), dict) else '',
                'center_name': (student_info.get('groups') or {}).get('center', '') if isinstance(student_info.get('groups'), dict) else '',
                'submitted_at': new_submission.data[0].get('submitted_at') or new_submission.data[0].get('created_at'),
                'total_grade': auto_grade if exam.get('show_result', True) else None,
                'max_grade': exam.get('max_grade', 100),
                'show_result': exam.get('show_result', True),
                'has_essay': has_essay,
                'questions_count': len(questions_map),
                'answered_count': len(answers)
            }
        }), 201
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Exam Receipt (Student) ============

@exams_bp.route('/api/exams/<int:exam_id>/receipt', methods=['GET'])
@token_required
def get_exam_receipt(exam_id):
    """إيصال تسليم الاختبار للطالب"""
    try:
        submission_res = supabase.table('student_exams')\
            .select('*')\
            .eq('exam_id', exam_id)\
            .eq('student_id', request.user_id)\
            .execute()

        # Fallback: إذا تم تمرير submission_id بدلاً من exam_id
        if not submission_res.data:
            sub_res = supabase.table('student_exams').select('*').eq('id', exam_id).eq('student_id', request.user_id).execute()
            if sub_res.data:
                submission_res = sub_res
                exam_id = sub_res.data[0]['exam_id']
            else:
                return jsonify({'success': False, 'error': 'لم يتم العثور على تسليم لهذا الاختبار'}), 404

        submission = submission_res.data[0]

        exam_res = supabase.table('exams').select('*').eq('id', exam_id).execute()
        exam = exam_res.data[0] if exam_res.data else {}

        student_info = {}
        try:
            st_res = supabase.table('students').select('id, name, barcode, phone, group_id, groups(grade, center)').eq('id', request.user_id).execute()
            if st_res.data:
                student_info = st_res.data[0]
        except Exception:
            pass

        # عدد الأسئلة والإجابات
        questions_res = supabase.table('questions').select('id').eq('exam_id', exam_id).execute()
        total_questions = len(questions_res.data or [])

        answers_res = supabase.table('student_answers').select('id, answer, answer_image_url').eq('student_exam_id', submission['id']).execute()
        answered_count = len([a for a in (answers_res.data or []) if a.get('answer') or a.get('answer_image_url')])

        groups_data = student_info.get('groups') or {}
        grade_name = groups_data.get('grade', '') if isinstance(groups_data, dict) else ''
        center_name = groups_data.get('center', '') if isinstance(groups_data, dict) else ''

        show_result = exam.get('show_result', True)

        receipt = {
            'submission_id': submission['id'],
            'receipt_code': f"REC-{exam_id}-{submission['id']}",
            'exam_id': exam_id,
            'exam_title': exam.get('title', ''),
            'exam_type': exam.get('exam_type', 'exam'),
            'student_id': request.user_id,
            'student_name': student_info.get('name', ''),
            'student_barcode': student_info.get('barcode', ''),
            'grade_level': grade_name,
            'center_name': center_name,
            'submitted_at': submission.get('submitted_at') or submission.get('created_at'),
            'total_grade': submission.get('total_grade') if show_result else None,
            'max_grade': submission.get('max_grade') or exam.get('max_grade', 100),
            'show_result': show_result,
            'questions_count': total_questions,
            'answered_count': answered_count,
            'status': submission.get('status', 'submitted')
        }

        return jsonify({
            'success': True,
            'receipt': receipt
        })
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

        # Fallback: إذا تم تمرير submission_id بدلاً من exam_id
        if not submission_res.data:
            sub_res = supabase.table('student_exams').select('*').eq('id', exam_id).eq('student_id', request.user_id).execute()
            if sub_res.data:
                submission_res = sub_res
                exam_id = sub_res.data[0]['exam_id']
            else:
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
                'correct_answer_image_url': q.get('correct_answer_image_url') if show_correct else None,
                'explanation': q.get('explanation') if show_correct else None,
            })

        return jsonify({
            'success': True,
            'exam': {
                'id': exam.get('id'),
                'title': exam.get('title'),
                'exam_type': exam.get('exam_type', 'exam'),
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
                .select('id, name, barcode, phone, parent_phone')\
                .in_('id', student_ids)\
                .execute()
            students_map = {s['id']: s for s in (students_res.data or [])}

            for s in submissions:
                student = students_map.get(s['student_id'], {})
                s['student_name'] = student.get('name', 'غير معروف')
                s['student_barcode'] = student.get('barcode')
                s['student_phone'] = student.get('phone')
                s['parent_phone'] = student.get('parent_phone')

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

    def _parse_img_urls(val):
        if not val or not isinstance(val, str):
            return []
        s = val.strip()
        if s.startswith('['):
            try:
                arr = json.loads(s)
                if isinstance(arr, list):
                    return [str(u).strip() for u in arr if u]
            except Exception:
                pass
        return [s] if s else []

    student_imgs = _parse_img_urls(student_answer_image_url)
    if len(student_imgs) > 1:
        contents[0] = contents[0] + "\n\nملاحظة: حل الطالب مرفوع على أكثر من صورة (" + str(len(student_imgs)) + " صفحات)، يرجى قراءة جميع الصور بالترتيب وتقييم الحل كاملاً."

    # إضافة الصور
    all_imgs = _parse_img_urls(question_image_url) + student_imgs + _parse_img_urls(correct_answer_image_url)
    for img_url in all_imgs:
        if img_url:
            try:
                with urllib.request.urlopen(img_url, timeout=10) as response:
                    img_data = response.read()
                img_type = 'image/png' if img_url.lower().endswith('.png') else 'image/jpeg'
                contents.append({'mime_type': img_type, 'data': img_data})
            except Exception as e:
                print(f"⚠️ فشل تحميل الصورة: {e}")

    response = generate_with_fallback(contents)

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



# ============ AI Quiz Generation Endpoint ============


def _enforce_arabic_symbols(questions):
    """تحويل أي رموز إنجليزية شاردة تسربت من الذكاء الاصطناعي إلى الرموز العربية المقابلة وتحويل كافة الأرقام إلى هندية"""
    indic_digits_map = {
        '0': '٠', '1': '١', '2': '٢', '3': '٣', '4': '٤',
        '5': '٥', '6': '٦', '7': '٧', '8': '٨', '9': '٩'
    }

    replacements = [
        (r'\\sin\b', r'\\text{جا}'),
        (r'\\cos\b', r'\\text{جتا}'),
        (r'\\tan\b', r'\\text{ظا}'),
        (r'\\cot\b', r'\\text{ظتا}'),
        (r'\\sec\b', r'\\text{قا}'),
        (r'\\csc\b', r'\\text{قتا}'),
        (r'\\ln\b', r'\\text{لو}_هـ'),
        (r'\\log\b', r'\\text{لو}'),
        (r'\\lim\b', r'\\text{نهـا}'),
        (r'\\frac\{dy\}\{dx\}', r'\\frac{ء ص}{ء س}'),
        (r'\bdy/dx\b', r'ء ص / ء س'),
        (r'\bdx\b', r'ء س'),
        (r'\bdy\b', r'ء ص'),
        (r'\be\^', r'هـ^'),
        (r'\be\_', r'هـ_'),
        (r'\bf\(x\)', r'د(س)'),
        (r'\bf\(س\)', r'د(س)'),
        (r'(?<![a-zA-Z\\])\bx\b', r'س'),
        (r'(?<![a-zA-Z\\])\by\b', r'ص'),
        (r'(?<![a-zA-Z\\])\bz\b', r'ع'),
    ]

    def sanitize_str(s):
        if not s or not isinstance(s, str):
            return s
        if '<svg' in s or s.startswith('data:'):
            return s
        res = s
        for pat, repl in replacements:
            res = re.sub(pat, repl, res)
        # تحويل الأرقام الإنجليزية 0-9 إلى أرقام هندية ٠-٩
        res = re.sub(r'(?<!\\[a-zA-Z])\d', lambda m: indic_digits_map.get(m.group(0), m.group(0)), res)
        return res

    for q in questions:
        if 'question' in q:
            q['question'] = sanitize_str(q['question'])
        if 'options' in q and isinstance(q['options'], list):
            q['options'] = [sanitize_str(opt) for opt in q['options']]
        if 'explanation' in q:
            q['explanation'] = sanitize_str(q['explanation'])
        if 'model_answer' in q:
            q['model_answer'] = sanitize_str(q['model_answer'])
    return questions

@exams_bp.route('/api/exams/generate-ai-quiz', methods=['POST'])
@token_required
def generate_ai_quiz():
    """توليد أسئلة واختبار بالذكاء الاصطناعي مع دعم قراءة الصور والـ PDF في الذاكرة دون تخزين"""
    try:
        data = request.get_json() or {}
        topic = data.get('topic', '').strip()
        video_title = data.get('video_title', '').strip()
        video_description = data.get('video_description', '').strip()
        count = int(data.get('count', 5))
        difficulty = data.get('difficulty', 'medium')
        math_symbols = data.get('math_symbols', '').strip()
        grade_name = data.get('grade_name', '').strip()
        # لازم نعرّف المادة قبل التحديد التلقائي (كانت بتتعرف بعده فتعمل خطأ)
        subject_name = (data.get('subject_name') or 'رياضيات').strip()
        # ذكاء التحديد التلقائي لنمط الرموز إن لم يكن محدداً يدوياً
        if not math_symbols or math_symbols == 'auto':
            is_physics = 'فيزياء' in subject_name.lower() or 'physics' in subject_name.lower()
            is_prep = any(p in grade_name for p in ['إعدادي', 'اعدادي', 'ابتدائي', 'عاشر'])
            is_sec = any(s in grade_name for s in ['ثانوي', 'بكالوريا']) or any(s in subject_name for s in ['ثانوي', 'بكالوريا'])
            if is_physics or is_prep:
                math_symbols = 'english'
            elif is_sec:
                math_symbols = 'arabic'
            else:
                math_symbols = 'arabic'
        question_format = data.get('question_format', 'mcq')  # 'mcq', 'essay', or 'mixed'

        # ملف مرفق (صورة أو PDF) يُقرأ في الذاكرة اللحظية فقط دون تخزين دائم
        file_base64 = data.get('file_base64')
        file_mime = data.get('file_mime')

        # دعم جدول مواصفات مستويات الصعوبة المتوازنة
        diff_dist = data.get('difficulty_distribution')
        dist_instruction = ""
        if diff_dist and isinstance(diff_dist, dict):
            easy_c = max(0, int(diff_dist.get('easy', 0)))
            med_c = max(0, int(diff_dist.get('medium', 0)))
            hard_c = max(0, int(diff_dist.get('hard', 0)))
            exp_c = max(0, int(diff_dist.get('expert', 0)))
            total_dist = easy_c + med_c + hard_c + exp_c
            if total_dist > 0:
                count = max(1, min(total_dist, 30))
                dist_instruction = f"""
جدول مواصفات مستويات الصعوبة الإلزامي:
يجب أن يحتوي الاختبار على إجمالي {count} سؤالاً موزعة حرفياً كالتالي:
- عدد {easy_c} أسئلة بمستوى "سهل ومباشر" (easy): تذكر القوانين وتطبيق مباشر فوري.
- عدد {med_c} أسئلة بمستوى "متوسط" (medium): تطبيق وفهم يحتاج خطوة أو خطوتين.
- عدد {hard_c} أسئلة بمستوى "صعب وغير نمطي" (hard): مسائل مركبة وربط أكثر من فكرة وحل مشكلات.
- عدد {exp_c} أسئلة بمستوى "مهارات تفكير عليا" (expert): مسائل إبداعية وتفكير ناقد وتمايز للمتفوقين.

إلزام صارم: يجب أن يحتوي كل كائن سؤال في JSON على حقل "difficulty" بالقيمة المطابقة له بدقة ("easy" | "medium" | "hard" | "expert").
"""
        if not dist_instruction:
            dist_instruction = f"مستوى الأسئلة المطلوب: {difficulty}."

        # السماح بما يصل إلى 30 سؤالاً
        count = max(1, min(count, 30))

        context = f"المادة والمنهج: {subject_name}. الدرس / الموضوع: {topic or video_title}."
        if video_description:
            context += f" تفاصيل وملاحظات إضافية: {video_description}."

        if math_symbols == 'arabic':
            symbols_rule = r"""
قواعد صارمة وإلزامية للرموز والأرقام بالعربية (منهج الرياضيات للثانوية العامة المصرية):
1. حظر تام للرموز الإنجليزية: ممنوع منعاً باتاً استخدام الحروف والرموز الإنجليزية داخل الأسئلة أو المعادلات أو الخيارات أو الشرح (ممنوع نهائياً x, y, z, e, i, sin, cos, tan, log, ln, dx, k, m, n).
2. جدول تحويل الرموز الإجباري (حتى لو كان الملف المرفق أو المسألة الأصلية مكتوبة برموز إنجليزية، حوّلها فوراً وبلا استثناء إلى العربية):
   - المتغيرات: س بدلاً من x ، ص بدلاً من y ، ع بدلاً من z ، ن بدلاً من n ، ر بدلاً من r.
   - المعاملات والثوابت: أ ، ب ، جـ ، ك بدلاً من k ، م بدلاً من m.
   - العدد التخيلي (الأعداد المركبة): ت بدلاً من i (حيث ت² = -1).
   - أساس اللوغاريتم الطبيعي: هـ بدلاً من e (مثال: هـ^س أو هـ^{٢س}).
   - الدوال المثلثية: جا بدلاً من sin ، جتا بدلاً من cos ، ظا بدلاً من tan ، ظتا بدلاً من cot ، قا بدلاً من sec ، قتا بدلاً من csc.
   - اللوغاريتمات: لو بدلاً من log ، لو_هـ بدلاً من ln.
   - التفاضل والتكامل: (ء ص / ء س) أو د(س) أو ص' بدلاً من dy/dx ، ورمز التكامل: \int ... ء س بدلاً من dx.
   - النهايات: \lim_{س \to أ} أو نهـا (س -> أ).
   - رمز التجميع والمتسلسلات: \sum_{ر=1}^{ن}.
   - المتجهات والمصفوفات: \vec{س} ، \vec{ص} أو الصورة الإحداثية (س ، ص).
   - الأرقام الهندية حصراً وإلزامياً (٠ ، ١ ، ٢ ، ٣ ، ٤ ، ٥ ، ٦ ، ٧ ، ٨ ، ٩):
     * يجب كتابة جميع الأعداد والمعاملات والأسس والمقامات والخيارات ونموذج الإجابة بالأرقام الهندية فقط (مثال: $د(س) = س^٢ + ٥ س - ٦ = ٠$ والخيارات: أ) ٠  ب) -٣  جـ) ١٢  د) ٥).
     * حظر تام وقطعي لكتابة أي رقم إنجليزي (0, 1, 2, 3...) عند تفعيل الرموز العربية منعاً لأي تعارض في اتجاه الكتابة أو انعكاس الأقواس والإشارات.
3. اتجاه وتنسيق المعادلات:
   - نص المسألة والمعادلات منسق من اليمين إلى اليسار (RTL).
   - ضع المعادلات بوضوح بين علامات $، واحرص على ألا تنعكس الإشارات أو الأقواس (مثال: $س + ص = ٥$ وليس $٥ = ص + س$).
"""
        else:
            symbols_rule = r"""
قواعد صارمة: الرموز والأرقام بالإنجليزية (Standard English Math / Physics):
1. للمرحلة الإعدادية ومادة الفيزياء: استخدم الرموز والأرقام الإنجليزية القياسية (x, y, z, f(x), i, e, \sin, \cos, \tan, \ln, \lim, dx, 1, 2, 3, 4, 5).
2. لمادة الفيزياء: استخدم رموز الكميات الفيزيائية الإنجليزية المعتمدة (v, a, t, F, E, P, m, r, I, V, R) مع الوحدات العالمية.
3. ضع التعبيرات الرياضية بدقة داخل صيغة LaTeX واضحة بين علامات $ مثل $f(x) = x^2 + 2x - 5$.
4. حظر تام للرموز العربية داخل المعادلات والخيارات: ممنوع (س، ص، جا، جتا، ظا، لو، هـ، ء س) وممنوع الأرقام الهندية (٠١٢٣٤٥٦٧٨٩) نهائياً، حتى لو الملف المرفق مكتوب بالعربي — حوّلها للإنجليزية.
5. نص السؤال نفسه يفضل بالعربي، لكن كل الأرقام والرموز والمتغيرات إنجليزية (مثال: "أوجد قيمة $x$ إذا كان $2x + 3 = 11$"). الكسر العشري بنقطة (0.5) مش فاصلة عربية.
"""

        diagram_guidelines = """
قواعد الرسومات التوضيحية والهندسية والفيزيائية المتقدمة (SVG Diagrams):
- إلزام التوليد والعمق الهندسي والفيزيائي: كلما كان السؤال يتناول (هندسة مستوية أو فراغية، حساب مثلثات، رسم بياني لدالة، اتزان وقوى وميكانيكا، حركة وبكرات، دوائر كهربية، أو ظواهر ضوئية وموجية)، **يجب رسم الشكل التوضيحي بالكامل داخل حقل "diagram_svg"**.
- تفاصيل ومعايير الرسم الاحترافي:
  1. الهندسة وحساب المثلثات:
     * رسم المثلثات (قائم، متساوي الساقين) مع وضع علامة الزاوية القائمة وقوس الزاوية وتسمية الرؤوس (أ، ب، جـ) أو (A, B, C) وأطوال الأضلاع.
     * الدوائر: مركز الدائرة م، الأوتار، المماسات، الزوايا المركزية والمحيطية.
     * الأشكال الرباعية والمجسمات الفراغية: الهرم، المخروط، متوازي المستطيلات بأبعاد واضحة وخطوط منقطة للأوجه الخلفية.
  2. الرسوم البيانية ومحاور الإحداثيات:
     * محاور متعامدة (س، ص) بأسهم وتدريج شبكي خفيف (Grid)، ورسم منحنى الدالة الملون (تربيعية، تكعيبية، مقياس، كسرية، جيبية).
     * تحديد نقطة رأس المنحنى ونقاط التقاطع مع المحاور بوضوح.
  3. الفيزياء والميكانيكا (استاتيكا وديناميكا):
     * مخططات القوى الحرة (Free-Body Diagram): وزن الجسم لأسفل، رد الفعل العمودي، قوة الاحتكاك، قوة الشد، وزاوية ميل السطح.
     * البكرات والكتل المتدلية: الخيوط المشدودة واتجاه الحركة بأسهم واضحة.
     * الدوائر الكهربية: بطارية، مقاومات موصلة على التوالي أو التوازي، ومفاتيح وأجهزة قياس.
  4. لغة الأرقام على الرسم:
     * إذا كان نمط الرموز عربياً: اكتب الأرقام على الرسم بالأرقام الهندية (مثال: ٥ سم ، ٣٠° ، ١٠ نيوتن) ليتناغم تماماً مع نص السؤال.
  5. المواصفات الفنية للـ SVG:
     * viewBox='0 0 380 240' مع خلفية فاتحة وأنيقة (مثل #f8fafc)، خطوط واضحة (stroke-width='2' أو '2.5')، نصوص واضحة ومقروءة بدون أي وسوم script.
- في حالة الأسئلة النظرية أو الجبرية البحتة التي لا تحتاج أي تمثيل هندسي، اجعل "diagram_svg": null.

معايير الدقة الرياضية والفيزيائية الصارمة:
1. الدقة الحسابية التامة: احسب كل ناتج ومعادلة وتعويض رقمي خطوة بخطوة وتأكد 100% أن correct_option_index يطابق الناتج الصحيح علمياً بلا أي خطأ.
2. منطقية المموهات (Distractors): اجعل الخيارات الخاطئة مبنية على أخطاء مفاهيمية أو إشارية شائعة لدى الطلاب، وتجنب الأرقام العشوائية.
3. التفصيل في explanation: اكتب خطوات الحل والقوانين الرياضية/الفيزيائية بوضوح مع وضع الرموز بين علامات $.
"""

        if question_format == 'essay':
            format_instructions = f"""المطلوب: ولّد {count} أسئلة مقالية دقيقة ومحكمة مطابقة لمنهج ({subject_name}).
{dist_instruction}
لكل سؤال مقالي، اكتب نص المسألة، وأرفق كود SVG إذا كان السؤال يحتاج رسماً أو شكلاً، وخطوات الإجابة النموذجية وتوزيع الدرجات.
{diagram_guidelines}
أجب بمصفوفة JSON الصافية التالية فقط:
[
  {{
    "type": "essay",
    "question": "نص المسألة المقالية متضمنا المعادلات بين $",
    "diagram_svg": "<svg viewBox='0 0 360 220' xmlns='http://www.w3.org/2000/svg'>...</svg> (أو null إذا لم يتطلب رسماً)",
    "model_answer": "خطوات الإجابة النموذجية مع القوانين والتعويض",
    "rubric": "توزيع الدرجات (مثال: درجة للقانون والتعويض، درجتان للتبسيط، درجة للناتج النهائي)",
    "points": 4,
    "explanation": "شرح الفكرة والمفاهيم الأساسية"
  }}
]"""
        elif question_format == 'mixed':
            mcq_count = max(1, count - 2) if count >= 4 else max(1, count - 1)
            essay_count = count - mcq_count
            format_instructions = f"""المطلوب: ولّد اختباراً شاملاً مكوّناً من {count} سؤالاً مطابقاً لمنهج ({subject_name}):
- عدد {mcq_count} سؤال اختيار من متعدد (MCQ) مع 4 خيارات وتحديد correct_option_index ورسم SVG عند الحاجة.
- عدد {essay_count} سؤال مقالي يحتاج خطوات حل كاملة مع model_answer و rubric ورسم SVG عند الحاجة.
{dist_instruction}
{diagram_guidelines}
أجب بمصفوفة JSON الصافية التالية فقط:
[
  {{
    "type": "mcq",
    "question": "نص السؤال متضمنا المعادلات بين $",
    "diagram_svg": "<svg viewBox='0 0 360 220' xmlns='http://www.w3.org/2000/svg'>...</svg> (أو null إذا لم يتطلب رسماً)",
    "options": ["أ", "ب", "ج", "د"],
    "correct_option_index": 0,
    "points": 2,
    "explanation": "خطوات الحل"
  }},
  {{
    "type": "essay",
    "question": "نص المسألة المقالية",
    "diagram_svg": null,
    "model_answer": "خطوات الحل النموذجية",
    "rubric": "توزيع الدرجات",
    "points": 4,
    "explanation": "شرح خطوات الحل"
  }}
]"""
        else:
            format_instructions = f"""المطلوب: ولّد {count} أسئلة اختيار من متعدد (MCQ) دقيقة وذكية ومطابقة لمنهج ({subject_name}).
{dist_instruction}
كل سؤال يحتوي على 4 اختيارات بالضبط مع تحديد الإجابة الصحيحة ورسم توضيحي SVG إذا كان السؤال هندسياً أو فيزيائياً أو بيانياً.
{diagram_guidelines}
أجب بمصفوفة JSON الصافية التالية فقط:
[
  {{
    "type": "mcq",
    "question": "نص السؤال الرياضي أو الفيزيائي متضمنا المعادلات بين $",
    "diagram_svg": "<svg viewBox='0 0 360 220' xmlns='http://www.w3.org/2000/svg'>...</svg> (أو null إذا لم يتطلب رسماً)",
    "options": ["اختيار أ", "اختيار ب", "اختيار ج", "اختيار د"],
    "correct_option_index": 0,
    "points": 2,
    "explanation": "خطوات الحل التفصيلية مع المعادلات بين $"
  }}
]"""

        strict_scope = data.get('strict_scope', True)
        file_notice = ""
        content_items = []
        if file_base64 and file_mime:
            try:
                import base64
                raw_bytes = base64.b64decode(file_base64)
                content_items.append({
                    'mime_type': file_mime,
                    'data': raw_bytes
                })
                file_notice = f"""
=====================================================
🛑 قاعدة تربوية ومنهجية ملزمة (الالتزام الحصري بنواتج تعلم الملف المرفق):
مرفق مع هذا الطلب مستند/ورقة تدريبات (PDF أو صورة) لدرس في مادة ({subject_name}).

التعليمات الصارمة والملزمة لأي مادة وأي مرحلة:
1. استخراج نواتج التعلم الحصرية من المرفق (Strict Learning Outcomes):
   - اقرأ الملف المرفق بعناية واستخرج أولاً نواتج التعلم، والقوانين، والمفاهيم، ومصطلحات المنهج المعروضة داخل صفحات هذا الملف حصراً.
   - الملف المرفق يمثل السقف الحصري لمهارات هذا الاختبار: ممنوع منعاً باتاً استدعاء أو إقحام أي مهارات أو موضوعات لم يشرحها هذا المرفق (حتى لو كانت تنتمي لنفس الوحدة أو دروس سابقة/تالية).
   - ركّز بنسبة 100% فقط على الأفكار والتمارين المعروضة في صفحات المرفق وبنفس مستوى الصعوبة ومصطلحات المنهج المكتوبة فيه.

2. توليد مسائل موازية تحاكي نفس الفكرة (Parallel Questions):
   - ولّد مسائل ذكية تحاكي نفس أفكار وتكنيك مسائل وتمارين الملف تماماً مع تنويع الأرقام والدوال والمعاملات الحسابية بذكاء.
   - الهدف هو قياس استيعاب الطالب لنفس نواتج تعلم المرفق بأرقام طازجة ومضبوطة رياضياً، دون حفظ أرقام الشيت نصاً، ودون الخروج نهائياً عن نواتج التعلم المحددة في الورقة.

3. الخيارات ونماذج الإجابة:
   - إذا كان المطلوب MCQ: ضع 4 خيارات حسابية واقعية بدقة واكتب خطوات الحل التفصيلية في explanation.
   - إذا كان المطلوب مقالي (Essay): اكتب مسألة متكاملة الخطوات مع نموذج الإجابة وتوزيع الدرجات rubric.

4. الالتزام التام بالعدد المطلوب:
   - التزم التزاماً صارماً بتوليد العدد المطلوب كاملاً دون نقصان ({count} سؤالاً) جميعها محصورة بنسبة 100% ضمن نواتج تعلم المرفق.
=====================================================
"""
            except Exception as fe:
                print(f"Error decoding attached file: {fe}")

        prompt = f"""أنت معلم وموجه خبير لمادة {subject_name} بأسلوب تعليمي رصين ودقيق.
بيانات الدرس: {context} {file_notice}

قواعد المعادلات والرموز:
- {symbols_rule}
- ضع أي معادلة أو تعبير رياضي بين علامتي دولار بصيغة LaTeX واضحة ونظيفة، مثل: $f(x) = \\sqrt{{x^2 + 1}}$

{format_instructions}
تذكر: أجب بمصفوفة JSON فقط بدون أي مقدمات أو كلام خارج الـ JSON."""

        content_items.append(prompt)

        response = generate_with_fallback(content_items if len(content_items) > 1 else prompt)
        res_text = response.text.strip()

        if res_text.startswith('```json'):
            res_text = res_text[7:]
        elif res_text.startswith('```'):
            res_text = res_text[3:]
        if res_text.endswith('```'):
            res_text = res_text[:-3]
        res_text = res_text.strip()

        # استخراج وتنظيف الـ JSON لمعادلات LaTeX ورموز الهروب \
        json_str = res_text
        json_match = re.search(r'\[.*\]', res_text, re.DOTALL)
        if json_match:
            json_str = json_match.group()

        questions = None
        # 1. محاولة القراءة المباشرة
        try:
            questions = json.loads(json_str, strict=False)
        except Exception:
            pass

        # 2. معالجة شرطات الـ LaTeX المائلة غير المهربة
        if questions is None:
            try:
                # استبدال الـ backslash غير المتبوع بـ " بـ \\
                sanitized = re.sub(r'\\(?!["\\/bfnrt]|u[0-9a-fA-F]{4})', r'\\\\', json_str)
                questions = json.loads(sanitized, strict=False)
            except Exception:
                pass

        if questions is None:
            try:
                # استبدال كل \ ليس بعدها "
                sanitized2 = re.sub(r'\\(?!")', r'\\\\', json_str)
                questions = json.loads(sanitized2, strict=False)
            except Exception:
                pass

        # 3. إذا تعذر كل ما سبق، القراءة بـ json.loads لإظهار الخطأ الدقيق
        if questions is None:
            questions = json.loads(json_str)

        if math_symbols == 'arabic' and questions:
            questions = _enforce_arabic_symbols(questions)

        import urllib.parse
        # التأكد من ملء الحقول الافتراضية ومعالجة رسومات الـ SVG
        for q in questions:
            if 'type' not in q:
                q['type'] = 'essay' if ('options' not in q or not q['options']) else 'mcq'
            if q['type'] == 'mcq' and ('options' not in q or not isinstance(q['options'], list)):
                q['options'] = ["أ", "ب", "ج", "د"]
            
            # تثبيت مستوى الصعوبة الدقيق لكل سؤال
            q['difficulty'] = q.get('difficulty') or difficulty
            
            # تجهيز رسم الـ SVG وحفظه كـ data URI ليعمل في كافة أجزاء المنصة بسلاسة
            diagram_svg = q.get('diagram_svg')
            if diagram_svg and isinstance(diagram_svg, str) and '<svg' in diagram_svg:
                clean_svg = clean_ai_svg(diagram_svg, math_symbols)
                q['diagram_svg'] = clean_svg  # None لو الرسم بايظ
                if clean_svg and not q.get('question_image_url'):
                    q['question_image_url'] = f"data:image/svg+xml;utf8,{urllib.parse.quote(clean_svg)}"
            else:
                q['diagram_svg'] = None

        return jsonify({
            'success': True,
            'count': len(questions),
            'questions': questions
        })
    except Exception as e:
        print(f"AI Quiz Error: {e}")
        msg = str(e)
        # كل الموديلات مشغولة أو وصلنا للحد المجاني (5 طلبات في الدقيقة) — رسالة واضحة بدل النص الإنجليزي الطويل
        if msg.startswith('AI_BUSY') or '429' in msg or '503' in msg or 'quota' in msg.lower():
            wait = re.search(r'retry in ([\d.]+)s', msg)
            secs = int(float(wait.group(1))) + 1 if wait else 60
            return jsonify({
                'success': False,
                'error': f'خدمة الذكاء الاصطناعي مشغولة حالياً أو وصلت للحد المسموح في الدقيقة. استنى حوالي {secs} ثانية وجرب تاني.',
                'retry_after': secs,
            }), 503
        return jsonify({
            'success': False,
            'error': f'فشل توليد الأسئلة: {msg[:300]}'
        }), 500


# ============ AI Extract Questions from File (PDF / Image) ============

@exams_bp.route('/api/exams/extract-from-file', methods=['POST'])
@token_required
def extract_questions_from_file():
    """
    يستقبل ملف PDF أو صورة ويستخرج منه أسئلة الاختيار من متعدد بالذكاء الاصطناعي.
    mode=image (الافتراضي): كل سؤال بيتقص كصورة من الملف زي ما هو (بيحافظ على العربي والرموز والرسومات)،
                            والاختيارات بتبقى أ/ب/ج/د، والذكاء الاصطناعي بيحل السؤال ويختار الإجابة.
    mode=text: الأسئلة بتتكتب كنص (الطريقة القديمة).
    في الحالتين correct_answer = رقم الاختيار ("0","1",...) زي ما التصحيح متوقع.
    """
    try:
        import base64, json as _json, uuid
        from concurrent.futures import ThreadPoolExecutor
        try:
            import pymupdf  # PyMuPDF
        except ImportError:
            return jsonify({'success': False, 'error': 'مكتبة PyMuPDF غير متاحة على السيرفر'}), 500

        if 'file' not in request.files:
            return jsonify({'success': False, 'error': 'يرجى رفع ملف'}), 400

        uploaded_file = request.files['file']
        filename = uploaded_file.filename or ''
        file_bytes = uploaded_file.read()
        try:
            points_per_q = int(request.form.get('points', 2) or 2)
        except ValueError:
            points_per_q = 2
        mode = (request.form.get('mode') or 'image').strip()
        file_ext = filename.rsplit('.', 1)[-1].lower() if '.' in filename else ''

        IMAGE_EXTS = {'jpg': 'jpeg', 'jpeg': 'jpeg', 'png': 'png', 'webp': 'webp', 'bmp': 'bmp', 'gif': 'gif'}
        if file_ext == 'pdf':
            doc = pymupdf.open(stream=file_bytes, filetype='pdf')
        elif file_ext in IMAGE_EXTS:
            # PyMuPDF بيفتح الصور كمستند من صفحة واحدة، فنقدر نقص منها بنفس الطريقة
            doc = pymupdf.open(stream=file_bytes, filetype=IMAGE_EXTS[file_ext])
            if not doc.is_pdf:
                doc = pymupdf.open('pdf', doc.convert_to_pdf())
        else:
            return jsonify({'success': False, 'error': 'صيغة الملف غير مدعومة. استخدم PDF أو JPG أو PNG'}), 400

        MAX_PAGES = 12
        if doc.page_count == 0:
            return jsonify({'success': False, 'error': 'الملف فارغ أو لا يحتوي صفحات'}), 400
        if doc.page_count > MAX_PAGES:
            return jsonify({'success': False, 'error': f'الملف فيه {doc.page_count} صفحة. الحد الأقصى {MAX_PAGES} صفحة في المرة — قسّمه لأكتر من ملف.'}), 400

        # -- صور الصفحات للذكاء الاصطناعي --
        content_parts = []
        for page in doc:
            pix = page.get_pixmap(matrix=pymupdf.Matrix(1.6, 1.6))
            content_parts.append({'inline_data': {'mime_type': 'image/png', 'data': base64.b64encode(pix.tobytes('png')).decode()}})

        if mode == 'image':
            ai_prompt = (
                "أنت نظام متخصص في تحليل أوراق الامتحانات المصورة.\n"
                f"الصور المرفقة هي {doc.page_count} صفحة من امتحان بالترتيب (أول صورة = الصفحة 1).\n\n"
                "مهمتك: لكل سؤال اختيار من متعدد في الصفحات:\n"
                "1. حدّد مكانه بدقة: page (رقم الصفحة يبدأ من 1) و box_2d = [ymin, xmin, ymax, xmax] بقيم من 0 إلى 1000 نسبةً لأبعاد الصفحة.\n"
                "   الصندوق لازم يشمل كل حاجة تخص السؤال: رقمه + نصه + أي رسم أو جدول أو شكل تابع له + كل الاختيارات.\n"
                "   وما يشملش أي جزء من سؤال تاني. خلّي الصندوق واسع شوية بدل ما يقص كلام.\n"
                "2. options_count: عدد الاختيارات المكتوبة في السؤال (من 2 إلى 5).\n"
                "3. حل السؤال بنفسك بدقة خطوة بخطوة، واختار correct_index (0 = أول اختيار، 1 = التاني، ...).\n"
                "4. question_text: نص السؤال مختصر (للمرجعية فقط).\n"
                "5. confidence: رقم من 0 إلى 1 يعبر عن ثقتك في الإجابة.\n\n"
                "تجاهل العناوين والتعليمات وبيانات الطالب والأسئلة المقالية.\n"
                "رتّب الأسئلة بنفس ترتيبها في الورقة.\n"
                "أعد مصفوفة JSON فقط بدون أي نص أو markdown، بالشكل ده:\n"
                '[{"page": 1, "box_2d": [120, 40, 330, 960], "options_count": 4, "correct_index": 2, "question_text": "...", "confidence": 0.9}]'
            )
        else:
            ai_prompt = (
                "أنت نظام ذكاء اصطناعي متخصص في استخراج أسئلة الاختبارات من صور الورق.\n\n"
                "مهمتك: استخرج كل سؤال اختيار من متعدد من الصور وأعد إجابة JSON بالتنسيق:\n"
                '[{"question_text": "نص السؤال كاملاً", "options": ["الاختيار الأول", "التاني", "التالت", "الرابع"], "correct_index": 0}]\n\n'
                "القواعد الصارمة:\n"
                "1. أعد فقط مصفوفة JSON بدون أي نص إضافي أو markdown\n"
                "2. اكتب نص السؤال والاختيارات كما هو مكتوب في الورقة بدون تعديل\n"
                "3. حل السؤال واختار correct_index (0=الأول، 1=الثاني، ...)\n"
                "4. أدرج نص الاختيار بدون الحرف/الرقم الذي يسبقه (أ/ب/ج/د أو 1/2/3/4 أو a/b/c/d)\n"
                "5. استخرج جميع الأسئلة من جميع الصفحات بالترتيب\n"
                "6. المعادلات الرياضية اكتبها بـ LaTeX داخل علامة $ مثل: $x^2 + y^2 = r^2$"
            )
        content_parts.append(ai_prompt)

        # وقت أقل من التوليد العادي عشان يفضل وقت للقص والرفع قبل timeout بتاع السيرفر (120 ثانية)
        response = generate_with_fallback(content_parts, total_budget=80, per_try=70)
        raw = (response.text or '').strip()
        raw = re.sub(r'^```(?:json)?\s*', '', raw, flags=re.MULTILINE)
        raw = re.sub(r'```\s*$', '', raw, flags=re.MULTILINE).strip()
        try:
            questions_data = _json.loads(raw)
        except Exception:
            m = re.search(r'\[.*\]', raw, re.DOTALL)
            if not m:
                return jsonify({'success': False, 'error': 'تعذر فهم رد الذكاء الاصطناعي. جرّب تاني.'}), 500
            questions_data = _json.loads(m.group())
        if not isinstance(questions_data, list) or not questions_data:
            return jsonify({'success': False, 'error': 'ما لقيتش أسئلة اختيار من متعدد في الملف.'}), 400

        ARABIC_LABELS = ['أ', 'ب', 'ج', 'د', 'هـ']
        result_questions = []

        if mode == 'image':
            tenant_id, _ = get_current_tenant_id()
            folder = f"imports/{tenant_id or 'general'}/{uuid.uuid4().hex[:8]}"
            crops = []  # (index_in_result, png_bytes)
            for q in questions_data:
                try:
                    p = int(q.get('page', 1)) - 1
                    ymin, xmin, ymax, xmax = [float(v) for v in q.get('box_2d', [])[:4]]
                except Exception:
                    continue
                if not (0 <= p < doc.page_count) or ymax <= ymin or xmax <= xmin:
                    continue
                page = doc[p]
                pr = page.rect
                w, h = pr.width, pr.height
                pad_x, pad_y = w * 0.02, h * 0.005  # هامش صغير فوق وتحت عشان ما يدخلش جزء من السؤال اللي بعده
                rect = pymupdf.Rect(
                    max(pr.x0, pr.x0 + xmin / 1000 * w - pad_x),
                    max(pr.y0, pr.y0 + ymin / 1000 * h - pad_y),
                    min(pr.x1, pr.x0 + xmax / 1000 * w + pad_x),
                    min(pr.y1, pr.y0 + ymax / 1000 * h + pad_y),
                )
                if rect.is_empty or rect.height < 8:
                    continue
                pix = page.get_pixmap(matrix=pymupdf.Matrix(2.5, 2.5), clip=rect)

                n_opts = q.get('options_count') or 4
                try:
                    n_opts = max(2, min(5, int(n_opts)))
                except ValueError:
                    n_opts = 4
                try:
                    correct_idx = int(q.get('correct_index', 0))
                except ValueError:
                    correct_idx = 0
                if not (0 <= correct_idx < n_opts):
                    correct_idx = 0

                result_questions.append({
                    'question_text': '',
                    'question_type': 'mcq',
                    'options': ARABIC_LABELS[:n_opts],
                    'correct_answer': str(correct_idx),
                    'points': points_per_q,
                    'question_image_url': '',
                    'correct_answer_image_url': '',
                    'ai_reference_text': (q.get('question_text') or '')[:300],
                    'ai_confidence': q.get('confidence'),
                })
                crops.append((len(result_questions) - 1, pix.tobytes('png')))

            def _upload(item):
                idx, png = item
                path = f"{folder}/q{idx + 1:02d}.png"
                supabase.storage.from_('exam-images').upload(path, png, file_options={'content-type': 'image/png'})
                return idx, supabase.storage.from_('exam-images').get_public_url(path)

            with ThreadPoolExecutor(max_workers=6) as pool:
                for idx, url in pool.map(_upload, crops):
                    result_questions[idx]['question_image_url'] = url

            result_questions = [q for q in result_questions if q['question_image_url']]
            if not result_questions:
                return jsonify({'success': False, 'error': 'الذكاء الاصطناعي ما قدرش يحدد أماكن الأسئلة في الملف. جرّب وضع "نص" أو ملف أوضح.'}), 400
        else:
            for q in questions_data:
                opts = [o for o in list(q.get('options') or []) if str(o).strip()][:5]
                while len(opts) < 2:
                    opts.append('')
                try:
                    correct_idx = int(q.get('correct_index', 0))
                except ValueError:
                    correct_idx = 0
                if not (0 <= correct_idx < len(opts)):
                    correct_idx = 0
                result_questions.append({
                    'question_text': q.get('question_text', ''),
                    'question_type': 'mcq',
                    'options': [f"{ARABIC_LABELS[i]} - {opts[i]}" if opts[i] else ARABIC_LABELS[i] for i in range(len(opts))],
                    'correct_answer': str(correct_idx),
                    'points': points_per_q,
                    'question_image_url': '',
                    'correct_answer_image_url': '',
                })

        return jsonify({
            'success': True,
            'count': len(result_questions),
            'questions': result_questions,
            'pages_processed': doc.page_count,
            'mode': mode,
        })

    except Exception as e:
        import traceback
        traceback.print_exc()
        msg = str(e)
        if msg.startswith('AI_BUSY') or '429' in msg or '503' in msg:
            return jsonify({'success': False, 'error': 'خدمة الذكاء الاصطناعي مشغولة حالياً. استنى دقيقة وجرب تاني.'}), 503
        return jsonify({'success': False, 'error': f'خطأ أثناء الاستخراج: {msg[:300]}'}), 500


# ============ AI Math Homework Hint Assistant ============

@exams_bp.route('/api/ai/math-hint', methods=['POST'])
@token_required
def get_math_homework_hint():
    """
    مساعد حل مسائل الرياضيات والواجبات بالذكاء الاصطناعي
    يقدم تلميحة ذكية ومفتاح الحل والخطوة الأولى دون إعطاء الناتج النهائي
    """
    try:
        data = request.get_json() or {}
        problem_text = data.get('problem', '').strip()
        subject_name = data.get('subject', 'الرياضيات للثانوية العامة')

        if not problem_text:
            return jsonify({'success': False, 'error': 'يرجى كتابة نص المسألة'}), 400

        prompt = f"""أنت معلم وموجه خبير للرياضيات للثانوية العامة المصرية بأسلوب أ. وليد الجارحي.
الطالب يحاول حل هذه المسألة ويطلب مساعدة وتوجيهاً:
المسألة:
"{problem_text}"

القواعد التربوية الإلزامية:
1. ممنوع منعاً باتاً إعطاء الناتج النهائي أو الحل الكامل حتى لا يعتمد الطالب على الغش.
2. حدد الفكرة الرياضية الأساسية للمسألة والقانون أو النظرية المستخدمة.
3. وجّه الطالب إلى "الخطوة الأولى" للبدء بالحل (Socratic Hint).
4. اكتب أي معادلة أو صيغة رياضية بدقة تامة بصيغة LaTeX محاطة بعلامتي دولار $ مثل: $f'(x) = 2x$.
5. اجعل الشرح مختصراً وذكياً وفي نقاط واضحة بلهجة تشجيعية دافئة.

أجب باللغة العربية مباشرة وبدون أي مقدمات طويلة."""

        response = generate_with_fallback(prompt)
        hint_text = response.text.strip()

        return jsonify({
            'success': True,
            'hint': hint_text
        })
    except Exception as e:
        print(f"Math Hint Error: {e}")
        return jsonify({'success': False, 'error': f'تعذر توليد التلميحة: {str(e)}'}), 500
