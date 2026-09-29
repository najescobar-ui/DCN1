import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 4200,
    strictPort: true,
    // Local development: same paths as API Gateway, served by the Spring Boot services.
    proxy: {
      '/api/bff': 'http://localhost:8080',
      '/api/productos': 'http://localhost:8081',
      '/api/pedidos': 'http://localhost:8082',
    },
  },
  test: {
    environment: 'jsdom',
  },
})
