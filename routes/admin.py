# routes/admin.py - Super Admin endpoints
from flask import Blueprint, jsonify, request
from datetime import datetime, timedelta

from db import supabase
from helpers import token_required, hash_password, verify_password

admin_bp = Blueprint('admin', __name__)


def _require_super_admin():
    """التحقق من أن المستخدم الحالي super_admin"""
    try:
        user_res = supabase.table('users').select('role').eq('id', request.user_id).execute()
        if not user_res.data or user_res.data[0].get('role') not in ['super_admin', 'superadmin']:
            return False, (jsonify({'success': False, 'error': 'غير مصرح'}), 403)
        return True, None
    except Exception as e:
        return False, (jsonify({'success': False, 'error': f'خطأ صلاحيات: {str(e)}'}), 500)


def _arabic_month(year, month):
    months = [
        'يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو',
        'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'
    ]
    return f"{months[month - 1]} {year}"


# ============ إحصائيات لوحة الأدمن ============

@admin_bp.route('/api/admin/stats', methods=['GET', 'OPTIONS'])
@token_required
def admin_stats():
    if request.method == 'OPTIONS':
        return '', 200
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        tenants_res = supabase.table('tenants').select('id, status').execute()
        tenants = tenants_res.data or []
        total_tenants = len(tenants)
        active_tenants = len([t for t in tenants if t.get('status') == 'active'])

        users_res = supabase.table('users').select('id').in_('role', ['teacher', 'assistant']).execute()
        total_users = len(users_res.data or [])

        students_res = supabase.table('students').select('id').execute()
        total_students = len(students_res.data or [])

        subs_res = supabase.table('subscriptions').select('amount, status').eq('status', 'active').execute()
        subs = subs_res.data or []
        total_revenue = sum(float(s.get('amount', 0)) for s in subs)
        active_subs = len(subs)

        soon = (datetime.now() + timedelta(days=7)).isoformat()
        expiring_res = supabase.table('tenants').select('id, name, subscription_end').lt('subscription_end', soon).gt('subscription_end', datetime.now().isoformat()).eq('status', 'active').execute()
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


# ============ إدارة السناتر ============

