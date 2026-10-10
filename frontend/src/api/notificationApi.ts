import axiosInstance from './axios';
import { Notification, PagedResponse } from '@/types';

export const notificationApi = {
  getNotifications: async (page: number = 0): Promise<PagedResponse<Notification>> => {
    const response = await axiosInstance.get(`/notifications?page=${page}`);
    return response.data;
  },

  getUnreadCount: async (): Promise<number> => {
    const response = await axiosInstance.get('/notifications/unread/count');
    return response.data;
  },

  markAsRead: async (notificationId: number): Promise<void> => {
    await axiosInstance.put(`/notifications/${notificationId}/read`);
  },

  markAllAsRead: async (): Promise<void> => {
    await axiosInstance.put('/notifications/read-all');
  },
};
