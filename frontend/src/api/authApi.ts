import axiosInstance from './axios';
import { AuthResponse, LoginRequest, RegisterRequest } from '@/types';

export const authApi = {
  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await axiosInstance.post('/auth/login', data);
    return response.data;
  },

  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await axiosInstance.post('/auth/register', data);
    return response.data;
  },

  refreshToken: async (refreshToken: string): Promise<AuthResponse> => {
    const response = await axiosInstance.post(
      '/auth/refresh',
      {},
      {
        headers: {
          'Refresh-Token': refreshToken,
        },
      }
    );
    return response.data;
  },

  getCurrentUser: async () => {
    const response = await axiosInstance.get('/users/me');
    return response.data;
  },
};
