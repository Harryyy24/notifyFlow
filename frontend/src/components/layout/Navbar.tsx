import { useLocation } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useTheme } from '@/context/ThemeContext';
import { useAuth } from '@/context/AuthContext';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import {
  Sun,
  Moon,
  Monitor,
  LogOut,
  ChevronDown,
} from 'lucide-react';
import { useState, useRef, useEffect } from 'react';
import { cn } from '@/lib/utils';

const pageTitles: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/send': 'Send Notification',
  '/history': 'Notification History',
  '/analytics': 'Analytics',
  '/preferences': 'Preferences',
  '/admin': 'Admin Panel',
  '/profile': 'Profile',
};

export default function Navbar() {
  const location = useLocation();
  const { theme, setTheme, resolvedTheme } = useTheme();
  const { user, logout } = useAuth();
  const [profileOpen, setProfileOpen] = useState(false);
  const [themeOpen, setThemeOpen] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);
  const themeRef = useRef<HTMLDivElement>(null);

  const title = pageTitles[location.pathname] || 'Dashboard';

  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (profileRef.current && !profileRef.current.contains(e.target as Node))
        setProfileOpen(false);
      if (themeRef.current && !themeRef.current.contains(e.target as Node))
        setThemeOpen(false);
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  const initials = user?.email?.charAt(0).toUpperCase() || 'U';

  const themeOptions = [
    { value: 'light', icon: Sun },
    { value: 'dark', icon: Moon },
    { value: 'system', icon: Monitor },
  ] as const;

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b bg-background/80 backdrop-blur-xl px-6 lg:px-8">
      <motion.div
        key={title}
        initial={{ opacity: 0, y: -4 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-lg font-semibold tracking-tight"
      >
        {title}
      </motion.div>

      <div className="flex items-center gap-3">
        {/* Theme Toggle */}
        <div ref={themeRef} className="relative">
          <button
            onClick={() => setThemeOpen(!themeOpen)}
            className="flex h-9 w-9 items-center justify-center rounded-lg border hover:bg-accent/5 transition-all duration-200"
          >
            {resolvedTheme === 'dark' ? (
              <Moon className="h-4 w-4" />
            ) : (
              <Sun className="h-4 w-4" />
            )}
          </button>
          {themeOpen && (
            <motion.div
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 8 }}
              className="absolute right-0 top-full mt-2 w-36 rounded-xl border bg-card p-1.5 shadow-xl"
            >
              {themeOptions.map(({ value, icon: Icon }) => (
                <button
                  key={value}
                  onClick={() => {
                    setTheme(value);
                    setThemeOpen(false);
                  }}
                  className={cn(
                    'flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm transition-all duration-200',
                    theme === value
                      ? 'bg-accent/10 text-accent'
                      : 'hover:bg-muted',
                  )}
                >
                  <Icon className="h-4 w-4" />
                  {value.charAt(0).toUpperCase() + value.slice(1)}
                </button>
              ))}
            </motion.div>
          )}
        </div>

        {/* Profile */}
        <div ref={profileRef} className="relative">
          <button
            onClick={() => setProfileOpen(!profileOpen)}
            className="flex items-center gap-2 rounded-lg border p-1.5 pr-3 hover:bg-accent/5 transition-all duration-200"
          >
            <Avatar className="h-7 w-7">
              <AvatarFallback className="text-xs bg-primary text-primary-foreground">
                {initials}
              </AvatarFallback>
            </Avatar>
            <span className="hidden text-sm font-medium sm:inline">
              {user?.email || 'User'}
            </span>
            <ChevronDown className="h-3 w-3 text-muted-foreground" />
          </button>
          {profileOpen && (
            <motion.div
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 8 }}
              className="absolute right-0 top-full mt-2 w-56 rounded-xl border bg-card p-1.5 shadow-xl"
            >
              <div className="border-b px-3 py-2">
                <p className="text-sm font-medium">{user?.email}</p>
                <p className="text-xs text-muted-foreground capitalize">
                  {user?.role?.toLowerCase()}
                </p>
              </div>
              <button
                onClick={() => {
                  setProfileOpen(false);
                  logout();
                }}
                className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-destructive hover:bg-destructive/5 transition-all duration-200 mt-1"
              >
                <LogOut className="h-4 w-4" />
                Log out
              </button>
            </motion.div>
          )}
        </div>
      </div>
    </header>
  );
}
