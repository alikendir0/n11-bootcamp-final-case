import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// The Vite dev server doubles as the SPA's same-origin reverse proxy: the browser makes
// relative `/api` calls and Vite forwards them to the gateway. The target is env-driven
// because it differs by where Vite runs:
//   - host dev (npm run dev):   http://localhost:9090   (gateway's published host port)
//   - docker-compose frontend:  http://api-gateway:8080 (gateway service on n11-net)
// Relative URLs + this proxy are what let the storefront work unchanged behind the
// Cloudflare tunnel (browser -> tunnel -> frontend:8083 -> gateway), with SSE streamed through.
const PROXY_TARGET = process.env.VITE_PROXY_TARGET ?? 'http://localhost:9090';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 8083,
    allowedHosts: ['n11-shop.alikendir.dev'],
    proxy: {
      '/api': {
        target: PROXY_TARGET,
        changeOrigin: true,
      },
    },
  },
});
