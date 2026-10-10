import axiosInstance from './axios';
import { Post, PostRequest, PagedResponse, Comment, CommentRequest } from '@/types';

export const postApi = {
  createPost: async (data: PostRequest): Promise<Post> => {
    const response = await axiosInstance.post('/posts', data);
    return response.data;
  },

  getFeed: async (page: number = 0): Promise<PagedResponse<Post>> => {
    const response = await axiosInstance.get(`/feed?page=${page}`);
    return response.data;
  },

  getUserPosts: async (userId: number, page: number = 0): Promise<PagedResponse<Post>> => {
    const response = await axiosInstance.get(`/posts/user/${userId}?page=${page}`);
    return response.data;
  },

  likePost: async (postId: number): Promise<void> => {
    await axiosInstance.post(`/posts/${postId}/like`);
  },

  unlikePost: async (postId: number): Promise<void> => {
    await axiosInstance.delete(`/posts/${postId}/like`);
  },

  getComments: async (postId: number, page: number = 0): Promise<PagedResponse<Comment>> => {
    const response = await axiosInstance.get(`/posts/${postId}/comments?page=${page}`);
    return response.data;
  },

  addComment: async (postId: number, data: CommentRequest): Promise<Comment> => {
    const response = await axiosInstance.post(`/posts/${postId}/comments`, data);
    return response.data;
  },
};
