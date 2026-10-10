import {
  Card,
  CardHeader,
  CardContent,
  CardActions,
  Avatar,
  Typography,
  IconButton,
  Box,
  Divider,
} from '@mui/material';
import {
  Favorite as FavoriteIcon,
  FavoriteBorder as FavoriteBorderIcon,
  ChatBubbleOutline as CommentIcon,
  Share as ShareIcon,
  MoreVert as MoreVertIcon,
} from '@mui/icons-material';
import { Post } from '@/types';
import { formatDistanceToNow } from 'date-fns';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { postApi } from '@/api/postApi';

interface PostCardProps {
  post: Post;
}

const PostCard = ({ post }: PostCardProps) => {
  const queryClient = useQueryClient();

  const likeMutation = useMutation({
    mutationFn: async () => {
      if (post.isLiked) {
        return postApi.unlikePost(post.id);
      } else {
        return postApi.likePost(post.id);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['feed'] });
      queryClient.invalidateQueries({ queryKey: ['user-posts', post.user.id] });
    },
  });

  const handleLike = () => {
    likeMutation.mutate();
  };

  return (
    <Card sx={{ mb: 3 }}>
      <CardHeader
        avatar={
          <Avatar
            src={post.user.avatarUrl}
            alt={post.user.username}
            sx={{ width: 40, height: 40 }}
          />
        }
        action={
          <IconButton aria-label="settings">
            <MoreVertIcon />
          </IconButton>
        }
        title={
          <Typography variant="subtitle1" fontWeight={700}>
            {post.user.username}
          </Typography>
        }
        subheader={formatDistanceToNow(new Date(post.createdAt), { addSuffix: true })}
      />

      <CardContent sx={{ pt: 0 }}>
        <Typography variant="body1" color="text.primary">
          {post.content}
        </Typography>
        {post.imageUrl && (
          <Box
            component="img"
            src={post.imageUrl}
            alt="Post image"
            sx={{
              width: '100%',
              borderRadius: 2,
              mt: 2,
              maxHeight: 500,
              objectFit: 'cover',
            }}
          />
        )}
      </CardContent>

      <Divider sx={{ mx: 2 }} />

      <CardActions disableSpacing sx={{ px: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mr: 2 }}>
          <IconButton 
            aria-label="like" 
            color={post.isLiked ? 'primary' : 'default'}
            onClick={handleLike}
            disabled={likeMutation.isPending}
          >
            {post.isLiked ? <FavoriteIcon /> : <FavoriteBorderIcon />}
          </IconButton>
          <Typography variant="body2" color="text.secondary">
            {post.likesCount}
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', mr: 2 }}>
          <IconButton aria-label="comment">
            <CommentIcon />
          </IconButton>
          <Typography variant="body2" color="text.secondary">
            {post.commentsCount}
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <IconButton aria-label="share">
            <ShareIcon />
          </IconButton>
          <Typography variant="body2" color="text.secondary">
            {post.shareCount}
          </Typography>
        </Box>
      </CardActions>
    </Card>
  );
};

export default PostCard;
