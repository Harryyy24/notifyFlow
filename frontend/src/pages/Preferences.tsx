import { motion } from 'framer-motion';
import { useState, useEffect, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { preferenceApi } from '@/services/api';
import { useAuth } from '@/context/AuthContext';
import { Mail, MessageSquare, Bell, Moon, Save, Clock, Info } from 'lucide-react';
import toast from 'react-hot-toast';

const HOURS = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0'));
const MINUTES = Array.from({ length: 60 }, (_, i) => String(i).padStart(2, '0'));

const formSchema = z.object({
  emailEnabled: z.boolean(),
  smsEnabled: z.boolean(),
  inAppEnabled: z.boolean(),
  startHour: z.string(),
  startMinute: z.string(),
  endHour: z.string(),
  endMinute: z.string(),
});

type FormData = z.infer<typeof formSchema>;

const channelCards = [
  { key: 'emailEnabled' as const, icon: Mail, name: 'Email', description: 'Receive notifications via email' },
  { key: 'smsEnabled' as const, icon: MessageSquare, name: 'SMS', description: 'Receive notifications via text message' },
  { key: 'inAppEnabled' as const, icon: Bell, name: 'In-App', description: 'Receive notifications within the application' },
];

export default function Preferences() {
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['preferences', user?.id],
    queryFn: () => preferenceApi.get(user!.id),
    enabled: !!user?.id,
  });

  const form = useForm<FormData>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      emailEnabled: false,
      smsEnabled: false,
      inAppEnabled: false,
      startHour: '22',
      startMinute: '00',
      endHour: '07',
      endMinute: '00',
    },
  });

  useEffect(() => {
    if (data) {
      form.reset({
        emailEnabled: data.emailEnabled,
        smsEnabled: data.smsEnabled,
        inAppEnabled: data.inAppEnabled,
        startHour: data.quietHoursStart?.split(':')[0] ?? '22',
        startMinute: data.quietHoursStart?.split(':')[1] ?? '00',
        endHour: data.quietHoursEnd?.split(':')[0] ?? '07',
        endMinute: data.quietHoursEnd?.split(':')[1] ?? '00',
      });
    }
  }, [data, form]);

  const [showSuccess, setShowSuccess] = useState(false);

  const mutation = useMutation({
    mutationFn: (formData: FormData) =>
      preferenceApi.update(user!.id, {
        emailEnabled: formData.emailEnabled,
        smsEnabled: formData.smsEnabled,
        inAppEnabled: formData.inAppEnabled,
        quietHoursStart: `${formData.startHour}:${formData.startMinute}`,
        quietHoursEnd: `${formData.endHour}:${formData.endMinute}`,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['preferences', user?.id] });
      toast.success('Preferences saved successfully');
      setShowSuccess(true);
      setTimeout(() => setShowSuccess(false), 2000);
    },
    onError: () => {
      toast.error('Failed to save preferences');
    },
  });

  const watchedValues = form.watch();

  const isQuietHoursActive = useMemo(() => {
    const now = new Date();
    const currentMin = now.getHours() * 60 + now.getMinutes();
    const startMin = parseInt(watchedValues.startHour) * 60 + parseInt(watchedValues.startMinute);
    const endMin = parseInt(watchedValues.endHour) * 60 + parseInt(watchedValues.endMinute);
    if (startMin <= endMin) {
      return currentMin >= startMin && currentMin < endMin;
    }
    return currentMin >= startMin || currentMin < endMin;
  }, [watchedValues.startHour, watchedValues.startMinute, watchedValues.endHour, watchedValues.endMinute]);

  const onSubmit = form.handleSubmit((formData) => mutation.mutate(formData));

  if (isLoading) {
    return (
      <div className="space-y-6">
        <div>
          <Skeleton className="h-8 w-48" />
          <Skeleton className="mt-2 h-4 w-72" />
        </div>
        {Array.from({ length: 4 }).map((_, i) => (
          <Card key={i}>
            <CardHeader>
              <Skeleton className="h-5 w-32" />
              <Skeleton className="mt-1 h-4 w-56" />
            </CardHeader>
            <CardContent>
              <Skeleton className="h-12 w-full" />
            </CardContent>
          </Card>
        ))}
      </div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="space-y-6"
    >
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Preferences</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Manage your notification channel preferences and quiet hours
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Notification Channels</CardTitle>
          <CardDescription>
            Toggle which channels you want to receive notifications on
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {channelCards.map((channel, index) => (
            <motion.div
              key={channel.key}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: index * 0.1, duration: 0.3 }}
            >
              <div className="flex items-center justify-between rounded-lg border p-4">
                <div className="flex items-center gap-4">
                  <div className="rounded-lg bg-primary/10 p-2">
                    <channel.icon className="h-5 w-5 text-primary" />
                  </div>
                  <div>
                    <p className="text-sm font-medium">{channel.name}</p>
                    <p className="text-xs text-muted-foreground">{channel.description}</p>
                  </div>
                </div>
                <Controller
                  name={channel.key}
                  control={form.control}
                  render={({ field }) => (
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  )}
                />
              </div>
            </motion.div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex items-start justify-between gap-4">
            <div>
              <CardTitle className="flex items-center gap-2">
                <Moon className="h-5 w-5" />
                Quiet Hours
              </CardTitle>
              <CardDescription>
                Suppress LOW priority notifications during specific hours
              </CardDescription>
            </div>
            <motion.div
              animate={{ opacity: isQuietHoursActive ? 1 : 0.6 }}
              className="flex shrink-0 items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium"
            >
              <motion.span
                animate={{ backgroundColor: isQuietHoursActive ? '#22c55e' : '#d1d5db' }}
                className="h-2 w-2 rounded-full"
              />
              {isQuietHoursActive ? 'Active' : 'Inactive'}
            </motion.div>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="startHour" className="flex items-center gap-2">
                <Clock className="h-4 w-4" />
                Start Time
              </Label>
              <div className="flex items-center gap-2">
                <Select id="startHour" {...form.register('startHour')}>
                  {HOURS.map((h) => (
                    <option key={h} value={h}>{h}</option>
                  ))}
                </Select>
                <span className="text-sm text-muted-foreground">:</span>
                <Select id="startMinute" {...form.register('startMinute')}>
                  {MINUTES.map((m) => (
                    <option key={m} value={m}>{m}</option>
                  ))}
                </Select>
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="endHour" className="flex items-center gap-2">
                <Clock className="h-4 w-4" />
                End Time
              </Label>
              <div className="flex items-center gap-2">
                <Select id="endHour" {...form.register('endHour')}>
                  {HOURS.map((h) => (
                    <option key={h} value={h}>{h}</option>
                  ))}
                </Select>
                <span className="text-sm text-muted-foreground">:</span>
                <Select id="endMinute" {...form.register('endMinute')}>
                  {MINUTES.map((m) => (
                    <option key={m} value={m}>{m}</option>
                  ))}
                </Select>
              </div>
            </div>
          </div>

          <div className="flex items-start gap-3 rounded-lg bg-muted/50 p-4 text-sm">
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
            <p className="leading-relaxed text-muted-foreground">
              Quiet hours suppress <strong>LOW</strong> priority notifications.
              {' '}HIGH and NORMAL priority notifications will still be delivered during this period.
            </p>
          </div>
        </CardContent>
      </Card>

      <div className="flex justify-end">
        <Button
          type="button"
          onClick={onSubmit}
          loading={mutation.isPending}
          size="lg"
          className="min-w-[180px] gap-2"
        >
          {showSuccess ? (
            <motion.span
              key="success"
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              className="flex items-center gap-2"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              Saved
            </motion.span>
          ) : (
            <span className="flex items-center gap-2">
              <Save className="h-4 w-4" />
              Save Preferences
            </span>
          )}
        </Button>
      </div>
    </motion.div>
  );
}
