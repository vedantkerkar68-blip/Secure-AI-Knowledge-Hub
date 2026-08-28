import { BrowserRouter } from 'react-router-dom';
import { useMemo } from 'react';
import { ThemeProvider } from '@mui/material/styles';
import { CssBaseline } from '@mui/material';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import createAppTheme from './theme';
import { AuthProvider, useAuth } from './context/AuthContext';
import { BackendHealthProvider } from './context/BackendHealthContext';
import { SettingsProvider, useSettings } from './context/SettingsContext';
import { ThemeModeProvider, useThemeMode } from './context/ThemeModeContext';
import AppRoutes from './routes/AppRoutes';
import SplashScreen from './components/branding/SplashScreen';

function AuthGate({ children }) {
  const { loading } = useAuth();

  if (loading) {
    return <SplashScreen />;
  }

  return children;
}

function SettingsAwareToast() {
  const { toastsEnabled } = useSettings();
  const { mode } = useThemeMode();

  if (!toastsEnabled) {
    return null;
  }

  return (
    <ToastContainer
      position="top-right"
      autoClose={3000}
      hideProgressBar={false}
      newestOnTop
      closeOnClick
      pauseOnFocusLoss
      draggable
      pauseOnHover
      theme={mode}
    />
  );
}

function ThemedApp() {
  const { mode } = useThemeMode();
  const theme = useMemo(() => createAppTheme(mode), [mode]);

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BackendHealthProvider>
        <AuthProvider>
          <AuthGate>
            <AppRoutes />
          </AuthGate>
          <SettingsAwareToast />
        </AuthProvider>
      </BackendHealthProvider>
    </ThemeProvider>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <ThemeModeProvider>
        <SettingsProvider>
          <ThemedApp />
        </SettingsProvider>
      </ThemeModeProvider>
    </BrowserRouter>
  );
}