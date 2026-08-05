import axios from 'axios';
import { redirectToLogin } from '../utils/authSession';

const baseURL = import.meta.env.VITE_API_URL || '';
const CORRELATION_ID_HEADER = 'X-Correlation-Id';
const DEFAULT_API_TIMEOUT_MS = 15_000;

const positiveTimeout = (value: unknown, fallback: number) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
};

// A contextual RAG turn can include rewrite, embedding, retrieval, generation,
// and an occasional summary update. Keep this budget isolated from normal APIs.
export const RAG_CHAT_TIMEOUT_MS = positiveTimeout(
  import.meta.env.VITE_RAG_CHAT_TIMEOUT_MS,
  330_000,
);

export const isTimeoutError = (error: unknown): boolean => {
  if (!axios.isAxiosError(error)) return false;
  return error.code === 'ECONNABORTED' ||
    error.code === 'ETIMEDOUT' ||
    String(error.message || '').toLowerCase().includes('timeout');
};

const createCorrelationId = () =>
  typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

export const axiosClient = axios.create({
  baseURL,
  withCredentials: true,
  timeout: DEFAULT_API_TIMEOUT_MS,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to append JWT Token
axiosClient.interceptors.request.use(
  (config) => {
    if (config.headers) {
      config.headers[CORRELATION_ID_HEADER] = createCorrelationId();
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor to catch unauthorized errors
axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const traceId =
      error.response?.headers?.['x-correlation-id'] ||
      error.response?.data?.traceId ||
      error.config?.headers?.[CORRELATION_ID_HEADER];
    console.error('API request failed', {
      method: error.config?.method?.toUpperCase(),
      url: error.config?.url,
      status: error.response?.status,
      code: error.code,
      traceId,
    });
    if (error.response?.status === 401) {
      const isLoginRequest = error.config && error.config.url && error.config.url.includes('/api/auth/login');
      if (!isLoginRequest) {
        redirectToLogin();
      }
    }
    const isTimeout = isTimeoutError(error);
    const isNetworkError = !error.response;
    const isServerError = error.response?.status >= 500;
    if (isTimeout || isNetworkError || isServerError) {
      const message = isTimeout
        ? 'Yêu cầu đã quá thời gian chờ. Vui lòng thử lại.'
        : isNetworkError
          ? 'Không thể kết nối tới hệ thống. Vui lòng kiểm tra mạng.'
          : 'Hệ thống đang gặp sự cố. Vui lòng thử lại sau.';
      window.dispatchEvent(new CustomEvent('system-error', { detail: message }));
    }
    return Promise.reject(error);
  }
);

// Helper to check if mock mode is active
export const isMockApi = (): boolean => {
  return import.meta.env.VITE_USE_MOCK_API === 'true';
};

// Helper to simulate network delay for mock endpoints
export const mockDelay = (ms: number = 300): Promise<void> => {
  return new Promise((resolve) => setTimeout(resolve, ms));
};
