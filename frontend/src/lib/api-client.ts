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

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && typeof window !== 'undefined') {
      // Clear tokens on 401 but don't force-redirect — let the calling code
      // handle the error gracefully (e.g. show mock data on the dashboard).
      // AuthGuard will redirect to /login on the next navigation.
      localStorage.removeItem('los_token');
      localStorage.removeItem('los_refresh_token');
      localStorage.removeItem('los_user');
    }
    return Promise.reject(error);
  }
);

export default apiClient;
