import path from 'node:path'
import { fileURLToPath } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// Backend (los-core) target for the dev proxy.
// - Local (non-Docker) dev: defaults to http://localhost:8083
// - Docker compose: set VITE_API_PROXY_TARGET=http://los-core:8083 (service name on the compose network)
const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8083'

// https://vite.dev/config/
export default defineConfig({
  base: process.env.VITE_BASE_PATH || '/los/',
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      '/los/api': {
        target: apiProxyTarget,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/los\/api/, '/api'),
      },
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
      },
    },
  },
})
