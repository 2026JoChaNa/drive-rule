import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = process.env.API_TARGET || env.API_TARGET || 'http://localhost:8080';
  const demo = process.env.VITE_DEMO_MODE === 'true';
  const key = process.env.ADMIN_API_KEY || env.ADMIN_API_KEY || '';
  return {
    plugins: [react()],
    define: { __API_LABEL__: JSON.stringify(target), __DEMO_MODE__: JSON.stringify(demo) },
    server: {
      port: 5173, strictPort: true, host: '127.0.0.1',
      proxy: { '/api': {
        target, changeOrigin: true,
        configure(proxy) {
          proxy.on('proxyReq', (proxyReq, req) => {
            // Keep keys server-side when configured. Never attach them to public app endpoints.
            if (req.url?.startsWith('/api/v1/admin/') && !req.headers['x-admin-key'] && key) {
              proxyReq.setHeader('X-Admin-Key', key);
            }
          });
        },
      } },
    },
  };
});
