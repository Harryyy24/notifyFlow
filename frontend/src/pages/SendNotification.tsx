import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod/v4';
import { motion, AnimatePresence } from 'framer-motion';
import { useState, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Select } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { notificationApi, usersApi } from '@/services/api';
import type { UserSummary } from '@/types';
import { Send, CheckCircle2, Mail, MessageSquare, Smartphone, AlertTriangle, ChevronRight } from 'lucide-react';
import toast from 'react-hot-toast';

const notificationSchema = z.object({
  userId: z.coerce.number({ message: 'User ID must be a number' }).positive('User ID must be positive'),
  channel: z.enum(['EMAIL', 'SMS', 'IN_APP'], { message: 'Please select a channel' }),
  title: z.string().min(1, 'Title is required').max(200, 'Title must be under 200 characters'),
  message: z.string().min(1, 'Message is required').max(1000, 'Message must be under 1000 characters'),
  priority: z.enum(['HIGH', 'NORMAL', 'LOW'], { message: 'Please select a priority' }),
});

type NotificationFormData = z.infer<typeof notificationSchema>;

const channelIcons = {
  EMAIL: Mail,
  SMS: Smartphone,
  IN_APP: MessageSquare,
} as const;

const channelLabels = {
  EMAIL: 'Email',
  SMS: 'SMS',
  IN_APP: 'In-App',
} as const;

const priorityColors = {
  HIGH: 'destructive' as const,
  NORMAL: 'default' as const,
  LOW: 'secondary' as const,
} as const;

function PreviewCard({ data, users }: { data: Partial<NotificationFormData>; users: UserSummary[] }) {
  const ChannelIcon = data.channel ? channelIcons[data.channel] : Mail;
  const selectedUser = users.find((u) => u.id === data.userId);

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: 'easeOut' }}
    >
      <Card className="overflow-hidden border-2 border-primary/20 bg-gradient-to-br from-background to-primary/5">
        <CardHeader className="border-b bg-muted/30 pb-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10">
                <ChannelIcon className="h-4 w-4 text-primary" />
              </div>
              <div>
                <CardTitle className="text-sm">Notification Preview</CardTitle>
                <CardDescription className="text-xs">
                  {data.channel ? channelLabels[data.channel] : 'Channel'} · {data.priority ?? 'Priority'}
                </CardDescription>
              </div>
            </div>
            {data.priority && (
              <Badge variant={priorityColors[data.priority]}>
                {data.priority}
              </Badge>
            )}
          </div>
        </CardHeader>
        <CardContent className="pt-4">
          <p className="mb-1 text-xs font-medium text-muted-foreground">
            To: {selectedUser ? `${selectedUser.name} (${selectedUser.email})` : `User #${data.userId || '___'}`}
          </p>
          <h3 className="mb-2 text-base font-semibold leading-tight">
            {data.title || 'Notification Title'}
          </h3>
          <p className="text-sm leading-relaxed text-muted-foreground">
            {data.message || 'Your notification message will appear here...'}
          </p>
        </CardContent>
      </Card>
    </motion.div>
  );
}

function SuccessScreen({ notificationId, onReset }: { notificationId: number; onReset: () => void }) {
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.9 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.5, ease: 'easeOut' }}
      className="flex flex-col items-center justify-center py-12"
    >
      <motion.div
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ delay: 0.2, type: 'spring', stiffness: 200, damping: 15 }}
        className="mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-emerald-500/10"
      >
        <motion.div
          initial={{ pathLength: 0 }}
          animate={{ pathLength: 1 }}
          transition={{ delay: 0.5, duration: 0.6, ease: 'easeInOut' }}
        >
          <CheckCircle2 className="h-10 w-10 text-emerald-500" />
        </motion.div>
      </motion.div>
      <motion.h2
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.4 }}
        className="mb-2 text-2xl font-bold"
      >
        Notification Sent!
      </motion.h2>
      <motion.p
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.5 }}
        className="mb-6 text-muted-foreground"
      >
        Your notification has been queued for delivery.
      </motion.p>
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.6 }}
        className="mb-8 rounded-lg border bg-muted/50 px-6 py-3"
      >
        <span className="text-sm text-muted-foreground">Notification ID: </span>
        <span className="font-mono font-bold text-primary">{notificationId}</span>
      </motion.div>
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.7 }}
      >
        <Button onClick={onReset} variant="outline">
          <Send className="mr-2 h-4 w-4" />
          Send Another
        </Button>
      </motion.div>
    </motion.div>
  );
}

