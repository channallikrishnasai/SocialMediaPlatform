import { Box, Container, Grid } from '@mui/material';
import Navbar from './Navbar';
import Sidebar from './Sidebar';

interface MainLayoutProps {
  children: React.ReactNode;
}

const MainLayout = ({ children }: MainLayoutProps) => {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <Navbar />
      <Container maxWidth="lg" sx={{ mt: 3, mb: 4, flexGrow: 1 }}>
        <Grid container spacing={3}>
          <Grid item xs={12} md={3} sx={{ display: { xs: 'none', md: 'block' } }}>
            <Sidebar />
          </Grid>
          <Grid item xs={12} md={9}>
            <Box component="main">{children}</Box>
          </Grid>
        </Grid>
      </Container>
    </Box>
  );
};

export default MainLayout;
