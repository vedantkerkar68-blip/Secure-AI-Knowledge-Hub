import { useState, useEffect } from 'react';
import { Box, Typography, Grid, Paper, CircularProgress } from '@mui/material';
import { toast } from 'react-toastify';
import PeopleIcon from '@mui/icons-material/People';
import BusinessIcon from '@mui/icons-material/Business';
import DescriptionIcon from '@mui/icons-material/Description';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import ChatIcon from '@mui/icons-material/Chat';
import MessageIcon from '@mui/icons-material/Message';
import StorageIcon from '@mui/icons-material/Storage';
import { getDashboard } from '../services/dashboardService';

const cardConfig = [
  { label: 'Total Users', key: 'users', icon: <PeopleIcon />, color: '#1565c0' },
  { label: 'Total Departments', key: 'departments', icon: <BusinessIcon />, color: '#2e7d32' },
  { label: 'Total Documents', key: 'documents', icon: <DescriptionIcon />, color: '#ed6c02' },
  { label: 'Processed Documents', key: 'processedDocuments', icon: <CheckCircleIcon />, color: '#00897b' },
  { label: 'Failed Documents', key: 'failedDocuments', icon: <ErrorIcon />, color: '#d32f2f' },
  { label: 'Chat Sessions', key: 'chatSessions', icon: <ChatIcon />, color: '#9c27b0' },
  { label: 'Chat Messages', key: 'chatMessages', icon: <MessageIcon />, color: '#3949ab' },
  { label: 'Vector Documents', key: 'vectorDocuments', icon: <StorageIcon />, color: '#00838f' },
];

export default function Dashboard() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    const fetchDashboard = async () => {
      try {
        const response = await getDashboard();
        if (mounted) setStats(response.data);
      } catch {
        if (mounted) toast.error('Failed to load dashboard data');
      } finally {
        if (mounted) setLoading(false);
      }
    };
    fetchDashboard();
    return () => { mounted = false; };
  }, []);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 300 }}>
        <CircularProgress size={48} />
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 3 }}>Dashboard</Typography>
      <Grid container spacing={3}>
        {cardConfig.map((card) => (
          <Grid item xs={12} sm={6} md={3} key={card.key}>
            <Paper
              elevation={2}
              sx={{
                p: 3,
                borderRadius: 3,
                display: 'flex',
                alignItems: 'center',
                gap: 2,
              }}
            >
              <Box
                sx={{
                  width: 52,
                  height: 52,
                  borderRadius: 2,
                  bgcolor: `${card.color}15`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: card.color,
                }}
              >
                {card.icon}
              </Box>
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 700 }}>
                  {stats ? stats[card.key]?.toLocaleString() : '0'}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {card.label}
                </Typography>
              </Box>
            </Paper>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
}
