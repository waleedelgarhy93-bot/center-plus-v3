# routes/videos.py - الفيديوهات + المشاهدات + القيود + دعم أكثر من مجموعة + الأكواد + ربط الاختبارات
from flask import Blueprint, jsonify, request
from datetime import datetime

from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    check_tenant_ownership,
)

videos_bp = Blueprint('videos', __name__)


# ============ Helper: حفظ/تحديث مجموعات الفيديو ============

def _save_video_groups(video_id, group_ids, tenant_id):
    """احذف القديم وأضف الجديد في video_groups"""
    supabase.table('video_groups').delete().eq('video_id', video_id).execute()
    if group_ids:
        rows = [{'video_id': video_id, 'group_id': gid, 'tenant_id': tenant_id} for gid in group_ids]
        supabase.table('video_groups').insert(rows).execute()


def _get_video_group_ids(video_id):
    """جلب قائمة group_id لفيديو معين"""
    res = supabase.table('video_groups').select('group_id').eq('video_id', video_id).execute()
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


def _enrich_videos_with_groups(videos):
    """إضافة group_ids وgroup_names لكل فيديو في القائمة"""
    if not videos:
        return videos

    video_ids = [v['id'] for v in videos]
    vg_res = supabase.table('video_groups').select('video_id, group_id').in_('video_id', video_ids).execute()
    vg_data = vg_res.data or []

    all_group_ids = list({r['group_id'] for r in vg_data})
    groups_map = {}
    if all_group_ids:
        grps = supabase.table('groups').select('id, grade, center').in_('id', all_group_ids).execute()
        groups_map = {g['id']: f"{g['grade']} - {g['center']}" for g in (grps.data or [])}

    video_groups_map = {}
    for r in vg_data:
        vid = r['video_id']
        gid = r['group_id']
        if vid not in video_groups_map:
            video_groups_map[vid] = []
        video_groups_map[vid].append(gid)

    for v in videos:
        gids = video_groups_map.get(v['id'], [])
        v['group_ids'] = gids
        v['group_names'] = [groups_map.get(gid, '') for gid in gids]
        v['group_id'] = gids[0] if gids else v.get('group_id')
        v['group_name'] = groups_map.get(v['group_id']) if v['group_id'] else None

    return videos


def _enrich_linked_exams(videos):
    """إضافة اسم الاختبار المرتبط إن وجد"""
    if not videos:
        return videos
    exam_ids = list({v['linked_exam_id'] for v in videos if v.get('linked_exam_id')})
    if exam_ids:
        exams_res = supabase.table('exams').select('id, title').in_('id', exam_ids).execute()
        exams_map = {e['id']: e['title'] for e in (exams_res.data or [])}
        for v in videos:
            v['linked_exam_title'] = exams_map.get(v.get('linked_exam_id'))
    else:
        for v in videos:
            v['linked_exam_title'] = None
    return videos


# ============ Videos CRUD (Teacher) ============

@videos_bp.route('/api/videos', methods=['GET'])
@token_required
def get_videos():
    try:
        query = supabase.table('videos').select('*').order('created_at', desc=True)
        query = apply_tenant_filter(query)
        response = query.execute()
        videos = response.data or []

        if videos:
            subject_ids = list({v['subject_id'] for v in videos if v.get('subject_id')})
            subjects_map = {}
            if subject_ids:
                subs = supabase.table('subjects').select('id, name').in_('id', subject_ids).execute()
                subjects_map = {s['id']: s['name'] for s in (subs.data or [])}
            for v in videos:
                v['subject_name'] = subjects_map.get(v.get('subject_id'))

            videos = _enrich_videos_with_groups(videos)
            videos = _enrich_categories(videos)
            videos = _enrich_linked_exams(videos)

        return jsonify({'success': True, 'count': len(videos), 'videos': videos})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@videos_bp.route('/api/videos/<int:video_id>', methods=['GET'])
@token_required
def get_video(video_id):
    try:
        video_res = supabase.table('videos').select('*').eq('id', video_id).execute()
        if not video_res.data:
            return jsonify({'success': False, 'error': 'الفيديو غير موجود'}), 404
        video = video_res.data[0]
        if not check_tenant_ownership(video):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        if video.get('subject_id'):
            s = supabase.table('subjects').select('name').eq('id', video['subject_id']).execute()
            if s.data: video['subject_name'] = s.data[0]['name']
        if video.get('category_id'):
            c = supabase.table('categories').select('name').eq('id', video['category_id']).execute()
            if c.data: video['category_name'] = c.data[0]['name']
        if video.get('linked_exam_id'):
            e = supabase.table('exams').select('title').eq('id', video['linked_exam_id']).execute()
            if e.data: video['linked_exam_title'] = e.data[0]['title']

        video['group_ids'] = _get_video_group_ids(video_id)

        return jsonify({'success': True, 'video': video})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@videos_bp.route('/api/videos', methods=['POST'])
