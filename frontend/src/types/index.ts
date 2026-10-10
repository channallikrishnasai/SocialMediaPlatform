export interface User {
  id: number;
  username: string;
  email: string;
  bio?: string;
  avatarUrl?: string;
  role: string;
  followersCount: number;
  followingCount: number;
  isFollowing?: boolean;
  createdAt: string;
}

export interface Post {
  id: number;
  content: string;
  imageUrl?: string;
  user: User;
  sharedPost?: Post;
  likesCount: number;
  commentsCount: number;
  shareCount: number;
  isLiked: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Comment {
  id: number;
  content: string;
  user: User;
  createdAt: string;
  updatedAt: string;
}

export interface Notification {
  id: number;
  type: string;
  message: string;
  relatedUserId?: number;
  relatedPostId?: number;
  isRead: boolean;
  createdAt: string;
}

export interface PagedResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface PostRequest {
  content: string;
  imageUrl?: string;
  sharedPostId?: number;
}

export interface CommentRequest {
  content: string;
}
