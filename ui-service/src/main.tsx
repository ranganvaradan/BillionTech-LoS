import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from '@/auth/AuthProvider'
import { BtToastHost } from '@/components/ui/BtToastHost'
import './index.css'
import App from './App.tsx'

/** Vite BASE_URL `/` → root (empty basename); `/los/` → `/los`. Never coerce root to `/los`. */
function routerBasename(baseUrl: string = import.meta.env.BASE_URL): string {
  const base = baseUrl || '/'
  if (base === '/') return ''
  return base.replace(/\/$/, '')
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter basename={routerBasename()}>
      <AuthProvider>
        <App />
        <BtToastHost />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
