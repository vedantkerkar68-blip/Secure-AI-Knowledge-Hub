import { Box, Typography, Paper, Avatar, Divider, Chip } from '@mui/material';
import { useAuth } from '../context/AuthContext';
import EmailIcon from '@mui/icons-material/Email';
import BadgeIcon from '@mui/icons-material/Badge';
import BusinessIcon from '@mui/icons-material/Business';

export default function Profile() {
  const { user } = useAuth();

  const fullName = user ? `${user.firstName} ${user.lastName}` : 'User';
  const initials = user?.firstName ? user.firstName.charAt(0).toUpperCase() : 'U';

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 3 }}>
        Profile
      </Typography>
      <Paper elevation={2} sx={{ borderRadius: 3, maxWidth: 600 }}>
        <Box sx={{ p: 4, textAlign: 'center' }}>
          <Avatar
            sx={{
              width: 80,
              height: 80,
              bgcolor: '#1565c0',
              fontSize: 32,
              mx: 'auto',
              mb: 2,
            }}
          >
            {initials}
          </Avatar>
          <Typography variant="h5">{fullName}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {user?.email || ''}
          </Typography>
          {user?.role && (
            <Chip
              label={user.role}
              size="small"
              color="primary"
              variant="outlined"
            />
          )}
        </Box>
        <Divider />
        <Box sx={{ p: 3 }}>
          <Typography variant="subtitle2" color="text.secondary" gutterBottom>
            Account Information
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
            <BadgeIcon fontSize="small" color="primary" />
            <Typography variant="body2">
              <strong>Name:</strong> {fullName}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
            <EmailIcon fontSize="small" color="primary" />
            <Typography variant="body2">
              <strong>Email:</strong> {user?.email || 'N/A'}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
            <BadgeIcon fontSize="small" color="primary" />
            <Typography variant="body2">
              <strong>Role:</strong> {user?.role || 'N/A'}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <BusinessIcon fontSize="small" color="primary" />
            <Typography variant="body2">
              <strong>Department:</strong> {user?.department || 'N/A'}
            </Typography>
          </Box>
        </Box>
      </Paper>
    </Box>
  );
}
