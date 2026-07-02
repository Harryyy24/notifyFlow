import {
  createContext,
  useContext,
  useState,
  useCallback,
  type ReactNode,
} from 'react';
import type { AuthResponse, User } from '@/types';
import { authApi } from '@/services/api';
import toast from 'react-hot-toast';

interface AuthContextType {
  user: User | null;
  token: string | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (
    name: string,
    email: string,
    password: string,
  ) => Promise<void>;
  logout: () => void;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => {
    const stored = localStorage.getItem('notifyflow_user');
    return stored ? JSON.parse(stored) : null;
  });
  const [token, setToken] = useState<string | null>(() =>
    localStorage.getItem('notifyflow_token'),
  );
  const [isLoading, setIsLoading] = useState(false);

  const saveAuth = useCallback((res: AuthResponse) => {
    localStorage.setItem('notifyflow_token', res.token);
    localStorage.setItem(
      'notifyflow_user',
      JSON.stringify({
        id: res.userId,
        email: res.email,
        role: res.role,
        name: '',
      } as User),
    );
    setToken(res.token);
    setUser({
      id: res.userId,
      email: res.email,
      role: res.role as 'USER' | 'ADMIN',
      name: '',
      createdAt: new Date().toISOString(),
    });
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      setIsLoading(true);
      try {
        const res = await authApi.login({ email, password });
        saveAuth(res);
        toast.success('Welcome back!');
      } finally {
        setIsLoading(false);
      }
    },
    [saveAuth],
  );

  const register = useCallback(
    async (name: string, email: string, password: string) => {
      setIsLoading(true);
      try {
        const res = await authApi.register({ name, email, password });
        saveAuth(res);
        toast.success('Account created successfully!');
      } finally {
        setIsLoading(false);
      }
    },
    [saveAuth],
  );

  const logout = useCallback(() => {
    localStorage.removeItem('notifyflow_token');
    localStorage.removeItem('notifyflow_user');
    setToken(null);
    setUser(null);
    toast.success('Logged out');
  }, []);

  const isAdmin = user?.role === 'ADMIN';

  return (
    <AuthContext.Provider
      value={{ user, token, isLoading, login, register, logout, isAdmin }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