export default function SendNotification() {
  const queryClient = useQueryClient();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successId, setSuccessId] = useState<number | null>(null);
  const [users, setUsers] = useState<UserSummary[]>([]);

  useEffect(() => {
    usersApi.list().then(setUsers).catch(() => {});
  }, []);

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<NotificationFormData>({
    resolver: zodResolver(notificationSchema) as any,
    defaultValues: {
      userId: undefined,
      channel: 'EMAIL',
      title: '',
      message: '',
      priority: 'NORMAL',
    },
  });

  const watchedValues = watch();
  const messageLength = watchedValues.message?.length ?? 0;

  const onSubmit = async (data: NotificationFormData) => {
    setIsSubmitting(true);
    try {
      const result = await notificationApi.send({
        userId: data.userId,
        channel: data.channel,
        title: data.title,
        message: data.message,
        priority: data.priority,
      });
      setSuccessId(result.id);
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-history'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-stats'] });
      toast.success('Notification sent successfully!');
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Failed to send notification';
      toast.error(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReset = () => {
    setSuccessId(null);
    reset();
  };

  return (
    <div className="mx-auto max-w-4xl px-4 py-8">
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
      >
        <div className="mb-8">
          <h1 className="text-3xl font-bold tracking-tight">Send Notification</h1>
          <p className="mt-1 text-muted-foreground">
            Compose and dispatch notifications across multiple channels.
          </p>
        </div>
      </motion.div>

      <AnimatePresence mode="wait">
        {successId ? (
          <motion.div key="success" exit={{ opacity: 0, scale: 0.95 }}>
            <Card>
              <CardContent>
                <SuccessScreen notificationId={successId} onReset={handleReset} />
              </CardContent>
            </Card>
          </motion.div>
        ) : (
          <motion.div
            key="form"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 20 }}
            transition={{ duration: 0.3 }}
            className="grid gap-8 lg:grid-cols-5"
          >
            <div className="lg:col-span-3">
              <Card>
                <CardHeader>
                  <CardTitle>Notification Details</CardTitle>
                  <CardDescription>
                    Fill in the details to send a new notification.
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
                    <motion.div
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.05 }}
                      className="space-y-2"
                    >
                      <Label htmlFor="userId">Recipient</Label>
                      <Select id="userId" {...register('userId')}>
                        <option value="">Select a user...</option>
                        {users.map((u) => (
                          <option key={u.id} value={u.id}>
                            {u.name} ({u.email}) — {u.role}
                          </option>
                        ))}
                      </Select>
                      {errors.userId && (
                        <p className="flex items-center gap-1 text-xs text-destructive">
                          <AlertTriangle className="h-3 w-3" />
                          {errors.userId.message}
                        </p>
                      )}
                    </motion.div>

                    <motion.div
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.1 }}
                      className="space-y-2"
                    >
                      <Label htmlFor="channel">Channel</Label>
                      <Select id="channel" {...register('channel')}>
                        <option value="EMAIL">Email</option>
                        <option value="SMS">SMS</option>
                        <option value="IN_APP">In-App</option>
                      </Select>
                      {errors.channel && (
                        <p className="flex items-center gap-1 text-xs text-destructive">
                          <AlertTriangle className="h-3 w-3" />
                          {errors.channel.message}
                        </p>
                      )}
                    </motion.div>

                    <motion.div
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.15 }}
                      className="space-y-2"
                    >
                      <Label htmlFor="title">Title</Label>
                      <Input
                        id="title"
                        placeholder="Notification title"
                        {...register('title')}
                      />
                      {errors.title && (
                        <p className="flex items-center gap-1 text-xs text-destructive">
                          <AlertTriangle className="h-3 w-3" />
                          {errors.title.message}
                        </p>
                      )}
                    </motion.div>

                    <motion.div
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.2 }}
                      className="space-y-2"
                    >
                      <div className="flex items-center justify-between">
                        <Label htmlFor="message">Message</Label>
                        <span
                          className={`text-xs tabular-nums ${
                            messageLength > 900
                              ? 'text-destructive'
                              : 'text-muted-foreground'
                          }`}
                        >
                          {messageLength} / 1000
                        </span>
                      </div>
                      <Textarea
                        id="message"
                        placeholder="Type your notification message..."
                        className="min-h-[120px] resize-y"
                        {...register('message')}
                      />
                      {errors.message && (
                        <p className="flex items-center gap-1 text-xs text-destructive">
                          <AlertTriangle className="h-3 w-3" />
                          {errors.message.message}
                        </p>
                      )}
                      <div className="h-1 w-full rounded-full bg-muted">
                        <motion.div
                          className={`h-full rounded-full transition-colors duration-300 ${
                            messageLength > 900
                              ? 'bg-destructive'
                              : messageLength > 700
                                ? 'bg-amber-500'
                                : 'bg-primary'
                          }`}
                          initial={{ width: '0%' }}
                          animate={{
                            width: `${Math.min((messageLength / 1000) * 100, 100)}%`,
                          }}
                          transition={{ duration: 0.3 }}
                        />
                      </div>
                    </motion.div>

                    <motion.div
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.25 }}
                      className="space-y-2"
                    >
                      <Label htmlFor="priority">Priority</Label>
                      <Select id="priority" {...register('priority')}>
                        <option value="HIGH">High</option>
                        <option value="NORMAL">Normal</option>
                        <option value="LOW">Low</option>
                      </Select>
                      {errors.priority && (
                        <p className="flex items-center gap-1 text-xs text-destructive">
                          <AlertTriangle className="h-3 w-3" />
                          {errors.priority.message}
                        </p>
                      )}
                    </motion.div>

                    <motion.div
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.3 }}
                      className="pt-2"
                    >
                      <Button
                        type="submit"
                        loading={isSubmitting}
                        className="w-full"
                        size="lg"
                      >
                        {isSubmitting ? (
                          'Sending...'
                        ) : (
                          <>
                            <Send className="h-4 w-4" />
                            Send Notification
                            <ChevronRight className="h-4 w-4" />
                          </>
                        )}
                      </Button>
                    </motion.div>
                  </form>
                </CardContent>
              </Card>
            </div>

            <div className="lg:col-span-2">
              <div className="sticky top-8">
                <div className="mb-4">
                  <h3 className="text-sm font-medium text-muted-foreground">
                    Live Preview
                  </h3>
                </div>
                <PreviewCard data={watchedValues} users={users} />
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
