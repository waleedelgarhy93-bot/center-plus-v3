# routes/students.py - CRUD الطلاب + Multi-tenancy + مجموعات متعددة للطالب
from flask import Blueprint, jsonify, request

from db import supabase
from helpers import (
    token_required,
    apply_tenant_filter,
    get_tenant_for_insert,
    get_current_tenant_id,
    check_tenant_ownership,
)

students_bp = Blueprint('students', __name__)


def _enrich_students_groups(students):
    """إثراء قائمة الطلاب ببيانات كل المجموعات المسجلين فيها"""
    if not students:
        return students

    student_ids = [s['id'] for s in students]
    try:
        sg_res = supabase.table('student_groups').select('student_id, group_id').in_('student_id', student_ids).execute()
        sg_rows = sg_res.data or []
    except Exception:
        sg_rows = []

    # جلب أسماء المجموعات
    all_group_ids = set([r['group_id'] for r in sg_rows] + [s['group_id'] for s in students if s.get('group_id')])
    groups_map = {}
    if all_group_ids:
        try:
            groups_res = supabase.table('groups').select('id, grade, center').in_('id', list(all_group_ids)).execute()
            for g in (groups_res.data or []):
                groups_map[g['id']] = f"{g['grade']} - {g['center']}"
        except Exception:
            pass

    # خريطة المجموعات لكل طالب
    s_groups_map = {}
    for r in sg_rows:
        sid = r['student_id']
        gid = r['group_id']
        if sid not in s_groups_map:
            s_groups_map[sid] = []
        if gid not in s_groups_map[sid]:
            s_groups_map[sid].append(gid)

    for s in students:
        sid = s['id']
        gids = s_groups_map.get(sid, [])
        if s.get('group_id') and s['group_id'] not in gids:
            gids.append(s['group_id'])
        s['group_ids'] = gids
        s['group_names'] = [groups_map[gid] for gid in gids if gid in groups_map]

    return students


# ============ Get All ============

