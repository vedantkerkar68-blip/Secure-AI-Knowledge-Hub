import { useLocation, useNavigate } from 'react-router-dom';
import {
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Divider,
  Typography,
  Box,
} from '@mui/material';
import DashboardIcon from '@mui/icons-material/Dashboard';
import PeopleIcon from '@mui/icons-material/People';
import BusinessIcon from '@mui/icons-material/Business';
import DescriptionIcon from '@mui/icons-material/Description';
import ChatIcon from '@mui/icons-material/Chat';
import HistoryIcon from '@mui/icons-material/History';
import PersonIcon from '@mui/icons-material/Person';
import { useAuth } from '../../context/AuthContext';

const drawerWidth = 260;

const allMenuItems = [
  { text: 'Dashboard', icon: <DashboardIcon />, path: '/dashboard', roles: ['ADMIN', 'MANAGER', 'EMPLOYEE'] },
  { text: 'Users', icon: <PeopleIcon />, path: '/users', roles: ['ADMIN'] },
  { text: 'Departments', icon: <BusinessIcon />, path: '/departments', roles: ['ADMIN'] },
  { text: 'Documents', icon: <DescriptionIcon />, path: '/documents', roles: ['ADMIN', 'MANAGER', 'EMPLOYEE'] },
  { text: 'Chat', icon: <ChatIcon />, path: '/chat', roles: ['ADMIN', 'MANAGER', 'EMPLOYEE'] },
  { text: 'Activity Logs', icon: <HistoryIcon />, path: '/activity-logs', roles: ['ADMIN'] },
  { text: 'Profile', icon: <PersonIcon />, path: '/profile', roles: ['ADMIN', 'MANAGER', 'EMPLOYEE'] },
];

export default function Sidebar() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useAuth();
  const role = user?.role || '';

  const menuItems = allMenuItems.filter((item) => item.roles.includes(role));

  return (
    <Drawer
      variant="permanent"
      sx={{
        width: drawerWidth,
        flexShrink: 0,
        '& .MuiDrawer-paper': {
          width: drawerWidth,
          boxSizing: 'border-box',
          bgcolor: '#0d47a1',
          color: '#ffffff',
        },
      }}
    >
      <Toolbar>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Box
            sx={{
              width: 32,
              height: 32,
              borderRadius: 1,
              bgcolor: '#ffffff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 700,
              color: '#0d47a1',
              fontSize: 16,
            }}
          >
            S
          </Box>
          <Typography variant="h6" noWrap sx={{ fontWeight: 700, fontSize: 18 }}>
            SAKH
          </Typography>
        </Box>
      </Toolbar>
      <Divider sx={{ borderColor: 'rgba(255,255,255,0.12)' }} />
      <List sx={{ px: 1, mt: 1 }}>
        {menuItems.map((item) => {
          const isActive = location.pathname === item.path;
          return (
            <ListItem key={item.text} disablePadding sx={{ mb: 0.5 }}>
              <ListItemButton
                onClick={() => navigate(item.path)}
                sx={{
                  borderRadius: 2,
                  bgcolor: isActive ? 'rgba(255,255,255,0.15)' : 'transparent',
                  '&:hover': { bgcolor: 'rgba(255,255,255,0.10)' },
                }}
              >
                <ListItemIcon
                  sx={{
                    color: isActive ? '#ffffff' : 'rgba(255,255,255,0.7)',
                    minWidth: 40,
                  }}
                >
                  {item.icon}
                </ListItemIcon>
                <ListItemText
                  primary={item.text}
                  sx={{
                    '& .MuiListItemText-primary': {
                      fontSize: 14,
                      fontWeight: isActive ? 600 : 400,
                      color: isActive ? '#ffffff' : 'rgba(255,255,255,0.85)',
                    },
                  }}
                />
              </ListItemButton>
            </ListItem>
          );
        })}
      </List>
    </Drawer>
  );
}
