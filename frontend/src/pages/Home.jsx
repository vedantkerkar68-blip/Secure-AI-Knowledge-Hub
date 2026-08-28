import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Typography,
  Skeleton,
  Dialog,
} from '@mui/material';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import AddBusinessIcon from '@mui/icons-material/AddBusiness';
import ForumIcon from '@mui/icons-material/Forum';
import { toast } from 'react-toastify';
import { useAuth } from '../context/AuthContext';
import { getDashboard } from '../services/dashboardService';
import KnowledgeBaseHero from '../components/admin/KnowledgeBaseHero';
import KpiTiles from '../components/admin/KpiRow';
import QuickActionsCard from '../components/dashboard/QuickActionsCard';
import WelcomeHero from '../components/dashboard/WelcomeHero';
import ActivityFeed from '../components/admin/ActivityFeed';
import Documents from './Documents';
import Departments from './Departments';
import Users from './Users';

const reveal = (delay) => ({
  animation: 'fadeSlideUp 0.45s cubic-bezier(0.22, 1, 0.36, 1) both',
  animationDelay: `${delay}ms`,
  '@keyframes fadeSlideUp': {
    from: { opacity: 0, transform: 'translateY(8px)' },
    to: { opacity: 1, transform: 'translateY(0)' },
  },
  '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
});

function SectionHeader({ title, subtitle }) {
  return (
    <Box sx={{ mb: 1.5 }}>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>{title}</Typography>
      {subtitle && (
        <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.25 }}>
          {subtitle}
        </Typography>
      )}
    </Box>
  );
}

export default function Home() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const role = user?.role || '';
  const isAdmin = role === 'ADMIN';

  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [statsForbidden, setStatsForbidden] = useState(false);
  const [usersOpen, setUsersOpen] = useState(false);

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

  const scrollTo = (id) => {
    const el = document.getElementById(id);
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const adminActions = [
    { label: 'Upload Document', icon: <CloudUploadIcon fontSize="small" />, onClick: () => scrollTo('documents-section') },
    { label: 'Add Employee', icon: <PersonAddIcon fontSize="small" />, onClick: () => setUsersOpen(true) },
    { label: 'Add Department', icon: <AddBusinessIcon fontSize="small" />, onClick: () => scrollTo('departments-section') },
    { label: 'New Chat', icon: <ForumIcon fontSize="small" />, onClick: () => navigate('/chat') },
  ];

  const nonAdminActions = [
    { label: 'Upload Document', icon: <CloudUploadIcon fontSize="small" />, onClick: () => scrollTo('documents-section') },
    { label: 'New Chat', icon: <ForumIcon fontSize="small" />, onClick: () => navigate('/chat') },
  ];

  const actions = isAdmin ? adminActions : nonAdminActions;
  const showStats = isAdmin && !statsForbidden;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Box sx={{ ...reveal(0) }}>
        <Typography variant="h4" sx={{ fontWeight: 700 }}>
          Home
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
          Welcome back{user?.firstName ? `, ${user.firstName}` : ''}. Here&rsquo;s what&rsquo;s happening.
        </Typography>
      </Box>

      <Box
        sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: { xs: '1fr', lg: 'repeat(12, 1fr)' },
        }}
      >
        {isAdmin ? (
          loading ? (
            <>
              <Box sx={{ gridColumn: { lg: 'span 8' } }}>
                <Skeleton variant="rounded" height={264} sx={{ borderRadius: 3 }} />
              </Box>
              <Box sx={{ gridColumn: { lg: 'span 4' } }}>
                <Skeleton variant="rounded" height={264} sx={{ borderRadius: 2 }} />
              </Box>
            </>
          ) : (
            <>
              <Box
                sx={{ gridColumn: { lg: 'span 8' }, cursor: 'pointer', ...reveal(40) }}
                onClick={() => scrollTo('documents-section')}
              >
                <KnowledgeBaseHero data={stats} />
              </Box>
              <Box sx={{ gridColumn: { lg: 'span 4' }, ...reveal(100) }}>
                <QuickActionsCard actions={actions} />
              </Box>
            </>
          )
        ) : (
          <>
            <Box sx={{ gridColumn: { lg: 'span 8' }, ...reveal(40) }}>
              <WelcomeHero
                user={user}
                onStartChat={() => navigate('/chat')}
                onUpload={() => scrollTo('documents-section')}
              />
            </Box>
            <Box sx={{ gridColumn: { lg: 'span 4' }, ...reveal(100) }}>
              <QuickActionsCard actions={actions} />
            </Box>
          </>
        )}
      </Box>

      {isAdmin && !loading && showStats && (
        <Box sx={{ ...reveal(160) }}>
          <KpiTiles data={stats} />
        </Box>
      )}

      <Box id="documents-section" sx={{ scrollMarginTop: 84 }}>
        <SectionHeader
          title="My Documents"
          subtitle={isAdmin ? undefined : 'Only documents approved for your department are shown here.'}
        />
        <Documents embedded />
      </Box>

      {isAdmin && (
        <Box id="departments-section" sx={{ scrollMarginTop: 84 }}>
          <SectionHeader title="Departments" subtitle="Organize documents into a department tree." />
          <Departments embedded />
        </Box>
      )}

      {isAdmin && (
        <Box>
          <SectionHeader title="Recent Activity" subtitle="Filter, review, and apply retention to the activity audit trail." />
          <ActivityFeed />
        </Box>
      )}

      <Dialog fullScreen open={usersOpen} onClose={() => setUsersOpen(false)}>
        <Box sx={{ p: { xs: 2, md: 4 }, maxWidth: 1100, mx: 'auto', width: '100%' }}>
          <Users onClose={() => setUsersOpen(false)} />
        </Box>
      </Dialog>
    </Box>
  );
}
