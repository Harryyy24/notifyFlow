export type NotificationChannel = 'EMAIL' | 'SMS' | 'IN_APP';
export type NotificationPriority = 'HIGH' | 'NORMAL' | 'LOW';
export type NotificationStatus = 'PENDING' | 'DELIVERED' | 'FAILED';
export type UserRole = 'USER' | 'ADMIN';

export interface User {
  id: number;
  name: string;
  email: string;
  role: UserRole;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  userId: number;
  email: string;
  role: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UserSummary {
  id: number;
  name: string;
  email: string;
  role: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export interface Notification {
  id: number;
  userId: number;
  channel: NotificationChannel;
  title: string;
  message: string;
  status: NotificationStatus;
  priority: NotificationPriority;
  kafkaOffset: number | null;
  retryCount: number;
  createdAt: string;
  deliveredAt: string | null;
}

export interface SendNotificationRequest {
  userId: number;
  channel: NotificationChannel;
  title: string;
  message: string;
  priority: NotificationPriority;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
  first: boolean;
}

export interface UserPreferences {
  userId: number;
  emailEnabled: boolean;
  smsEnabled: boolean;
  inAppEnabled: boolean;
  quietHoursStart: string | null;
  quietHoursEnd: string | null;
}

export interface NotificationStats {
  total: number;
  byStatus: Record<string, number>;
  byChannel: Record<string, number>;
}

export interface StatusUpdateRequest {
  status: NotificationStatus;
}
