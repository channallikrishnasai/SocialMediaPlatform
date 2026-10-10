import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { Provider } from 'react-redux';
import { ThemeProvider, CssBaseline } from '@mui/material';
import { store } from './redux/store';
import { theme } from './utils/theme';
import { useSelector } from 'react-redux';
import { RootState } from './redux/store';

// Pages
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import FeedPage from './pages/FeedPage';
import ProfilePage from './pages/ProfilePage';
import FollowersPage from './pages/FollowersPage';
import NotificationsPage from './pages/NotificationsPage';
import SettingsPage from './pages/SettingsPage';

// Layout
import MainLayout from './components/layout/MainLayout';
import { useEffect } from 'react';
import { useDispatch } from 'react-redux';
import { userApi } from './api/userApi';
import { connectNotifications, disconnectNotifications } from './api/websocket';
import { setCredentials, setLoading } from './redux/slices/authSlice';
import { addNotification } from './redux/slices/notificationSlice';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 5 * 60 * 1000,
    },
  },
});

interface ProtectedRouteProps {
  children: React.ReactNode;
}

const ProtectedRoute = ({ children }: ProtectedRouteProps) => {
  const isAuthenticated = useSelector((state: RootState) => state.auth.isAuthenticated);
  return isAuthenticated ? <MainLayout>{children}</MainLayout> : <Navigate to="/login" replace />;
};

function AppRoutes() {
  const dispatch = useDispatch();
  const { user, isAuthenticated } = useSelector((state: RootState) => state.auth);

  useEffect(() => {
    const fetchUser = async () => {
      const token = localStorage.getItem('accessToken');
      const refreshToken = localStorage.getItem('refreshToken');
      if (token && !user) {
        try {
          dispatch(setLoading(true));
          const userData = await userApi.getCurrentUser();
          dispatch(setCredentials({ 
            user: userData, 
            accessToken: token, 
            refreshToken: refreshToken || '' 
          }));
        } catch (error) {
          console.error('Failed to fetch user:', error);
          localStorage.removeItem('accessToken');
          localStorage.removeItem('refreshToken');
        } finally {
          dispatch(setLoading(false));
        }
      }
    };

    fetchUser();
  }, [dispatch, user]);

  useEffect(() => {
    if (!isAuthenticated) {
      disconnectNotifications();
      return;
    }

    connectNotifications((notification) => {
      dispatch(addNotification(notification));
    });

    return () => disconnectNotifications();
  }, [isAuthenticated, dispatch]);

  return (
    <Routes>
      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />}
      />
      <Route
        path="/register"
        element={isAuthenticated ? <Navigate to="/" replace /> : <RegisterPage />}
      />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <FeedPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/profile/:userId"
        element={
          <ProtectedRoute>
            <ProfilePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/followers/:userId"
        element={
          <ProtectedRoute>
            <FollowersPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/notifications"
        element={
          <ProtectedRoute>
            <NotificationsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/settings"
        element={
          <ProtectedRoute>
            <SettingsPage />
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

function App() {
  return (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <CssBaseline />
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
          <ReactQueryDevtools initialIsOpen={false} />
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>
  );
}

export default App;
