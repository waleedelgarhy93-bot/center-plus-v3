@echo off
chcp 65001 >nul
echo ========================================================
echo         النسخ الاحتياطي التلقائي لمنصة Center Plus
echo ========================================================
echo.
C:\center-saas\venv\Scripts\python.exe C:\center-saas\scripts\backup_db.py
echo.
echo اضغط أي مفتاح للإغلاق...
pause >nul
