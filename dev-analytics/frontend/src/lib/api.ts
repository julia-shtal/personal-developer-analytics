import axios from 'axios';

// Access token lives in memory only — never in localStorage.
let _accessToken: string | null = null;

export function setAccessToken(token: string | null) {
  _accessToken = token;
}

export function getAccessToken() {
  return _accessToken;
}

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  // withCredentials lets the browser send the httpOnly refresh_token cookie
  // on cross-origin requests (needed for the Vite dev server on :5173).
  withCredentials: true,
});

// Attach access token to every request
api.interceptors.request.use((config) => {
  if (_accessToken) {
    config.headers.Authorization = `Bearer ${_accessToken}`;
  }
  return config;
});

// Single in-flight refresh promise — all concurrent 401s share one refresh call.
let refreshPromise: Promise<string> | null = null;

// On 401, attempt token refresh (cookie is sent automatically) then retry.
api.interceptors.response.use(
  (res) => res,
  async (error) => {
    // Network error (no response) — backend is down; don't attempt refresh
    if (!error.response) {
      return Promise.reject(error);
    }
    const original = error.config;
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true;
      try {
        if (!refreshPromise) {
          refreshPromise = axios
            .post<{ accessToken: string }>('/api/auth/refresh', null, { withCredentials: true })
            .then(({ data }) => {
              _accessToken = data.accessToken;
              return data.accessToken;
            })
            .finally(() => {
              refreshPromise = null;
            });
        }
        const newToken = await refreshPromise;
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      } catch {
        clearTokens();
        window.location.href = '/login';
        return Promise.reject(error);
      }
    }
    return Promise.reject(error);
  }
);

export function clearTokens() {
  _accessToken = null;
}

export default api;