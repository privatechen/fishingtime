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
    // Keep generated frontend files out of src/main/resources.
    // Maven copies this directory into target/classes/static at package time.
    outDir: resolve(__dirname, 'dist'),
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
