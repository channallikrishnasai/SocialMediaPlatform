import { Box, Typography, Skeleton } from '@mui/material';
import CreatePost from '@/components/posts/CreatePost';
import PostCard from '@/components/posts/PostCard';
import { useInfiniteQuery } from '@tanstack/react-query';
import { useInView } from 'react-intersection-observer';
import { useEffect } from 'react';
import axiosInstance from '@/api/axios';
import { PagedResponse, Post } from '@/types';

const FeedPage = () => {
  const { ref, inView } = useInView();

  const fetchFeed = async ({ pageParam = 0 }) => {
    const response = await axiosInstance.get<PagedResponse<Post>>(`/feed?page=${pageParam}`);
    return response.data;
  };

  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    status,
  } = useInfiniteQuery({
    queryKey: ['feed'],
    queryFn: fetchFeed,
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.pageNumber + 1),
  });

  useEffect(() => {
    if (inView && hasNextPage) {
      fetchNextPage();
    }
  }, [inView, hasNextPage, fetchNextPage]);

  return (
    <Box sx={{ maxWidth: 600, mx: 'auto' }}>
      <CreatePost />

      {status === 'pending' ? (
        [1, 2, 3].map((i) => (
          <Box key={i} sx={{ mb: 3 }}>
            <Skeleton variant="circular" width={40} height={40} sx={{ mb: 1 }} />
            <Skeleton variant="rectangular" height={200} sx={{ borderRadius: 2 }} />
          </Box>
        ))
      ) : status === 'error' ? (
        <Typography color="error" align="center">
          Error loading feed. Please try again later.
        </Typography>
      ) : (
        <>
          {data?.pages.map((page) =>
            page.content.map((post) => (
              <PostCard key={post.id} post={post} />
            ))
          )}

          <div ref={ref}>
            {isFetchingNextPage && (
              <Box sx={{ mb: 3 }}>
                <Skeleton variant="circular" width={40} height={40} sx={{ mb: 1 }} />
                <Skeleton variant="rectangular" height={200} sx={{ borderRadius: 2 }} />
              </Box>
            )}
          </div>

          {!hasNextPage && data?.pages[0].totalElements !== 0 && (
            <Typography variant="body2" color="text.secondary" align="center" sx={{ py: 4 }}>
              You've seen all posts!
            </Typography>
          )}

          {data?.pages[0].totalElements === 0 && (
            <Typography variant="h6" align="center" sx={{ py: 8 }}>
              Your feed is empty. Follow some users to see their posts!
            </Typography>
          )}
        </>
      )}
    </Box>
  );
};

export default FeedPage;
