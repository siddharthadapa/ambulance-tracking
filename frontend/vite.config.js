import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // sockjs-client (used by our STOMP client for the WebSocket fallback)
  // references Node's `global` object. CRA/Webpack polyfilled that
  // automatically; Vite doesn't, so we alias it to the browser's globalThis.
  define: {
    global: 'globalThis',
  },
  server: {
    port: 5173,
    open: true,
  },
});
