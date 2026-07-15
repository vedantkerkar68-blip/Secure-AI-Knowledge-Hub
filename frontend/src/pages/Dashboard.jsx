import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Typography, Grid, Paper, Chip, Button, Card, CardContent, Skeleton,
} from '@mui/material';
import { toast } from 'react-toastify';
import PeopleIcon from '@mui/icons-material/People';
import BusinessIcon from '@mui/icons-material/Business';
import DescriptionIcon from '@mui/icons-material/Description';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import ChatIcon from '@mui/icons-material/Chat';
import MessageIcon from '@mui/icons-material/Message';
import StorageIcon from '@mui/icons-material/Storage';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import AddBusinessIcon from '@mui/icons-material/AddBusiness';
import ForumIcon from '@mui/icons-material/Forum';
import HistoryIcon from '@mui/icons-material/History';
import { useAuth } from '../context/AuthContext';
import { getDashboard } from '../services/dashboardService';
import * as documentService from '../services/documentService';

function formatDate(iso) {
  if (!iso) return '-';
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
}

export default function Dashboard() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const role = user?.role || '';

  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [statsForbidden, setStatsForbidden] = useState(false);
  const [recentDocs, setRecentDocs] = useState([]);
  const [docsLoading, setDocsLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    getDashboard()
      .then((res) => { if (mounted) setStats(res.data); })
      .catch((err) => {
        if (mounted) {
          if (err.response?.status === 403) {
            setStatsForbidden(true);
          } else {
            toast.error('Failed to load dashboard stats');
          }
        }
      })
      .finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, []);

  useEffect(() => {
    documentService.getAll({ page: 0, size: 5, sort: 'id,desc' })
      .then((res) => setRecentDocs(res.data.content ?? []))
      .catch(() => {})
      .finally(() => setDocsLoading(false));
  }, []);

  const adminCards = [
    { label: 'Total Users', key: 'users', icon: <PeopleIcon />, color: '#1565c0' },
    { label: 'Total Departments', key: 'departments', icon: <BusinessIcon />, color: '#2e7d32' },
    { label: 'Total Documents', key: 'documents', icon: <DescriptionIcon />, color: '#ed6c02' },
    { label: 'Processed Documents', key: 'processedDocuments', icon: <CheckCircleIcon />, color: '#00897b' },
    { label: 'Failed Documents', key: 'failedDocuments', icon: <ErrorIcon />, color: '#d32f2f' },
    { label: 'Chat Sessions', key: 'chatSessions', icon: <ChatIcon />, color: '#9c27b0' },
    { label: 'Chat Messages', key: 'chatMessages', icon: <MessageIcon />, color: '#3949ab' },
    { label: 'Vector Documents', key: 'vectorDocuments', icon: <StorageIcon />, color: '#00838f' },
  ];

  const adminActions = [
    { label: 'Upload Document', icon: <CloudUploadIcon />, path: '/documents', color: '#1565c0' },
    { label: 'Add Employee', icon: <PersonAddIcon />, path: '/users', color: '#2e7d32' },
    { label: 'Add Department', icon: <AddBusinessIcon />, path: '/departments', color: '#ed6c02' },
    { label: 'New Chat', icon: <ForumIcon />, path: '/chat', color: '#9c27b0' },
  ];

  const nonAdminActions = [
    { label: 'Upload Document', icon: <CloudUploadIcon />, path: '/documents', color: '#1565c0' },
    { label: 'New Chat', icon: <ForumIcon />, path: '/chat', color: '#9c27b0' },
  ];

  const isAdmin = role === 'ADMIN';
  const actions = isAdmin ? adminActions : nonAdminActions;
  const showStats = isAdmin && !statsForbidden;

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 3 }}>Dashboard</Typography>

      {loading && showStats ? (
        <Grid container spacing={3}>
          {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => (
            <Grid item xs={12} sm={6} md={3} key={i}>
              <Skeleton variant="rounded" height={100} sx={{ borderRadius: 3 }} />
            </Grid>
          ))}
        </Grid>
      ) : showStats ? (
        <Grid container spacing={3} sx={{ mb: 4 }}>
          {adminCards.map((card) => (
            <Grid item xs={12} sm={6} md={3} key={card.key}>
              <Paper elevation={2} sx={{ p: 3, borderRadius: 3, display: 'flex', alignItems: 'center', gap: 2 }}>
                <Box sx={{ width: 52, height: 52, borderRadius: 2, bgcolor: `${card.color}15`, display: 'flex', alignItems: 'center', justifyContent: 'center', color: card.color }}>
                  {card.icon}
                </Box>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 700 }}>{stats ? stats[card.key]?.toLocaleString() : '0'}</Typography>
                  <Typography variant="body2" color="text.secondary">{card.label}</Typography>
                </Box>
              </Paper>
            </Grid>
          ))}
        </Grid>
      ) : null}

      <Typography variant="h5" sx={{ mb: 2 }}>Quick Actions</Typography>
      <Grid container spacing={2} sx={{ mb: 4 }}>
        {actions.map((action) => (
          <Grid item xs={6} sm={3} key={action.label}>
            <Card elevation={2} sx={{ borderRadius: 3, cursor: 'pointer', transition: '0.2s', '&:hover': { transform: 'translateY(-2px)', boxShadow: 4 } }}
              onClick={() => navigate(action.path)}>
              <CardContent sx={{ textAlign: 'center', py: 3 }}>
                <Box sx={{ color: action.color, mb: 1 }}>{action.icon}</Box>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>{action.label}</Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Typography variant="h5" sx={{ mb: 2 }}>Recent Documents</Typography>
          <Paper elevation={2} sx={{ borderRadius: 3, p: 2 }}>
            {docsLoading ? (
              <Box sx={{ p: 1 }}>{[1, 2, 3, 4, 5].map((i) => <Skeleton key={i} height={36} sx={{ mb: 1 }} />)}</Box>
            ) : recentDocs.length === 0 ? (
              <Box sx={{ textAlign: 'center', py: 4 }}>
                <DescriptionIcon sx={{ fontSize: 40, color: 'text.disabled', mb: 1 }} />
                <Typography variant="body2" color="text.secondary">No documents uploaded yet.</Typography>
                <Button size="small" variant="outlined" sx={{ mt: 1 }} onClick={() => navigate('/documents')}>Go to Documents</Button>
              </Box>
            ) : (
              recentDocs.map((doc) => (
                <Box key={doc.id} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', py: 1, borderBottom: '1px solid', borderColor: 'divider', '&:last-child': { borderBottom: 'none' } }}>
                  <Box sx={{ minWidth: 0, mr: 1 }}>
                    <Typography variant="body2" sx={{ fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{doc.title}</Typography>
                    <Typography variant="caption" color="text.secondary">{doc.department || 'General'} &mdash; {formatDate(doc.uploadedAt)}</Typography>
                  </Box>
                  <Chip label={doc.status} size="small" color={doc.status === 'READY' ? 'success' : 'default'} sx={{ flexShrink: 0 }} />
                </Box>
              ))
            )}
          </Paper>
        </Grid>
        <Grid item xs={12} md={6}>
          <Typography variant="h5" sx={{ mb: 2 }}>Recent Activity</Typography>
          <Paper elevation={2} sx={{ borderRadius: 3, p: 2 }}>
            <Box sx={{ textAlign: 'center', py: 4 }}>
              <HistoryIcon sx={{ fontSize: 40, color: 'text.disabled', mb: 1 }} />
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                View full activity log for detailed audit trail.
              </Typography>
              <Button variant="outlined" size="small" onClick={() => navigate('/activity-logs')}>View Activity Logs</Button>
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}
