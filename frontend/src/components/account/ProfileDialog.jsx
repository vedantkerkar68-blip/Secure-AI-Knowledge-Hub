import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Avatar,
  Typography,
  Chip,
  Divider,
} from '@mui/material';
import EmailIcon from '@mui/icons-material/Email';
import BadgeIcon from '@mui/icons-material/Badge';
import BusinessIcon from '@mui/icons-material/Business';
import { useAuth } from '../../context/AuthContext';

const STATUS_COLORS = { ACTIVE: 'success', INACTIVE: 'default', LOCKED: 'error' };

export default function ProfileDialog({ open, onClose }) {
  const { user } = useAuth();

  const fullName = user ? `${user.firstName} ${user.lastName}` : 'User';
  const initials = user?.firstName ? `${user.firstName.charAt(0)}${user.lastName?.charAt(0) || ''}`.toUpperCase() : 'U';

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Profile</DialogTitle>
      <DialogContent>
        <Box sx={{ textAlign: 'center', py: 1 }}>
          <Avatar sx={{ width: 72, height: 72, bgcolor: 'primary.main', fontSize: 26, mx: 'auto', mb: 1.5 }}>
            {initials}
          </Avatar>
          <Typography variant="h6">{fullName}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {user?.email || ''}
          </Typography>
          <Box sx={{ display: 'flex', justifyContent: 'center', gap: 1 }}>
            {user?.role && <Chip label={user.role} size="small" color="primary" variant="outlined" />}
            {user?.status && <Chip label={user.status} size="small" color={STATUS_COLORS[user.status] || 'default'} />}
          </Box>
        </Box>
        <Divider sx={{ my: 2 }} />
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <EmailIcon fontSize="small" color="primary" />
            <Typography variant="body2">
              <strong>Email:</strong> {user?.email || '—'}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <BadgeIcon fontSize="small" color="primary" />
            <Typography variant="body2">
              <strong>Role:</strong> {user?.role || '—'}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <BusinessIcon fontSize="small" color="primary" />
            <Typography variant="body2">
              <strong>Department:</strong> {user?.department || '—'}
            </Typography>
          </Box>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}