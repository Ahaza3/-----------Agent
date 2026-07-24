FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONUTF8=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

COPY requirements-docker.txt .
RUN pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu 'torch==2.3.1+cpu' \
    && pip install --no-cache-dir -r requirements-docker.txt

COPY app.py .
COPY models ./models

EXPOSE 5000
HEALTHCHECK --interval=30s --timeout=10s --start-period=20s --retries=5 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:5000/health', timeout=5)" || exit 1

CMD ["python", "app.py"]
