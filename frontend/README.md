# Frontend

React and TypeScript web application for AI Love Workout.

회원가입·로그인·운동 프로필 최초 설정·조회·부분 수정·로그아웃과 연결 확인 화면을 제공한다. Access Token은 React 메모리에만 보관하여 새로고침 시 재로그인한다. Node 22.12 이상(검증 환경: Node 24)에서 npm ci 후 npm run dev로 실행한다. /api 요청은 Vite 개발 프록시를 통해 Spring Boot로 전달되며 FastAPI를 직접 호출하지 않는다. npm run build는 TypeScript 검사와 번들 생성을 수행한다. 상세 순서는 [개발 환경 안내](../docs/development.md)를 따른다.
