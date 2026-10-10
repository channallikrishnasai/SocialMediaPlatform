import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import PostCard from '../PostCard';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Post } from '@/types';
import { ThemeProvider } from '@mui/material';
import { theme } from '@/utils/theme';

// Mock postApi
vi.mock('@/api/postApi', () => ({
  postApi: {
    likePost: vi.fn(),
    unlikePost: vi.fn(),
  },
}));

const mockPost: Post = {
  id: 1,
  content: 'Test post content',
  imageUrl: 'test-image.jpg',
  user: {
    id: 1,
    username: 'testuser',
    avatarUrl: 'avatar.jpg',
    email: 'test@example.com',
    role: 'USER',
    followersCount: 0,
    followingCount: 0,
    isFollowing: false,
    createdAt: '2023-01-01',
  },
  likesCount: 10,
  commentsCount: 5,
  shareCount: 2,
  isLiked: false,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

const renderPostCard = (post: Post) => {
  return render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <PostCard post={post} />
      </QueryClientProvider>
    </ThemeProvider>
  );
};

describe('PostCard', () => {
  it('renders post content and user info', () => {
    renderPostCard(mockPost);

    expect(screen.getByText('testuser')).toBeInTheDocument();
    expect(screen.getByText('Test post content')).toBeInTheDocument();
    expect(screen.getByText('10')).toBeInTheDocument(); // likes
    expect(screen.getByText('5')).toBeInTheDocument();  // comments
    expect(screen.getByAltText('Post image')).toBeInTheDocument();
  });

  it('shows filled heart icon when post is liked', () => {
    const likedPost = { ...mockPost, isLiked: true };
    renderPostCard(likedPost);

    // MUI IconButton with color="primary" applies a class but might not keep the attribute in the DOM
    // Let's check for the filled heart icon which is only present when liked
    expect(screen.getByTestId('FavoriteIcon')).toBeInTheDocument();
  });
});
