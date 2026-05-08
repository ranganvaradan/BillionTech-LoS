import axios, { AxiosHeaders, AxiosInstance, InternalAxiosRequestConfig } from 'axios';

// API calls go directly to the gateway from the browser in local Docker setup
const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || '';

const apiClient: AxiosInstance = axios.create({
  baseURL: apiBaseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('los_token');
    if (token) {
      const headers =
        config.headers instanceof AxiosHeaders
          ? config.headers
          : new AxiosHeaders(config.headers);

      headers.set('Authorization', `Bearer ${token}`);
      config.headers = headers;
    }
  }
  return config;
});

// No 401 interceptor — pages handle API errors gracefully with mock data fallback.
// Tokens are only cleared on explicit logout (Header/Sidebar logout buttons).

export default apiClient;
