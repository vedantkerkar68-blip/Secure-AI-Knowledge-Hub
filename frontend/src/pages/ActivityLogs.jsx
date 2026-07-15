import { Box, Typography, Paper } from '@mui/material';
import HistoryIcon from '@mui/icons-material/History';

export default function ActivityLogs() {
  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 3 }}>
        Activity Logs
      </Typography>
      <Paper elevation={2} sx={{ p: 4, borderRadius: 3, textAlign: 'center' }}>
        <HistoryIcon sx={{ fontSize: 64, color: '#1565c0', mb: 2 }} />
        <Typography variant="h6" color="text.secondary">
          Activity logs will be available here
        </Typography>
      </Paper>
    </Box>
  );
}
