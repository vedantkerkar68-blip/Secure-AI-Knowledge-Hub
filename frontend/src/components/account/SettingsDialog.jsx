import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Switch,
  Divider,
  Typography,
  Box,
} from '@mui/material';
import NotificationsIcon from '@mui/icons-material/Notifications';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import TuneIcon from '@mui/icons-material/Tune';
import { useSettings } from '../../context/SettingsContext';

export default function SettingsDialog({ open, onClose }) {
  const { toastsEnabled, setToastsEnabled, outageAlertsEnabled, setOutageAlertsEnabled } = useSettings();

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Settings</DialogTitle>
      <DialogContent>
        <Typography
          variant="overline"
          sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'text.secondary' }}
        >
          <TuneIcon fontSize="small" />
          Notifications
        </Typography>
        <List disablePadding sx={{ mt: 0.5 }}>
          <ListItem sx={{ px: 0 }}>
            <ListItemIcon>
              <NotificationsIcon color="primary" />
            </ListItemIcon>
            <ListItemText
              primary="Toast notifications"
              secondary="Show success and error messages for actions"
              slotProps={{ secondary: { fontSize: '0.8rem' } }}
            />
            <Switch
              checked={toastsEnabled}
              onChange={(e) => setToastsEnabled(e.target.checked)}
              inputProps={{ 'aria-label': 'Toast notifications' }}
            />
          </ListItem>
          <Divider component="li" />
          <ListItem sx={{ px: 0 }}>
            <ListItemIcon>
              <NotificationsActiveIcon color="primary" />
            </ListItemIcon>
            <ListItemText
              primary="Backend outage alerts"
              secondary="Notify when the knowledge service becomes unreachable"
              slotProps={{ secondary: { fontSize: '0.8rem' } }}
            />
            <Switch
              checked={outageAlertsEnabled}
              onChange={(e) => setOutageAlertsEnabled(e.target.checked)}
              inputProps={{ 'aria-label': 'Backend outage alerts' }}
            />
          </ListItem>
        </List>
        <Box sx={{ mt: 2 }}>
          <Typography variant="caption" color="text.secondary">
            Preferences are stored locally in your browser.
          </Typography>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Done</Button>
      </DialogActions>
    </Dialog>
  );
}