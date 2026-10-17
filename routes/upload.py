# routes/upload.py - رفع الصور
import uuid
from flask import Blueprint, jsonify, request

from db import supabase
from helpers import token_required, get_current_tenant_id

upload_bp = Blueprint('upload', __name__)


@upload_bp.route('/api/upload/image', methods=['POST'])
@token_required
def upload_image():
    """رفع صورة إلى Supabase Storage"""
    try:
        if 'file' not in request.files:
            return jsonify({'success': False, 'error': 'file مطلوب'}), 400

        file = request.files['file']
        folder = request.form.get('folder', 'general')

        if file.filename == '':
            return jsonify({'success': False, 'error': 'اسم الملف فاضي'}), 400

        # الحد الأقصى 1 MB
        file.seek(0, 2)
        file_size = file.tell()
        file.seek(0)

        if file_size > 1 * 1024 * 1024:
            return jsonify({
                'success': False,
                'error': f'حجم الصورة أكبر من 1 MB ({file_size // 1024} KB)'
            }), 400

        # اسم فريد
        ext = file.filename.rsplit('.', 1)[-1].lower() if '.' in file.filename else 'jpg'
        unique_name = f"{folder}/{uuid.uuid4().hex}.{ext}"

        file_content = file.read()

        # رفع
        try:
            supabase.storage.from_('exam-images').upload(
                unique_name,
                file_content,
                file_options={'content-type': file.content_type or 'image/jpeg'}
            )
        except Exception:
            unique_name = f"{folder}/{uuid.uuid4().hex}_2.{ext}"
            supabase.storage.from_('exam-images').upload(
                unique_name,
                file_content,
                file_options={'content-type': file.content_type or 'image/jpeg'}
            )

        public_url = supabase.storage.from_('exam-images').get_public_url(unique_name)

        return jsonify({
            'success': True,
            'url': public_url,
            'path': unique_name,
            'size': file_size
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500