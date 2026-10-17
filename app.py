# app.py - Center Plus SaaS (Main Entry Point)
import os
from flask import Flask, jsonify
from flask_cors import CORS
from dotenv import load_dotenv

# تجاهل WinError 10035
import warnings
warnings.filterwarnings('ignore')

load_dotenv()

# ============ Flask App ============

app = Flask(__name__)
app.json.ensure_ascii = False
app.config['SECRET_KEY'] = os.getenv('FLASK_SECRET_KEY')

CORS(app)


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


# ============ Run ============

if __name__ == '__main__':
    port = int(os.getenv('PORT', 5000))
    print("=" * 50)
    print("Center Plus API v2.0")
    print("Server: http://localhost:" + str(port))
    print("Architecture: Modular Blueprints")
    print("=" * 50)
    app.run(host='0.0.0.0', port=port, debug=False)