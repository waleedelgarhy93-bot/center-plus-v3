# routes/cards.py - CRUD الكروت + التوليد
from flask import Blueprint, jsonify, request
import random
import string
from datetime import datetime

from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    check_tenant_ownership,
)

cards_bp = Blueprint('cards', __name__)


@cards_bp.route('/api/cards', methods=['GET'])
@token_required
def get_cards():
    try:
        query = supabase.table('cards').select('*').order('created_at', desc=True)
        query = apply_tenant_filter(query)
        response = query.execute()

        return jsonify({
            'success': True,
            'count': len(response.data),
            'cards': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@cards_bp.route('/api/cards/unlinked', methods=['GET'])
@token_required
def get_unlinked_cards():
    try:
        query = supabase.table('cards').select('*').is_('student_id', 'null')
        query = apply_tenant_filter(query)
        response = query.execute()

        return jsonify({
            'success': True,
            'count': len(response.data),
            'cards': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@cards_bp.route('/api/cards/<string:barcode>', methods=['GET'])
@token_required
def get_card_by_barcode(barcode):
    try:
        response = supabase.table('cards').select('*').eq('barcode', barcode).execute()
        if not response.data:
            return jsonify({'success': False, 'error': 'لا يوجد كارت بهذا الرقم'}), 404

        card = response.data[0]
        if not check_tenant_ownership(card):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        return jsonify({'success': True, 'card': card})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@cards_bp.route('/api/cards', methods=['POST'])
@token_required
def create_card():
    try:
        data = request.get_json() or {}
        barcode = data.get('barcode')

        if not barcode:
            return jsonify({'success': False, 'error': 'barcode مطلوب'}), 400

        existing = supabase.table('cards').select('*').eq('barcode', barcode).execute()
        if existing.data:
            return jsonify({
                'success': False,
                'error': 'يوجد كارت بنفس الرقم بالفعل',
                'card': existing.data[0]
            }), 409

        tenant_id = get_tenant_for_insert()

        new_card = supabase.table('cards').insert({
            'barcode': barcode,
            'qr_data': data.get('qr_data'),
            'username': data.get('username'),
            'password': data.get('password'),
            'card_type': data.get('card_type', 'student'),
            'source': data.get('source', 'mps'),
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم إنشاء الكارت',
            'card': new_card.data[0] if new_card.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@cards_bp.route('/api/cards/link', methods=['POST'])
@token_required
def link_card_to_student():
    try:
        data = request.get_json() or {}
        barcode = data.get('barcode')
        student_id = data.get('student_id')

        if not barcode or not student_id:
            return jsonify({'success': False, 'error': 'barcode و student_id مطلوبين'}), 400

        card_res = supabase.table('cards').select('*').eq('barcode', barcode).execute()
        if not card_res.data:
            return jsonify({'success': False, 'error': 'الكارت غير موجود'}), 404

        card = card_res.data[0]
        if not check_tenant_ownership(card):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        if card.get('student_id') and card['student_id'] != student_id:
            return jsonify({'success': False, 'error': 'الكارت مربوط بطالب آخر'}), 400

        student_res = supabase.table('students').select('*').eq('id', student_id).execute()
        if not student_res.data:
            return jsonify({'success': False, 'error': 'الطالب غير موجود'}), 404

        if not check_tenant_ownership(student_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        result = supabase.table('cards').update({
            'student_id': student_id
        }).eq('barcode', barcode).execute()

        return jsonify({
            'success': True,
            'message': 'تم ربط الكارت بالطالب',
            'card': result.data[0] if result.data else None
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@cards_bp.route('/api/cards/<int:card_id>/unlink', methods=['POST'])
@token_required
def unlink_card(card_id):
    try:
        existing = supabase.table('cards').select('tenant_id').eq('id', card_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'الكارت غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('cards').update({'student_id': None}).eq('id', card_id).execute()

        return jsonify({'success': True, 'message': 'تم فك الربط'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@cards_bp.route('/api/cards/<int:card_id>', methods=['DELETE'])
@token_required
def delete_card(card_id):
    try:
        existing = supabase.table('cards').select('tenant_id').eq('id', card_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'الكارت غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('cards').delete().eq('id', card_id).execute()

        return jsonify({'success': True, 'message': 'تم الحذف'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@cards_bp.route('/api/cards/generate', methods=['POST'])
@token_required
def generate_card():
    try:
        data = request.get_json() or {}
        student_id = data.get('student_id')
        card_type = data.get('card_type', 'student')
        tenant_id = get_tenant_for_insert()

        # توليد barcode فريد
        barcode = None
        for _ in range(20):
            candidate = ''.join(random.choices(string.digits, k=4))
            check = supabase.table('cards').select('id').eq('barcode', candidate).execute()
            if not check.data:
                barcode = candidate
                break

        if not barcode:
            return jsonify({'success': False, 'error': 'فشل توليد barcode'}), 500

        username = ''.join(random.choices(string.digits, k=8))
        password = ''.join(random.choices(string.digits, k=8))

        new_card = supabase.table('cards').insert({
            'barcode': barcode,
            'username': username,
            'password': password,
            'card_type': card_type,
            'source': 'generated',
            'student_id': student_id,
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم توليد الكارت',
            'card': new_card.data[0] if new_card.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@cards_bp.route('/api/cards/generate-batch', methods=['POST'])
@token_required
def generate_cards_batch():
    try:
        data = request.get_json() or {}
        student_ids = data.get('student_ids', [])
        card_type = data.get('card_type', 'student')

        if not student_ids:
            return jsonify({'success': False, 'error': 'student_ids مطلوب'}), 400

        tenant_id = get_tenant_for_insert()

        students_res = supabase.table('students').select('*').in_('id', student_ids).execute()
        students = students_res.data or []

        if not students:
            return jsonify({'success': False, 'error': 'لا يوجد طلاب'}), 404

        generated_cards = []
        skipped = []

        for student in students:
            if not check_tenant_ownership(student):
                skipped.append({
                    'student_id': student['id'],
                    'student_name': student['name'],
                    'reason': 'غير مصرح'
                })
                continue

            existing = supabase.table('cards').select('*').eq('student_id', student['id']).execute()
            if existing.data:
                skipped.append({
                    'student_id': student['id'],
                    'student_name': student['name'],
                    'reason': 'عنده كارت بالفعل',
                    'barcode': existing.data[0]['barcode']
                })
                continue

            barcode = None
            for _ in range(20):
                candidate = ''.join(random.choices(string.digits, k=4))
                check = supabase.table('cards').select('id').eq('barcode', candidate).execute()
                if not check.data:
                    barcode = candidate
                    break

            if not barcode:
                skipped.append({
                    'student_id': student['id'],
                    'student_name': student['name'],
                    'reason': 'فشل توليد barcode'
                })
                continue

            generated_cards.append({
                'barcode': barcode,
                'username': ''.join(random.choices(string.digits, k=8)),
                'password': ''.join(random.choices(string.digits, k=8)),
                'card_type': card_type,
                'source': 'generated',
                'student_id': student['id'],
                'tenant_id': tenant_id,
            })

        inserted_cards = []
        if generated_cards:
            result = supabase.table('cards').insert(generated_cards).execute()
            inserted_cards = result.data or []

        enriched = []
        for card in inserted_cards:
            student = next((s for s in students if s['id'] == card['student_id']), None)
            if student:
                enriched.append({
                    **card,
                    'student_name': student['name'],
                    'student_grade': student.get('grade'),
                })

        return jsonify({
            'success': True,
            'message': f'تم توليد {len(inserted_cards)} كارت',
            'generated_count': len(inserted_cards),
            'skipped_count': len(skipped),
            'cards': enriched,
            'skipped': skipped
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@cards_bp.route('/api/cards/<int:card_id>/mark-printed', methods=['POST'])
@token_required
def mark_card_printed(card_id):
    try:
        existing = supabase.table('cards').select('tenant_id').eq('id', card_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'الكارت غير موجود'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        result = supabase.table('cards').update({
            'is_printed': True,
            'printed_at': datetime.utcnow().isoformat()
        }).eq('id', card_id).execute()

        return jsonify({
            'success': True,
            'message': 'تم تحديث حالة الكارت',
            'card': result.data[0] if result.data else None
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500