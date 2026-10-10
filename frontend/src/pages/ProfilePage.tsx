import {
  Box,
  Typography,
  Paper,
  Avatar,
  Grid,
  Button,
  Divider,
} from '@mui/material';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useSelector } from 'react-redux';
import { RootState } from '@/redux/store';
import { Post } from '@/types';
import PostCard from '@/components/posts/PostCard';
import { userApi } from '@/api/userApi';
import { postApi } from '@/api/postApi';

const ProfilePage = () => {
  const { userId } = useParams<{ userId: string }>();
  const queryClient = useQueryClient();
  const currentUser = useSelector((state: RootState) => state.auth.user);

  const { data: user, isLoading: isUserLoading } = useQuery({
    queryKey: ['user', userId],
    queryFn: () => userApi.getUserProfile(userId!),
    enabled: !!userId,
  });

  const { data: posts, isLoading: isPostsLoading } = useQuery({
    queryKey: ['user-posts', userId],
    queryFn: () => postApi.getUserPosts(Number(userId)),
    enabled: !!userId,
  });

  const followMutation = useMutation({
    mutationFn: async () => {
      if (user?.isFollowing) {
        return userApi.unfollowUser(Number(userId));
      } else {
        return userApi.followUser(Number(userId));
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user', userId] });
    },
  });

  if (isUserLoading) return <Typography>Loading profile...</Typography>;
  if (!user) return <Typography>User not found</Typography>;

  return (
    <Box sx={{ maxWidth: 800, mx: 'auto' }}>
      <Paper sx={{ borderRadius: 4, overflow: 'hidden', mb: 3 }}>
        {/* Cover Image Placeholder */}
        <Box
          sx={{
            height: 200,
            background: 'linear-gradient(90deg, #6366f1 0%, #a855f7 100%)',
          }}
        />

        <Box sx={{ p: 4, mt: -8 }}>
          <Box
            sx={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'flex-end',
              mb: 3,
            }}
          >
            <Avatar
              src={user.avatarUrl}
              alt={user.username}
              sx={{
                width: 128,
                height: 128,
                border: '4px solid white',
                boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
              }}
            />
            {currentUser?.id.toString() !== userId && (
              <Button
                variant="contained"
                sx={{ borderRadius: 20, px: 4, mb: 1 }}
                onClick={() => followMutation.mutate()}
                disabled={followMutation.isPending}
              >
                {user.isFollowing ? 'Unfollow' : 'Follow'}
              </Button>
            )}
          </Box>

          <Typography variant="h4" fontWeight={800}>
            {user.username}
          </Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
            {user.bio || 'No bio available'}
          </Typography>

          <Grid container spacing={3} sx={{ mb: 3 }}>
            <Grid item>
              <Typography variant="h6" fontWeight={700} display="inline">
                {user.followersCount}
              </Typography>
              <Typography variant="body2" color="text.secondary" display="inline" sx={{ ml: 1 }}>
                Followers
              </Typography>
            </Grid>
            <Grid item>
              <Typography variant="h6" fontWeight={700} display="inline">
                {user.followingCount}
              </Typography>
              <Typography variant="body2" color="text.secondary" display="inline" sx={{ ml: 1 }}>
                Following
              </Typography>
            </Grid>
          </Grid>

          <Divider />
        </Box>
      </Paper>

      <Box sx={{ mt: 2 }}>
        {isPostsLoading ? (
          <Typography>Loading posts...</Typography>
        ) : posts?.content.length === 0 ? (
          <Paper sx={{ p: 4, textAlign: 'center', borderRadius: 3 }}>
            <Typography color="text.secondary">No posts yet</Typography>
          </Paper>
        ) : (
          posts?.content.map((post: Post) => (
            <PostCard key={post.id} post={post} />
          ))
        )}
      </Box>
    </Box>
  );
};

export default ProfilePage;
