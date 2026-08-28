import { AppBar, Toolbar, Box, Button, IconButton, Tooltip } from '@mui/material';
import { useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import LightModeIcon from '@mui/icons-material/LightMode';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import AccountMenu from './AccountMenu';
import AppLogo from '../branding/AppLogo';
import { toast } from 'react-toastify';
import { useBackendHealth, HEALTH_STATUS } from '../../context/BackendHealthContext';
import { useSettings } from '../../context/SettingsContext';
import { useThemeMode } from '../../context/ThemeModeContext';

const NAV_TABS = [
  { label: 'Home', path: '/dashboard' },
  { label: 'Chat', path: '/chat' },
];

function BackendStatusIndicator() {
  const { status, retry } = useBackendHealth();

  if (status === HEALTH_STATUS.READY) {
    return (
      <Tooltip title="Knowledge Hub ready">
        <Box
          aria-label="Backend online"
          sx={{
            width: 9,
            height: 9,
            borderRadius: '50%',
            bgcolor: 'success.main',
            boxShadow: '0 0 0 3px rgba(22,163,74,0.15)',
          }}
        />
      </Tooltip>
    );
  }

  if (status === HEALTH_STATUS.UNAVAILABLE) {
    return (
      <Tooltip title="Knowledge service unavailable — click to retry">
        <Box
          component="button"
          aria-label="Backend offline, retry"
          onClick={retry}
          sx={{
            width: 9,
            height: 9,
            borderRadius: '50%',
            bgcolor: 'error.main',
            border: 'none',
            cursor: 'pointer',
            p: 0,
            boxShadow: '0 0 0 3px rgba(220,38,38,0.15)',
          }}
        />
      </Tooltip>
    );
  }

  return (
    <Tooltip title="Connecting to Knowledge Hub...">
      <Box
        aria-label="Connecting to backend"
        sx={{
          width: 9,
          height: 9,
          borderRadius: '50%',
          bgcolor: 'warning.main',
          animation: 'pulse 1.4s ease-in-out infinite',
          '@keyframes pulse': { '0%,100%': { opacity: 0.4 }, '50%': { opacity: 1 } },
          '@media (prefers-reduced-motion: reduce)': { animation: 'none', opacity: 1 },
        }}
      />
    </Tooltip>
  );
}

export default function Navbar() {
  const location = useLocation();
  const navigate = useNavigate();

  const { status } = useBackendHealth();
  const { outageAlertsEnabled } = useSettings();
  const { mode, toggleMode } = useThemeMode();
  const prevStatus = useRef(status);

  useEffect(() => {
    if (prevStatus.current === HEALTH_STATUS.READY && status === HEALTH_STATUS.UNAVAILABLE && outageAlertsEnabled) {
      toast.error('Connection to the knowledge service was lost. Reconnecting...', { autoClose: 5000 });
    }
    prevStatus.current = status;
  }, [status, outageAlertsEnabled]);

  const ThemeToggle = (
    <Tooltip title={mode === 'light' ? 'Switch to dark mode' : 'Switch to light mode'}>
      <IconButton
        onClick={toggleMode}
        size="small"
        aria-label="Toggle color theme"
        sx={{
          borderRadius: 999,
          border: '1px solid',
          borderColor: 'divider',
          bgcolor: 'background.paper',
          color: 'text.secondary',
          '&:hover': { bgcolor: 'action.hover' },
        }}
      >
        {mode === 'light' ? <DarkModeIcon fontSize="small" /> : <LightModeIcon fontSize="small" />}
      </IconButton>
    </Tooltip>
  );

  return (
    <AppBar
      position="fixed"
      color="inherit"
      sx={{
        width: '100%',
        bgcolor: 'background.paper',
        borderBottom: '1px solid',
        borderColor: 'divider',
        boxShadow: 'none',
      }}
    >
      <Toolbar sx={{ px: { xs: 1.5, sm: 2.5 }, minHeight: { xs: 56, sm: 64 }, gap: 1 }}>
        <Box sx={{ display: { xs: 'none', sm: 'block' } }}>
          <AppLogo />
        </Box>
        <Box sx={{ display: { xs: 'block', sm: 'none' } }}>
          <AppLogo compact />
        </Box>

        <Box component="nav" sx={{ display: 'flex', alignItems: 'center', gap: 0.5, ml: { xs: 1, sm: 3 } }}>
          {NAV_TABS.map((tab) => {
            const isActive = location.pathname === tab.path;
            return (
              <Button
                key={tab.path}
                onClick={() => navigate(tab.path)}
                sx={{
                  borderRadius: 999,
                  textTransform: 'none',
                  fontSize: 13.5,
                  px: { xs: 1.25, sm: 1.75 },
                  py: 0.75,
                  minWidth: 0,
                  fontWeight: isActive ? 700 : 500,
                  color: isActive ? 'primary.dark' : 'text.secondary',
                  bgcolor: isActive ? 'primary.soft' : 'transparent',
                  '&:hover': { color: 'text.primary', bgcolor: isActive ? 'primary.soft' : 'action.hover' },
                }}
              >
                {tab.label}
              </Button>
            );
          })}
        </Box>

        <Box sx={{ flexGrow: 1 }} />
        {ThemeToggle}
        <BackendStatusIndicator />
        <AccountMenu />
      </Toolbar>
    </AppBar>
  );
}