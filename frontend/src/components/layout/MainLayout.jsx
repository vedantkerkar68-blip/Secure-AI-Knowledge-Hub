import { Outlet, useLocation } from 'react-router-dom';
import { Box, Toolbar } from '@mui/material';
import Navbar from './Navbar';

export default function MainLayout() {
  const location = useLocation();
  const isChat = location.pathname === '/chat';

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <Navbar />
      <Box component="main" sx={{ flexGrow: 1 }}>
        <Toolbar />
        <Box
          sx={{
            p: isChat ? 0 : { xs: 2, md: 3 },
            maxWidth: isChat ? 'none' : 1440,
            mx: 'auto',
            width: '100%',
          }}
        >
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}