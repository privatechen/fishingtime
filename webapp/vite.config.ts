import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  base: '/',
  build: {
    outDir: resolve(__dirname, '../src/main/resources/static'),
    emptyOutDir: true,
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        // 用 127.0.0.1 避免本机 localhost 解析到 ::1 导致 Node 代理 ECONNRESET
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