@token_required
def create_video():
    try:
        data = request.get_json() or {}
        title = data.get('title')
        video_url = data.get('video_url')

        if not title or not video_url:
            return jsonify({'success': False, 'error': 'title و video_url مطلوبين'}), 400

        tenant_id = get_tenant_for_insert()
        group_ids = data.get('group_ids', [])

        if not group_ids and data.get('group_id'):
            group_ids = [data['group_id']]

        new_video = supabase.table('videos').insert({
            'title': title,
            'description': data.get('description'),
            'video_url': video_url,
            'subject_id': data.get('subject_id'),
            'category_id': int(data['category_id']) if data.get('category_id') else None,
            'group_id': group_ids[0] if group_ids else None,
            'teacher_id': request.user_id,
            'max_views': data.get('max_views', 0),
            'access_type': data.get('access_type', 'free'),
            'access_code': data.get('access_code') if data.get('access_type') == 'code' else None,
            'price': float(data.get('price', 0)) if data.get('price') else 0,
            'linked_exam_id': int(data['linked_exam_id']) if data.get('linked_exam_id') else None,
            'require_exam': bool(data.get('require_exam', False)),
            'tenant_id': tenant_id,
        }).execute()

        video = new_video.data[0] if new_video.data else None

        if video and group_ids:
            _save_video_groups(video['id'], group_ids, tenant_id)
            video['group_ids'] = group_ids

        return jsonify({'success': True, 'message': 'تم إضافة الفيديو', 'video': video}), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@videos_bp.route('/api/videos/<int:video_id>', methods=['PUT'])
