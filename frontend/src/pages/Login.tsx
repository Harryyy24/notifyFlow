import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod/v4';
import { motion } from 'framer-motion';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Bell, Mail, Lock, Eye, EyeOff } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useState } from 'react';

const loginSchema = z.object({
  email: z.string().email('Please enter a valid email'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

type LoginForm = z.infer<typeof loginSchema>;

export default function Login() {
  const navigate = useNavigate();
  const { login, isLoading } = useAuth();
  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    mode: 'onBlur',
  });

  const onSubmit = async (data: LoginForm) => {
    await login(data.email, data.password);
    navigate('/dashboard');
  };

  const fadeUp = (delay: number) => ({
    initial: { opacity: 0, y: 12 },
    animate: { opacity: 1, y: 0 },
    transition: { delay, duration: 0.4, ease: [0.25, 0.1, 0.25, 1] as const },
  });

  return (
    <div className="min-h-screen flex items-center justify-center p-4 relative overflow-hidden bg-background">
      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute -top-40 -right-40 w-[500px] h-[500px] bg-accent/8 rounded-full blur-[120px]" />
        <div className="absolute -bottom-40 -left-40 w-[500px] h-[500px] bg-primary/5 rounded-full blur-[120px]" />
      </div>

      <motion.div
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.5, ease: [0.25, 0.1, 0.25, 1] as const }}
        className="w-full max-w-[420px]"
      >
        <div className="relative">
          <div className="absolute -inset-[2px] bg-gradient-to-b from-accent/20 via-accent/5 to-transparent rounded-2xl blur-xl" />
          <div className="relative glass rounded-2xl p-8 shadow-2xl shadow-black/5 dark:shadow-black/20">
            <motion.div {...fadeUp(0.05)} className="flex flex-col items-center mb-8">
              <div className="w-12 h-12 rounded-xl bg-accent flex items-center justify-center mb-4 ring-4 ring-accent/20">
                <Bell className="w-6 h-6 text-accent-foreground" />
              </div>
              <h1 className="text-xl font-bold">Welcome back</h1>
              <p className="text-sm text-muted-foreground mt-1">Sign in to your account</p>
            </motion.div>

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <motion.div {...fadeUp(0.1)} className="space-y-2">
                <Label htmlFor="email">Email</Label>
                <div className="relative">
                  <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input
                    id="email"
                    type="email"
                    placeholder="you@example.com"
                    className="pl-10"
                    {...register('email')}
                  />
                </div>
                {errors.email && (
                  <p className="text-sm text-destructive">{errors.email.message}</p>
                )}
              </motion.div>

              <motion.div {...fadeUp(0.15)} className="space-y-2">
                <Label htmlFor="password">Password</Label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    placeholder="••••••••"
                    className="pl-10 pr-10"
                    {...register('password')}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                    tabIndex={-1}
                  >
                    {showPassword ? (
                      <EyeOff className="w-4 h-4" />
                    ) : (
                      <Eye className="w-4 h-4" />
                    )}
                  </button>
                </div>
                {errors.password && (
                  <p className="text-sm text-destructive">{errors.password.message}</p>
                )}
              </motion.div>

              <motion.div {...fadeUp(0.2)}>
                <Button type="submit" loading={isLoading} className="w-full h-11 mt-2">
                  Sign in
                </Button>
              </motion.div>
            </form>

            <motion.p
              {...fadeUp(0.25)}
              className="mt-6 text-center text-sm text-muted-foreground"
            >
              Don&apos;t have an account?{' '}
              <Link
                to="/register"
                className="text-accent font-medium hover:underline"
              >
                Create one
              </Link>
            </motion.p>
          </div>
        </div>
      </motion.div>
    </div>
  );
}
