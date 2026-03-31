@echo off
chcp 65001 > nul
title BatteryInsight SOH 서버

echo.
echo ========================================
echo   BatteryInsight SOH 서버 시작
echo ========================================
echo.

:: 현재 배치 파일 위치를 프로젝트 루트로 설정
cd /d "%~dp0"

:: .venv 존재 여부 확인
if not exist ".venv\Scripts\activate.bat" (
    echo [오류] .venv 가상환경을 찾을 수 없습니다.
    echo        BatteryInsight_Android 폴더에서 실행했는지 확인하세요.
    pause
    exit /b 1
)

:: 가상환경 활성화
call .venv\Scripts\activate.bat
echo [완료] 가상환경 활성화

:: uvicorn 설치 여부 확인
python -c "import uvicorn" 2>nul
if errorlevel 1 (
    echo [설치] uvicorn 및 패키지 설치 중...
    pip install -r soh_service\requirements.txt -q
    echo [완료] 패키지 설치
)

:: pkl 파일 존재 여부 확인
if not exist "soh_service\model\soh_model.pkl" (
    echo [오류] soh_model.pkl 파일이 없습니다.
    echo        soh_service\model\ 폴더에 pkl 파일을 넣어주세요.
    pause
    exit /b 1
)

echo [완료] 모델 파일 확인
echo.
echo 서버 주소: http://127.0.0.1:8000
echo API 문서:  http://127.0.0.1:8000/docs
echo.
echo 종료하려면 Ctrl+C 를 누르세요.
echo ========================================
echo.

:: 서버 실행
uvicorn soh_service.main:app --host 127.0.0.1 --port 8000 --reload

pause