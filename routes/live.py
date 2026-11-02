# -*- coding: utf-8 -*-
# routes/live.py - غرفة البث المباشر التفاعلية (Live Streaming Room)
from flask import Blueprint, jsonify, request
import time
import uuid
from datetime import datetime, timezone

from db import supabase
from helpers import (
    token_required,
    get_current_tenant_id,
    get_tenant_for_insert,
)

live_bp = Blueprint('live', __name__)

# مخزن سريع للجلسات الحية والشات والكويز اللحظي
# tenant_id -> state
LIVE_STATE = {}

def _get_tenant_state(tenant_id):
    tid = str(tenant_id or 'default')
    if tid not in LIVE_STATE:
        LIVE_STATE[tid] = {
            'session': {
                'is_active': False,
                'title': 'بث مباشر سنتر لوبيتال',
                'stream_url': '',
                'group_ids': [],
                'allow_chat': True,
                'started_at': None,
            },
            'messages': [],
            'attendance': set(),
            'quiz': None, # { id, question, options, correct_index, answers: { student_id: option_index } }
        }
    return LIVE_STATE[tid]


# ============ إعدادات وبدء البث (المعلم) ============

@live_bp.route('/api/live/session', methods=['GET'])
@token_required
def get_live_session():
    """جلب حالة البث المباشر الحالي"""
    try:
        tenant_id, role = get_current_tenant_id()
        state = _get_tenant_state(tenant_id)
        session = state['session']

        # إذا كان طالباً، نتحقق من مجموعته إذا كان البث مخصصاً لمجموعات معينة
        if role == 'student' and session.get('is_active') and session.get('group_ids'):
            student_res = supabase.table('students').select('group_id').eq('id', request.user_id).execute()
            if student_res.data:
                sg_id = student_res.data[0].get('group_id')
                if sg_id and sg_id not in session['group_ids']:
                    return jsonify({
                        'success': True,
                        'session': {
                            'is_active': False,
                            'title': session['title'],
                            'message': 'هذا البث مخصص لمجموعة أخرى'
                        }
                    })

        return jsonify({
            'success': True,
            'session': session,
            'viewer_count': len(state['attendance']),
            'has_active_quiz': state['quiz'] is not None
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@live_bp.route('/api/live/session', methods=['POST'])
@token_required
def update_live_session():
    """بدء أو تحديث أو إيقاف البث المباشر (المعلم)"""
    try:
        tenant_id, role = get_current_tenant_id()
        if role != 'teacher':
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        state = _get_tenant_state(tenant_id)

        is_active = data.get('is_active', True)
        title = data.get('title', state['session'].get('title', 'بث مباشر سنتر لوبيتال'))
        stream_url = data.get('stream_url', state['session'].get('stream_url', '')).strip()
        group_ids = data.get('group_ids', [])
        allow_chat = data.get('allow_chat', True)

        if is_active and not stream_url:
            return jsonify({'success': False, 'error': 'يرجى وضع رابط البث (YouTube Live أو غيره)'}), 400

        state['session'] = {
            'is_active': is_active,
            'title': title,
            'stream_url': stream_url,
            'group_ids': group_ids,
            'allow_chat': allow_chat,
            'started_at': datetime.now(timezone.utc).isoformat() if is_active else None,
        }

        # إذا بدأ بث جديد، نعيد تصفير الحضور والكويز
        if is_active:
            state['attendance'] = set()
            state['quiz'] = None

        return jsonify({
            'success': True,
            'message': 'تم تحديث حالة البث بنجاح',
            'session': state['session']
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ الشات المباشر (Live Chat) ============

@live_bp.route('/api/live/messages', methods=['GET'])
@token_required
def get_live_messages():
    """جلب رسائل الشات الحية"""
    try:
        tenant_id, role = get_current_tenant_id()
        state = _get_tenant_state(tenant_id)
        # نرجع آخر 50 رسالة
        msgs = state['messages'][-50:]
        return jsonify({
            'success': True,
            'count': len(msgs),
            'messages': msgs,
            'allow_chat': state['session'].get('allow_chat', True)
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@live_bp.route('/api/live/messages', methods=['POST'])
@token_required
def send_live_message():
    """إرسال رسالة في الشات المباشر"""
    try:
        tenant_id, role = get_current_tenant_id()
        state = _get_tenant_state(tenant_id)

        if not state['session'].get('is_active'):
            return jsonify({'success': False, 'error': 'لا يوجد بث مباشر نشط حالياً'}), 400

        if not state['session'].get('allow_chat', True) and role != 'teacher':
            return jsonify({'success': False, 'error': 'المعلم قام بتعطيل الشات مؤقتاً'}), 403

        data = request.get_json() or {}
        text = data.get('text', '').strip()
        if not text:
            return jsonify({'success': False, 'error': 'الرسالة فارغة'}), 400

        # جلب اسم المرسل
        sender_name = 'مستخدم'
        if role == 'teacher':
            sender_name = 'أ/ وليد الجارحي (المعلم) 🌟'
        else:
            student_res = supabase.table('students').select('name').eq('id', request.user_id).execute()
            if student_res.data:
                sender_name = student_res.data[0]['name']

        msg = {
            'id': str(uuid.uuid4())[:8],
            'sender_id': request.user_id,
            'sender_name': sender_name,
            'role': role,
            'text': text[:300], # بحد أقصى 300 حرف
            'created_at': datetime.now(timezone.utc).strftime('%I:%M %p'),
            'is_pinned': False,
        }

        state['messages'].append(msg)
        if len(state['messages']) > 200:
            state['messages'] = state['messages'][-100:]

        return jsonify({'success': True, 'message': msg}), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@live_bp.route('/api/live/messages/<msg_id>', methods=['DELETE'])
@token_required
def delete_live_message(msg_id):
    """حذف رسالة من الشات (المعلم فقط)"""
    try:
        tenant_id, role = get_current_tenant_id()
        if role != 'teacher':
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        state = _get_tenant_state(tenant_id)
        state['messages'] = [m for m in state['messages'] if m['id'] != msg_id]
        return jsonify({'success': True, 'message': 'تم حذف الرسالة'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ تسجيل حضور البث المباشر (Live Attendance) ============

@live_bp.route('/api/live/attendance', methods=['POST'])
@token_required
def record_live_attendance():
    """تسجيل حضور الطالب في البث المباشر وإعطائه نقاط تفاعل"""
    try:
        tenant_id, role = get_current_tenant_id()
        if role != 'student':
            return jsonify({'success': True, 'message': 'حساب معلم'})

        state = _get_tenant_state(tenant_id)
        student_id = request.user_id

        # إذا لم يكن مسجلاً في هذه الجلسة الحية
        if student_id not in state['attendance']:
            state['attendance'].add(student_id)
            # إضافة نقاط تشجيعية في لوحة الشرف
            try:
                supabase.table('honor_points').insert({
                    'student_id': student_id,
                    'points': 5,
                    'reason': 'حضور والتزام في البث المباشر 🔴',
                    'tenant_id': tenant_id
                }).execute()
            except Exception:
                pass

        return jsonify({
            'success': True,
            'message': 'تم تسجيل حضورك في البث المباشر بنجاح 🎉',
            'viewer_count': len(state['attendance'])
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ سؤال التحدي اللحظي المباشر (Live Pop-Quiz) ============

@live_bp.route('/api/live/quiz', methods=['POST'])
@token_required
def launch_live_quiz():
    """المعلم يطلق سؤال كويز سريع مباشر يظهر لجميع الطلاب بعد تنازلي"""
    try:
        tenant_id, role = get_current_tenant_id()
        if role != 'teacher':
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        question = data.get('question', '').strip()
        options = data.get('options', [])
        correct_index = int(data.get('correct_index', 0))
        duration_seconds = int(data.get('duration_seconds', 60))

        if not question or len(options) < 2:
            return jsonify({'success': False, 'error': 'السؤال والاختيارات مطلوبة'}), 400

        state = _get_tenant_state(tenant_id)
        state['quiz'] = {
            'id': str(uuid.uuid4())[:8],
            'question': question,
            'options': options,
            'correct_index': correct_index,
            'duration_seconds': duration_seconds,
            'expires_at': time.time() + duration_seconds,
            'answers': {}, # student_id: { option_index, is_correct, student_name }
        }

        return jsonify({'success': True, 'quiz': state['quiz']})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@live_bp.route('/api/live/quiz', methods=['GET'])
@token_required
def get_live_quiz():
    """جلب سؤال الكويز السريع الحالي للطلاب أو المعلم"""
    try:
        tenant_id, role = get_current_tenant_id()
        state = _get_tenant_state(tenant_id)
        quiz = state.get('quiz')

        if not quiz:
            return jsonify({'success': True, 'quiz': None})

        # فحص انتهاء الوقت
        is_expired = time.time() > quiz['expires_at']
        remaining_seconds = max(0, int(quiz['expires_at'] - time.time()))

        # للطالب: لا نكشف correct_index إلا إذا انتهى الوقت أو جاوب
        student_answered = request.user_id in quiz['answers']
        sanitized_quiz = {
            'id': quiz['id'],
            'question': quiz['question'],
            'options': quiz['options'],
            'remaining_seconds': remaining_seconds,
            'is_expired': is_expired,
            'student_answered': student_answered,
            'student_answer': quiz['answers'].get(request.user_id, {}).get('option_index') if student_answered else None,
        }

        if role == 'teacher' or is_expired or student_answered:
            sanitized_quiz['correct_index'] = quiz['correct_index']

        if role == 'teacher':
            sanitized_quiz['total_answers'] = len(quiz['answers'])
            sanitized_quiz['correct_answers'] = sum(1 for a in quiz['answers'].values() if a.get('is_correct'))

        return jsonify({'success': True, 'quiz': sanitized_quiz})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@live_bp.route('/api/live/quiz/answer', methods=['POST'])
@token_required
def answer_live_quiz():
    """إجابة الطالب على سؤال الكويز السريع"""
    try:
        tenant_id, role = get_current_tenant_id()
        if role != 'student':
            return jsonify({'success': False, 'error': 'للطلاب فقط'}), 403

        state = _get_tenant_state(tenant_id)
        quiz = state.get('quiz')
        if not quiz:
            return jsonify({'success': False, 'error': 'لا يوجد كويز نشط حالياً'}), 400

        if time.time() > quiz['expires_at']:
            return jsonify({'success': False, 'error': 'انتهى وقت الإجابة على السؤال'}), 400

        data = request.get_json() or {}
        option_index = int(data.get('option_index', -1))
        if option_index < 0 or option_index >= len(quiz['options']):
            return jsonify({'success': False, 'error': 'اختيار غير صالح'}), 400

        student_id = request.user_id
        is_correct = (option_index == quiz['correct_index'])

        quiz['answers'][student_id] = {
            'option_index': option_index,
            'is_correct': is_correct,
            'time': time.time(),
        }

        # لو صحيحة، نكافئه بـ 10 نقاط فورية في لوحة الشرف
        if is_correct:
            try:
                supabase.table('honor_points').insert({
                    'student_id': student_id,
                    'points': 10,
                    'reason': 'إجابة صحيحة وسريعة في كويز البث المباشر 🎯',
                    'tenant_id': tenant_id
                }).execute()
            except Exception:
                pass

        return jsonify({
            'success': True,
            'is_correct': is_correct,
            'correct_index': quiz['correct_index'],
            'message': 'إجابة صحيحة وعبقرية! 🎉 (+10 نقاط)' if is_correct else 'للأسف إجابة غير صحيحة، ركز في السؤال القادم!'
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500
