import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: {
    global: 'window',
  },
  server: {
    allowedHosts: [
      'f261-2a0d-5600-235-8000-b9bd-a457-2cf4-8038.ngrok-free.app'
    ]
  }
})
