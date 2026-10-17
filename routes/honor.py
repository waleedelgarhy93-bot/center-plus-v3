# routes/honor.py - لوحة الشرف + الإشعارات
from flask import Blueprint, jsonify, request
from datetime import datetime, timedelta

from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_current_tenant_id,
    check_tenant_ownership,
)

honor_bp = Blueprint('honor', __name__)


# ============ Helper: حساب نقاط الطالب ============

def _calculate_student_score(student_id, tenant_id, weights=None):
    """يحسب نقاط الطالب من كل المؤشرات"""
    if weights is None:
        weights = {
            'exams': 40,
            'attendance': 20,
            'videos': 20,
            'homework': 20,
        }

    # 1) الدرجات (من student_exams)
    exams_res = supabase.table('student_exams')\
        .select('total_grade, max_grade')\
        .eq('student_id', student_id)\
        .execute()
    
    exams_data = exams_res.data or []
    total_grade = sum(float(e.get('total_grade', 0)) for e in exams_data)
    total_max = sum(float(e.get('max_grade', 0)) for e in exams_data)
    exams_score = (total_grade / total_max * 100) if total_max > 0 else 0

    # 2) الحضور (آخر 30 يوم)
    thirty_days_ago = (datetime.now() - timedelta(days=30)).strftime('%Y-%m-%d')
    attendance_res = supabase.table('attendance')\
        .select('id')\
        .eq('student_id', student_id)\
        .gte('date', thirty_days_ago)\
        .execute()
    
    attendance_count = len(attendance_res.data or [])
    # كل 30 يوم = 100% لو حضر 12 مرة على الأقل
    attendance_score = min((attendance_count / 12) * 100, 100) if attendance_count > 0 else 0

    # 3) الفيديوهات
    videos_res = supabase.table('video_views')\
        .select('views_count')\
        .eq('student_id', student_id)\
        .execute()
    
    videos_data = videos_res.data or []
    videos_watched = len(videos_data)
    videos_score = min((videos_watched / 10) * 100, 100) if videos_watched > 0 else 0

    # 4) الواجبات (بديل مؤقت: عدد الامتحانات المُسلّمة)
    homework_score = min((len(exams_data) / 5) * 100, 100) if len(exams_data) > 0 else 0

    # 5) نقاط يدوية من المعلم
    points_res = supabase.table('honor_points')\
        .select('points')\
        .eq('student_id', student_id)\
        .execute()
    
    points_data = points_res.data or []
    manual_points = sum(int(p.get('points', 0)) for p in points_data)

    # 6) الحساب النهائي
    total_score = (
        (exams_score * weights['exams'] / 100) +
        (attendance_score * weights['attendance'] / 100) +
        (videos_score * weights['videos'] / 100) +
        (homework_score * weights['homework'] / 100)
    )

    # إضافة النقاط اليدوية (تحويلها لنسبة)
    total_score += manual_points

    return {
        'total_score': round(total_score, 1),
        'exams_score': round(exams_score, 1),
        'attendance_score': round(attendance_score, 1),
        'videos_score': round(videos_score, 1),
        'homework_score': round(homework_score, 1),
        'manual_points': manual_points,
        'exams_count': len(exams_data),
        'attendance_count': attendance_count,
        'videos_count': videos_watched,
    }


# ============ Leaderboard ============

