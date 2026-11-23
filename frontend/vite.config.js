import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置：开发服务器 + 后端代理 + @ 别名
export default defineConfig(({ mode }) => {
  // 读取 .env.development / .env.production 中的变量
  const env = loadEnv(mode, process.cwd())

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      port: 5173,
      open: true,
      // 开发环境代理：前端 /api/** -> 后端 http://localhost:8080/**
      // 这样浏览器请求同源，开发阶段无需依赖后端 CORS
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, '')
        }
      }
    },
    build: {
      outDir: 'dist',
      sourcemap: false
    }
  }
})
