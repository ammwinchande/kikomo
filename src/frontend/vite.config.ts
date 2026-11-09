import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

const backend = 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  build: { outDir: 'dist' },
  server: { proxy: { '/api': backend, '/mcp': backend, '/demo': backend } },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['src/test/setup.ts'],
    css: false,
    restoreMocks: true,
  },
});
