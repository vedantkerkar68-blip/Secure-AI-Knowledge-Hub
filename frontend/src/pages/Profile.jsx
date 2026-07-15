import { useState } from 'react';
import {
  Box, Typography, Paper, Avatar, Divider, Chip, Button, TextField, CircularProgress,
} from '@mui/material';
import { toast } from 'react-toastify';
import { useAuth } from '../context/AuthContext';
import * as userService from '../services/userService';
import EmailIcon from '@mui/icons-material/Email';
import BadgeIcon from '@mui/icons-material/Badge';
import BusinessIcon from '@mui/icons-material/Business';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import EditIcon from '@mui/icons-material/Edit';
import SaveIcon from '@mui/icons-material/Save';
import CloseIcon from '@mui/icons-material/Close';

function formatDate(iso) {
  if (!iso) return 'N/A';
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });
}

export default function Profile() {
  const { user, login: reLogin } = useAuth();
  const [editing, setEditing] = useState(false);
  const [firstName, setFirstName] = useState(user?.firstName || '');
  const [lastName, setLastName] = useState(user?.lastName || '');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const fullName = user ? `${user.firstName} ${user.lastName}` : 'User';
  const initials = user?.firstName ? user.firstName.charAt(0).toUpperCase() : 'U';

  const handleCancel = () => {
    setEditing(false);
    setFirstName(user?.firstName || '');
    setLastName(user?.lastName || '');
    setPassword('');
  };

  const handleSave = async () => {
    if (!firstName.trim() || !lastName.trim()) {
      toast.error('First name and last name are required');
      return;
    }
    setSubmitting(true);
    try {
      await userService.update(user.id, {
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        ...(password ? { password } : {}),
      });
      toast.success('Profile updated');
      setEditing(false);
      setPassword('');
      // Refresh user data via re-login is not possible; just update locally
      // The AuthContext user will be stale until next login
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update profile');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 3 }}>Profile</Typography>
      <Paper elevation={2} sx={{ borderRadius: 3, maxWidth: 600 }}>
        <Box sx={{ p: 4, textAlign: 'center' }}>
          <Avatar sx={{ width: 80, height: 80, bgcolor: '#1565c0', fontSize: 32, mx: 'auto', mb: 2 }}>
            {initials}
          </Avatar>
          <Typography variant="h5">{fullName}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{user?.email || ''}</Typography>
          {user?.role && <Chip label={user.role} size="small" color="primary" variant="outlined" />}
        </Box>
        <Divider />
        <Box sx={{ p: 3 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
            <Typography variant="subtitle2" color="text.secondary">Account Information</Typography>
            {!editing ? (
              <Button size="small" startIcon={<EditIcon />} onClick={() => setEditing(true)}>Edit</Button>
            ) : (
              <Box sx={{ display: 'flex', gap: 1 }}>
                <Button size="small" variant="outlined" startIcon={<CloseIcon />} onClick={handleCancel} disabled={submitting}>Cancel</Button>
                <Button size="small" variant="contained" startIcon={submitting ? <CircularProgress size={16} /> : <SaveIcon />}
                  onClick={handleSave} disabled={submitting}>Save</Button>
              </Box>
            )}
          </Box>

          {editing ? (
            <Box component="form" sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              <Box sx={{ display: 'flex', gap: 2 }}>
                <TextField fullWidth label="First Name" size="small" value={firstName}
                  onChange={(e) => setFirstName(e.target.value)} disabled={submitting} />
                <TextField fullWidth label="Last Name" size="small" value={lastName}
                  onChange={(e) => setLastName(e.target.value)} disabled={submitting} />
              </Box>
              <TextField fullWidth label="Email" size="small" value={user?.email || ''} disabled />
              <TextField fullWidth label="New Password (leave blank to keep current)" type="password" size="small"
                value={password} onChange={(e) => setPassword(e.target.value)} disabled={submitting} />
            </Box>
          ) : (
            <>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
                <BadgeIcon fontSize="small" color="primary" />
                <Typography variant="body2" sx={{ wordBreak: 'break-word' }}><strong>Name:</strong> {fullName}</Typography>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
                <EmailIcon fontSize="small" color="primary" />
                <Typography variant="body2" sx={{ wordBreak: 'break-word' }}><strong>Email:</strong> {user?.email || 'N/A'}</Typography>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
                <BadgeIcon fontSize="small" color="primary" />
                <Typography variant="body2"><strong>Role:</strong> {user?.role || 'N/A'}</Typography>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
                <BusinessIcon fontSize="small" color="primary" />
                <Typography variant="body2"><strong>Department:</strong> {user?.department || 'N/A'}</Typography>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <CalendarTodayIcon fontSize="small" color="primary" />
                <Typography variant="body2"><strong>Member since:</strong> {formatDate(user?.createdAt)}</Typography>
              </Box>
            </>
          )}
        </Box>
      </Paper>
    </Box>
  );
}