@students_bp.route('/api/students', methods=['GET'])
@token_required
def get_students():
    """جلب كل الطلاب (مفلتر حسب السنتر، مع إمكانية التصفية بالمجموعة)"""
    try:
        group_id_param = request.args.get('group_id')
        query = supabase.table('students').select('*')
        query = apply_tenant_filter(query)

        if group_id_param:
            try:
                gid = int(group_id_param)
                sg_res = supabase.table('student_groups').select('student_id').eq('group_id', gid).execute()
                sg_sids = [r['student_id'] for r in (sg_res.data or [])]
                if sg_sids:
                    query = query.or_(f"group_id.eq.{gid},id.in.({','.join(map(str, sg_sids))})")
                else:
                    query = query.eq('group_id', gid)
            except ValueError:
                pass

        response = query.order('name', desc=False).execute()
        students = _enrich_students_groups(response.data or [])

        return jsonify({
            'success': True,
            'count': len(students),
            'students': students
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Get One ============

@students_bp.route('/api/students/<int:student_id>', methods=['GET'])
@token_required
def get_student(student_id):
    """جلب طالب واحد مع مجموعاته"""
    try:
        response = supabase.table('students')\
            .select('*')\
            .eq('id', student_id)\
            .execute()

        if not response.data:
            return jsonify({'success': False, 'error': 'Student not found'}), 404

        student = response.data[0]

        if not check_tenant_ownership(student):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        enriched = _enrich_students_groups([student])
        return jsonify({'success': True, 'student': enriched[0]})

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Create ============

@students_bp.route('/api/students', methods=['POST'])
@token_required
def create_student():
    """إضافة طالب جديد مع دعم مجموعة أو أكثر"""
    try:
        data = request.get_json() or {}

        name = data.get('name')
        if not name:
            return jsonify({'success': False, 'error': 'name مطلوب'}), 400

        tenant_id = get_tenant_for_insert()

        # معالجة المجموعات (group_ids قائمة أو group_id مفرد)
        group_ids = data.get('group_ids')
        if isinstance(group_ids, list):
            valid_gids = [int(g) for g in group_ids if g is not None]
            primary_group_id = valid_gids[0] if valid_gids else None
        else:
            primary_group_id = data.get('group_id')
            valid_gids = [int(primary_group_id)] if primary_group_id else []

        new_student = supabase.table('students').insert({
            'name': name,
            'barcode': data.get('barcode'),
            'phone': data.get('phone'),
            'parent_phone': data.get('parent_phone'),
            'type': data.get('type', 'center'),
            'group_id': primary_group_id,
            'tenant_id': tenant_id,
        }).execute()

        created_student = new_student.data[0] if new_student.data else None
        if created_student and valid_gids:
            student_id = created_student['id']
            sg_rows = [
                {'student_id': student_id, 'group_id': gid, 'tenant_id': tenant_id}
                for gid in valid_gids
            ]
            supabase.table('student_groups').insert(sg_rows).execute()

        enriched = _enrich_students_groups([created_student]) if created_student else []

        return jsonify({
            'success': True,
            'message': 'تم إنشاء الطالب',
            'student': enriched[0] if enriched else created_student
        }), 201

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Update ============

@students_bp.route('/api/students/<int:student_id>', methods=['PUT'])
@token_required
def update_student(student_id):
    """تعديل طالب ومجموعاته"""
    try:
        existing = supabase.table('students')\
            .select('tenant_id')\
            .eq('id', student_id)\
            .execute()

        if not existing.data:
            return jsonify({'success': False, 'error': 'Student not found'}), 404

        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        tenant_id = existing.data[0].get('tenant_id')
        data = request.get_json() or {}

        update_data = {}
        for field in ['name', 'barcode', 'phone', 'parent_phone', 'type']:
            if field in data:
                update_data[field] = data[field]

        # تحديث المجموعات
        if 'group_ids' in data and isinstance(data['group_ids'], list):
            valid_gids = [int(g) for g in data['group_ids'] if g is not None]
            update_data['group_id'] = valid_gids[0] if valid_gids else None

            # مزامنة student_groups
            supabase.table('student_groups').delete().eq('student_id', student_id).execute()
            if valid_gids:
                sg_rows = [
                    {'student_id': student_id, 'group_id': gid, 'tenant_id': tenant_id}
                    for gid in valid_gids
                ]
                supabase.table('student_groups').insert(sg_rows).execute()
        elif 'group_id' in data:
            gid = data['group_id']
            update_data['group_id'] = gid
            supabase.table('student_groups').delete().eq('student_id', student_id).execute()
            if gid:
                supabase.table('student_groups').insert([
                    {'student_id': student_id, 'group_id': int(gid), 'tenant_id': tenant_id}
                ]).execute()

        if not update_data:
            return jsonify({'success': False, 'error': 'لا يوجد بيانات للتعديل'}), 400

        result = supabase.table('students')\
            .update(update_data)\
            .eq('id', student_id)\
            .execute()

        enriched = _enrich_students_groups(result.data or [])

        return jsonify({
            'success': True,
            'message': 'تم التعديل',
            'student': enriched[0] if enriched else None
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Delete ============

@students_bp.route('/api/students/<int:student_id>', methods=['DELETE'])
@token_required
def delete_student(student_id):
    """حذف طالب"""
    try:
        existing = supabase.table('students')\
            .select('tenant_id')\
            .eq('id', student_id)\
            .execute()

        if not existing.data:
            return jsonify({'success': False, 'error': 'Student not found'}), 404

        if not check_tenant_ownership(existing.data[0]):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        # حذف المجموعات أولاً
        supabase.table('student_groups').delete().eq('student_id', student_id).execute()
        supabase.table('students').delete().eq('id', student_id).execute()

        return jsonify({'success': True, 'message': 'تم الحذف'})

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Get by Barcode ============

@students_bp.route('/api/students/barcode/<string:barcode>', methods=['GET'])
@token_required
def get_student_by_barcode(barcode):
    """جلب طالب بالباركود"""
    try:
        response = supabase.table('students')\
            .select('*')\
            .eq('barcode', barcode)\
            .execute()

        if not response.data:
            return jsonify({'success': False, 'error': 'لا يوجد طالب بهذا الكارت'}), 404

        student = response.data[0]

        if not check_tenant_ownership(student):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        enriched = _enrich_students_groups([student])
        return jsonify({'success': True, 'student': enriched[0]})

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Create Parent Account ============

@students_bp.route('/api/students/<int:student_id>/create-parent', methods=['POST'])
@token_required
def create_parent_account(student_id):
    """إنشاء حساب ولي أمر لطالب"""
    try:
        import random
        import string

        student_res = supabase.table('students')\
            .select('*')\
            .eq('id', student_id)\
            .execute()

        if not student_res.data:
            return jsonify({'success': False, 'error': 'الطالب غير موجود'}), 404

        student = student_res.data[0]

        if not check_tenant_ownership(student):
            return jsonify({'success': False, 'error': 'غير مصرح'}), 403

        existing = supabase.table('users')\
            .select('*')\
            .eq('student_id', student_id)\
            .eq('role', 'parent')\
            .execute()

        if existing.data:
            parent = existing.data[0]
            return jsonify({
                'success': True,
                'already_exists': True,
                'message': 'يوجد حساب ولي أمر بالفعل',
                'parent': {
                    'id': parent['id'],
                    'username': parent['username'],
                    'full_name': parent.get('full_name'),
                    'student_name': student['name']
                }
            })

        from helpers import hash_password

        parent_username = None
        for _ in range(30):
            candidate = 'par_' + ''.join(random.choices(string.digits, k=6))
            check = supabase.table('users').select('id').eq('username', candidate).execute()
            if not check.data:
                parent_username = candidate
                break

        if not parent_username:
            return jsonify({'success': False, 'error': 'فشل توليد username'}), 500

        parent_password = ''.join(random.choices(string.digits, k=8))

        new_user = supabase.table('users').insert({
            'username': parent_username,
            'password': hash_password(parent_password),
            'role': 'parent',
            'full_name': f"ولي أمر {student['name']}",
            'phone': student.get('parent_phone'),
            'student_id': student_id,
            'tenant_id': student.get('tenant_id'),
        }).execute()

        return jsonify({
            'success': True,
            'message': f"تم إنشاء حساب ولي الأمر لـ {student['name']}",
            'parent': {
                'id': new_user.data[0]['id'],
                'username': parent_username,
                'password': parent_password,
                'full_name': new_user.data[0]['full_name'],
                'student_name': student['name'],
                'student_id': student_id
            }
        }), 201

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Group Students ============

@students_bp.route('/api/groups/<int:group_id>/students', methods=['GET'])
@token_required
def get_group_students(group_id):
    """جلب طلاب مجموعة معينة (سواء أساسية أو عبر student_groups)"""
    try:
        sg_res = supabase.table('student_groups').select('student_id').eq('group_id', group_id).execute()
        sg_student_ids = [r['student_id'] for r in (sg_res.data or [])]

        query = supabase.table('students').select('*')
        query = apply_tenant_filter(query)

        if sg_student_ids:
            query = query.or_(f"group_id.eq.{group_id},id.in.({','.join(map(str, sg_student_ids))})")
        else:
            query = query.eq('group_id', group_id)

        response = query.order('name', desc=False).execute()
        students = _enrich_students_groups(response.data or [])

        return jsonify({
            'success': True,
            'count': len(students),
            'students': students
        })

    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


# ============ Student Activity & Online Tracker ============

@students_bp.route('/api/students/activity-tracker', methods=['GET'])
@token_required
def get_students_activity_tracker():
    """
    لوحة متابعة نشاط وتواجد الطلاب للمعلم
    من متواجد أونلاين الآن، من دخل اليوم، ومن غائب
    """
    try:
        from datetime import datetime, timezone

        tenant_id = get_current_tenant_id()[0]

        # 1. جلب جميع الطلاب في السنتر
        query = supabase.table('students').select('id, name, barcode, phone, parent_phone, group_id')
        query = apply_tenant_filter(query)
        students_res = query.order('name', desc=False).execute()
        students_data = students_res.data or []

        # جلب أسماء المجموعات
        group_ids = list({s.get('group_id') for s in students_data if s.get('group_id')})
        groups_map = {}
        if group_ids:
            grps_res = supabase.table('groups').select('id, grade, center').in_('id', group_ids).execute()
            groups_map = {g['id']: f"{g['grade']} - {g['center']}" for g in (grps_res.data or [])}

        # 2. جلب جلسات الطلاب من active_sessions
        sessions_query = supabase.table('active_sessions')\
            .select('user_id, last_active, is_active')\
            .eq('user_type', 'student')
        if tenant_id:
            sessions_query = sessions_query.eq('tenant_id', tenant_id)

        sessions_res = sessions_query.execute()
        sessions = sessions_res.data or []

        # دمج أحدث نشاط لكل طالب
        latest_active_map = {}
        for sess in sessions:
            uid = sess.get('user_id')
            la_str = sess.get('last_active')
            if uid and la_str:
                if uid not in latest_active_map or la_str > latest_active_map[uid]:
                    latest_active_map[uid] = la_str

        now_utc = datetime.now(timezone.utc)
        today_start = datetime(now_utc.year, now_utc.month, now_utc.day, tzinfo=timezone.utc)

        enriched_students = []
        online_count = 0
        active_today_count = 0
        dormant_count = 0

        for s in students_data:
            sid = s['id']
            last_active_str = latest_active_map.get(sid)
            is_online = False
            is_today = False
            days_inactive = None
            last_active_formatted = 'لم يسجل دخول بعد'

            if last_active_str:
                try:
                    # تحويل التاريخ
                    clean_iso = last_active_str.replace('Z', '+00:00')
                    dt = datetime.fromisoformat(clean_iso)
                    if dt.tzinfo is None:
                        dt = dt.replace(tzinfo=timezone.utc)

                    diff_sec = (now_utc - dt).total_seconds()
                    days_inactive = int(diff_sec // 86400)

                    # نشط خلال آخر 15 دقيقة = أونلاين الآن
                    if diff_sec <= 900:
                        is_online = True
                        online_count += 1

                    # نشط اليوم
                    if dt >= today_start:
                        is_today = True
                        active_today_count += 1

                    if days_inactive >= 3:
                        dormant_count += 1

                    last_active_formatted = dt.strftime('%Y-%m-%d %I:%M %p')
                except Exception:
                    pass
            else:
                dormant_count += 1

            enriched_students.append({
                'id': sid,
                'name': s['name'],
                'barcode': s.get('barcode'),
                'phone': s.get('phone'),
                'parent_phone': s.get('parent_phone'),
                'group_id': s.get('group_id'),
                'group_name': groups_map.get(s.get('group_id'), 'غير محدد'),
                'last_active': last_active_str,
                'last_active_formatted': last_active_formatted,
                'is_online': is_online,
                'is_active_today': is_today,
                'days_inactive': days_inactive,
            })

        # ترتيب: الأونلاين أولاً، ثم النشطين اليوم، ثم الباقي
        enriched_students.sort(key=lambda x: (not x['is_online'], not x['is_active_today'], x['days_inactive'] if x['days_inactive'] is not None else 9999))

        return jsonify({
            'success': True,
            'summary': {
                'total_students': len(enriched_students),
                'online_now': online_count,
                'active_today': active_today_count,
                'inactive_today': len(enriched_students) - active_today_count,
                'dormant_3days': dormant_count,
            },
            'students': enriched_students
        })
    except Exception as e:
        print(f"Activity Tracker Error: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500
