import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  // Send API calls to the Java backend (backend/, HospitalServer on port 8080) during development.
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
  preview: {
    proxy: { '/api': 'http://localhost:8080' },
  },
})
