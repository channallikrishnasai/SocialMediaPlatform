import { describe, it, expect, beforeEach } from 'vitest';
import authReducer, { setCredentials, logout, updateUser } from '../authSlice';
import { User } from '@/types';

describe('authSlice', () => {
  const initialState = {
    user: null,
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,
    loading: false,
  };

  const mockUser: User = {
    id: 1,
    username: 'testuser',
    email: 'test@example.com',
    role: 'USER',
    followersCount: 0,
    followingCount: 0,
    isFollowing: false,
    createdAt: '2023-01-01',
  };

  beforeEach(() => {
    localStorage.clear();
  });

  it('should handle setCredentials', () => {
    const payload = {
      user: mockUser,
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
    };
    const state = authReducer(initialState, setCredentials(payload));

    expect(state.user).toEqual(mockUser);
    expect(state.accessToken).toBe('access-token');
    expect(state.isAuthenticated).toBe(true);
    expect(localStorage.getItem('accessToken')).toBe('access-token');
  });

  it('should handle logout', () => {
    const loggedInState = {
      user: mockUser,
      accessToken: 'token',
      refreshToken: 'refresh',
      isAuthenticated: true,
      loading: false,
    };
    const state = authReducer(loggedInState, logout());

    expect(state.user).toBeNull();
    expect(state.isAuthenticated).toBe(false);
    expect(localStorage.getItem('accessToken')).toBeNull();
  });

  it('should handle updateUser', () => {
    const loggedInState = {
      user: mockUser,
      accessToken: 'token',
      refreshToken: 'refresh',
      isAuthenticated: true,
      loading: false,
    };
    const state = authReducer(loggedInState, updateUser({ username: 'newname' }));

    expect(state.user?.username).toBe('newname');
  });
});
