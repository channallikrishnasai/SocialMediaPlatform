import { useEffect } from 'react';
import { Box, Typography, Paper, List, ListItemButton, ListItemText, Button, Divider } from '@mui/material';
import { useDispatch, useSelector } from 'react-redux';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { formatDistanceToNow } from 'date-fns';
import { RootState } from '@/redux/store';
import { notificationApi } from '@/api/notificationApi';
import { setNotifications, markAsRead, markAllAsRead } from '@/redux/slices/notificationSlice';

const NotificationsPage = () => {
  const dispatch = useDispatch();
  const { notifications, unreadCount } = useSelector((state: RootState) => state.notification);
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => notificationApi.getNotifications(),
  });

  useEffect(() => {
    if (data) {
      dispatch(setNotifications(data.content));
    }
  }, [data, dispatch]);

  const markReadMutation = useMutation({
    mutationFn: (id: number) => notificationApi.markAsRead(id),
    onSuccess: (_data, id) => dispatch(markAsRead(id)),
  });

  const markAllReadMutation = useMutation({
    mutationFn: () => notificationApi.markAllAsRead(),
    onSuccess: () => {
      dispatch(markAllAsRead());
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  return (
    <Box sx={{ maxWidth: 600, mx: 'auto', mt: 4 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h5" fontWeight={800}>
          Notifications
        </Typography>
        {unreadCount > 0 && (
          <Button size="small" onClick={() => markAllReadMutation.mutate()}>
            Mark all as read
          </Button>
        )}
      </Box>
      <Paper sx={{ borderRadius: 3, overflow: 'hidden' }}>
        {isLoading ? (
          <Typography color="text.secondary" sx={{ p: 4, textAlign: 'center' }}>
            Loading...
          </Typography>
        ) : notifications.length === 0 ? (
          <Typography color="text.secondary" sx={{ p: 4, textAlign: 'center' }}>
            No new notifications
          </Typography>
        ) : (
          <List disablePadding>
            {notifications.map((notification, index) => (
              <Box key={notification.id}>
                {index > 0 && <Divider />}
                <ListItemButton
                  onClick={() => !notification.isRead && markReadMutation.mutate(notification.id)}
                  sx={{ bgcolor: notification.isRead ? 'transparent' : 'action.hover' }}
                >
                  <ListItemText
                    primary={notification.message}
                    secondary={formatDistanceToNow(new Date(notification.createdAt), { addSuffix: true })}
                  />
                </ListItemButton>
              </Box>
            ))}
          </List>
        )}
      </Paper>
    </Box>
  );
};

export default NotificationsPage;
