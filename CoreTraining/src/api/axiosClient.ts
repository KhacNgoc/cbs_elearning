import axios from "axios";
import { redirectToLogin } from "../utils/authSession";

const baseURL = import.meta.env.VITE_API_URL || "";
const CORRELATION_ID_HEADER = "X-Correlation-Id";
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

export const QUIZ_GENERATION_TIMEOUT_MS = positiveTimeout(
  import.meta.env.VITE_QUIZ_GENERATION_TIMEOUT_MS,
  420_000,
);

// Lesson files are fetched whole before they render; when a class opens the
// same deck at once over a shared link, 15s only covers a couple of megabytes.
export const DOCUMENT_DOWNLOAD_TIMEOUT_MS = positiveTimeout(
  import.meta.env.VITE_DOCUMENT_DOWNLOAD_TIMEOUT_MS,
  600_000,
);

export const isTimeoutError =(error: unknown): boolean => {
  if (!axios.isAxiosError(error)) return false;
  return (
    error.code === "ECONNABORTED" ||
    error.code === "ETIMEDOUT" ||
    String(error.message || "")
      .toLowerCase()
      .includes("timeout")
  );
};

const createCorrelationId = () =>
  typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

export const axiosClient = axios.create({
  baseURL,
  withCredentials: true,
  timeout: DEFAULT_API_TIMEOUT_MS,
  headers: {
    "Content-Type": "application/json",
  },
});

const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_TIMEOUT_MS = 10_000;
const isMutating = (method?: string) =>
  !["get", "head", "options"].includes((method || "get").toLowerCase());

// The server keeps the CSRF token only in this readable cookie, so its value is the token.
// Reading it saves a round trip before every write, which used to stall the UI whenever the
// backend was slow. The endpoint is only asked when the cookie is missing or rejected.
const readCsrfCookie = () =>
  document.cookie
    .split("; ")
    .find((part) => part.startsWith(`${CSRF_COOKIE}=`))
    ?.slice(CSRF_COOKIE.length + 1);
let fetchedCsrfToken: string | null = null;
let csrfRequest: Promise<string> | null = null;
const fetchCsrfToken = () =>
  (csrfRequest ??= axios
    .get<{ token: string }>(`${baseURL}/api/auth/csrf`, {
      withCredentials: true,
      timeout: CSRF_TIMEOUT_MS,
    })
    .then((response) => (fetchedCsrfToken = response.data.token))
    .finally(() => {
      csrfRequest = null;
    }));
// A cross-origin API (VITE_API_URL) hides its cookie from document.cookie, hence the memory copy.
const csrfToken = async () =>
  readCsrfCookie() || fetchedCsrfToken || (await fetchCsrfToken());

// Request interceptor to append JWT Token
axiosClient.interceptors.request.use(
  async (config) => {
    // A retry already carries the token it was re-sent with.
    if (isMutating(config.method) && !(config as { csrfRetried?: boolean }).csrfRetried) {
      config.headers.set(CSRF_HEADER, await csrfToken());
    }
    if (config.headers) {
      config.headers[CORRELATION_ID_HEADER] = createCorrelationId();
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

// Response interceptor to catch unauthorized errors
axiosClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    // A rotated or expired CSRF token (e.g. after login) gets one retry with a fresh token.
    const config = error.config as (typeof error.config & { csrfRetried?: boolean }) | undefined;
    if (error.response?.status === 403 && config && isMutating(config.method) && !config.csrfRetried) {
      config.csrfRetried = true;
      fetchedCsrfToken = null;
      try {
        config.headers.set(CSRF_HEADER, await fetchCsrfToken());
        return await axiosClient.request(config);
      } catch (retryError) {
        return Promise.reject(retryError);
      }
    }
    const traceId =
      error.response?.headers?.["x-correlation-id"] ||
      error.response?.data?.traceId ||
      error.config?.headers?.[CORRELATION_ID_HEADER];
    console.error("API request failed", {
      method: error.config?.method?.toUpperCase(),
      url: error.config?.url,
      status: error.response?.status,
      code: error.code,
      traceId,
    });
    if (error.response?.status === 401) {
      const isLoginRequest =
        error.config &&
        error.config.url &&
        error.config.url.includes("/api/auth/login");
      if (!isLoginRequest) {
        redirectToLogin();
      }
    }
    // The caller owns user-facing errors (toast, form validation, or retry state).
    // Emitting a second global notification here duplicates that feedback.
    return Promise.reject(error);
  },
);
