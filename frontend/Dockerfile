FROM node:24-alpine
# 운영용 정적 서버가 아닌 개발용 Vite 서버이며 /api는 Spring Boot로 프록시한다.
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY index.html tsconfig.json vite.config.ts ./
COPY src src
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
