import { AppBar, Toolbar, Typography, Box, IconButton, Avatar, Menu, MenuItem, Divider } from '@mui/material';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import LogoutIcon from '@mui/icons-material/Logout';
import PersonIcon from '@mui/icons-material/Person';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [anchorEl, setAnchorEl] = useState(null);

  const fullName = user ? `${user.firstName} ${user.lastName}` : 'User';
  const initials = user?.firstName ? user.firstName.charAt(0).toUpperCase() : 'U';

  const handleMenu = (event) => setAnchorEl(event.currentTarget);
  const handleClose = () => setAnchorEl(null);

  const handleLogout = () => {
    handleClose();
    logout();
    navigate('/login');
  };

  const handleProfile = () => {
    handleClose();
    navigate('/profile');
  };

  return (
    <AppBar
      position="fixed"
      sx={{
        width: `calc(100% - 260px)`,
        ml: '260px',
        bgcolor: '#ffffff',
        color: '#333333',
      }}
    >
      <Toolbar>
        <Typography variant="h6" noWrap sx={{ flexGrow: 1, fontWeight: 600, color: '#1565c0' }}>
          Secure AI Knowledge Hub
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="body2" sx={{ color: '#666' }}>
            {fullName}
          </Typography>
          <IconButton onClick={handleMenu} size="small">
            <Avatar sx={{ width: 34, height: 34, bgcolor: '#1565c0', fontSize: 14 }}>
              {initials}
            </Avatar>
          </IconButton>
          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={handleClose}
            transformOrigin={{ horizontal: 'right', vertical: 'top' }}
            anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
            slotProps={{ paper: { sx: { minWidth: 200, borderRadius: 2, mt: 0.5 } } }}
          >
            <Box sx={{ px: 2, py: 1.5 }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                {fullName}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {user?.email || ''}
              </Typography>
              {user?.role && (
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                  {user.role}{user.department ? ` — ${user.department}` : ''}
                </Typography>
              )}
            </Box>
            <Divider />
            <MenuItem onClick={handleProfile}>
              <PersonIcon sx={{ mr: 1.5, fontSize: 20 }} /> Profile
            </MenuItem>
            <MenuItem onClick={handleLogout}>
              <LogoutIcon sx={{ mr: 1.5, fontSize: 20 }} /> Logout
            </MenuItem>
          </Menu>
        </Box>
      </Toolbar>
    </AppBar>
  );
}
