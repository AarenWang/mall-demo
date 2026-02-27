import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const isMerchantB = mode === 'merchant-b'
  return {
    plugins: [react()],
    server: {
      port: isMerchantB ? 5174 : 5173,
      host: true
    }
  }
})
