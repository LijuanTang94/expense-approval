import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Proxy /api to the Spring Boot app so the dev server behaves like production, where the
    // backend serves the built SPA and the API is same-origin. Keeping both environments
    // same-origin means the app never depends on a cross-origin setup working.
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
