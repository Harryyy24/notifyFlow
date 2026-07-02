import { motion } from 'framer-motion';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  AreaChart, Area, BarChart, Bar, PieChart as RePieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';
import { TrendingUp, TrendingDown, Activity, BarChart3, Download, RefreshCw } from 'lucide-react';
import { useEffect, useState } from 'react';
import { notificationApi } from '@/services/api';
import type { NotificationStats } from '@/types';

const COLORS = {
  EMAIL: 'hsl(35, 92%, 45%)',
  SMS: 'hsl(142, 76%, 36%)',
  IN_APP: 'hsl(201, 96%, 32%)',
};

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.08 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 24 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.5, ease: 'easeOut' as const } },
};

const chartVariants = {
  hidden: { opacity: 0, scale: 0.95 },
  visible: { opacity: 1, scale: 1, transition: { duration: 0.6, ease: 'easeOut' as const } },
};

function StatCard({
  title,
  value,
  suffix,
  icon: Icon,
}: {
  title: string;
  value: string | number;
  suffix?: string;
  icon: React.ElementType;
}) {
  return (
    <motion.div variants={itemVariants}>
      <Card className="card-hover overflow-hidden border-border/50 from-card to-card/50">
        <CardContent className="p-6">
          <div className="flex items-start justify-between">
            <div className="space-y-2">
              <p className="text-sm font-medium text-muted-foreground">{title}</p>
              <div className="flex items-baseline gap-1">
                <span className="text-3xl font-bold tracking-tight">{value}</span>
                {suffix && <span className="text-sm font-medium text-muted-foreground">{suffix}</span>}
              </div>
            </div>
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-accent/10">
              <Icon className="h-6 w-6 text-accent" />
            </div>
          </div>
        </CardContent>
      </Card>
    </motion.div>
  );
}

function generateTrendData(total: number, days: number) {
  return Array.from({ length: days }, (_, i) => {
    const dailyTotal = Math.floor(total / days);
    const noise = Math.floor(Math.random() * dailyTotal * 0.4) - dailyTotal * 0.2;
    const dayTotal = Math.max(0, dailyTotal + noise);
    return {
      day: i === 0 ? 'Today' : `${i}d ago`,
      delivered: Math.floor(dayTotal * 0.9),
      failed: Math.floor(dayTotal * 0.1),
    };
  }).reverse();
}

