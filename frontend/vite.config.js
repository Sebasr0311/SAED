import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [react()],
  base: process.env.VITE_BASE_PATH || '/',
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // Dev server: permite el proxy de Vercel v0 (hosts dinamicos sb-*.vercel.run).
  // Solo afecta al dev server local; no se usa en el build de produccion.
  server: {
    allowedHosts: ['.vercel.run'],
  },
  // Preview server (vite preview): v0 tambien puede servir el build por aqui.
  preview: {
    allowedHosts: ['.vercel.run'],
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom', 'react-router-dom'],
        },
      },
    },
  },
});
