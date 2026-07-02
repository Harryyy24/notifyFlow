import { motion } from 'framer-motion';
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Bell, CheckCircle, Clock, XCircle, Send, BarChart3, Activity,
  Database, Server, TrendingUp, PieChart, Mail, MessageSquare, Smartphone,
  ArrowRight, AlertCircle
} from 'lucide-react';
import {
  AreaChart, Area, PieChart as RePieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts';
import { notificationApi } from '@/services/api';
import { useAuth } from '@/context/AuthContext';
import type { Notification } from '@/types';

interface StatCard {
  label: string;
  value: number;
  icon: React.ElementType;
  color: string;
  bgColor: string;
  textColor: string;
}

interface ChannelDataPoint {
  name: string;
  value: number;
  color: string;
  icon: React.ElementType;
}

interface SystemService {
  name: string;
  icon: React.ElementType;
  status: 'healthy' | 'degraded' | 'down';
  latency: string;
}

interface ActivityItem {
  id: string;
  title: string;
  channel: string;
  status: 'delivered' | 'pending' | 'failed';
  timestamp: string;
}

const systemServices: SystemService[] = [
  { name: 'Kafka', icon: Server, status: 'healthy', latency: '12ms' },
  { name: 'Redis', icon: Database, status: 'healthy', latency: '3ms' },
  { name: 'Database', icon: Database, status: 'healthy', latency: '8ms' },
];

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.08, ease: 'easeOut' as const },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: 'easeOut' as const } },
};

const statusBadgeVariant: Record<string, 'success' | 'warning' | 'destructive'> = {
  healthy: 'success',
  degraded: 'warning',
  down: 'destructive',
};

const statusIconColor: Record<string, string> = {
  healthy: 'text-emerald-500',
  degraded: 'text-amber-500',
  down: 'text-red-500',
};

const activityBadgeVariant: Record<string, 'success' | 'pending' | 'destructive'> = {
  delivered: 'success',
  pending: 'pending',
  failed: 'destructive',
};

const channelConfig: Record<string, { color: string; icon: React.ElementType; label: string }> = {
  EMAIL: { color: '#3b82f6', icon: Mail, label: 'Email' },
  SMS: { color: '#8b5cf6', icon: MessageSquare, label: 'SMS' },
  IN_APP: { color: '#f59e0b', icon: Smartphone, label: 'In-App' },
};

function CountUp({ target, duration = 2000 }: { target: number; duration?: number }) {
  const [count, setCount] = useState(0);

  useEffect(() => {
    let start = 0;
    const increment = target / (duration / 16);
    let rafId: number;

    const step = () => {
      start += increment;
      if (start >= target) {
        setCount(target);
        return;
      }
      setCount(Math.floor(start));
      rafId = requestAnimationFrame(step);
    };

    rafId = requestAnimationFrame(step);
    return () => cancelAnimationFrame(rafId);
  }, [target, duration]);

  return <span>{count.toLocaleString()}</span>;
}

function DashboardSkeleton() {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="space-y-6"
    >
      <div className="space-y-1">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-4 w-72" />
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-32 rounded-xl" />
        ))}
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <Skeleton className="h-80 rounded-xl" />
        <Skeleton className="h-80 rounded-xl" />
      </div>
      <div className="grid gap-6 lg:grid-cols-3">
        <Skeleton className="h-40 rounded-xl" />
        <Skeleton className="h-40 rounded-xl" />
        <Skeleton className="h-40 rounded-xl" />
      </div>
    </motion.div>
  );
}

