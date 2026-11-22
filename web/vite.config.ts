import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'

// Due pagine: il portale classico (/) e la nuova interfaccia in anteprima (/nuovo/).
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        nuovo: resolve(__dirname, 'nuovo/index.html')
      }
    }
  }
})
