import axiosInstance from './axios';
import { User, PagedResponse } from '@/types';

export const userApi = {
  getUserProfile: async (userId: number | string): Promise<User> => {
    const response = await axiosInstance.get(`/users/${userId}`);
    return response.data;
  },

  getCurrentUser: async (): Promise<User> => {
    const response = await axiosInstance.get('/users/me');
    return response.data;
  },

  updateProfile: async (userId: number, data: { bio?: string; avatarUrl?: string }): Promise<User> => {
    const response = await axiosInstance.put(`/users/${userId}`, null, { params: data });
    return response.data;
  },

  followUser: async (userId: number): Promise<void> => {
    await axiosInstance.post(`/users/${userId}/follow`);
  },

  unfollowUser: async (userId: number): Promise<void> => {
    await axiosInstance.delete(`/users/${userId}/follow`);
  },

  getFollowers: async (userId: number | string, page: number = 0): Promise<PagedResponse<User>> => {
    const response = await axiosInstance.get(`/users/${userId}/followers?page=${page}`);
    return response.data;
  },

  getFollowing: async (userId: number | string, page: number = 0): Promise<PagedResponse<User>> => {
    const response = await axiosInstance.get(`/users/${userId}/following?page=${page}`);
    return response.data;
  },
};
