import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios';

// API calls use relative URLs — proxied through Next.js rewrites to the gateway
const apiClient: AxiosInstance = axios.create({
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('los_token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

// No 401 interceptor — pages handle API errors gracefully with mock data fallback.
// Tokens are only cleared on explicit logout (Header/Sidebar logout buttons).

export default apiClient;
