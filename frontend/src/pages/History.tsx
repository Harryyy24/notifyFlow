import { motion, AnimatePresence } from 'framer-motion';
import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { notificationApi } from '@/services/api';
import { useAuth } from '@/context/AuthContext';
import { Search, Filter, Download, ChevronLeft, ChevronRight, Eye, ExternalLink, X, Inbox, Clock, AlertCircle, CheckCircle2 } from 'lucide-react';
import type { Notification, NotificationStatus, NotificationPriority, NotificationChannel } from '@/types';

const PAGE_SIZE = 15;

const statusConfig: Record<NotificationStatus, { variant: 'success' | 'warning' | 'destructive'; label: string }> = {
  DELIVERED: { variant: 'success', label: 'Delivered' },
  PENDING: { variant: 'warning', label: 'Pending' },
  FAILED: { variant: 'destructive', label: 'Failed' },
};

const priorityColors: Record<NotificationPriority, string> = {
  HIGH: 'bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/20',
  NORMAL: 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20',
  LOW: 'bg-slate-500/10 text-slate-600 dark:text-slate-400 border-slate-500/20',
};

const channelIcons: Record<NotificationChannel, string> = {
  EMAIL: '✉',
  SMS: '📱',
  IN_APP: '💬',
};

interface Filters {
  search: string;
  status: string;
  priority: string;
  channel: string;
  dateFrom: string;
  dateTo: string;
}

function StatusBadge({ status }: { status: NotificationStatus }) {
  const config = statusConfig[status];
  const icon = {
    DELIVERED: CheckCircle2,
    PENDING: Clock,
    FAILED: AlertCircle,
  }[status];

  const Icon = icon;

  return (
    <Badge variant={config.variant} className="gap-1">
      <Icon className="h-3 w-3" />
      {config.label}
    </Badge>
  );
}

function DetailSlideOver({
  notification,
  onClose,
}: {
  notification: Notification;
  onClose: () => void;
}) {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.2 }}
      className="fixed inset-0 z-50 flex justify-end bg-black/40 backdrop-blur-sm"
      onClick={onClose}
    >
      <motion.div
        initial={{ x: '100%' }}
        animate={{ x: 0 }}
        exit={{ x: '100%' }}
        transition={{ type: 'spring', damping: 30, stiffness: 300 }}
        className="w-full max-w-lg border-l bg-background shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex h-full flex-col">
          <div className="flex items-center justify-between border-b px-6 py-4">
            <h2 className="text-lg font-semibold">Notification Details</h2>
            <Button variant="ghost" size="icon" onClick={onClose}>
              <X className="h-5 w-5" />
            </Button>
          </div>

          <div className="flex-1 space-y-6 overflow-y-auto p-6">
            <div className="flex items-center justify-between">
              <StatusBadge status={notification.status} />
              <Badge className={priorityColors[notification.priority]}>
                {notification.priority}
              </Badge>
            </div>

            <div>
              <p className="mb-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
                ID
              </p>
              <p className="font-mono text-sm font-semibold">#{notification.id}</p>
            </div>

            <div>
              <p className="mb-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
                User ID
              </p>
              <p className="font-mono text-sm">#{notification.userId}</p>
            </div>

            <div>
              <p className="mb-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
                Channel
              </p>
              <p className="text-sm font-medium">
                {channelIcons[notification.channel]} {notification.channel}
              </p>
            </div>

            <div>
              <p className="mb-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
                Title
              </p>
              <p className="text-sm font-medium">{notification.title}</p>
            </div>

            <div>
              <p className="mb-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
                Message
              </p>
              <p className="text-sm leading-relaxed text-muted-foreground">
                {notification.message}
              </p>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="mb-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
                  Created At
                </p>
                <p className="text-sm tabular-nums">
                  {new Date(notification.createdAt).toLocaleString()}
                </p>
              </div>
              <div>
                <p className="mb-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
                  Delivered At
                </p>
                <p className="text-sm tabular-nums">
                  {notification.deliveredAt
                    ? new Date(notification.deliveredAt).toLocaleString()
                    : '—'}
                </p>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="mb-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
                  Retry Count
                </p>
                <p className="font-mono text-sm">{notification.retryCount}</p>
              </div>
              <div>
                <p className="mb-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
                  Kafka Offset
                </p>
                <p className="font-mono text-sm">
                  {notification.kafkaOffset !== null
                    ? notification.kafkaOffset
                    : '—'}
                </p>
              </div>
            </div>
          </div>
        </div>
      </motion.div>
    </motion.div>
  );
}

