FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt ./
# 테스트 의존성은 실행 이미지에 포함하지 않는다.
RUN pip install --no-cache-dir -r requirements.txt
COPY app app
EXPOSE 8000
# 컨테이너 네트워크의 Spring Boot가 접근할 수 있도록 내부 인터페이스에 바인딩한다.
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
