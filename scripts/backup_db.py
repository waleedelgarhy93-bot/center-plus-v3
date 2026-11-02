# scripts/backup_db.py - النسخ الاحتياطي التلقائي لبيانات المنصة
import os
import sys
import json
from datetime import datetime
from dotenv import load_dotenv

# ضبط الترميز
sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')

BASE_DIR = r'C:\center-saas'
load_dotenv(os.path.join(BASE_DIR, '.env'))

sys.path.insert(0, BASE_DIR)
from db import supabase

TABLES = [
    'tenants',
    'students',
    'student_groups',
    'groups',
    'subjects',
    'categories',
    'exams',
    'exam_groups',
    'questions',
    'question_bank',
    'student_exams',
    'student_answers',
    'videos',
    'video_groups',
    'video_views',
    'video_unlocks',
    'attendance',
    'payments',
]

def run_backup():
    timestamp = datetime.now().strftime('%Y-%m-%d_%H-%M-%S')
    backup_dir = os.path.join(BASE_DIR, 'backups')
    os.makedirs(backup_dir, exist_ok=True)
    
    backup_file = os.path.join(backup_dir, f'center_plus_backup_{timestamp}.json')
    print(f"🚀 بدء النسخ الاحتياطي لبيانات المنصة ({timestamp})...")

    backup_data = {
        'timestamp': timestamp,
        'created_at': datetime.now().isoformat(),
        'tables': {}
    }

    total_records = 0
    for tbl in TABLES:
        try:
            res = supabase.table(tbl).select('*').limit(10000).execute()
            rows = res.data or []
            backup_data['tables'][tbl] = rows
            total_records += len(rows)
            print(f"  ✅ تم حفظ جدول {tbl:<16}: {len(rows)} سجل")
        except Exception as e:
            print(f"  ⚠️ خطأ في جدول {tbl}: {e}")
            backup_data['tables'][tbl] = []

    # كتابة الملف
    with open(backup_file, 'w', encoding='utf-8') as f:
        json.dump(backup_data, f, ensure_ascii=False, indent=2)

    file_size_kb = os.path.getsize(backup_file) / 1024
    print("-" * 50)
    print(f"🎉 اكتمل النسخ الاحتياطي بنجاح!")
    print(f"📁 مكان الملف: {backup_file}")
    print(f"📊 إجمالي السجلات: {total_records} سجل ({file_size_kb:.2f} KB)")
    print("-" * 50)

if __name__ == '__main__':
    run_backup()
