import { Toaster } from 'react-hot-toast'

export function BtToastHost() {
  return (
    <Toaster
      position="top-right"
      gutter={12}
      toastOptions={{
        duration: 4500,
        style: {
          borderRadius: '10px',
          fontSize: '14px',
          lineHeight: '1.45',
          fontFamily: 'Inter, sans-serif',
          maxWidth: '480px',
          padding: '12px 14px',
          boxShadow: '0 10px 30px rgba(15, 23, 42, 0.12)',
        },
        success: {
          className: 'bt-toast bt-toast-success',
        },
        error: {
          className: 'bt-toast bt-toast-error',
          duration: 6500,
        },
      }}
    />
  )
}
