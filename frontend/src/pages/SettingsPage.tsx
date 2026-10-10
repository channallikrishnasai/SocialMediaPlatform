import { Box, Typography, Paper } from '@mui/material';

const SettingsPage = () => {
  return (
    <Box sx={{ maxWidth: 600, mx: 'auto', mt: 4 }}>
      <Typography variant="h5" fontWeight={800} sx={{ mb: 3 }}>
        Settings
      </Typography>
      <Paper sx={{ p: 4, textAlign: 'center', borderRadius: 3 }}>
        <Typography color="text.secondary">Settings functionality coming soon</Typography>
      </Paper>
    </Box>
  );
};

export default SettingsPage;
