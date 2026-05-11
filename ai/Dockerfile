FROM python:3.11-slim

WORKDIR /app

COPY soh_service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY soh_service/ ./soh_service/
COPY artifacts/curve_fit/standard_curve.json ./artifacts/curve_fit/standard_curve.json

ENV PYTHONPATH=/app

EXPOSE 8000

CMD ["uvicorn", "soh_service.main:app", "--host", "0.0.0.0", "--port", "8000"]