export default function Dashboard() {
  const navigate = useNavigate();
  const { user } = useAuth();

  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['dashboard-stats'],
    queryFn: () => notificationApi.getStats(7),
  });

  const { data: historyData, isLoading: historyLoading } = useQuery({
    queryKey: ['dashboard-history', user?.id],
    queryFn: () => notificationApi.getHistory(user!.id, 0, 10),
    enabled: !!user?.id,
    refetchInterval: 10_000,
  });

  const loading = statsLoading || historyLoading;

  if (loading) {
    return <DashboardSkeleton />;
  }

  const total = stats?.total ?? 0;
  const delivered = stats?.byStatus?.DELIVERED ?? 0;
  const pending = stats?.byStatus?.PENDING ?? 0;
  const failed = stats?.byStatus?.FAILED ?? 0;
  const maxStat = Math.max(total, 1);

  const statCards: StatCard[] = [
    { label: 'Total Notifications', value: total, icon: Bell, color: 'blue', bgColor: 'bg-blue-500/10', textColor: 'text-blue-600 dark:text-blue-400' },
    { label: 'Delivered', value: delivered, icon: CheckCircle, color: 'green', bgColor: 'bg-emerald-500/10', textColor: 'text-emerald-600 dark:text-emerald-400' },
    { label: 'Pending', value: pending, icon: Clock, color: 'amber', bgColor: 'bg-amber-500/10', textColor: 'text-amber-600 dark:text-amber-400' },
    { label: 'Failed', value: failed, icon: XCircle, color: 'red', bgColor: 'bg-red-500/10', textColor: 'text-red-600 dark:text-red-400' },
  ];

  const channelData: ChannelDataPoint[] = stats
    ? Object.entries(stats.byChannel ?? {}).map(([key, value]) => {
        const cfg = channelConfig[key] ?? { color: '#6b7280', icon: Mail, label: key };
        return { name: cfg.label, value, color: cfg.color, icon: cfg.icon };
      })
    : [];

  const deliveryTrendData = [
    { name: 'Delivered', deliveries: delivered },
    { name: 'Pending', deliveries: pending },
    { name: 'Failed', deliveries: failed },
  ];

  const notifications: Notification[] = historyData?.content ?? [];
  const recentActivity: ActivityItem[] = notifications.map((n) => {
    const rawStatus = n.status.toLowerCase() as 'delivered' | 'pending' | 'failed';
    const minsAgo = Math.floor(
      (Date.now() - new Date(n.createdAt).getTime()) / 60000,
    );
    const timestamp =
      minsAgo < 1
        ? 'Just now'
        : minsAgo < 60
          ? `${minsAgo} min ago`
          : `${Math.floor(minsAgo / 60)} hr ago`;
    return {
      id: `#${n.id}`,
      title: n.title,
      channel: n.channel,
      status: rawStatus,
      timestamp,
    };
  });

  return (
    <motion.div
      variants={containerVariants}
      initial="hidden"
      animate="visible"
      className="space-y-6"
    >
      <motion.div variants={itemVariants} className="space-y-1">
        <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Real-time overview of your notification system
        </p>
      </motion.div>

      <motion.div
        variants={itemVariants}
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"
      >
        {statCards.map((stat) => (
          <Card key={stat.label} className="overflow-hidden border-0 shadow-sm transition-all duration-200 hover:shadow-md">
            <CardContent className="p-6">
              <div className="flex items-start justify-between">
                <div className={`rounded-xl p-2.5 ${stat.bgColor}`}>
                  <stat.icon className={`h-5 w-5 ${stat.textColor}`} />
                </div>
              </div>
              <div className="mt-4">
                <p className="text-sm font-medium text-muted-foreground">{stat.label}</p>
                <p className={`mt-1 text-2xl font-bold tracking-tight ${stat.textColor}`}>
                  <CountUp target={stat.value} />
                </p>
              </div>
              <div className="mt-3 h-1 w-full rounded-full bg-muted/50">
                <div
                  className={`h-1 rounded-full transition-all duration-1000 ${
                    stat.color === 'blue' ? 'bg-blue-500' :
                    stat.color === 'green' ? 'bg-emerald-500' :
                    stat.color === 'amber' ? 'bg-amber-500' : 'bg-red-500'
                  }`}
                  style={{ width: `${(stat.value / maxStat) * 100}%` }}
                />
              </div>
            </CardContent>
          </Card>
        ))}
      </motion.div>

      <motion.div
        variants={itemVariants}
        className="grid gap-6 lg:grid-cols-2"
      >
        <Card className="border-0 shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <div className="flex items-center gap-2">
              <TrendingUp className="h-4 w-4 text-muted-foreground" />
              <CardTitle className="text-base font-semibold">Delivery Status</CardTitle>
            </div>
          </CardHeader>
          <CardContent className="pt-4">
            <div className="h-[280px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={deliveryTrendData} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
                  <defs>
                    <linearGradient id="deliveryGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="hsl(var(--border))" />
                  <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fontSize: 12, fill: 'hsl(var(--muted-foreground))' }} />
                  <YAxis axisLine={false} tickLine={false} tick={{ fontSize: 12, fill: 'hsl(var(--muted-foreground))' }} />
                  <Tooltip
                    contentStyle={{
                      borderRadius: '8px',
                      border: '1px solid hsl(var(--border))',
                      background: 'hsl(var(--card))',
                      boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
                    }}
                    labelStyle={{ fontWeight: 600 }}
                  />
                  <Area
                    type="monotone"
                    dataKey="deliveries"
                    stroke="#3b82f6"
                    strokeWidth={2}
                    fill="url(#deliveryGradient)"
                    animationDuration={1200}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        <Card className="border-0 shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <div className="flex items-center gap-2">
              <PieChart className="h-4 w-4 text-muted-foreground" />
              <CardTitle className="text-base font-semibold">Channel Distribution</CardTitle>
            </div>
          </CardHeader>
          <CardContent className="pt-4">
            {channelData.length === 0 ? (
              <div className="flex h-[280px] items-center justify-center text-sm text-muted-foreground">
                No data yet
              </div>
            ) : (
              <div className="flex h-[280px] items-center gap-4">
                <div className="flex-1 h-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <RePieChart>
                      <Pie
                        data={channelData}
                        cx="50%"
                        cy="50%"
                        innerRadius={60}
                        outerRadius={90}
                        paddingAngle={4}
                        dataKey="value"
                        animationDuration={1000}
                        animationBegin={200}
                      >
                        {channelData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={entry.color} />
                        ))}
                      </Pie>
                      <Tooltip
                        contentStyle={{
                          borderRadius: '8px',
                          border: '1px solid hsl(var(--border))',
                          background: 'hsl(var(--card))',
                          boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
                        }}
                      />
                    </RePieChart>
                  </ResponsiveContainer>
                </div>
                <div className="flex flex-col gap-3">
                  {channelData.map((channel) => (
                    <div key={channel.name} className="flex items-center gap-2.5">
                      <div className="flex h-8 w-8 items-center justify-center rounded-lg" style={{ backgroundColor: `${channel.color}1a` }}>
                        <channel.icon className="h-4 w-4" style={{ color: channel.color }} />
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm font-medium">{channel.name}</p>
                        <p className="text-xs text-muted-foreground">{channel.value} notifications</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </motion.div>

      <motion.div
        variants={itemVariants}
        className="grid gap-6 lg:grid-cols-3"
      >
        {systemServices.map((service) => (
          <Card key={service.name} className="border-0 shadow-sm transition-all duration-200 hover:shadow-md">
            <CardContent className="flex items-center gap-4 p-6">
              <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-muted/50">
                <service.icon className="h-5 w-5 text-muted-foreground" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold">{service.name}</p>
                <div className="mt-1 flex items-center gap-2">
                  <span className={`inline-block h-2 w-2 rounded-full ${statusIconColor[service.status]}`} />
                  <span className="text-xs capitalize text-muted-foreground">{service.status}</span>
                  <span className="text-xs text-muted-foreground">·</span>
                  <span className="text-xs text-muted-foreground">{service.latency}</span>
                </div>
              </div>
              <Badge variant={statusBadgeVariant[service.status]}>
                {service.status === 'healthy' ? 'Online' : service.status === 'degraded' ? 'Degraded' : 'Down'}
              </Badge>
            </CardContent>
          </Card>
        ))}
      </motion.div>

      <motion.div
        variants={itemVariants}
        className="grid gap-6 lg:grid-cols-3"
      >
        <Card className="border-0 shadow-sm lg:col-span-2">
          <CardHeader className="flex flex-row items-center justify-between pb-3">
            <div className="flex items-center gap-2">
              <Activity className="h-4 w-4 text-muted-foreground" />
              <CardTitle className="text-base font-semibold">Recent Activity</CardTitle>
            </div>
            <Button variant="ghost" size="sm" className="gap-1 text-xs" onClick={() => navigate('/history')}>
              View all <ArrowRight className="h-3 w-3" />
            </Button>
          </CardHeader>
          <CardContent className="p-0">
            {recentActivity.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-sm text-muted-foreground">
                <Activity className="mb-2 h-8 w-8 opacity-40" />
                No recent activity
              </div>
            ) : (
              <div className="divide-y divide-border/50">
                {recentActivity.map((item) => (
                  <div key={item.id} className="flex items-center gap-4 px-6 py-3.5 transition-colors hover:bg-muted/30">
                    <div className="flex h-9 w-9 items-center justify-center rounded-full bg-muted/50">
                      {item.status === 'delivered' ? (
                        <CheckCircle className="h-4 w-4 text-emerald-500" />
                      ) : item.status === 'pending' ? (
                        <Clock className="h-4 w-4 text-amber-500" />
                      ) : (
                        <XCircle className="h-4 w-4 text-red-500" />
                      )}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <p className="truncate text-sm font-medium">{item.title}</p>
                        <Badge
                          variant={activityBadgeVariant[item.status]}
                          className="shrink-0 text-[10px] px-1.5 py-0"
                        >
                          {item.status}
                        </Badge>
                      </div>
                      <div className="mt-0.5 flex items-center gap-2 text-xs text-muted-foreground">
                        <span>{item.id}</span>
                        <span>·</span>
                        <span>{item.channel}</span>
                        <span>·</span>
                        <span>{item.timestamp}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="border-0 shadow-sm">
          <CardHeader className="pb-3">
            <CardTitle className="text-base font-semibold">Quick Actions</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <Button
              className="w-full justify-start gap-3 h-11"
              onClick={() => navigate('/send')}
            >
              <span className="flex h-7 w-7 items-center justify-center rounded-md bg-primary-foreground/20">
                <Send className="h-3.5 w-3.5" />
              </span>
              Send Notification
            </Button>
            <Button
              variant="secondary"
              className="w-full justify-start gap-3 h-11"
              onClick={() => navigate('/history')}
            >
              <span className="flex h-7 w-7 items-center justify-center rounded-md bg-secondary-foreground/10">
                <BarChart3 className="h-3.5 w-3.5" />
              </span>
              View History
            </Button>
            <Button
              variant="outline"
              className="w-full justify-start gap-3 h-11"
              onClick={() => navigate('/analytics')}
            >
              <span className="flex h-7 w-7 items-center justify-center rounded-md bg-muted-foreground/10">
                <TrendingUp className="h-3.5 w-3.5" />
              </span>
              Analytics
            </Button>
            <div className="mt-4 rounded-xl bg-amber-500/5 border border-amber-500/10 p-4">
              <div className="flex items-start gap-3">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-amber-500" />
                <div className="space-y-1">
                  <p className="text-xs font-medium">System Notice</p>
                  <p className="text-[11px] leading-relaxed text-muted-foreground">
                    Your notification quota resets in 3 days. 72% of monthly limit used.
                  </p>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      </motion.div>
    </motion.div>
  );
}
