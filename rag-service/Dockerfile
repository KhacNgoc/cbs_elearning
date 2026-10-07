FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
# Keep downloaded packages across builds without adding the cache to the image.
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --timeout 120 --retries 10 -r requirements.txt
COPY app ./app
EXPOSE 8001
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
