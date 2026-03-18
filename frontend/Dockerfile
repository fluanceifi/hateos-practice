# frontend/Dockerfile

FROM node:20-alpine

WORKDIR /app

# 의존성 캐싱을 위해 package.json을 먼저 복사합니다.
COPY package.json .
RUN npm install --legacy-peer-deps # React 관련 의존성 문제 무시

# 나머지 소스 코드를 복사합니다.
COPY . .

# React 개발 서버의 기본 포트인 3000번을 엽니다.
EXPOSE 3000

# 개발 서버를 시작합니다.
CMD ["npm", "start"]