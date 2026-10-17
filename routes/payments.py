# routes/payments.py - المدفوعات + إحصائيات
from flask import Blueprint, jsonify, request
from datetime import datetime

from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    check_tenant_ownership,
)

payments_bp = Blueprint('payments', __name__)


# ============ CRUD ============

@payments_bp.route('/api/payments', methods=['GET'])
@token_required
def get_payments():
    try:
        query = supabase.table('payments').select('*')
        query = apply_tenant_filter(query)
        response = query.execute()

        return jsonify({
            'success': True,
            'count': len(response.data),
            'payments': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@payments_bp.route('/api/payments/student/<int:student_id>', methods=['GET'])
@token_required
def get_student_payments(student_id):
    try:
        student_res = supabase.table('students').select('tenant_id').eq('id', student_id).execute()
        if student_res.data and not check_tenant_ownership(student_res.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        query = supabase.table('payments').select('*').eq('student_id', student_id)
        query = apply_tenant_filter(query)
        response = query.execute()

        return jsonify({
            'success': True,
            'count': len(response.data),
            'payments': response.data
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@payments_bp.route('/api/payments', methods=['POST'])
@token_required
def create_payment():
    try:
        data = request.get_json() or {}
        student_id = data.get('student_id')
        amount = data.get('amount')
        month = data.get('month')

        if not student_id or amount is None or not month:
            return jsonify({'success': False, 'error': 'student_id, amount, month مطلوبين'}), 400

        tenant_id = get_tenant_for_insert()

        new_payment = supabase.table('payments').insert({
            'student_id': student_id,
            'subject_id': data.get('subject_id'),
            'amount': amount,
            'month': month,
            'status': data.get('status', 'unpaid'),
            'paid_at': data.get('paid_at'),
            'tenant_id': tenant_id,
        }).execute()

        return jsonify({
            'success': True,
            'message': 'تم إنشاء الدفعة',
            'payment': new_payment.data[0] if new_payment.data else None
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@payments_bp.route('/api/payments/<int:payment_id>', methods=['PUT'])
@token_required
def update_payment(payment_id):
    try:
        existing = supabase.table('payments').select('tenant_id').eq('id', payment_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'Payment not found'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        data = request.get_json() or {}
        update_data = {}
        for field in ['student_id', 'subject_id', 'amount', 'month', 'status', 'paid_at', 'group_id', 'notes', 'payment_method']:
            if field in data:
                update_data[field] = data[field]

        if not update_data:
            return jsonify({'success': False, 'error': 'مفيش بيانات للتعديل'}), 400

        result = supabase.table('payments').update(update_data).eq('id', payment_id).execute()

        return jsonify({
            'success': True,
            'message': 'تم التعديل',
            'payment': result.data[0] if result.data else None
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@payments_bp.route('/api/payments/<int:payment_id>', methods=['DELETE'])
@token_required
def delete_payment(payment_id):
    try:
        existing = supabase.table('payments').select('tenant_id').eq('id', payment_id).execute()
        if not existing.data:
            return jsonify({'success': False, 'error': 'Payment not found'}), 404
        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        supabase.table('payments').delete().eq('id', payment_id).execute()

        return jsonify({'success': True, 'message': 'تم الحذف'})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Month Payments ============

@payments_bp.route('/api/payments/month/<string:month>', methods=['GET'])
@token_required
def get_month_payments(month):
    """مدفوعات شهر معين"""
    try:
        group_id = request.args.get('group_id', type=int)

        # الطلاب
        students_query = supabase.table('students').select('id, name, barcode, group_id')
        students_query = apply_tenant_filter(students_query)
        if group_id:
            students_query = students_query.eq('group_id', group_id)
        students_res = students_query.execute()
        students = students_res.data or []

        # المدفوعات
        payments_query = supabase.table('payments').select('*').eq('month', month)
        payments_query = apply_tenant_filter(payments_query)
        payments_res = payments_query.execute()
        payments = payments_res.data or []

        # المجموعات
        groups_res = supabase.table('groups').select('id, grade, center, price, subject_id').execute()
        groups_map = {g['id']: g for g in (groups_res.data or [])}

        payments_map = {p['student_id']: p for p in payments}

        result = []
        total_paid = 0
        total_expected = 0

        for student in students:
            group = groups_map.get(student.get('group_id'), {})
            expected_amount = 0
            if group.get('price'):
                try:
                    expected_amount = float(group['price'])
                except:
                    expected_amount = 0

            payment = payments_map.get(student['id'])
            status = 'unpaid'
            paid_amount = 0

            if payment:
                paid_amount = float(payment.get('amount', 0))
                if payment.get('status') == 'paid':
                    status = 'paid'
                elif paid_amount > 0:
                    status = 'partial'

            total_paid += paid_amount
            total_expected += expected_amount

            result.append({
                'student_id': student['id'],
                'student_name': student['name'],
                'student_barcode': student.get('barcode'),
                'group_id': student.get('group_id'),
                'group_name': f"{group.get('grade', '')} - {group.get('center', '')}" if group else None,
                'expected_amount': expected_amount,
                'paid_amount': paid_amount,
                'status': status,
                'payment_id': payment['id'] if payment else None,
                'paid_at': payment.get('paid_at') if payment else None,
                'payment_method': payment.get('payment_method') if payment else None,
                'notes': payment.get('notes') if payment else None,
            })

        return jsonify({
            'success': True,
            'month': month,
            'group_id': group_id,
            'count': len(result),
            'total_paid': total_paid,
            'total_expected': total_expected,
            'remaining': total_expected - total_paid,
            'payments': result
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@payments_bp.route('/api/payments/upsert', methods=['POST'])
@token_required
def create_or_update_payment():
    """إضافة/تحديث دفعة"""
    try:
        data = request.get_json() or {}
        student_id = data.get('student_id')
        month = data.get('month')
        amount = data.get('amount', 0)

        if not student_id or not month:
            return jsonify({'success': False, 'error': 'student_id و month مطلوبين'}), 400

        tenant_id = get_tenant_for_insert()

        existing = supabase.table('payments')\
            .select('*')\
            .eq('student_id', student_id)\
            .eq('month', month)\
            .execute()

        payment_data = {
            'student_id': student_id,
            'month': month,
            'amount': amount,
            'status': 'paid' if amount > 0 else 'unpaid',
            'group_id': data.get('group_id'),
            'subject_id': data.get('subject_id'),
            'notes': data.get('notes'),
            'payment_method': data.get('payment_method', 'cash'),
            'paid_at': data.get('paid_at') or (datetime.utcnow().isoformat() if amount > 0 else None),
            'created_by': request.user_id,
        }

        if existing.data:
            # تحقق من الملكية قبل التحديث
            if not check_tenant_ownership(existing.data[0]):
                return jsonify({'success': False, 'error': 'غير مصرح'}), 403
            result = supabase.table('payments')\
                .update(payment_data)\
                .eq('id', existing.data[0]['id'])\
                .execute()
        else:
            payment_data['tenant_id'] = tenant_id
            result = supabase.table('payments').insert(payment_data).execute()

        return jsonify({
            'success': True,
            'message': 'تم الحفظ',
            'payment': result.data[0] if result.data else None
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@payments_bp.route('/api/payments/stats', methods=['GET'])
@token_required
def get_payments_stats():
    """إحصائيات المدفوعات — كل الشهور"""
    try:
        query = supabase.table('payments').select('*')
        query = apply_tenant_filter(query)
        response = query.execute()
        payments = response.data or []

        monthly: dict = {}
        for p in payments:
            month = p.get('month', '')
            if month not in monthly:
                monthly[month] = {'month': month, 'total': 0, 'count': 0}

            if p.get('status') == 'paid':
                monthly[month]['total'] += float(p.get('amount', 0))
                monthly[month]['count'] += 1

        months = sorted(monthly.values(), key=lambda x: x['month'], reverse=True)

        return jsonify({
            'success': True,
            'months': months,
            'total_all': sum(m['total'] for m in months)
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500