@admin_bp.route('/api/admin/tenants', methods=['GET', 'OPTIONS'])
@token_required
def admin_get_tenants():
    if request.method == 'OPTIONS':
        return '', 200
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        response = supabase.table('tenants').select('*').order('created_at', desc=True).execute()
        tenants = response.data or []

        for t in tenants:
            t_id = t.get('id')
            try:
                students_res = supabase.table('students').select('id').eq('tenant_id', t_id).execute()
                t['students_count'] = len(students_res.data or [])
            except Exception:
                t['students_count'] = 0

            try:
                users_res = supabase.table('users').select('id').eq('tenant_id', t_id).execute()
                t['users_count'] = len(users_res.data or [])
            except Exception:
                t['users_count'] = 0

            try:
                groups_res = supabase.table('groups').select('id').eq('tenant_id', t_id).execute()
                t['groups_count'] = len(groups_res.data or [])
            except Exception:
                t['groups_count'] = 0

        return jsonify({
            'success': True,
            'count': len(tenants),
            'tenants': tenants
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@admin_bp.route('/api/admin/tenants/<tenant_id>', methods=['GET', 'OPTIONS'])
@token_required
def admin_get_single_tenant(tenant_id):
    if request.method == 'OPTIONS':
        return '', 200
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        tenant_res = supabase.table('tenants').select('*').eq('id', tenant_id).execute()
        if not tenant_res.data:
            return jsonify({'success': False, 'error': 'السنتر غير موجود'}), 404

        return jsonify({'success': True, 'tenant': tenant_res.data[0]})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@admin_bp.route('/api/admin/tenants', methods=['POST', 'OPTIONS'])
@token_required
def admin_create_tenant():
    if request.method == 'OPTIONS':
        return '', 200
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
            return jsonify({'success': False, 'error': 'name, owner_name, email مطلوبين'}), 400

        if not owner_username or not owner_password:
            return jsonify({'success': False, 'error': 'owner_username و owner_password مطلوبين'}), 400

        existing = supabase.table('users').select('id').eq('username', owner_username).execute()
        if existing.data:
            return jsonify({'success': False, 'error': 'username موجود بالفعل'}), 409

        now = datetime.now()
        sub_end = now + timedelta(days=30 * int(duration_months))

        tenant_payload = {
            'name': name,
            'owner_name': owner_name,
            'phone': phone,
            'email': email,
            'plan': plan,
            'status': 'active',
            'subscription_start': now.isoformat(),
            'subscription_end': sub_end.isoformat(),
            'max_students': int(data.get('max_students', 100)),
            'max_groups': int(data.get('max_groups', 10)),
        }

        tenant_res = supabase.table('tenants').insert(tenant_payload).execute()
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


# ============ تعديل بيانات السنتر (يدعم POST و PUT) ============

@admin_bp.route('/api/admin/tenants/<tenant_id>/update', methods=['POST', 'OPTIONS'])
@admin_bp.route('/api/admin/tenants/<tenant_id>', methods=['PUT', 'POST', 'OPTIONS'])
@token_required
def admin_update_tenant(tenant_id):
    if request.method == 'OPTIONS':
        return '', 200
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        data = request.get_json() or {}
        update_data = {}
        fields = ['name', 'owner_name', 'phone', 'email', 'status', 'plan', 'max_students', 'max_groups', 'subscription_end']
        
        for field in fields:
            if field in data and data[field] is not None:
                if field in ['max_students', 'max_groups']:
                    update_data[field] = int(data[field])
                else:
                    update_data[field] = data[field]

        if not update_data:
            return jsonify({'success': False, 'error': 'لا توجد بيانات للتعديل'}), 400

        result = supabase.table('tenants').update(update_data).eq('id', tenant_id).execute()
        if not result.data:
            return jsonify({'success': False, 'error': 'السنتر غير موجود'}), 404

        return jsonify({
            'success': True,
            'message': 'تم التعديل بنجاح',
            'tenant': result.data[0]
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ حذف السنتر (يدعم POST و DELETE لتجاوز حظر الشبكة) ============

@admin_bp.route('/api/admin/tenants/<tenant_id>/delete', methods=['POST', 'OPTIONS'])
@admin_bp.route('/api/admin/tenants/<tenant_id>', methods=['DELETE', 'POST', 'OPTIONS'])
@token_required
def admin_delete_tenant(tenant_id):
    if request.method == 'OPTIONS':
        return '', 200
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        # التحقق من وجود السنتر أولاً
        tenant_res = supabase.table('tenants').select('id, name').eq('id', tenant_id).execute()
        if not tenant_res.data:
            return jsonify({'success': False, 'error': 'السنتر غير موجود'}), 404

        center_name = tenant_res.data[0].get('name', 'السنتر')

        # استدعاء دالة الحذف المتسلسلة المعتمدة في Supabase
        try:
            supabase.rpc('delete_tenant_cascade', {'target_tenant_id': str(tenant_id)}).execute()
        except Exception as rpc_err:
            # مسار بديل للحذف البرمجي في حال تعذر تشغيل RPC
            tables = [
                'student_answers', 'student_exams', 'questions', 'exams',
                'video_views', 'video_restrictions', 'videos', 'attendance',
                'grades', 'cards', 'students', 'groups', 'subjects',
                'device_settings', 'active_sessions', 'honor_points',
                'honor_notifications', 'honor_settings', 'subscriptions', 'users'
            ]
            for table in tables:
                try:
                    supabase.table(table).delete().eq('tenant_id', tenant_id).execute()
                except Exception:
                    pass
            supabase.table('tenants').delete().eq('id', tenant_id).execute()

        return jsonify({
            'success': True,
            'message': f'تم حذف سنتر "{center_name}" وكافة بياناته نهائياً بنجاح'
        })
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({'success': False, 'error': f'فشل حذف السنتر: {str(e)}'}), 500


# ============ تفعيل وإيقاف السنتر ============

@admin_bp.route('/api/admin/tenants/<tenant_id>/toggle', methods=['POST', 'PUT', 'OPTIONS'])
@token_required
def admin_toggle_tenant(tenant_id):
    if request.method == 'OPTIONS':
        return '', 200
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        tenant_res = supabase.table('tenants').select('status, name').eq('id', tenant_id).execute()
        if not tenant_res.data:
            return jsonify({'success': False, 'error': 'السنتر غير موجود'}), 404

        current_status = tenant_res.data[0].get('status', 'active')
        new_status = 'suspended' if current_status == 'active' else 'active'

        result = supabase.table('tenants').update({'status': new_status}).eq('id', tenant_id).execute()

        return jsonify({
            'success': True,
            'message': f'تم {"تفعيل" if new_status == "active" else "إيقاف"} السنتر',
            'new_status': new_status,
            'tenant': result.data[0] if result.data else None
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ تغيير كلمة سر السوبر أدمن ============

@admin_bp.route('/api/admin/change-password', methods=['POST', 'OPTIONS'])
@token_required
def admin_change_password():
    if request.method == 'OPTIONS':
        return '', 200
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        data = request.get_json() or {}
        current_password = data.get('current_password')
        new_password = data.get('new_password')

        if not current_password or not new_password:
            return jsonify({'success': False, 'error': 'كلمة المرور الحالية والجديدة مطلوبتان'}), 400

        # جلب بيانات السوبر أدمن الحالي
        user_res = supabase.table('users').select('id, password').eq('id', request.user_id).execute()
        if not user_res.data:
            return jsonify({'success': False, 'error': 'المستخدم غير موجود'}), 404

        user = user_res.data[0]

        # التحقق من صحة كلمة المرور الحالية
        if not verify_password(current_password, user['password']):
            return jsonify({'success': False, 'error': 'كلمة المرور الحالية غير صحيحة'}), 400

        # تشفير وحفظ كلمة المرور الجديدة
        supabase.table('users').update({
            'password': hash_password(new_password)
        }).eq('id', user['id']).execute()

        return jsonify({
            'success': True,
            'message': 'تم تغيير كلمة المرور بنجاح'
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ الاشتراكات والتقارير ============

@admin_bp.route('/api/admin/subscriptions', methods=['GET', 'OPTIONS'])
@token_required
def admin_get_subscriptions():
    if request.method == 'OPTIONS':
        return '', 200
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        response = supabase.table('subscriptions').select('*').order('created_at', desc=True).execute()
        subs = response.data or []

        if subs:
            tenant_ids = list({s['tenant_id'] for s in subs if s.get('tenant_id')})
            if tenant_ids:
                tenants_res = supabase.table('tenants').select('id, name, phone, owner_name, email').in_('id', tenant_ids).execute()
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


@admin_bp.route('/api/admin/reports', methods=['GET', 'OPTIONS'])
@token_required
def admin_get_reports():
    if request.method == 'OPTIONS':
        return '', 200
    try:
        ok, err = _require_super_admin()
        if not ok:
            return err

        now = datetime.now()

        tenants_res = supabase.table('tenants').select('id, status, name, owner_name, phone, subscription_end').execute()
        tenants = tenants_res.data or []
        total_tenants = len(tenants)
        active_tenants = len([t for t in tenants if t.get('status') == 'active'])

        users_res = supabase.table('users').select('id').in_('role', ['teacher', 'assistant']).execute()
        total_users = len(users_res.data or [])

        students_res = supabase.table('students').select('id').execute()
        total_students = len(students_res.data or [])

        subs_res = supabase.table('subscriptions').select('amount').eq('status', 'active').execute()
        subs = subs_res.data or []
        total_revenue = sum(float(s.get('amount', 0)) for s in subs)

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

            month_subs = supabase.table('subscriptions').select('amount').gte('created_at', month_start).lt('created_at', month_end).execute()
            month_total = sum(float(s.get('amount', 0)) for s in (month_subs.data or []))

            monthly_revenue.append({
                'month': month_str,
                'month_label': _arabic_month(year, month),
                'revenue': month_total,
            })

        top_by_students = []
        for t in tenants:
            students_count = len(supabase.table('students').select('id').eq('tenant_id', t['id']).execute().data or [])
            top_by_students.append({
                'tenant_id': t['id'],
                'tenant_name': t.get('name', '—'),
                'owner_name': t.get('owner_name', '—'),
                'students_count': students_count,
            })
        top_by_students.sort(key=lambda x: x['students_count'], reverse=True)
        top_by_students = top_by_students[:5]

        top_by_revenue = []
        for t in tenants:
            tenant_subs = supabase.table('subscriptions').select('amount').eq('tenant_id', t['id']).eq('status', 'active').execute()
            revenue = sum(float(s.get('amount', 0)) for s in (tenant_subs.data or []))
            top_by_revenue.append({
                'tenant_id': t['id'],
                'tenant_name': t.get('name', '—'),
                'owner_name': t.get('owner_name', '—'),
                'revenue': revenue,
            })
        top_by_revenue.sort(key=lambda x: x['revenue'], reverse=True)
        top_by_revenue = top_by_revenue[:5]

        soon = (now + timedelta(days=7)).isoformat()
        expiring_soon = [
            t for t in tenants
            if t.get('status') == 'active'
            and t.get('subscription_end')
            and now.isoformat() < t['subscription_end'] < soon
        ]

        inactive_tenants = []
        for t in tenants:
            if t.get('status') != 'active':
                continue
            students_count = len(supabase.table('students').select('id').eq('tenant_id', t['id']).execute().data or [])
            if students_count == 0:
                inactive_tenants.append({
                    'tenant_id': t['id'],
                    'tenant_name': t.get('name', '—'),
                    'owner_name': t.get('owner_name', '—'),
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