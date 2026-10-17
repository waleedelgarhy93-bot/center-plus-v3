# routes/reports.py - التقارير العامة
from flask import Blueprint, jsonify, request
from datetime import datetime, timedelta

from db import supabase
from helpers import token_required, apply_tenant_filter, get_current_tenant_id

reports_bp = Blueprint('reports', __name__)


@reports_bp.route('/api/reports/overview', methods=['GET'])
@token_required
def get_reports_overview():
    """نظرة عامة: أعداد + إحصائيات"""
    try:
        tenant_id, role = get_current_tenant_id()
        is_super = role == 'super_admin'

        def count_table(table):
            q = supabase.table(table).select('id')
            if not is_super and tenant_id:
                q = q.eq('tenant_id', tenant_id)
            return len(q.execute().data or [])

        students_count = count_table('students')
        subjects_count = count_table('subjects')
        groups_count = count_table('groups')
        exams_count = count_table('exams')
        cards_count = count_table('cards')

        # حضور اليوم
        today = datetime.now().strftime('%Y-%m-%d')
        today_q = supabase.table('attendance').select('id').eq('date', today)
        if not is_super and tenant_id:
            today_q = today_q.eq('tenant_id', tenant_id)
        today_present = len(today_q.execute().data or [])

        # مالية الشهر
        current_month = datetime.now().strftime('%Y-%m')
        month_q = supabase.table('payments').select('*').eq('month', current_month)
        if not is_super and tenant_id:
            month_q = month_q.eq('tenant_id', tenant_id)
        month_payments = month_q.execute().data or []
        month_paid = sum(float(p.get('amount', 0)) for p in month_payments if p.get('status') == 'paid')

        # إجمالي
        all_q = supabase.table('payments').select('amount, status')
        if not is_super and tenant_id:
            all_q = all_q.eq('tenant_id', tenant_id)
        all_payments = all_q.execute().data or []
        all_paid = sum(float(p.get('amount', 0)) for p in all_payments if p.get('status') == 'paid')

        return jsonify({
            'success': True,
            'overview': {
                'students': students_count,
                'subjects': subjects_count,
                'groups': groups_count,
                'exams': exams_count,
                'cards': cards_count,
                'today_present': today_present,
                'month_paid': month_paid,
                'total_paid': all_paid,
                'current_month': current_month,
                'today': today,
            }
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@reports_bp.route('/api/reports/students', methods=['GET'])
@token_required
def get_reports_students():
    """تقارير الطلاب: أفضل الطلاب + الحضور"""
    try:
        tenant_id, role = get_current_tenant_id()
        is_super = role == 'super_admin'

        # الطلاب
        students_q = supabase.table('students').select('id, name, barcode, group_id')
        if not is_super and tenant_id:
            students_q = students_q.eq('tenant_id', tenant_id)
        students = students_q.execute().data or []

        if not students:
            return jsonify({
                'success': True,
                'top_by_grade': [],
                'top_by_attendance': [],
                'most_absent': []
            })

        student_ids = [s['id'] for s in students]

        # درجات
        subs_q = supabase.table('student_exams')\
            .select('student_id, total_grade, max_grade')\
            .in_('student_id', student_ids)
        subs_data = subs_q.execute().data or []

        student_avg = {}
        for sub in subs_data:
            sid = sub['student_id']
            if sid not in student_avg:
                student_avg[sid] = {'total': 0, 'max': 0, 'count': 0}
            student_avg[sid]['total'] += float(sub.get('total_grade', 0))
            student_avg[sid]['max'] += float(sub.get('max_grade', 0))
            student_avg[sid]['count'] += 1

        top_by_grade = []
        for s in students:
            if s['id'] in student_avg:
                data = student_avg[s['id']]
                if data['max'] > 0:
                    avg = (data['total'] / data['max']) * 100
                    top_by_grade.append({
                        'student_id': s['id'],
                        'student_name': s['name'],
                        'student_barcode': s.get('barcode'),
                        'average': round(avg, 1),
                        'exams_count': data['count'],
                    })

        top_by_grade.sort(key=lambda x: x['average'], reverse=True)
        top_by_grade = top_by_grade[:10]

        # حضور
        att_q = supabase.table('attendance').select('student_id').in_('student_id', student_ids)
        attendance_data = att_q.execute().data or []

        attendance_count = {}
        for a in attendance_data:
            sid = a['student_id']
            attendance_count[sid] = attendance_count.get(sid, 0) + 1

        top_by_attendance = []
        for s in students:
            top_by_attendance.append({
                'student_id': s['id'],
                'student_name': s['name'],
                'student_barcode': s.get('barcode'),
                'attendance_count': attendance_count.get(s['id'], 0),
            })

        top_by_attendance.sort(key=lambda x: x['attendance_count'], reverse=True)
        top_by_attendance = top_by_attendance[:10]

        # الأكثر غياباً (30 يوم)
        thirty_days_ago = (datetime.now() - timedelta(days=30)).strftime('%Y-%m-%d')
        recent_q = supabase.table('attendance')\
            .select('student_id')\
            .in_('student_id', student_ids)\
            .gte('date', thirty_days_ago)
        recent_data = recent_q.execute().data or []

        recent_count = {}
        for a in recent_data:
            sid = a['student_id']
            recent_count[sid] = recent_count.get(sid, 0) + 1

        most_absent = []
        for s in students:
            most_absent.append({
                'student_id': s['id'],
                'student_name': s['name'],
                'student_barcode': s.get('barcode'),
                'attendance_count': recent_count.get(s['id'], 0),
            })

        most_absent.sort(key=lambda x: x['attendance_count'])
        most_absent = most_absent[:5]

        return jsonify({
            'success': True,
            'top_by_grade': top_by_grade,
            'top_by_attendance': top_by_attendance,
            'most_absent': most_absent,
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@reports_bp.route('/api/reports/payments', methods=['GET'])
@token_required
def get_reports_payments():
    """تقارير المدفوعات: آخر 6 شهور"""
    try:
        tenant_id, role = get_current_tenant_id()
        is_super = role == 'super_admin'

        # آخر 6 شهور
        months = []
        now = datetime.now()
        for i in range(5, -1, -1):
            year = now.year
            month = now.month - i
            while month <= 0:
                month += 12
                year -= 1
            months.append(f'{year}-{str(month).zfill(2)}')

        all_q = supabase.table('payments').select('*')
        if not is_super and tenant_id:
            all_q = all_q.eq('tenant_id', tenant_id)
        payments = all_q.execute().data or []

        monthly_data = []
        for month in months:
            month_payments = [p for p in payments if p.get('month') == month]
            paid = sum(float(p.get('amount', 0)) for p in month_payments if p.get('status') == 'paid')
            count = len([p for p in month_payments if p.get('status') == 'paid'])

            monthly_data.append({
                'month': month,
                'paid': paid,
                'count': count,
            })

        total_paid = sum(m['paid'] for m in monthly_data)

        return jsonify({
            'success': True,
            'months': monthly_data,
            'total_paid': total_paid,
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@reports_bp.route('/api/reports/attendance', methods=['GET'])
@token_required
def get_reports_attendance():
    """تقارير الحضور: آخر 7 أيام + الشهر"""
    try:
        tenant_id, role = get_current_tenant_id()
        is_super = role == 'super_admin'

        # عدد الطلاب
        students_q = supabase.table('students').select('id')
        if not is_super and tenant_id:
            students_q = students_q.eq('tenant_id', tenant_id)
        students_count = len(students_q.execute().data or [])

        # آخر 7 أيام
        daily_data = []
        for i in range(6, -1, -1):
            date = (datetime.now() - timedelta(days=i)).strftime('%Y-%m-%d')
            q = supabase.table('attendance').select('id').eq('date', date)
            if not is_super and tenant_id:
                q = q.eq('tenant_id', tenant_id)
            count = len(q.execute().data or [])
            daily_data.append({'date': date, 'count': count})

        # الشهر (30 يوم)
        thirty_days_ago = (datetime.now() - timedelta(days=30)).strftime('%Y-%m-%d')
        month_q = supabase.table('attendance').select('id').gte('date', thirty_days_ago)
        if not is_super and tenant_id:
            month_q = month_q.eq('tenant_id', tenant_id)
        month_total = len(month_q.execute().data or [])

        # اليوم
        today = datetime.now().strftime('%Y-%m-%d')
        today_q = supabase.table('attendance').select('id').eq('date', today)
        if not is_super and tenant_id:
            today_q = today_q.eq('tenant_id', tenant_id)
        today_total = len(today_q.execute().data or [])

        return jsonify({
            'success': True,
            'daily': daily_data,
            'month_total': month_total,
            'today_total': today_total,
            'students_count': students_count,
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500