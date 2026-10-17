# routes/admin.py - Super Admin endpoints
from flask import Blueprint, jsonify, request
from datetime import datetime, timedelta

from db import supabase
from helpers import token_required

admin_bp = Blueprint('admin', __name__)


def _require_super_admin():
    """يتحقق إن المستخدم الحالي super_admin"""
    user_res = supabase.table('users')\
        .select('role')\
        .eq('id', request.user_id)\
        .execute()

    if not user_res.data or user_res.data[0]['role'] != 'super_admin':
        return False, (jsonify({'success': False, 'error': 'غير مصرح'}), 403)

    return True, None


def _arabic_month(year, month):
    """يرجّع اسم الشهر بالعربي"""
    months = [
        'يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو',
        'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'
    ]
    return f"{months[month - 1]} {year}"


# ============ Stats ============

@admin_bp.route('/api/admin/stats', methods=['GET'])
@token_required
def admin_stats():
    """إحصائيات المنصة كلها"""
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        tenants_res = supabase.table('tenants').select('id, status').execute()
        tenants = tenants_res.data or []
        total_tenants = len(tenants)
        active_tenants = len([t for t in tenants if t.get('status') == 'active'])

        users_res = supabase.table('users')\
            .select('id')\
            .in_('role', ['teacher', 'assistant'])\
            .execute()
        total_users = len(users_res.data or [])

        students_res = supabase.table('students').select('id').execute()
        total_students = len(students_res.data or [])

        subs_res = supabase.table('subscriptions')\
            .select('amount, status')\
            .eq('status', 'active')\
            .execute()
        subs = subs_res.data or []
        total_revenue = sum(float(s.get('amount', 0)) for s in subs)
        active_subs = len(subs)

        soon = (datetime.now() + timedelta(days=7)).isoformat()
        expiring_res = supabase.table('tenants')\
            .select('id, name, subscription_end')\
            .lt('subscription_end', soon)\
            .gt('subscription_end', datetime.now().isoformat())\
            .eq('status', 'active')\
            .execute()
        expiring_soon = expiring_res.data or []

        return jsonify({
            'success': True,
            'stats': {
                'total_tenants': total_tenants,
                'active_tenants': active_tenants,
                'suspended_tenants': total_tenants - active_tenants,
                'total_users': total_users,
                'total_students': total_students,
                'total_revenue': total_revenue,
                'active_subscriptions': active_subs,
                'expiring_soon': len(expiring_soon),
            },
            'expiring_soon': expiring_soon,
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Tenants Management ============

@admin_bp.route('/api/admin/tenants', methods=['GET'])
@token_required
def admin_get_tenants():
    """جلب كل السناتر + إحصائيات كل واحد"""
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        response = supabase.table('tenants')\
            .select('*')\
            .order('created_at', desc=True)\
            .execute()
        tenants = response.data or []

        for t in tenants:
            students_res = supabase.table('students')\
                .select('id')\
                .eq('tenant_id', t['id'])\
                .execute()
            users_res = supabase.table('users')\
                .select('id')\
                .eq('tenant_id', t['id'])\
                .execute()
            groups_res = supabase.table('groups')\
                .select('id')\
                .eq('tenant_id', t['id'])\
                .execute()

            t['students_count'] = len(students_res.data or [])
            t['users_count'] = len(users_res.data or [])
            t['groups_count'] = len(groups_res.data or [])

        return jsonify({
            'success': True,
            'count': len(tenants),
            'tenants': tenants
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@admin_bp.route('/api/admin/tenants', methods=['POST'])
@token_required
def admin_create_tenant():
    """إنشاء سنتر جديد + حساب صاحبه + اشتراك"""
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        data = request.get_json() or {}

        name = data.get('name')
        owner_name = data.get('owner_name')
        phone = data.get('phone')
        email = data.get('email')
        plan = data.get('plan', 'monthly')
        duration_months = data.get('duration_months', 1)
        owner_username = data.get('owner_username')
        owner_password = data.get('owner_password')

        if not name or not owner_name or not email:
            return jsonify({
                'success': False,
                'error': 'name, owner_name, email مطلوبين'
            }), 400

        if not owner_username or not owner_password:
            return jsonify({
                'success': False,
                'error': 'owner_username و owner_password مطلوبين'
            }), 400

        existing = supabase.table('users')\
            .select('id')\
            .eq('username', owner_username)\
            .execute()
        if existing.data:
            return jsonify({
                'success': False,
                'error': 'username موجود بالفعل'
            }), 409

        from helpers import hash_password

        now = datetime.now()
        sub_end = now + timedelta(days=30 * duration_months)

        tenant_res = supabase.table('tenants').insert({
            'name': name,
            'owner_name': owner_name,
            'phone': phone,
            'email': email,
            'plan': plan,
            'status': 'active',
            'subscription_start': now.isoformat(),
            'subscription_end': sub_end.isoformat(),
            'max_students': 100,
            'max_groups': 10,
        }).execute()

        if not tenant_res.data:
            return jsonify({'success': False, 'error': 'فشل إنشاء السنتر'}), 500

        tenant_id = tenant_res.data[0]['id']

        user_res = supabase.table('users').insert({
            'username': owner_username,
            'password': hash_password(owner_password),
            'role': 'teacher',
            'full_name': owner_name,
            'phone': phone,
            'tenant_id': tenant_id,
        }).execute()

        plan_amounts = {'monthly': 200, 'yearly': 2000}
        supabase.table('subscriptions').insert({
            'tenant_id': tenant_id,
            'plan': plan,
            'amount': plan_amounts.get(plan, 200),
            'start_date': now.isoformat(),
            'end_date': sub_end.isoformat(),
            'status': 'active',
            'payment_method': 'manual',
        }).execute()

        return jsonify({
            'success': True,
            'message': f'تم إنشاء السنتر "{name}" + حساب صاحبه',
            'tenant': tenant_res.data[0],
            'user': {
                'id': user_res.data[0]['id'] if user_res.data else None,
                'username': owner_username,
                'role': 'teacher',
                'tenant_id': tenant_id,
            }
        }), 201

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@admin_bp.route('/api/admin/tenants/<int:tenant_id>/toggle', methods=['PUT'])
@token_required
def admin_toggle_tenant(tenant_id):
    """تفعيل/إيقاف سنتر"""
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        tenant_res = supabase.table('tenants')\
            .select('status, name')\
            .eq('id', tenant_id)\
            .execute()

        if not tenant_res.data:
            return jsonify({'success': False, 'error': 'السنتر غير موجود'}), 404

        current_status = tenant_res.data[0].get('status', 'active')
        new_status = 'suspended' if current_status == 'active' else 'active'

        result = supabase.table('tenants')\
            .update({'status': new_status})\
            .eq('id', tenant_id)\
            .execute()

        return jsonify({
            'success': True,
            'message': f'تم {"تفعيل" if new_status == "active" else "إيقاف"} السنتر',
            'new_status': new_status,
            'tenant': result.data[0] if result.data else None
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Subscriptions ============

@admin_bp.route('/api/admin/subscriptions', methods=['GET'])
@token_required
def admin_get_subscriptions():
    """جلب كل الاشتراكات"""
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        response = supabase.table('subscriptions')\
            .select('*')\
            .order('created_at', desc=True)\
            .execute()
        subs = response.data or []

        if subs:
            tenant_ids = list({s['tenant_id'] for s in subs if s.get('tenant_id')})
            if tenant_ids:
                tenants_res = supabase.table('tenants')\
                    .select('id, name, phone, owner_name, email')\
                    .in_('id', tenant_ids)\
                    .execute()
                tenants_map = {t['id']: t for t in (tenants_res.data or [])}
                for s in subs:
                    tenant = tenants_map.get(s.get('tenant_id'), {})
                    s['tenant_name'] = tenant.get('name', '—')
                    s['tenant_phone'] = tenant.get('phone')
                    s['tenant_owner'] = tenant.get('owner_name')
                    s['tenant_email'] = tenant.get('email')

        return jsonify({
            'success': True,
            'count': len(subs),
            'subscriptions': subs
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Reports ============

@admin_bp.route('/api/admin/reports', methods=['GET'])
@token_required
def admin_get_reports():
    """تقارير شاملة للمنصة"""
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        now = datetime.now()

        # 1) الإحصائيات العامة
        tenants_res = supabase.table('tenants').select('id, status').execute()
        tenants = tenants_res.data or []
        total_tenants = len(tenants)
        active_tenants = len([t for t in tenants if t.get('status') == 'active'])

        users_res = supabase.table('users').select('id')\
            .in_('role', ['teacher', 'assistant']).execute()
        total_users = len(users_res.data or [])

        students_res = supabase.table('students').select('id').execute()
        total_students = len(students_res.data or [])

        subs_res = supabase.table('subscriptions')\
            .select('amount, status')\
            .eq('status', 'active')\
            .execute()
        subs = subs_res.data or []
        total_revenue = sum(float(s.get('amount', 0)) for s in subs)

        # 2) إيرادات آخر 6 شهور
        monthly_revenue = []
        for i in range(5, -1, -1):
            year = now.year
            month = now.month - i
            while month <= 0:
                month += 12
                year -= 1

            month_str = f'{year}-{str(month).zfill(2)}'
            month_start = f'{month_str}-01'

            next_month = month + 1
            next_year = year
            if next_month > 12:
                next_month = 1
                next_year += 1
            month_end = f'{next_year}-{str(next_month).zfill(2)}-01'

            month_subs = supabase.table('subscriptions')\
                .select('amount')\
                .gte('created_at', month_start)\
                .lt('created_at', month_end)\
                .execute()

            month_total = sum(float(s.get('amount', 0)) for s in (month_subs.data or []))

            monthly_revenue.append({
                'month': month_str,
                'month_label': _arabic_month(year, month),
                'revenue': month_total,
            })

        # 3) أعلى 5 سناتر بالطلاب
        top_by_students = []
        for t in tenants:
            students_count = len(
                (supabase.table('students').select('id')\
                    .eq('tenant_id', t['id']).execute().data or [])
            )
            tenant_info = supabase.table('tenants')\
                .select('name, owner_name')\
                .eq('id', t['id'])\
                .execute()
            if tenant_info.data:
                top_by_students.append({
                    'tenant_id': t['id'],
                    'tenant_name': tenant_info.data[0].get('name', '—'),
                    'owner_name': tenant_info.data[0].get('owner_name', '—'),
                    'students_count': students_count,
                })

        top_by_students.sort(key=lambda x: x['students_count'], reverse=True)
        top_by_students = top_by_students[:5]

        # 4) أعلى 5 سناتر بالإيرادات
        top_by_revenue = []
        for t in tenants:
            tenant_subs = supabase.table('subscriptions')\
                .select('amount')\
                .eq('tenant_id', t['id'])\
                .eq('status', 'active')\
                .execute()
            revenue = sum(float(s.get('amount', 0)) for s in (tenant_subs.data or []))

            tenant_info = supabase.table('tenants')\
                .select('name, owner_name')\
                .eq('id', t['id'])\
                .execute()
            if tenant_info.data:
                top_by_revenue.append({
                    'tenant_id': t['id'],
                    'tenant_name': tenant_info.data[0].get('name', '—'),
                    'owner_name': tenant_info.data[0].get('owner_name', '—'),
                    'revenue': revenue,
                })

        top_by_revenue.sort(key=lambda x: x['revenue'], reverse=True)
        top_by_revenue = top_by_revenue[:5]

        # 5) اشتراكات قريبة الانتهاء
        soon = (now + timedelta(days=7)).isoformat()
        expiring_res = supabase.table('tenants')\
            .select('id, name, owner_name, subscription_end, phone')\
            .lt('subscription_end', soon)\
            .gt('subscription_end', now.isoformat())\
            .eq('status', 'active')\
            .order('subscription_end')\
            .execute()
        expiring_soon = expiring_res.data or []

        # 6) سناتر بلا نشاط
        inactive_tenants = []
        for t in tenants:
            if t.get('status') != 'active':
                continue
            students_count = len(
                (supabase.table('students').select('id')\
                    .eq('tenant_id', t['id']).execute().data or [])
            )
            if students_count == 0:
                tenant_info = supabase.table('tenants')\
                    .select('name, owner_name, created_at')\
                    .eq('id', t['id'])\
                    .execute()
                if tenant_info.data:
                    inactive_tenants.append({
                        'tenant_id': t['id'],
                        'tenant_name': tenant_info.data[0].get('name', '—'),
                        'owner_name': tenant_info.data[0].get('owner_name', '—'),
                        'created_at': tenant_info.data[0].get('created_at'),
                    })

        return jsonify({
            'success': True,
            'overview': {
                'total_tenants': total_tenants,
                'active_tenants': active_tenants,
                'suspended_tenants': total_tenants - active_tenants,
                'total_users': total_users,
                'total_students': total_students,
                'total_revenue': total_revenue,
                'active_subscriptions': len(subs),
            },
            'monthly_revenue': monthly_revenue,
            'top_by_students': top_by_students,
            'top_by_revenue': top_by_revenue,
            'expiring_soon': expiring_soon,
            'inactive_tenants': inactive_tenants,
        })

    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({'success': False, 'error': str(e)}), 500