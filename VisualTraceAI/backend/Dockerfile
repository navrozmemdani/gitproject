FROM python:3.13-slim AS base
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg tesseract-ocr libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
ARG WITH_ML=false
COPY requirements-ml.txt .
RUN if [ "$WITH_ML" = "true" ]; then pip install --no-cache-dir -r requirements-ml.txt; fi
COPY . .
RUN useradd --system --uid 10001 app && mkdir -p /srv/tmp && chown -R app /srv/tmp
USER app
EXPOSE 8000
CMD ["sh", "-c", "alembic upgrade head && uvicorn app.main:create_app --factory --host 0.0.0.0 --port 8000 --proxy-headers --workers 1"]
