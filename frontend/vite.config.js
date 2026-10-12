import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // Fixed port: the backend's CORS config allows http://localhost:5173.
  server: { port: 5173, strictPort: true },
})