@honor_bp.route('/api/honor/leaderboard', methods=['GET'])
@token_required
def get_leaderboard():
    """لوحة الشرف - ترتيب الطلاب"""
    try:
        tenant_id, role = get_current_tenant_id()

        # جلب الإعدادات
        weights = {'exams': 40, 'attendance': 20, 'videos': 20, 'homework': 20}
        if tenant_id:
            settings_res = supabase.table('honor_settings')\
                .select('*')\
                .eq('tenant_id', tenant_id)\
                .execute()
            if settings_res.data:
                s = settings_res.data[0]
                weights = {
                    'exams': s.get('weight_exams', 40),
                    'attendance': s.get('weight_attendance', 20),
                    'videos': s.get('weight_videos', 20),
                    'homework': s.get('weight_homework', 20),
                }

        # جلب الطلاب (مفلتر حسب السنتر)
        query = supabase.table('students').select('id, name, barcode, group_id')
        query = apply_tenant_filter(query)
        students = query.execute().data or []

        # جلب أسماء المجموعات
        group_ids = list({s.get('group_id') for s in students if s.get('group_id')})
        groups_map = {}
        if group_ids:
            groups_res = supabase.table('groups').select('id, grade, center').in_('id', group_ids).execute()
            groups_map = {g['id']: f"{g['grade']} - {g['center']}" for g in (groups_res.data or [])}

        # حساب النقاط
        leaderboard = []
        for s in students:
            score_data = _calculate_student_score(s['id'], tenant_id, weights)
            leaderboard.append({
                'student_id': s['id'],
                'student_name': s['name'],
                'student_barcode': s.get('barcode'),
                'group_name': groups_map.get(s.get('group_id'), '—'),
                **score_data,
            })

        leaderboard.sort(key=lambda x: x['total_score'], reverse=True)

        # إضافة الترتيب
        for i, s in enumerate(leaderboard):
            s['rank'] = i + 1

        return jsonify({
            'success': True,
            'count': len(leaderboard),
            'weights': weights,
            'leaderboard': leaderboard,
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@honor_bp.route('/api/honor/my-rank', methods=['GET'])
@token_required
def get_my_rank():
    """ترتيب الطالب الحالي"""
    try:
        student_id = request.user_id

        student_res = supabase.table('students').select('*').eq('id', student_id).execute()
        if not student_res.data:
            return jsonify({'success': False, 'error': 'الطالب غير موجود'}), 404

        student = student_res.data[0]
        tenant_id = student.get('tenant_id')

        # جلب الإعدادات
        weights = {'exams': 40, 'attendance': 20, 'videos': 20, 'homework': 20}
        if tenant_id:
            settings_res = supabase.table('honor_settings')\
                .select('*')\
                .eq('tenant_id', tenant_id)\
                .execute()
            if settings_res.data:
                s = settings_res.data[0]
                weights = {
                    'exams': s.get('weight_exams', 40),
                    'attendance': s.get('weight_attendance', 20),
                    'videos': s.get('weight_videos', 20),
                    'homework': s.get('weight_homework', 20),
                }

        # حساب نقاط الطالب الحالي
        my_score = _calculate_student_score(student_id, tenant_id, weights)

        # جلب كل الطلاب في السنتر لحساب الترتيب
        students_res = supabase.table('students')\
            .select('id')\
            .eq('tenant_id', tenant_id)\
            .execute()
        students = students_res.data or []

        all_scores = []
        for s in students:
            sc = _calculate_student_score(s['id'], tenant_id, weights)
            all_scores.append({'id': s['id'], 'score': sc['total_score']})

        all_scores.sort(key=lambda x: x['score'], reverse=True)
        my_rank = next((i + 1 for i, s in enumerate(all_scores) if s['id'] == student_id), 0)

        # جلب أعلى 3 طلاب (للمقارنة)
        top_3 = []
        for i, s in enumerate(all_scores[:3]):
            student_info = supabase.table('students').select('name').eq('id', s['id']).execute()
            if student_info.data:
                top_3.append({
                    'rank': i + 1,
                    'student_name': student_info.data[0]['name'],
                    'score': s['score'],
                })

        return jsonify({
            'success': True,
            'my_rank': my_rank,
            'my_score': my_score,
            'total_students': len(all_scores),
            'top_3': top_3,
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Notifications ============

@honor_bp.route('/api/honor/notifications', methods=['GET'])
@token_required
def get_notifications():
    """جلب إشعارات السنتر"""
    try:
        query = supabase.table('honor_notifications')\
            .select('*')\
            .order('created_at', desc=True)\
            .limit(50)
        query = apply_tenant_filter(query)
        notifications = query.execute().data or []

        # جلب أسماء الطلاب
        student_ids = list({n['student_id'] for n in notifications if n.get('student_id')})
        students_map = {}
        if student_ids:
            students_res = supabase.table('students').select('id, name').in_('id', student_ids).execute()
            students_map = {s['id']: s['name'] for s in (students_res.data or [])}

        for n in notifications:
            n['student_name'] = students_map.get(n.get('student_id'), None)

        return jsonify({
            'success': True,
            'count': len(notifications),
            'notifications': notifications,
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@honor_bp.route('/api/honor/notifications/mark-read', methods=['POST'])
@token_required
def mark_notifications_read():
    """تعليم كل الإشعارات كمقروءة"""
    try:
        tenant_id, _ = get_current_tenant_id()
        if not tenant_id:
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('honor_notifications')\
            .update({'is_read': True})\
            .eq('tenant_id', tenant_id)\
            .eq('is_read', False)\
            .execute()

        return jsonify({'success': True, 'message': 'تم التعليم كمقروء'})

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Manual Points (Teacher) ============

@honor_bp.route('/api/honor/points/add', methods=['POST'])
@token_required
def add_points():
    """إضافة نقاط يدوية لطالب (للمعلم فقط)"""
    try:
        data = request.get_json() or {}
        student_id = data.get('student_id')
        points = data.get('points')
        reason = data.get('reason', '')

        if not student_id or points is None:
            return jsonify({'success': False, 'error': 'student_id و points مطلوبين'}), 400

        # التحقق من الطالب
        student_res = supabase.table('students').select('tenant_id').eq('id', student_id).execute()
        if not student_res.data:
            return jsonify({'success': False, 'error': 'الطالب غير موجود'}), 404

        if not check_tenant_ownership(student_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        # إضافة النقاط
        tenant_id = student_res.data[0].get('tenant_id')
        new_point = supabase.table('honor_points').insert({
            'tenant_id': tenant_id,
            'student_id': student_id,
            'points': int(points),
            'reason': reason,
            'added_by': request.user_id,
        }).execute()

        # إنشاء إشعار
        student_info = supabase.table('students').select('name').eq('id', student_id).execute()
        student_name = student_info.data[0]['name'] if student_info.data else 'طالب'

        if points > 0:
            title = f"🏆 {student_name} حصل على {points} نقطة"
            icon = '🏆'
        else:
            title = f"⚠️ {student_name} خسر {abs(points)} نقطة"
            icon = '⚠️'

        supabase.table('honor_notifications').insert({
            'tenant_id': tenant_id,
            'student_id': student_id,
            'type': 'honor',
            'title': title,
            'message': reason,
            'icon': icon,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم إضافة النقاط',
            'point': new_point.data[0] if new_point.data else None,
        }), 201

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@honor_bp.route('/api/honor/points/<int:student_id>', methods=['GET'])
@token_required
def get_student_points(student_id):
    """جلب نقاط طالب معين"""
    try:
        student_res = supabase.table('students').select('tenant_id').eq('id', student_id).execute()
        if not student_res.data:
            return jsonify({'success': False, 'error': 'الطالب غير موجود'}), 404

        if not check_tenant_ownership(student_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        points_res = supabase.table('honor_points')\
            .select('*')\
            .eq('student_id', student_id)\
            .order('created_at', desc=True)\
            .execute()

        return jsonify({
            'success': True,
            'count': len(points_res.data or []),
            'points': points_res.data or [],
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@honor_bp.route('/api/honor/points/<int:point_id>', methods=['DELETE'])
@token_required
def delete_point(point_id):
    """حذف نقطة يدوية"""
    try:
        point_res = supabase.table('honor_points').select('tenant_id').eq('id', point_id).execute()
        if not point_res.data:
            return jsonify({'success': False, 'error': 'النقطة غير موجودة'}), 404

        if not check_tenant_ownership(point_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('honor_points').delete().eq('id', point_id).execute()
        return jsonify({'success': True, 'message': 'تم الحذف'})

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Settings (Weights) ============

@honor_bp.route('/api/honor/settings', methods=['GET'])
@token_required
def get_settings():
    """جلب إعدادات الأوزان"""
    try:
        tenant_id, _ = get_current_tenant_id()
        if not tenant_id:
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        res = supabase.table('honor_settings').select('*').eq('tenant_id', tenant_id).execute()

        if res.data:
            return jsonify({'success': True, 'settings': res.data[0]})

        # القيم الافتراضية
        return jsonify({
            'success': True,
            'settings': {
                'tenant_id': tenant_id,
                'weight_exams': 40,
                'weight_attendance': 20,
                'weight_videos': 20,
                'weight_homework': 20,
            }
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@honor_bp.route('/api/honor/settings', methods=['PUT'])
@token_required
def update_settings():
    """تعديل إعدادات الأوزان"""
    try:
        tenant_id, _ = get_current_tenant_id()
        if not tenant_id:
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        weights = {
            'weight_exams': int(data.get('weight_exams', 40)),
            'weight_attendance': int(data.get('weight_attendance', 20)),
            'weight_videos': int(data.get('weight_videos', 20)),
            'weight_homework': int(data.get('weight_homework', 20)),
        }

        # التحقق إن المجموع = 100
        total = sum(weights.values())
        if total != 100:
            return jsonify({
                'success': False,
                'error': f'مجموع الأوزان لازم يكون 100 (الحالي: {total})'
            }), 400

        # upsert
        existing = supabase.table('honor_settings').select('tenant_id').eq('tenant_id', tenant_id).execute()

        if existing.data:
            result = supabase.table('honor_settings')\
                .update({**weights, 'updated_at': datetime.utcnow().isoformat()})\
                .eq('tenant_id', tenant_id)\
                .execute()
        else:
            result = supabase.table('honor_settings').insert({
                'tenant_id': tenant_id,
                **weights,
            }).execute()

        return jsonify({
            'success': True,
            'message': 'تم التحديث',
            'settings': result.data[0] if result.data else None,
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500