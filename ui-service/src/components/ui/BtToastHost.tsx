import { Toaster } from 'react-hot-toast'

export function BtToastHost() {
  return (
    <Toaster
      position="top-right"
      gutter={10}
      toastOptions={{
        duration: 4500,
        style: {
          borderRadius: '8px',
          fontSize: '13px',
          fontFamily: 'Inter, sans-serif',
          maxWidth: '420px',
        },
        success: {
          className: 'bt-toast bt-toast-success',
        },
        error: {
          className: 'bt-toast bt-toast-error',
          duration: 5500,
        },
      }}
    />
  )
}
