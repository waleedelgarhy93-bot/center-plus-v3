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
    genai.configure(api_key=GEMINI_API_KEY)
    print("✅ Gemini AI initialized")
else:
    print("⚠️ Gemini API key not found")


# ============ Helpers للاستخدام السريع ============

def get_supabase() -> Client:
    """يرجّع Supabase client"""
    return supabase


def get_gemini_model():
    """يرجّع Gemini model"""
    return genai.GenerativeModel('gemini-3.8-flash')