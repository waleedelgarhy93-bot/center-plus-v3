# routes/categories.py - إدارة الأقسام والفروع والوحدات
from flask import Blueprint, jsonify, request
from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    check_tenant_ownership,
    get_student_access,
    student_matches_grade_subject,
    normalize_grade,
)

categories_bp = Blueprint('categories', __name__)


def grade_key(grade):
    """مفتاح موحّد للصف: "تالتة ثانوي" و "الثالث الثانوي" نفس الصف،
    أما "الأول الثانوي (الصف العاشر)" صف مختلف (منهج تاني)."""
    g = (grade or '').strip()
    n = normalize_grade(g)
    if not n:
        return ('raw', g)
    return (n[0], n[1], '(' in g)


_STAGE_ORDER = {'pri': 0, 'prep': 1, 'sec': 2}


def grade_sort_value(grade):
    k = grade_key(grade)
    if k[0] == 'raw':
        return 999
    return _STAGE_ORDER.get(k[0], 9) * 100 + k[1] * 10 + (5 if k[2] else 0)


# ===================== CATEGORIES (القديمة للتوافق) =====================

@categories_bp.route('/api/categories', methods=['GET'])
@token_required
def get_categories():
    try:
        subject_id = request.args.get('subject_id')
        grade = request.args.get('grade')
        query = supabase.table('categories').select('*').order('created_at')
        query = apply_tenant_filter(query)

        if subject_id:
            query = query.eq('subject_id', int(subject_id))
        if grade:
            query = query.eq('grade', grade)

        res = query.execute()
        categories = res.data or []

        # الطالب: بس فروع/تصنيفات مادته وصفه
        if getattr(request, 'user_role', None) == 'student':
            _access = get_student_access(request.user_id, request.args.get('group_id', type=int))
            categories = [r for r in categories if student_matches_grade_subject(r, _access)]

        if categories:
            sub_ids = list({c['subject_id'] for c in categories if c.get('subject_id')})
            if sub_ids:
                subs_res = supabase.table('subjects').select('id, name').in_('id', sub_ids).execute()
                subs_map = {s['id']: s['name'] for s in (subs_res.data or [])}
                for c in categories:
                    c['subject_name'] = subs_map.get(c.get('subject_id'))

        return jsonify({'success': True, 'count': len(categories), 'categories': categories})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@categories_bp.route('/api/categories', methods=['POST'])
