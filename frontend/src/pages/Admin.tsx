import { motion } from 'framer-motion';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { notificationApi } from '@/services/api';
import { useAuth } from '@/context/AuthContext';
import { Shield, Users, Bell, AlertTriangle, CheckCircle, RefreshCw } from 'lucide-react';
import toast from 'react-hot-toast';
import type { Notification, NotificationStatus } from '@/types';

const statusColors: Record<NotificationStatus, 'success' | 'warning' | 'destructive' | 'pending'> = {
  DELIVERED: 'success',
  FAILED: 'destructive',
  PENDING: 'pending',
};

const priorityBadgeColors: Record<string, 'destructive' | 'default' | 'secondary'> = {
  HIGH: 'destructive',
  NORMAL: 'default',
  LOW: 'secondary',
};

const channelLabels: Record<string, string> = {
  EMAIL: 'Email',
  SMS: 'SMS',
  IN_APP: 'In-App',
};

function StatusUpdateRow({ notification }: { notification: Notification }) {
  const queryClient = useQueryClient();
  const [selectedStatus, setSelectedStatus] = useState<NotificationStatus>(notification.status);

  const mutation = useMutation({
    mutationFn: (status: NotificationStatus) =>
      notificationApi.updateStatus(notification.id, { status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-notifications'] });
      toast.success(`Notification #${notification.id} updated to ${selectedStatus}`);
    },
    onError: () => {
      toast.error('Failed to update notification status');
    },
  });

  const hasChanged = selectedStatus !== notification.status;

  return (
    <motion.tr
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      className="border-b border-border/40 transition-colors hover:bg-muted/20"
    >
      <td className="px-4 py-3">
        <span className="font-mono text-xs font-medium text-muted-foreground">
          #{notification.id}
        </span>
      </td>
      <td className="px-4 py-3">
        <div className="max-w-[200px] truncate text-sm font-medium">
          {notification.title}
        </div>
      </td>
      <td className="px-4 py-3">
        <Badge variant={priorityBadgeColors[notification.priority]}>
          {notification.priority}
        </Badge>
      </td>
      <td className="px-4 py-3">
        <span className="text-sm text-muted-foreground">
          {channelLabels[notification.channel]}
        </span>
      </td>
      <td className="px-4 py-3">
        <Badge variant={statusColors[notification.status]}>
          {notification.status}
        </Badge>
      </td>
      <td className="px-4 py-3">
        <Select
          value={selectedStatus}
          onChange={(e) => setSelectedStatus(e.target.value as NotificationStatus)}
          className="h-8 w-[140px] text-xs"
        >
          <option value="PENDING">Pending</option>
          <option value="DELIVERED">Delivered</option>
          <option value="FAILED">Failed</option>
        </Select>
      </td>
      <td className="px-4 py-3">
        <Button
          size="sm"
          variant={hasChanged ? 'default' : 'outline'}
          disabled={!hasChanged || mutation.isPending}
          loading={mutation.isPending}
          onClick={() => mutation.mutate(selectedStatus)}
          className="h-8 min-w-[80px] text-xs"
        >
          {mutation.isPending ? 'Saving...' : hasChanged ? 'Update' : 'Current'}
        </Button>
      </td>
    </motion.tr>
  );
}

const mockNotifications: Notification[] = Array.from({ length: 20 }, (_, i) => ({
  id: 1000 + i,
  userId: 42 + (i % 5),
  channel: (['EMAIL', 'SMS', 'IN_APP'] as const)[i % 3],
  title: [
    'Welcome to NotifyFlow',
    'Password Reset Request',
    'Payment Confirmed',
    'Weekly Digest',
    'Account Suspension Warning',
    'New Feature Announcement',
    'Order Shipped',
    'Security Alert',
    'Subscription Renewal',
    'Profile Update Required',
  ][i % 10],
  message: 'Notification message content...',
  status: (['DELIVERED', 'PENDING', 'FAILED'] as const)[i % 3],
  priority: (['HIGH', 'NORMAL', 'LOW'] as const)[i % 3],
  kafkaOffset: i * 10,
  retryCount: i % 3 === 2 ? 2 : 0,
  createdAt: new Date(Date.now() - i * 86400000).toISOString(),
  deliveredAt: i % 3 === 0 ? new Date(Date.now() - i * 86400000).toISOString() : null,
}));

const adminStats = {
  totalUsers: 2847,
  totalNotifications: 45820,
  failedCount: 732,
  pendingCount: 289,
  deliveredCount: 44799,
  activeUsers: 1892,
};

function StatCard({
  title,
  value,
  subtitle,
  icon: Icon,
  color,
}: {
  title: string;
  value: string | number;
  subtitle?: string;
  icon: React.ElementType;
  color: string;
}) {
  return (
    <Card className="card-hover overflow-hidden border-border/50">
      <CardContent className="p-5">
        <div className="flex items-start justify-between">
          <div className="space-y-1">
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
              {title}
            </p>
            <p className="text-2xl font-bold tracking-tight">{value.toLocaleString()}</p>
            {subtitle && (
              <p className="text-xs text-muted-foreground">{subtitle}</p>
            )}
          </div>
          <div className="flex h-10 w-10 items-center justify-center rounded-xl" style={{ background: `${color}15` }}>
            <Icon className="h-5 w-5" style={{ color }} />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

export default function Admin() {
  const { isAdmin } = useAuth();
  const navigate = useNavigate();

  const { data: notifications, isLoading } = useQuery({
    queryKey: ['admin-notifications'],
    queryFn: async () => {
      await new Promise((r) => setTimeout(r, 600));
      return mockNotifications;
    },
    enabled: isAdmin,
  });

  if (!isAdmin) {
    navigate('/dashboard', { replace: true });
    return null;
  }

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="space-y-6"
    >
      <motion.div
        initial={{ opacity: 0, y: -16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between"
      >
        <div>
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-accent/10">
              <Shield className="h-4 w-4 text-accent" />
            </div>
            <h1 className="text-3xl font-bold tracking-tight">Admin Panel</h1>
          </div>
          <p className="mt-1 text-muted-foreground">
            Manage notifications and oversee platform operations.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => navigate('/dashboard')}
          >
            Dashboard
          </Button>
        </div>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.1 }}
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6"
      >
        <StatCard title="Total Users" value={adminStats.totalUsers} subtitle={`${adminStats.activeUsers} active`} icon={Users} color="hsl(201, 96%, 32%)" />
        <StatCard title="Total Sent" value={adminStats.totalNotifications} subtitle="All time" icon={Bell} color="hsl(35, 92%, 45%)" />
        <StatCard title="Delivered" value={adminStats.deliveredCount} subtitle={`${(adminStats.deliveredCount / adminStats.totalNotifications * 100).toFixed(1)}% rate`} icon={CheckCircle} color="hsl(142, 76%, 36%)" />
        <StatCard title="Failed" value={adminStats.failedCount} subtitle={`${(adminStats.failedCount / adminStats.totalNotifications * 100).toFixed(1)}% rate`} icon={AlertTriangle} color="hsl(0, 84%, 60%)" />
        <StatCard title="Pending" value={adminStats.pendingCount} subtitle="Awaiting delivery" icon={Bell} color="hsl(215, 16%, 47%)" />
        <StatCard title="Delivery Rate" value={`${((adminStats.deliveredCount / adminStats.totalNotifications) * 100).toFixed(1)}%`} subtitle="Success rate" icon={CheckCircle} color="hsl(35, 92%, 45%)" />
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.2 }}
      >
        <Card className="border-border/50">
          <CardHeader className="flex flex-row items-center justify-between pb-3">
            <div>
              <CardTitle className="text-base font-semibold">All Notifications</CardTitle>
              <p className="mt-0.5 text-xs text-muted-foreground">
                Manage and update notification delivery statuses.
              </p>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={() => window.location.reload()}
            >
              <RefreshCw className="mr-1.5 h-3.5 w-3.5" />
              Refresh
            </Button>
          </CardHeader>
          <CardContent className="p-0">
            {isLoading ? (
              <div className="space-y-3 p-6">
                {Array.from({ length: 5 }).map((_, i) => (
                  <div key={i} className="flex items-center gap-4">
                    <Skeleton className="h-4 w-16" />
                    <Skeleton className="h-4 flex-1" />
                    <Skeleton className="h-5 w-16 rounded-full" />
                    <Skeleton className="h-5 w-16 rounded-full" />
                    <Skeleton className="h-8 w-[140px]" />
                    <Skeleton className="h-8 w-[80px]" />
                  </div>
                ))}
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="border-b border-border/40 bg-muted/20 text-left">
                      <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                        ID
                      </th>
                      <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                        Title
                      </th>
                      <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                        Priority
                      </th>
                      <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                        Channel
                      </th>
                      <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                        Status
                      </th>
                      <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                        Update Status
                      </th>
                      <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                        Action
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {notifications?.map((n) => (
                      <StatusUpdateRow key={n.id} notification={n} />
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      </motion.div>
    </motion.div>
  );
}
