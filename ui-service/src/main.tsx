import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from '@/auth/AuthProvider'
import { BtToastHost } from '@/components/ui/BtToastHost'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter basename={import.meta.env.BASE_URL.replace(/\/$/, '') || '/los'}>
      <AuthProvider>
        <App />
        <BtToastHost />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
