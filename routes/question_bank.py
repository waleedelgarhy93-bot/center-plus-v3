# routes/question_bank.py - إدارة بنك الأسئلة المركزي
from flask import Blueprint, jsonify, request

from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    check_tenant_ownership,
)

question_bank_bp = Blueprint('question_bank', __name__)


def _enrich_questions(questions):
    """إثراء الأسئلة بأسماء المواد والأقسام"""
    if not questions:
        return questions

    subject_ids = list({q['subject_id'] for q in questions if q.get('subject_id')})
    category_ids = list({q['category_id'] for q in questions if q.get('category_id')})

    subjects_map = {}
    if subject_ids:
        try:
            s_res = supabase.table('subjects').select('id, name').in_('id', subject_ids).execute()
            for s in (s_res.data or []):
                subjects_map[s['id']] = s['name']
        except Exception:
            pass

    categories_map = {}
    if category_ids:
        try:
            c_res = supabase.table('categories').select('id, name').in_('id', category_ids).execute()
            for c in (c_res.data or []):
                categories_map[c['id']] = c['name']
        except Exception:
            pass

    for q in questions:
        q['subject_name'] = subjects_map.get(q.get('subject_id'))
        q['category_name'] = categories_map.get(q.get('category_id'))

    return questions


# ============ Get All ============

@question_bank_bp.route('/api/question-bank', methods=['GET'])
@token_required
def get_bank_questions():
    """جلب أسئلة بنك الأسئلة مع فلاتر ذكية"""
    try:
        query = supabase.table('question_bank').select('*')
        query = apply_tenant_filter(query)

        subject_id = request.args.get('subject_id')
        category_id = request.args.get('category_id')
        difficulty = request.args.get('difficulty')
        topic = request.args.get('topic')
        qtype = request.args.get('question_type')

        if subject_id:
            try:
                query = query.eq('subject_id', int(subject_id))
            except ValueError:
                pass

        if category_id:
            try:
                query = query.eq('category_id', int(category_id))
            except ValueError:
                pass

        if difficulty:
            query = query.eq('difficulty', difficulty)

        if topic:
            query = query.ilike('topic', f"%{topic}%")

        if qtype:
            query = query.eq('question_type', qtype)

        res = query.order('id', desc=True).execute()
        questions = _enrich_questions(res.data or [])

        return jsonify({
            'success': True,
            'count': len(questions),
            'questions': questions
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Create One ============

@question_bank_bp.route('/api/question-bank', methods=['POST'])
@token_required
def create_bank_question():
    """إضافة سؤال جديد إلى بنك الأسئلة"""
    try:
        data = request.get_json() or {}
        q_text = data.get('question_text', '').strip()
        q_image = data.get('question_image_url')

        if not q_text and not q_image:
            return jsonify({'success': False, 'error': 'نص السؤال أو الصورة مطلوب'}), 400

        tenant_id = get_tenant_for_insert()

        new_q = supabase.table('question_bank').insert({
            'tenant_id': tenant_id,
            'subject_id': data.get('subject_id'),
            'category_id': data.get('category_id'),
            'topic': data.get('topic'),
            'difficulty': data.get('difficulty', 'medium'),
            'question_text': q_text,
            'question_type': data.get('question_type', 'mcq'),
            'options': data.get('options'),
            'correct_answer': data.get('correct_answer'),
            'points': int(data.get('points', 1)),
            'question_image_url': q_image,
            'correct_answer_image_url': data.get('correct_answer_image_url'),
        }).execute()

        created = new_q.data[0] if new_q.data else None
        enriched = _enrich_questions([created]) if created else []

        return jsonify({
            'success': True,
            'message': 'تمت إضافة السؤال لبنك الأسئلة',
            'question': enriched[0] if enriched else created
        }), 201

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Update ============

@question_bank_bp.route('/api/question-bank/<int:qid>', methods=['PUT'])
@token_required
def update_bank_question(qid):
    """تعديل سؤال في بنك الأسئلة"""
    try:
        existing = supabase.table('question_bank').select('tenant_id').eq('id', qid).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'السؤال غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        update_data = {}
        for f in ['subject_id', 'category_id', 'topic', 'difficulty', 'question_text',
                  'question_type', 'options', 'correct_answer', 'points',
                  'question_image_url', 'correct_answer_image_url']:
            if f in data:
                update_data[f] = data[f]

        res = supabase.table('question_bank').update(update_data).eq('id', qid).execute()
        enriched = _enrich_questions(res.data or [])

        return jsonify({
            'success': True,
            'message': 'تم تعديل السؤال بنجاح',
            'question': enriched[0] if enriched else None
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Delete ============

@question_bank_bp.route('/api/question-bank/<int:qid>', methods=['DELETE'])
@token_required
def delete_bank_question(qid):
    """حذف سؤال من بنك الأسئلة"""
    try:
        existing = supabase.table('question_bank').select('tenant_id').eq('id', qid).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'السؤال غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('question_bank').delete().eq('id', qid).execute()
        return jsonify({'success': True, 'message': 'تم حذف السؤال من بنك الأسئلة'})

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Batch Import / Save Questions ============

@question_bank_bp.route('/api/question-bank/batch-import', methods=['POST'])
@token_required
def batch_import_questions():
    """استيراد أو حفظ مجموعة أسئلة في بنك الأسئلة"""
    try:
        data = request.get_json() or {}
        raw_questions = data.get('questions', [])
        if not raw_questions or not isinstance(raw_questions, list):
            return jsonify({'success': False, 'error': 'قائمة الأسئلة فارغة'}), 400

        tenant_id = get_tenant_for_insert()
        subject_id = data.get('subject_id')
        category_id = data.get('category_id')

        rows_to_insert = []
        for q in raw_questions:
            q_text = (q.get('question_text') or '').strip()
            q_img = q.get('question_image_url')
            if not q_text and not q_img:
                continue

            rows_to_insert.append({
                'tenant_id': tenant_id,
                'subject_id': q.get('subject_id') or subject_id,
                'category_id': q.get('category_id') or category_id,
                'topic': q.get('topic'),
                'difficulty': q.get('difficulty', 'medium'),
                'question_text': q_text,
                'question_type': q.get('question_type', 'mcq'),
                'options': q.get('options'),
                'correct_answer': q.get('correct_answer'),
                'points': int(q.get('points', 1)),
                'question_image_url': q_img,
                'correct_answer_image_url': q.get('correct_answer_image_url'),
            })

        if not rows_to_insert:
            return jsonify({'success': False, 'error': 'لا توجد أسئلة صالحة للإضافة'}), 400

        res = supabase.table('question_bank').insert(rows_to_insert).execute()
        return jsonify({
            'success': True,
            'message': f'تمت إضافة {len(res.data or [])} سؤال إلى بنك الأسئلة بنجاح',
            'inserted_count': len(res.data or [])
        }), 201

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500
