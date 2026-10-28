import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 포트가 점유되면 다른 포트로 바뀌지 않도록 하여 안내된 검증 주소를 유지한다.
    strictPort: true,
    // BACKEND_URL은 Vite 서버용 설정이며 브라우저에 비밀 환경 변수를 전달하지 않는다.
    proxy: { '/api': { target: process.env.BACKEND_URL ?? 'http://127.0.0.1:8080' } },
  },
});
