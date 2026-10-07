import path from 'node:path'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 백엔드 프록시 대상. 환경 변수로 교체 가능
const backend = process.env.VITE_BACKEND_ORIGIN ?? 'http://localhost:8080'

// changeOrigin: Host를 백엔드 주소로 치환.
// 미설정 시 redirect_uri가 localhost:5173으로 생성되어 카카오 미등록 주소(KOE006).
const toBackend = { target: backend, changeOrigin: true }

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
  server: {
    // 백엔드와 동일 출처 구성. 세션 쿠키 SameSite=Lax라 타 사이트 요청에는 미전송
    proxy: {
      '/api': toBackend,
      // 카카오 로그인 시작. A_002를 명세 경로로 옮기면 제거
      '/oauth2': toBackend,
    },
  },
})