@token_required
def update_video(video_id):
    try:
        existing = supabase.table('videos').select('tenant_id').eq('id', video_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'الفيديو غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        update_data = {}
        for field in ['title', 'description', 'video_url', 'subject_id', 'category_id', 'max_views', 'access_type', 'access_code', 'price', 'linked_exam_id', 'require_exam']:
            if field in data:
                if field == 'category_id':
                    update_data[field] = int(data[field]) if data[field] else None
                elif field == 'linked_exam_id':
                    update_data[field] = int(data[field]) if data[field] else None
                elif field == 'require_exam':
                    update_data[field] = bool(data[field])
                elif field == 'price':
                    update_data[field] = float(data[field]) if data[field] else 0
                else:
                    update_data[field] = data[field]

        group_ids = data.get('group_ids')
        if group_ids is None and 'group_id' in data:
            group_ids = [data['group_id']] if data['group_id'] else []

        if group_ids is not None:
            update_data['group_id'] = group_ids[0] if group_ids else None
            tenant_id = existing.data[0].get('tenant_id')
            _save_video_groups(video_id, group_ids, tenant_id)

        if update_data:
            result = supabase.table('videos').update(update_data).eq('id', video_id).execute()
            video = result.data[0] if result.data else None
        else:
            video = None

        return jsonify({'success': True, 'message': 'تم التعديل', 'video': video})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@videos_bp.route('/api/videos/<int:video_id>', methods=['DELETE'])
@token_required
def delete_video(video_id):
    try:
        existing = supabase.table('videos').select('tenant_id').eq('id', video_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'الفيديو غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('videos').delete().eq('id', video_id).execute()
        return jsonify({'success': True, 'message': 'تم الحذف'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Student Videos ============

@videos_bp.route('/api/student/videos', methods=['GET'])
@token_required
def get_student_videos():
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

        # جلب IDs الفيديوهات المخصصة لأي من مجموعات الطالب عبر video_groups
        vg_video_ids = []
        if student_group_ids:
            vg_res = supabase.table('video_groups').select('video_id').in_('group_id', student_group_ids).execute()
            vg_video_ids = [r['video_id'] for r in (vg_res.data or [])]

        # الفيديوهات حسب سنتر الطالب
        query = supabase.table('videos').select('*').eq('tenant_id', student_tenant)

        if student_group_ids:
            query = query.or_(f"group_id.is.null,group_id.in.({','.join(map(str, student_group_ids))})")
        else:
            query = query.or_('group_id.is.null')

        videos_res = query.order('created_at', desc=True).execute()
        videos = videos_res.data or []

        # إضافة الفيديوهات من video_groups التي لم تُجلب بعد
        existing_ids = {v['id'] for v in videos}
        remaining_ids = [vid for vid in vg_video_ids if vid not in existing_ids]
        if remaining_ids:
            extra_res = supabase.table('videos').select('*').in_('id', remaining_ids).execute()
            videos += (extra_res.data or [])

        # جلب المشاهدات الحالية للطالب
        views_res = supabase.table('video_views').select('*').eq('student_id', request.user_id).execute()
        views_map = {v['video_id']: v for v in (views_res.data or [])}

        # جلب الحظر
        restrictions_res = supabase.table('video_restrictions')\
            .select('*')\
            .eq('student_id', request.user_id)\
            .eq('is_blocked', True)\
            .execute()
        blocked_ids = {r['video_id'] for r in (restrictions_res.data or [])}

        # جلب الفيديوهات المفعلة بكود للطالب
        unlocks_res = supabase.table('video_unlocks').select('video_id').eq('student_id', request.user_id).execute()
        unlocked_video_ids = {u['video_id'] for u in (unlocks_res.data or [])}

        # جلب الامتحانات التي قدمها الطالب
        student_exams_res = supabase.table('student_exams').select('exam_id').eq('student_id', request.user_id).execute()
        submitted_exam_ids = {se['exam_id'] for se in (student_exams_res.data or [])}

        result = []
        for v in videos:
            if v['id'] in blocked_ids:
                continue

            view_data = views_map.get(v['id'], {'views_count': 0})
            views_count = view_data.get('views_count', 0)
            max_views = v.get('max_views', 0)

            # حالة القفل بكود
            access_type = v.get('access_type') or 'free'
            is_locked_by_code = (access_type == 'code') and (v['id'] not in unlocked_video_ids)

            # حالة القفل باختبار
            linked_exam_id = v.get('linked_exam_id')
            require_exam = bool(v.get('require_exam', False))
            has_submitted_exam = (linked_exam_id in submitted_exam_ids) if linked_exam_id else True
            is_locked_by_exam = bool(require_exam and linked_exam_id and not has_submitted_exam)

            views_exceeded = bool(max_views > 0 and views_count >= max_views)

            can_watch = (not is_locked_by_code) and (not is_locked_by_exam) and (not views_exceeded)

            result.append({
                **v,
                'views_count': views_count,
                'views_remaining': (max_views - views_count) if max_views > 0 else -1,
                'is_blocked': False,
                'access_type': access_type,
                'is_locked_by_code': is_locked_by_code,
                'is_locked_by_exam': is_locked_by_exam,
                'has_submitted_exam': has_submitted_exam,
                'views_exceeded': views_exceeded,
                'can_watch': can_watch,
            })

        if result:
            subject_ids = list({v['subject_id'] for v in result if v.get('subject_id')})
            subjects_map = {}
            if subject_ids:
                subs = supabase.table('subjects').select('id, name').in_('id', subject_ids).execute()
                subjects_map = {s['id']: s['name'] for s in (subs.data or [])}
            for v in result:
                v['subject_name'] = subjects_map.get(v.get('subject_id'))

        result = _enrich_categories(result)
        result = _enrich_linked_exams(result)

        return jsonify({'success': True, 'count': len(result), 'videos': result})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@videos_bp.route('/api/videos/<int:video_id>/view', methods=['POST'])
@token_required
def record_video_view(video_id):
    try:
        student_id = request.user_id
        video_res = supabase.table('videos').select('*').eq('id', video_id).execute()
        if not video_res.data:
            return jsonify({'success': False, 'error': 'الفيديو غير موجود'}), 404

        video = video_res.data[0]
        max_views = video.get('max_views', 0)

        # 1. فحص الحظر
        block_res = supabase.table('video_restrictions')\
            .select('*').eq('video_id', video_id).eq('student_id', student_id).eq('is_blocked', True).execute()
        if block_res.data:
            return jsonify({'success': False, 'error': 'الفيديو مقفول لك من قبل المدرس'}), 403

        # 2. فحص كود التفعيل
        if (video.get('access_type') == 'code'):
            unlock_res = supabase.table('video_unlocks').select('id').eq('video_id', video_id).eq('student_id', student_id).execute()
            if not unlock_res.data:
                return jsonify({'success': False, 'error': 'هذا الفيديو يتطلب كود تفعيل للمشاهدة', 'is_locked_by_code': True}), 403

        # 3. فحص شرط حل الاختبار
        if video.get('require_exam') and video.get('linked_exam_id'):
            exam_id = video['linked_exam_id']
            exam_res = supabase.table('student_exams').select('id').eq('exam_id', exam_id).eq('student_id', student_id).execute()
            if not exam_res.data:
                return jsonify({'success': False, 'error': 'يجب حل الامتحان المرتبط بهذا الفيديو أولاً قبل المشاهدة', 'is_locked_by_exam': True}), 403

        # 4. فحص وتسجيل المشاهدات
        view_res = supabase.table('video_views')\
            .select('*').eq('video_id', video_id).eq('student_id', student_id).execute()

        now = datetime.utcnow().isoformat()
        if view_res.data:
            view = view_res.data[0]
            views_count = view.get('views_count', 0)
            if max_views > 0 and views_count >= max_views:
                return jsonify({'success': False, 'error': 'انتهت عدد المشاهدات المتاحة لك لهذا الفيديو',
                                'views_count': views_count, 'max_views': max_views}), 403
            supabase.table('video_views').update({'views_count': views_count + 1, 'last_viewed_at': now})\
                .eq('id', view['id']).execute()
            new_count = views_count + 1
        else:
            supabase.table('video_views').insert({
                'video_id': video_id, 'student_id': student_id,
                'views_count': 1, 'last_viewed_at': now, 'tenant_id': video.get('tenant_id'),
            }).execute()
            new_count = 1

        return jsonify({'success': True, 'message': 'تم تسجيل المشاهدة', 'views_count': new_count,
                        'max_views': max_views, 'views_remaining': (max_views - new_count) if max_views > 0 else -1})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@videos_bp.route('/api/videos/<int:video_id>/unlock', methods=['POST'])
@token_required
def unlock_video_with_code(video_id):
    """طالب يفك قفل الفيديو بكود"""
    try:
        student_id = request.user_id
        data = request.get_json() or {}
        entered_code = str(data.get('code', '')).strip()

        if not entered_code:
            return jsonify({'success': False, 'error': 'يرجى إدخال كود التفعيل'}), 400

        video_res = supabase.table('videos').select('*').eq('id', video_id).execute()
        if not video_res.data:
            return jsonify({'success': False, 'error': 'الفيديو غير موجود'}), 404

        video = video_res.data[0]
        actual_code = str(video.get('access_code') or '').strip()

        if not actual_code:
            return jsonify({'success': False, 'error': 'هذا الفيديو متاح مجاناً ولا يتطلب كود'}), 400

        if entered_code.lower() != actual_code.lower():
            return jsonify({'success': False, 'error': 'كود التفعيل غير صحيح، تأكد من الكود وأعد المحاولة'}), 400

        # تحقق هل تم التفعيل مسبقاً
        existing = supabase.table('video_unlocks').select('id').eq('video_id', video_id).eq('student_id', student_id).execute()
        if not existing.data:
            supabase.table('video_unlocks').insert({
                'video_id': video_id,
                'student_id': student_id,
                'tenant_id': video.get('tenant_id'),
                'code_used': entered_code,
                'unlocked_by': 'code'
            }).execute()

        return jsonify({'success': True, 'message': '🎉 تم تفعيل الفيديو بنجاح! يمكنك الآن مشاهدته.'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@videos_bp.route('/api/videos/<int:video_id>/block', methods=['POST'])
@token_required
def block_video_for_student(video_id):
    try:
        data = request.get_json() or {}
        student_id = data.get('student_id')
        is_blocked = data.get('is_blocked', True)
        if not student_id:
            return jsonify({'success': False, 'error': 'student_id مطلوب'}), 400

        video_res = supabase.table('videos').select('tenant_id').eq('id', video_id).execute()
        if not video_res.data:
            return jsonify({'success': False, 'error': 'الفيديو غير موجود'}), 404
        if not check_tenant_ownership(video_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        existing = supabase.table('video_restrictions')\
            .select('*').eq('video_id', video_id).eq('student_id', student_id).execute()
        if existing.data:
            supabase.table('video_restrictions').update({'is_blocked': is_blocked})\
                .eq('id', existing.data[0]['id']).execute()
        else:
            supabase.table('video_restrictions').insert({
                'video_id': video_id, 'student_id': student_id,
                'is_blocked': is_blocked, 'tenant_id': video_res.data[0].get('tenant_id'),
            }).execute()

        return jsonify({'success': True, 'message': 'تم حظر الطالب من الفيديو' if is_blocked else 'تم إلغاء حظر الطالب'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@videos_bp.route('/api/videos/<int:video_id>/reset-views', methods=['POST'])
@token_required
def reset_video_views(video_id):
    """المدرس يعيد تصفير مشاهدات طالب معين"""
    try:
        data = request.get_json() or {}
        student_id = data.get('student_id')
        if not student_id:
            return jsonify({'success': False, 'error': 'student_id مطلوب'}), 400

        video_res = supabase.table('videos').select('tenant_id').eq('id', video_id).execute()
        if not video_res.data:
            return jsonify({'success': False, 'error': 'الفيديو غير موجود'}), 404
        if not check_tenant_ownership(video_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('video_views').update({'views_count': 0}).eq('video_id', video_id).eq('student_id', student_id).execute()

        return jsonify({'success': True, 'message': 'تم إعادة تصفير مشاهدات الطالب بنجاح'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@videos_bp.route('/api/videos/<int:video_id>/manual-unlock', methods=['POST'])
@token_required
def manual_unlock_video(video_id):
    """المدرس يفتح الفيديو لطالب يدوياً بدون كود"""
    try:
        data = request.get_json() or {}
        student_id = data.get('student_id')
        if not student_id:
            return jsonify({'success': False, 'error': 'student_id مطلوب'}), 400

        video_res = supabase.table('videos').select('tenant_id').eq('id', video_id).execute()
        if not video_res.data:
            return jsonify({'success': False, 'error': 'الفيديو غير موجود'}), 404
        if not check_tenant_ownership(video_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        existing = supabase.table('video_unlocks').select('id').eq('video_id', video_id).eq('student_id', student_id).execute()
        if not existing.data:
            supabase.table('video_unlocks').insert({
                'video_id': video_id,
                'student_id': student_id,
                'tenant_id': video_res.data[0].get('tenant_id'),
                'code_used': 'MANUAL_BY_TEACHER',
                'unlocked_by': 'teacher'
            }).execute()

        return jsonify({'success': True, 'message': 'تم فتح وتفعيل الفيديو للطالب بنجاح'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@videos_bp.route('/api/videos/<int:video_id>/views', methods=['GET'])
@token_required
def get_video_views(video_id):
    try:
        video_res = supabase.table('videos').select('*').eq('id', video_id).execute()
        if not video_res.data:
            return jsonify({'success': False, 'error': 'الفيديو غير موجود'}), 404
        video = video_res.data[0]
        if not check_tenant_ownership(video):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        # إثراء بيانات الفيديو
        if video.get('subject_id'):
            s = supabase.table('subjects').select('name').eq('id', video['subject_id']).execute()
            if s.data: video['subject_name'] = s.data[0]['name']
        if video.get('category_id'):
            c = supabase.table('categories').select('name').eq('id', video['category_id']).execute()
            if c.data: video['category_name'] = c.data[0]['name']
        if video.get('linked_exam_id'):
            e = supabase.table('exams').select('title').eq('id', video['linked_exam_id']).execute()
            if e.data: video['linked_exam_title'] = e.data[0]['title']

        video['group_ids'] = _get_video_group_ids(video_id)

        # جلب سجل المشاهدات
        views_res = supabase.table('video_views').select('*').eq('video_id', video_id).order('last_viewed_at', desc=True).execute()
        views = views_res.data or []

        # جلب الحظر
        restrictions_res = supabase.table('video_restrictions').select('*').eq('video_id', video_id).execute()
        restrictions_map = {r['student_id']: r.get('is_blocked', False) for r in (restrictions_res.data or [])}

        # جلب التفعيلات
        unlocks_res = supabase.table('video_unlocks').select('*').eq('video_id', video_id).execute()
        unlocks_map = {u['student_id']: u for u in (unlocks_res.data or [])}

        all_student_ids = list({v['student_id'] for v in views} | set(restrictions_map.keys()) | set(unlocks_map.keys()))
        students_map = {}
        if all_student_ids:
            students_res = supabase.table('students').select('id, name, phone, code').in_('id', all_student_ids).execute()
            students_map = {s['id']: s for s in (students_res.data or [])}

        enriched_views = []
        for v in views:
            sid = v['student_id']
            st = students_map.get(sid, {})
            enriched_views.append({
                'id': v['id'],
                'student_id': sid,
                'student_name': st.get('name', 'غير معروف'),
                'student_phone': st.get('phone', ''),
                'student_code': st.get('code', ''),
                'views_count': v.get('views_count', 0),
                'last_viewed_at': v.get('last_viewed_at'),
                'created_at': v.get('created_at'),
                'is_blocked': restrictions_map.get(sid, False),
                'is_unlocked': sid in unlocks_map,
                'unlocked_info': unlocks_map.get(sid)
            })

        return jsonify({
            'success': True,
            'video': video,
            'views': enriched_views,
            'total_views': sum(v['views_count'] for v in enriched_views),
            'unique_viewers': len(enriched_views)
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500
