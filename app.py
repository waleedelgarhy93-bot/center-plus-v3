# app.py - Center Plus SaaS (Main Entry Point)
import os
import warnings
from flask import Flask, jsonify, request
from flask_cors import CORS
from dotenv import load_dotenv

warnings.filterwarnings('ignore')
load_dotenv()

app = Flask(__name__)
app.json.ensure_ascii = False
app.config['SECRET_KEY'] = os.getenv('FLASK_SECRET_KEY')

# إعداد CORS الشامل لـ Vercel والمحلي
CORS(
    app,
    resources={r"/*": {
        "origins": [
            "https://lopital-center.vercel.app",
            "http://localhost:5173",
            "http://localhost:3000"
        ]
    }},
    methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Content-Type", "Authorization", "X-Requested-With"],
    supports_credentials=True,
)

# دعم الرد على OPTIONS عالمياً
@app.before_request
def handle_preflight():
    if request.method == "OPTIONS":
        response = app.make_default_options_response()
        response.headers["Access-Control-Allow-Origin"] = request.headers.get("Origin", "*")
        response.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, PATCH, DELETE, OPTIONS"
        response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization, X-Requested-With"
        response.headers["Access-Control-Allow-Credentials"] = "true"
        return response, 200

# ============ Register Blueprints ============

from routes.auth import auth_bp
from routes.students import students_bp
from routes.subjects import subjects_bp
from routes.groups import groups_bp
from routes.attendance import attendance_bp
from routes.cards import cards_bp
from routes.grades import grades_bp
from routes.payments import payments_bp
from routes.videos import videos_bp
from routes.exams import exams_bp
from routes.reports import reports_bp
from routes.upload import upload_bp
from routes.admin import admin_bp
from routes.honor import honor_bp
from routes.devices import devices_bp

app.register_blueprint(auth_bp)
app.register_blueprint(students_bp)
app.register_blueprint(subjects_bp)
app.register_blueprint(groups_bp)
app.register_blueprint(attendance_bp)
app.register_blueprint(cards_bp)
app.register_blueprint(grades_bp)
app.register_blueprint(payments_bp)
app.register_blueprint(videos_bp)
app.register_blueprint(exams_bp)
app.register_blueprint(reports_bp)
app.register_blueprint(upload_bp)
app.register_blueprint(admin_bp)
app.register_blueprint(honor_bp)
app.register_blueprint(devices_bp)

# ============ Basic Routes ============

@app.route('/')
def home():
    return jsonify({
        'app': 'Center Plus',
        'version': '2.0.0',
        'status': 'running',
        'architecture': 'modular (blueprints)'
    })

@app.route('/api/health')
def health():
    return jsonify({
        'status': 'healthy',
        'supabase_url': os.getenv('SUPABASE_URL')
    })

@app.route('/api/test')
def test():
    return jsonify({'message': 'API is working!'})

if __name__ == '__main__':
    port = int(os.getenv('PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=False)