@token_required
def create_category():
    try:
        data = request.get_json() or {}
        name = (data.get('name') or '').strip()
        subject_id = data.get('subject_id')
        grade = data.get('grade')

        if not name:
            return jsonify({'success': False, 'error': 'اسم القسم مطلوب'}), 400

        tenant_id = get_tenant_for_insert()

        insert_payload = {
            'name': name,
            'subject_id': int(subject_id) if subject_id else None,
            'tenant_id': tenant_id,
        }
        if grade:
            insert_payload['grade'] = grade

        new_cat = supabase.table('categories').insert(insert_payload).execute()

        return jsonify({
            'success': True,
            'message': 'تم إضافة القسم بنجاح',
            'category': new_cat.data[0] if new_cat.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@categories_bp.route('/api/categories/<int:cat_id>', methods=['DELETE'])
@token_required
def delete_category(cat_id):
    try:
        existing = supabase.table('categories').select('tenant_id').eq('id', cat_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'القسم غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('categories').delete().eq('id', cat_id).execute()
        return jsonify({'success': True, 'message': 'تم الحذف'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ===================== BRANCHES (النظام الهرمي الجديد) =====================

@categories_bp.route('/api/branches', methods=['GET'])
@token_required
def get_branches_alias():
    try:
        grade = request.args.get('grade')
        subject_id = request.args.get('subject_id', type=int)

        query = supabase.table('branches').select('*').order('sort_order').order('created_at')
        query = apply_tenant_filter(query)

        if subject_id:
            query = query.eq('subject_id', subject_id)

        res = query.execute()
        branches = res.data or []

        # فلتر الصف بالمعنى مش بالنص ("تالتة ثانوي" = "الثالث الثانوي")
        if grade:
            wanted = grade_key(grade)
            branches = [b for b in branches if grade_key(b.get('grade')) == wanted]

        # الطالب: بس فروع/تصنيفات مادته وصفه
        if getattr(request, 'user_role', None) == 'student':
            _access = get_student_access(request.user_id, request.args.get('group_id', type=int))
            branches = [r for r in branches if student_matches_grade_subject(r, _access)]

        if branches:
            sub_ids = list({b['subject_id'] for b in branches if b.get('subject_id')})
            if sub_ids:
                subs_res = supabase.table('subjects').select('id, name').in_('id', sub_ids).execute()
                subs_map = {s['id']: s['name'] for s in (subs_res.data or [])}
                for b in branches:
                    b['subject_name'] = subs_map.get(b.get('subject_id'))

        return jsonify({'success': True, 'count': len(branches), 'branches': branches})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@categories_bp.route('/api/branches/grades-overview', methods=['GET'])
@token_required
def get_grades_overview_alias():
    """الصفوف الدراسية: نفس الصفوف والمواد اللي في صفحات الفيديوهات/الاختبارات/الواجبات/المذكرات.
    - الصفوف من مجموعات السنتر (بنفس أسمائها) + أي صف عنده وحدات بس.
    - "تالتة ثانوي" و "الثالث الثانوي" صف واحد.
    - لكل وحدة: عدد المحتوى اللي جواها (فيديوهات/اختبارات/واجبات/مذكرات)."""
    try:
        groups = apply_tenant_filter(supabase.table('groups').select('grade, subject_id')).execute().data or []
        all_branches = apply_tenant_filter(
            supabase.table('branches').select('*').order('sort_order').order('created_at')
        ).execute().data or []
        subs_map = {
            s['id']: s
            for s in (apply_tenant_filter(supabase.table('subjects').select('id, name, color, icon')).execute().data or [])
        }

        # عدد المحتوى في كل وحدة
        branch_ids = [b['id'] for b in all_branches]
        counts = {bid: {'videos': 0, 'exams': 0, 'homework': 0, 'documents': 0} for bid in branch_ids}
        if branch_ids:
            for v in supabase.table('videos').select('branch_id').in_('branch_id', branch_ids).execute().data or []:
                counts[v['branch_id']]['videos'] += 1
            for e in supabase.table('exams').select('branch_id, exam_type').in_('branch_id', branch_ids).execute().data or []:
                counts[e['branch_id']]['homework' if e.get('exam_type') == 'homework' else 'exams'] += 1
            for d in supabase.table('documents').select('branch_id').in_('branch_id', branch_ids).execute().data or []:
                counts[d['branch_id']]['documents'] += 1

        grades = {}  # key -> {'label', 'subjects': {sid: {...}}}

        def ensure_grade(label):
            k = grade_key(label)
            if k not in grades:
                grades[k] = {'label': (label or '').strip(), 'subjects': {}}
            return grades[k]

        def ensure_subject(entry, sid):
            if sid not in entry['subjects']:
                s = subs_map.get(sid, {})
                entry['subjects'][sid] = {
                    'subject_id': sid,
                    'subject_name': (s.get('name') or '').strip(),
                    'subject_color': s.get('color') or 'blue',
                    'branches_count': 0,
                    'content_count': 0,
                    'branches': [],
                }
            return entry['subjects'][sid]

        # الصفوف والمواد من المجموعات أولاً (عشان الاسم يبقى نفس اسم المجموعة)
        for g in groups:
            if (g.get('grade') or '').strip():
                entry = ensure_grade(g['grade'])
                if g.get('subject_id'):
                    ensure_subject(entry, g['subject_id'])

        for b in all_branches:
            entry = ensure_grade(b.get('grade'))
            sub = ensure_subject(entry, b.get('subject_id'))
            c = counts.get(b['id'], {})
            total = sum(c.values())
            sub['branches_count'] += 1
            sub['content_count'] += total
            sub['branches'].append({
                'id': b['id'],
                'name': b['name'],
                'sort_order': b.get('sort_order', 0),
                'counts': c,
                'content_count': total,
            })

        result = []
        for entry in sorted(grades.values(), key=lambda e: (grade_sort_value(e['label']), e['label'])):
            subjects = sorted(entry['subjects'].values(), key=lambda x: x.get('subject_name') or '')
            result.append({'grade': entry['label'], 'subjects': subjects})

        return jsonify({'success': True, 'grades': result})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@categories_bp.route('/api/branches', methods=['POST'])
@token_required
def create_branch_alias():
    try:
        data = request.get_json() or {}
        name = (data.get('name') or '').strip()
        subject_id = data.get('subject_id')
        grade = (data.get('grade') or '').strip()

        if not name:
            return jsonify({'success': False, 'error': 'اسم الفرع مطلوب'}), 400
        if not grade:
            return jsonify({'success': False, 'error': 'الصف مطلوب'}), 400
        if not subject_id:
            return jsonify({'success': False, 'error': 'المادة مطلوبة'}), 400

        tenant_id = get_tenant_for_insert()

        new_branch = supabase.table('branches').insert({
            'name': name,
            'subject_id': int(subject_id),
            'grade': grade,
            'sort_order': int(data.get('sort_order', 0)),
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم إضافة الفرع بنجاح',
            'branch': new_branch.data[0] if new_branch.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@categories_bp.route('/api/branches/<int:branch_id>', methods=['PUT'])
@token_required
def update_branch_alias(branch_id):
    try:
        existing = supabase.table('branches').select('tenant_id').eq('id', branch_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'الفرع غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        update_data = {}
        for field in ['name', 'sort_order', 'grade', 'subject_id']:
            if field in data:
                update_data[field] = data[field]

        if not update_data:
            return jsonify({'success': False, 'error': 'لا توجد بيانات للتعديل'}), 400

        result = supabase.table('branches').update(update_data).eq('id', branch_id).execute()

        return jsonify({
            'success': True,
            'message': 'تم التعديل',
            'branch': result.data[0] if result.data else None
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@categories_bp.route('/api/branches/<int:branch_id>', methods=['DELETE'])
@token_required
def delete_branch_alias(branch_id):
    try:
        existing = supabase.table('branches').select('tenant_id').eq('id', branch_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'الفرع غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('branches').delete().eq('id', branch_id).execute()
        return jsonify({'success': True, 'message': 'تم حذف الفرع'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@categories_bp.route('/api/branches/<int:branch_id>/content', methods=['GET'])
@token_required
def get_branch_content_alias(branch_id):
    try:
        branch_res = supabase.table('branches').select('*').eq('id', branch_id).execute()
        if not branch_res.data:
            return jsonify({'success': False, 'error': 'الفرع غير موجود'}), 404

        branch = branch_res.data[0]
        if not check_tenant_ownership(branch):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        videos_res = supabase.table('videos').select('id, title, description, video_url, created_at').eq('branch_id', branch_id).execute()
        exams_res = supabase.table('exams').select('id, title, description, created_at, max_grade').eq('branch_id', branch_id).neq('exam_type', 'homework').execute()

        try:
            hw_res = supabase.table('exams').select('id, title, description, created_at, max_grade').eq('branch_id', branch_id).eq('exam_type', 'homework').execute()
            homework = hw_res.data or []
        except Exception:
            homework = []

        try:
            docs_res = supabase.table('documents').select('id, title, description, file_url, created_at').eq('branch_id', branch_id).execute()
            documents = docs_res.data or []
        except Exception:
            documents = []

        return jsonify({
            'success': True,
            'branch': branch,
            'content': {
                'videos': videos_res.data or [],
                'exams': exams_res.data or [],
                'homework': homework,
                'documents': documents,
            },
            'counts': {
                'videos': len(videos_res.data or []),
                'exams': len(exams_res.data or []),
                'homework': len(homework),
                'documents': len(documents),
            }
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500