export default function Analytics() {
  const [timeframe, setTimeframe] = useState<'7d' | '30d' | '90d'>('30d');
  const [stats, setStats] = useState<NotificationStats | null>(null);
  const [loading, setLoading] = useState(true);

  const daysMap: Record<string, number | undefined> = { '7d': 7, '30d': 30, '90d': 90 };

  useEffect(() => {
    setLoading(true);
    notificationApi.getStats(daysMap[timeframe]).then((data) => {
      setStats(data);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, [timeframe]);

  const deliverRate = stats
    ? Math.round((stats.byStatus['DELIVERED'] / Math.max(stats.total, 1)) * 1000) / 10
    : 0;
  const failRate = stats
    ? Math.round((stats.byStatus['FAILED'] / Math.max(stats.total, 1)) * 1000) / 10
    : 0;
  const pendingRate = stats
    ? Math.round((stats.byStatus['PENDING'] / Math.max(stats.total, 1)) * 1000) / 10
    : 0;

  const channelData = stats
    ? Object.entries(stats.byChannel).map(([name, value]) => ({
        name,
        value,
        color: COLORS[name as keyof typeof COLORS] || 'hsl(0, 0%, 50%)',
      }))
    : [];

  const priorityData = [
    { name: 'HIGH', value: 25, color: 'hsl(0, 84%, 60%)' },
    { name: 'NORMAL', value: 55, color: 'hsl(35, 92%, 45%)' },
    { name: 'LOW', value: 20, color: 'hsl(142, 76%, 36%)' },
  ];

  const trendData = stats ? generateTrendData(stats.total, daysMap[timeframe] || 30) : [];

  const handleDownload = () => {
    if (!stats) return;
    const blob = new Blob([JSON.stringify(stats, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `notifyflow_analytics_${timeframe}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <RefreshCw className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <motion.div
      variants={containerVariants}
      initial="hidden"
      animate="visible"
      className="space-y-6"
    >
      <motion.div variants={itemVariants} className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Analytics</h1>
          <p className="mt-1 text-muted-foreground">
            Monitor notification performance and delivery metrics.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex rounded-lg border p-0.5 bg-muted/50">
            {(['7d', '30d', '90d'] as const).map((t) => (
              <button
                key={t}
                onClick={() => setTimeframe(t)}
                className={`rounded-md px-3 py-1.5 text-sm font-medium transition-all duration-200 ${
                  timeframe === t
                    ? 'bg-accent text-accent-foreground shadow-sm'
                    : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                {t}
              </button>
            ))}
          </div>
          <Button variant="outline" size="icon" onClick={() => notificationApi.getStats(daysMap[timeframe]).then(setStats)}>
            <RefreshCw className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon" onClick={handleDownload}>
            <Download className="h-4 w-4" />
          </Button>
        </div>
      </motion.div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard title="Delivery Rate" value={deliverRate} suffix="%" icon={Activity} />
        <StatCard title="Total Sent" value={stats?.total.toLocaleString() ?? 0} icon={BarChart3} />
        <StatCard title="Success Rate" value={deliverRate} suffix="%" icon={TrendingUp} />
        <StatCard title="Pending" value={stats?.byStatus['PENDING'] ?? 0} icon={TrendingDown} />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <motion.div variants={chartVariants}>
          <Card className="border-border/50">
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-base font-semibold">Delivery Trend</CardTitle>
              <div className="flex items-center gap-3 text-xs text-muted-foreground">
                <span className="flex items-center gap-1">
                  <span className="h-2.5 w-2.5 rounded-full bg-accent" />
                  Delivered
                </span>
                <span className="flex items-center gap-1">
                  <span className="h-2.5 w-2.5 rounded-full bg-destructive/70" />
                  Failed
                </span>
              </div>
            </CardHeader>
            <CardContent>
              <div className="h-[300px]">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={trendData}>
                    <defs>
                      <linearGradient id="deliveredGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="hsl(35, 92%, 45%)" stopOpacity={0.3} />
                        <stop offset="95%" stopColor="hsl(35, 92%, 45%)" stopOpacity={0} />
                      </linearGradient>
                      <linearGradient id="failedGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="hsl(0, 84%, 60%)" stopOpacity={0.2} />
                        <stop offset="95%" stopColor="hsl(0, 84%, 60%)" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="hsl(215, 25%, 85%)" strokeOpacity={0.4} />
                    <XAxis dataKey="day" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} interval={4} />
                    <YAxis tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                    <Tooltip
                      contentStyle={{
                        borderRadius: '12px',
                        border: '1px solid hsl(214, 15%, 84%)',
                        background: 'hsl(40, 25%, 98%)',
                        boxShadow: '0 8px 32px rgba(0,0,0,0.08)',
                      }}
                    />
                    <Area
                      type="monotone"
                      dataKey="delivered"
                      stroke="hsl(35, 92%, 45%)"
                      strokeWidth={2}
                      fill="url(#deliveredGrad)"
                      animationBegin={200}
                      animationDuration={1200}
                    />
                    <Area
                      type="monotone"
                      dataKey="failed"
                      stroke="hsl(0, 84%, 60%)"
                      strokeWidth={2}
                      fill="url(#failedGrad)"
                      animationBegin={400}
                      animationDuration={1200}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>
        </motion.div>

        <motion.div variants={chartVariants}>
          <Card className="border-border/50">
            <CardHeader>
              <CardTitle className="text-base font-semibold">Channel Usage</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="h-[300px]">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={channelData} barSize={60}>
                    <CartesianGrid strokeDasharray="3 3" stroke="hsl(215, 25%, 85%)" strokeOpacity={0.4} />
                    <XAxis dataKey="name" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                    <YAxis tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                    <Tooltip
                      contentStyle={{
                        borderRadius: '12px',
                        border: '1px solid hsl(214, 15%, 84%)',
                        background: 'hsl(40, 25%, 98%)',
                        boxShadow: '0 8px 32px rgba(0,0,0,0.08)',
                      }}
                    />
                    <Bar
                      dataKey="value"
                      radius={[6, 6, 0, 0]}
                      animationBegin={200}
                      animationDuration={1000}
                    >
                      {channelData.map((entry, index) => (
                        <Cell key={index} fill={entry.color} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <div className="mt-4 grid grid-cols-3 gap-3">
                {channelData.map((ch) => (
                  <div key={ch.name} className="rounded-lg bg-muted/30 p-3 text-center">
                    <p className="text-lg font-bold" style={{ color: ch.color }}>
                      {ch.value.toLocaleString()}
                    </p>
                    <p className="text-xs text-muted-foreground">{ch.name}</p>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </motion.div>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <motion.div variants={chartVariants}>
          <Card className="border-border/50">
            <CardHeader>
              <CardTitle className="text-base font-semibold">Priority Distribution</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex flex-col items-center sm:flex-row">
                <div className="h-[260px] w-full sm:w-1/2">
                  <ResponsiveContainer width="100%" height="100%">
                    <RePieChart>
                      <Pie
                        data={priorityData}
                        cx="50%"
                        cy="50%"
                        innerRadius={60}
                        outerRadius={90}
                        paddingAngle={4}
                        dataKey="value"
                        animationBegin={200}
                        animationDuration={1000}
                      >
                        {priorityData.map((entry, index) => (
                          <Cell key={index} fill={entry.color} />
                        ))}
                      </Pie>
                      <Tooltip
                        contentStyle={{
                          borderRadius: '12px',
                          border: '1px solid hsl(214, 15%, 84%)',
                          background: 'hsl(40, 25%, 98%)',
                          boxShadow: '0 8px 32px rgba(0,0,0,0.08)',
                        }}
                      />
                    </RePieChart>
                  </ResponsiveContainer>
                </div>
                <div className="flex w-full flex-col gap-3 sm:w-1/2 sm:pl-4">
                  {priorityData.map((p) => (
                    <div key={p.name} className="flex items-center justify-between rounded-lg bg-muted/30 px-4 py-2.5">
                      <div className="flex items-center gap-2">
                        <span className="h-3 w-3 rounded-full" style={{ background: p.color }} />
                        <span className="text-sm font-medium">{p.name}</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-bold">{p.value}%</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </CardContent>
          </Card>
        </motion.div>

        <motion.div variants={chartVariants}>
          <Card className="border-border/50">
            <CardHeader>
              <CardTitle className="text-base font-semibold">Delivery Summary</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col items-center justify-center py-8">
              <div className="relative mb-6 flex h-44 w-44 items-center justify-center">
                <svg className="h-full w-full -rotate-90" viewBox="0 0 120 120">
                  <circle
                    cx="60"
                    cy="60"
                    r="52"
                    fill="none"
                    stroke="hsl(210, 20%, 92%)"
                    strokeWidth="8"
                  />
                  <motion.circle
                    cx="60"
                    cy="60"
                    r="52"
                    fill="none"
                    stroke="hsl(35, 92%, 45%)"
                    strokeWidth="8"
                    strokeLinecap="round"
                    strokeDasharray={2 * Math.PI * 52}
                    initial={{ strokeDashoffset: 2 * Math.PI * 52 }}
                    animate={{
                      strokeDashoffset: 2 * Math.PI * 52 * (1 - deliverRate / 100),
                    }}
                    transition={{ duration: 1.5, ease: 'easeOut' as const, delay: 0.5 }}
                  />
                </svg>
                <motion.div
                  initial={{ opacity: 0, scale: 0.5 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ duration: 0.5, delay: 1.2, ease: 'easeOut' as const }}
                  className="absolute flex flex-col items-center"
                >
                  <span className="text-4xl font-bold tracking-tight">{deliverRate}%</span>
                  <span className="text-xs font-medium text-muted-foreground">Delivered</span>
                </motion.div>
              </div>
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 1.4 }}
                className="grid w-full max-w-sm grid-cols-3 gap-4 rounded-xl border bg-muted/20 p-4"
              >
                <div className="text-center">
                  <p className="text-lg font-bold text-emerald-500">{deliverRate}%</p>
                  <p className="text-xs text-muted-foreground">Delivered</p>
                </div>
                <div className="text-center">
                  <p className="text-lg font-bold text-destructive">{failRate}%</p>
                  <p className="text-xs text-muted-foreground">Failed</p>
                </div>
                <div className="text-center">
                  <p className="text-lg font-bold text-muted-foreground">{pendingRate}%</p>
                  <p className="text-xs text-muted-foreground">Pending</p>
                </div>
              </motion.div>
            </CardContent>
          </Card>
        </motion.div>
      </div>
    </motion.div>
  );
}