function SkeletonRow() {
  return (
    <tr>
      {Array.from({ length: 7 }).map((_, i) => (
        <td key={i} className="px-4 py-3">
          <Skeleton className="h-5 w-full max-w-[120px]" />
        </td>
      ))}
    </tr>
  );
}

function EmptyState() {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="flex flex-col items-center justify-center py-16"
    >
      <motion.div
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ delay: 0.2, type: 'spring', stiffness: 200 }}
        className="mb-6 flex h-24 w-24 items-center justify-center rounded-full bg-muted"
      >
        <Inbox className="h-12 w-12 text-muted-foreground/50" />
      </motion.div>
      <h3 className="mb-2 text-lg font-semibold">No notifications found</h3>
      <p className="max-w-sm text-center text-sm text-muted-foreground">
        {`There are no notifications matching your criteria. Try adjusting your filters or send a new notification.`}
      </p>
    </motion.div>
  );
}

export default function History() {
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const [selectedNotification, setSelectedNotification] = useState<Notification | null>(null);
  const [filters, setFilters] = useState<Filters>({
    search: '',
    status: '',
    priority: '',
    channel: '',
    dateFrom: '',
    dateTo: '',
  });
  const [sortField, setSortField] = useState<string>('createdAt');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');

  const { data, isLoading } = useQuery({
    queryKey: ['notifications', user?.id, page],
    queryFn: () => notificationApi.getHistory(user!.id, page, PAGE_SIZE),
    enabled: !!user?.id,
    refetchInterval: 10_000,
  });

  const filteredNotifications = useMemo(() => {
    if (!data?.content) return [];

    return data.content.filter((n) => {
      if (filters.search) {
        const q = filters.search.toLowerCase();
        if (
          !n.title.toLowerCase().includes(q) &&
          !n.message.toLowerCase().includes(q) &&
          !String(n.id).includes(q)
        )
          return false;
      }
      if (filters.status && n.status !== filters.status) return false;
      if (filters.priority && n.priority !== filters.priority) return false;
      if (filters.channel && n.channel !== filters.channel) return false;
      if (filters.dateFrom && new Date(n.createdAt) < new Date(filters.dateFrom))
        return false;
      if (filters.dateTo && new Date(n.createdAt) > new Date(filters.dateTo))
        return false;
      return true;
    }).sort((a, b) => {
      let cmp = 0;
      switch (sortField) {
        case 'id': cmp = a.id - b.id; break;
        case 'createdAt': cmp = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(); break;
        case 'title': cmp = a.title.localeCompare(b.title); break;
        case 'priority': {
          const order = { HIGH: 0, NORMAL: 1, LOW: 2 };
          cmp = order[a.priority] - order[b.priority];
          break;
        }
        default: cmp = 0;
      }
      return sortDir === 'asc' ? cmp : -cmp;
    });
  }, [data, filters, sortField, sortDir]);

  const handleSort = (field: string) => {
    if (sortField === field) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortDir('asc');
    }
  };

  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="mb-8"
      >
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold tracking-tight">Notification History</h1>
            <p className="mt-1 text-muted-foreground">
              View and manage all sent notifications.
            </p>
          </div>
          <Button variant="outline" className="gap-2">
            <Download className="h-4 w-4" />
            Export
          </Button>
        </div>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1, duration: 0.3 }}
        className="mb-6"
      >
        <Card>
          <CardContent className="pt-6">
            <div className="flex flex-wrap items-end gap-4">
              <div className="min-w-[240px] flex-1">
                <Label htmlFor="search">Search</Label>
                <div className="relative mt-1.5">
                  <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    id="search"
                    placeholder="Search by ID, title, or message..."
                    className="pl-9"
                    value={filters.search}
                    onChange={(e) => {
                      setFilters((f) => ({ ...f, search: e.target.value }));
                      setPage(0);
                    }}
                  />
                </div>
              </div>

              <div className="w-[140px]">
                <Label htmlFor="status">Status</Label>
                <Select
                  id="status"
                  value={filters.status}
                  onChange={(e) => {
                    setFilters((f) => ({ ...f, status: e.target.value }));
                    setPage(0);
                  }}
                  className="mt-1.5"
                >
                  <option value="">All</option>
                  <option value="DELIVERED">Delivered</option>
                  <option value="PENDING">Pending</option>
                  <option value="FAILED">Failed</option>
                </Select>
              </div>

              <div className="w-[140px]">
                <Label htmlFor="priority">Priority</Label>
                <Select
                  id="priority"
                  value={filters.priority}
                  onChange={(e) => {
                    setFilters((f) => ({ ...f, priority: e.target.value }));
                    setPage(0);
                  }}
                  className="mt-1.5"
                >
                  <option value="">All</option>
                  <option value="HIGH">High</option>
                  <option value="NORMAL">Normal</option>
                  <option value="LOW">Low</option>
                </Select>
              </div>

              <div className="w-[140px]">
                <Label htmlFor="channel">Channel</Label>
                <Select
                  id="channel"
                  value={filters.channel}
                  onChange={(e) => {
                    setFilters((f) => ({ ...f, channel: e.target.value }));
                    setPage(0);
                  }}
                  className="mt-1.5"
                >
                  <option value="">All</option>
                  <option value="EMAIL">Email</option>
                  <option value="SMS">SMS</option>
                  <option value="IN_APP">In-App</option>
                </Select>
              </div>

              <div className="w-[160px]">
                <Label htmlFor="dateFrom">From</Label>
                <Input
                  id="dateFrom"
                  type="date"
                  value={filters.dateFrom}
                  onChange={(e) => {
                    setFilters((f) => ({ ...f, dateFrom: e.target.value }));
                    setPage(0);
                  }}
                  className="mt-1.5"
                />
              </div>

              <div className="w-[160px]">
                <Label htmlFor="dateTo">To</Label>
                <Input
                  id="dateTo"
                  type="date"
                  value={filters.dateTo}
                  onChange={(e) => {
                    setFilters((f) => ({ ...f, dateTo: e.target.value }));
                    setPage(0);
                  }}
                  className="mt-1.5"
                />
              </div>

              <Button
                variant="ghost"
                size="sm"
                className="mb-0.5"
                onClick={() => {
                  setFilters({ search: '', status: '', priority: '', channel: '', dateFrom: '', dateTo: '' });
                  setPage(0);
                }}
              >
                <Filter className="mr-1 h-4 w-4" />
                Clear
              </Button>
            </div>
          </CardContent>
        </Card>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2, duration: 0.3 }}
      >
        <Card>
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b bg-muted/30">
                    {[
                      { key: 'id', label: 'ID', sortable: true },
                      { key: 'channel', label: 'Channel', sortable: false },
                      { key: 'title', label: 'Title', sortable: true },
                      { key: 'status', label: 'Status', sortable: false },
                      { key: 'priority', label: 'Priority', sortable: true },
                      { key: 'createdAt', label: 'Created', sortable: true },
                      { key: 'actions', label: 'Actions', sortable: false },
                    ].map((col) => (
                      <th
                        key={col.key}
                        className={`px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-muted-foreground ${
                          col.sortable ? 'cursor-pointer select-none hover:text-foreground' : ''
                        }`}
                        onClick={() => col.sortable && handleSort(col.key)}
                      >
                        <div className="flex items-center gap-1">
                          {col.label}
                          {col.sortable && sortField === col.key && (
                            <span className="text-primary">
                              {sortDir === 'asc' ? '↑' : '↓'}
                            </span>
                          )}
                        </div>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {isLoading ? (
                    Array.from({ length: 8 }).map((_, i) => (
                      <SkeletonRow key={i} />
                    ))
                  ) : filteredNotifications.length === 0 ? (
                    <tr>
                      <td colSpan={7}>
                        <EmptyState />
                      </td>
                    </tr>
                  ) : (
                    <AnimatePresence mode="popLayout">
                      {filteredNotifications.map((notification, index) => (
                        <motion.tr
                          key={notification.id}
                          initial={{ opacity: 0, y: -8 }}
                          animate={{ opacity: 1, y: 0 }}
                          exit={{ opacity: 0, y: 8 }}
                          transition={{ delay: index * 0.02, duration: 0.2 }}
                          className="cursor-pointer border-b last:border-b-0 transition-colors hover:bg-muted/40"
                          onClick={() => setSelectedNotification(notification)}
                        >
                          <td className="px-4 py-3">
                            <span className="font-mono text-sm font-medium">
                              #{notification.id}
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            <span className="text-sm">
                              {channelIcons[notification.channel]} {notification.channel}
                            </span>
                          </td>
                          <td className="max-w-[200px] truncate px-4 py-3">
                            <span className="text-sm font-medium">
                              {notification.title}
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            <StatusBadge status={notification.status} />
                          </td>
                          <td className="px-4 py-3">
                            <Badge className={priorityColors[notification.priority]}>
                              {notification.priority}
                            </Badge>
                          </td>
                          <td className="px-4 py-3">
                            <span className="text-sm tabular-nums text-muted-foreground">
                              {new Date(notification.createdAt).toLocaleDateString()}
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-1">
                              <Button
                                variant="ghost"
                                size="icon"
                                className="h-8 w-8"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setSelectedNotification(notification);
                                }}
                              >
                                <Eye className="h-4 w-4" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                className="h-8 w-8"
                                onClick={(e) => e.stopPropagation()}
                              >
                                <ExternalLink className="h-4 w-4" />
                              </Button>
                            </div>
                          </td>
                        </motion.tr>
                      ))}
                    </AnimatePresence>
                  )}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      </motion.div>

      {totalPages > 0 && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.3 }}
          className="mt-4 flex items-center justify-between"
        >
          <p className="text-sm text-muted-foreground">
            Showing page {page + 1} of {totalPages}
            {data && ` · ${data.totalElements} total`}
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              <ChevronLeft className="h-4 w-4" />
              Previous
            </Button>
            <div className="flex items-center gap-1">
              {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => {
                const start = Math.max(0, Math.min(page - 2, totalPages - 5));
                const pageNum = start + i;
                if (pageNum >= totalPages) return null;
                return (
                  <Button
                    key={pageNum}
                    variant={pageNum === page ? 'default' : 'ghost'}
                    size="sm"
                    className="min-w-[36px]"
                    onClick={() => setPage(pageNum)}
                  >
                    {pageNum + 1}
                  </Button>
                );
              })}
            </div>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </motion.div>
      )}

      <AnimatePresence>
        {selectedNotification && (
          <DetailSlideOver
            notification={selectedNotification}
            onClose={() => setSelectedNotification(null)}
          />
        )}
      </AnimatePresence>
    </div>
  );
}

function Label({ children, htmlFor }: { children: React.ReactNode; htmlFor?: string }) {
  return (
    <label htmlFor={htmlFor} className="text-sm font-medium leading-none text-muted-foreground">
      {children}
    </label>
  );
}
