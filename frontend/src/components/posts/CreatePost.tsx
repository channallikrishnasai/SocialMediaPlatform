import React, { useState } from 'react';
import {
  Paper,
  Avatar,
  TextField,
  Button,
  Box,
  IconButton,
  Tooltip,
} from '@mui/material';
import {
  ImageOutlined as ImageIcon,
  EmojiEmotionsOutlined as EmojiIcon,
  Send as SendIcon,
} from '@mui/icons-material';
import { useSelector } from 'react-redux';
import { RootState } from '@/redux/store';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { postApi } from '@/api/postApi';

const CreatePost = () => {
  const [content, setContent] = useState('');
  const { user } = useSelector((state: RootState) => state.auth);
  const queryClient = useQueryClient();

  const createPostMutation = useMutation({
    mutationFn: (data: { content: string }) => postApi.createPost(data),
    onSuccess: () => {
      setContent('');
      queryClient.invalidateQueries({ queryKey: ['feed'] });
      if (user?.id) {
        queryClient.invalidateQueries({ queryKey: ['user-posts', user.id.toString()] });
      }
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim()) {
      return;
    }
    createPostMutation.mutate({ content });
  };

  return (
    <Paper sx={{ p: 2, mb: 3, borderRadius: 3 }}>
      <Box sx={{ display: 'flex', gap: 2 }}>
        <Avatar src={user?.avatarUrl} alt={user?.username} />
        <form 
          onSubmit={handleSubmit} 
          style={{ flexGrow: 1 }}
        >
          <TextField
            fullWidth
            multiline
            rows={2}
            placeholder={`What's on your mind, ${user?.username}?`}
            variant="standard"
            value={content}
            onChange={(e) => setContent(e.target.value)}
            InputProps={{
              disableUnderline: true,
              sx: { fontSize: '1.1rem' },
            }}
          />
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              mt: 2,
              pt: 1,
              borderTop: '1px solid',
              borderColor: 'divider',
            }}
          >
            <Box>
              <Tooltip title="Add Image">
                <IconButton size="small" color="primary">
                  <ImageIcon />
                </IconButton>
              </Tooltip>
              <Tooltip title="Add Emoji">
                <IconButton size="small" color="primary">
                  <EmojiIcon />
                </IconButton>
              </Tooltip>
            </Box>
            <Button
              variant="contained"
              disabled={!content.trim() || createPostMutation.isPending}
              endIcon={<SendIcon />}
              type="submit"
              sx={{ borderRadius: 20, px: 3 }}
            >
              {createPostMutation.isPending ? 'Posting...' : 'Post'}
            </Button>
          </Box>
        </form>
      </Box>
    </Paper>
  );
};

export default CreatePost;
