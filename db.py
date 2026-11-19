# db.py - Supabase + Gemini Setup
import os
from dotenv import load_dotenv
from supabase import create_client, Client

# تجاهل WinError 10035
import warnings
warnings.filterwarnings('ignore')

load_dotenv()

SUPABASE_URL = os.getenv('SUPABASE_URL')
SUPABASE_KEY = os.getenv('SUPABASE_SECRET_KEY')

if not SUPABASE_URL or not SUPABASE_KEY:
    raise ValueError("❌ SUPABASE_URL أو SUPABASE_SECRET_KEY مش موجودين في .env")

supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)


# ============ Gemini AI Setup ============

import google.generativeai as genai

GEMINI_API_KEY = os.getenv('GEMINI_API_KEY')

if GEMINI_API_KEY:
    # استخدام REST transport لضمان السرعة وتجنب تعليق gRPC
    genai.configure(api_key=GEMINI_API_KEY, transport='rest')
    pass
else:
    pass


# ============ Helpers للاستخدام السريع ============

def get_supabase() -> Client:
    """يرجّع Supabase client"""
    return supabase


# موديلات بديلة بالترتيب: لو الأول مشغول (503) أو وصل للحد (429) أو اتأخر، نجرب اللي بعده
GEMINI_MODEL_CHAIN = ['gemini-3.5-flash', 'gemini-3.8-flash', 'gemini-flash-latest', 'gemini-3.5-flash-lite']
_RETRYABLE = ('503', '429', '504', '500', '404', 'not found', 'no longer available', 'unavailable', 'high demand', 'overloaded',
              'resource_exhausted', 'quota', 'deadline', 'timed out', 'timeout')


def generate_with_fallback(contents, total_budget=105, per_try=80, generation_config=None):
    """توليد بالذكاء الاصطناعي مع تجربة موديلات بديلة.
    - من غير retry داخلي طويل (المكتبة كانت بتعيد المحاولة لحد 10 دقايق وتعلّق الطلب)
    - مجموع الوقت أقل من timeout بتاع gunicorn (120 ثانية)
    - generation_config اختياري (مثلاً response_mime_type=application/json و max_output_tokens)"""
    import time
    deadline = time.time() + total_budget
    errors = []
    for name in GEMINI_MODEL_CHAIN:
        remaining = deadline - time.time()
        if remaining < 10:
            break
        try:
            model = genai.GenerativeModel(name)
            return model.generate_content(
                contents,
                generation_config=generation_config,
                request_options={'timeout': min(per_try, remaining), 'retry': None},
            )
        except Exception as e:
            msg = str(e)
            errors.append(f'{name}: {msg[:200]}')
            print(f'[gemini] {name} failed: {msg[:200]}')
            if any(k in msg.lower() for k in _RETRYABLE):
                continue
            raise
    raise RuntimeError('AI_BUSY | ' + ' || '.join(errors))


def get_gemini_model():
    """يرجّع Gemini model السريع والمستقر مع بدائل"""
    try:
        return genai.GenerativeModel('gemini-3.5-flash')
    except Exception:
        return genai.GenerativeModel('gemini-3.8-flash')
