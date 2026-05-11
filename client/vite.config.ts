import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
    global: 'window',
  },
  server: {
    allowedHosts: [
      'e0ba-2605-6440-4013-3000-b13a-9d38-6b02-ef95.ngrok-free.app'
    ]
  }
})
