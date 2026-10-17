# routes/videos.py - الفيديوهات + المشاهدات + القيود
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
            group_ids = list({v['group_id'] for v in videos if v.get('group_id')})

            subjects_map = {}
            if subject_ids:
                subs = supabase.table('subjects').select('id, name').in_('id', subject_ids).execute()
                subjects_map = {s['id']: s['name'] for s in (subs.data or [])}

            groups_map = {}
            if group_ids:
                grps = supabase.table('groups').select('id, grade, center').in_('id', group_ids).execute()
                groups_map = {g['id']: f"{g['grade']} - {g['center']}" for g in (grps.data or [])}

            for v in videos:
                v['subject_name'] = subjects_map.get(v.get('subject_id'))
                v['group_name'] = groups_map.get(v.get('group_id'))

        return jsonify({
            'success': True,
            'count': len(videos),
            'videos': videos
        })
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

        new_video = supabase.table('videos').insert({
            'title': title,
            'description': data.get('description'),
            'video_url': video_url,
            'subject_id': data.get('subject_id'),
            'group_id': data.get('group_id'),
            'teacher_id': request.user_id,
            'max_views': data.get('max_views', 0),
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم إضافة الفيديو',
            'video': new_video.data[0] if new_video.data else None
        }), 201
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
        for field in ['title', 'description', 'video_url', 'subject_id', 'group_id', 'max_views']:
            if field in data:
                update_data[field] = data[field]

        if not update_data:
            return jsonify({'success': False, 'error': 'مفيش بيانات للتعديل'}), 400

        result = supabase.table('videos').update(update_data).eq('id', video_id).execute()

        return jsonify({
            'success': True,
            'message': 'تم التعديل',
            'video': result.data[0] if result.data else None
        })
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
        group_id = student.get('group_id')
        student_tenant = student.get('tenant_id')

        subject_id = None
        if group_id:
            group_res = supabase.table('groups').select('subject_id').eq('id', group_id).execute()
            if group_res.data:
                subject_id = group_res.data[0].get('subject_id')

        # الفيديوهات حسب سنتر الطالب
        query = supabase.table('videos').select('*').eq('tenant_id', student_tenant)

        if group_id and subject_id:
            query = query.or_(f'group_id.eq.{group_id},subject_id.eq.{subject_id},group_id.is.null')
        elif group_id:
            query = query.or_(f'group_id.eq.{group_id},group_id.is.null')
        elif subject_id:
            query = query.or_(f'subject_id.eq.{subject_id},subject_id.is.null')

        videos_res = query.order('created_at', desc=True).execute()
        videos = videos_res.data or []

        views_res = supabase.table('video_views').select('*').eq('student_id', request.user_id).execute()
        views_map = {v['video_id']: v for v in (views_res.data or [])}

        restrictions_res = supabase.table('video_restrictions')\
            .select('*')\
            .eq('student_id', request.user_id)\
            .eq('is_blocked', True)\
            .execute()
        blocked_ids = {r['video_id'] for r in (restrictions_res.data or [])}

        result = []
        for v in videos:
            if v['id'] in blocked_ids:
                continue

            view_data = views_map.get(v['id'], {'views_count': 0})
            views_count = view_data.get('views_count', 0)
            max_views = v.get('max_views', 0)

            if max_views > 0 and views_count >= max_views:
                continue

            result.append({
                **v,
                'views_count': views_count,
                'views_remaining': (max_views - views_count) if max_views > 0 else -1,
                'is_blocked': False,
            })

        if result:
            subject_ids = list({v['subject_id'] for v in result if v.get('subject_id')})
            subjects_map = {}
            if subject_ids:
                subs = supabase.table('subjects').select('id, name').in_('id', subject_ids).execute()
                subjects_map = {s['id']: s['name'] for s in (subs.data or [])}

            for v in result:
                v['subject_name'] = subjects_map.get(v.get('subject_id'))

        return jsonify({
            'success': True,
            'count': len(result),
            'videos': result
        })
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

        block_res = supabase.table('video_restrictions')\
            .select('*')\
            .eq('video_id', video_id)\
            .eq('student_id', student_id)\
            .eq('is_blocked', True)\
            .execute()

        if block_res.data:
            return jsonify({'success': False, 'error': 'الفيديو مقفول لك'}), 403

        view_res = supabase.table('video_views')\
            .select('*')\
            .eq('video_id', video_id)\
            .eq('student_id', student_id)\
            .execute()

        now = datetime.utcnow().isoformat()

        if view_res.data:
            view = view_res.data[0]
            views_count = view.get('views_count', 0)

            if max_views > 0 and views_count >= max_views:
                return jsonify({
                    'success': False,
                    'error': 'انتهت عدد المشاهدات',
                    'views_count': views_count,
                    'max_views': max_views
                }), 403

            supabase.table('video_views').update({
                'views_count': views_count + 1,
                'last_viewed_at': now
            }).eq('id', view['id']).execute()

            new_count = views_count + 1
        else:
            supabase.table('video_views').insert({
                'video_id': video_id,
                'student_id': student_id,
                'views_count': 1,
                'last_viewed_at': now,
                'tenant_id': video.get('tenant_id'),
            }).execute()

            new_count = 1

        return jsonify({
            'success': True,
            'message': 'تم تسجيل المشاهدة',
            'views_count': new_count,
            'max_views': max_views,
            'views_remaining': (max_views - new_count) if max_views > 0 else -1
        })
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

        # التحقق من الفيديو
        video_res = supabase.table('videos').select('tenant_id').eq('id', video_id).execute()
        if not video_res.data:
            return jsonify({'success': False, 'error': 'الفيديو غير موجود'}), 404
        if not check_tenant_ownership(video_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        existing = supabase.table('video_restrictions')\
            .select('*')\
            .eq('video_id', video_id)\
            .eq('student_id', student_id)\
            .execute()

        if existing.data:
            result = supabase.table('video_restrictions')\
                .update({'is_blocked': is_blocked})\
                .eq('id', existing.data[0]['id'])\
                .execute()
        else:
            result = supabase.table('video_restrictions').insert({
                'video_id': video_id,
                'student_id': student_id,
                'is_blocked': is_blocked,
                'tenant_id': video_res.data[0].get('tenant_id'),
            }).execute()

        return jsonify({
            'success': True,
            'message': 'مقفول' if is_blocked else 'مفتوح'
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@videos_bp.route('/api/videos/<int:video_id>/views', methods=['GET'])
@token_required
def get_video_views(video_id):
    try:
        video_res = supabase.table('videos').select('tenant_id').eq('id', video_id).execute()
        if not video_res.data:
            return jsonify({'success': False, 'error': 'الفيديو غير موجود'}), 404
        if not check_tenant_ownership(video_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        views_res = supabase.table('video_views').select('*').eq('video_id', video_id).execute()
        views = views_res.data or []

        if views:
            student_ids = [v['student_id'] for v in views]
            students_res = supabase.table('students').select('id, name').in_('id', student_ids).execute()
            students_map = {s['id']: s['name'] for s in (students_res.data or [])}

            for v in views:
                v['student_name'] = students_map.get(v['student_id'], 'غير معروف')

        return jsonify({'success': True, 'views': views})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500