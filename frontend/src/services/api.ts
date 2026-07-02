import axios, { AxiosError } from 'axios';
import type { InternalAxiosRequestConfig } from 'axios';
import type {
  AuthResponse,
  LoginRequest,
  Notification,
  NotificationStats,
  PagedResponse,
  RegisterRequest,
  SendNotificationRequest,
  StatusUpdateRequest,
  UserPreferences,
  UserSummary,
} from '@/types';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('notifyflow_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('notifyflow_token');
      localStorage.removeItem('notifyflow_user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  },
);

export const authApi = {
  register: (data: RegisterRequest) =>
    api.post<AuthResponse>('/auth/register', data).then((r) => r.data),
  login: (data: LoginRequest) =>
    api.post<AuthResponse>('/auth/login', data).then((r) => r.data),
};

export const notificationApi = {
  send: (data: SendNotificationRequest) =>
    api.post<Notification>('/notifications/send', data).then((r) => r.data),
  getHistory: (userId: number, page = 0, size = 20) =>
    api
      .get<PagedResponse<Notification>>(`/notifications/${userId}/history`, {
        params: { page, size },
      })
      .then((r) => r.data),
  getStats: (days?: number) =>
    api.get<NotificationStats>('/notifications/stats', { params: { days } }).then((r) => r.data),
  getStatus: (id: number) =>
    api.get<Notification>(`/notifications/${id}/status`).then((r) => r.data),
  updateStatus: (id: number, data: StatusUpdateRequest) =>
    api
      .patch<Notification>(`/notifications/${id}/status`, data)
      .then((r) => r.data),
};

export const usersApi = {
  list: () => api.get<UserSummary[]>('/users').then((r) => r.data),
};

export const preferenceApi = {
  get: (userId: number) =>
    api.get<UserPreferences>(`/preferences/${userId}`).then((r) => r.data),
  update: (userId: number, data: Partial<UserPreferences>) =>
    api
      .put<UserPreferences>(`/preferences/${userId}`, data)
      .then((r) => r.data),
};

export default api;
