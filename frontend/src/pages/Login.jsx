import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  Alert,
  CircularProgress,
} from '@mui/material';
import { toast } from 'react-toastify';
import { useAuth } from '../context/AuthContext';
import { useBackendHealth, HEALTH_STATUS } from '../context/BackendHealthContext';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import SecurityOutlinedIcon from '@mui/icons-material/SecurityOutlined';
import SearchOutlinedIcon from '@mui/icons-material/SearchOutlined';

const BRAND_PILLS = [
  { icon: <SecurityOutlinedIcon sx={{ fontSize: 16 }} />, label: 'Access controlled' },
  { icon: <SearchOutlinedIcon sx={{ fontSize: 16 }} />, label: 'Smart search' },
  { icon: <LockOutlinedIcon sx={{ fontSize: 16 }} />, label: 'Your data stays private' },
];

export default function Login() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const { status, retry } = useBackendHealth();
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const backendDown = status === HEALTH_STATUS.UNAVAILABLE;
  const backendConnecting = status === HEALTH_STATUS.CONNECTING;

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!form.email || !form.password) {
      setError('Please fill in all fields');
      return;
    }

    if (backendDown) {
      setError('The knowledge service is unreachable right now. Please try again shortly.');
      return;
    }

    setSubmitting(true);
    try {
      await login({ email: form.email, password: form.password });
      toast.success('Login successful');
      navigate('/dashboard');
    } catch (err) {
      const message =
        err.response?.data?.message ||
        err.response?.data?.error ||
        'Invalid email or password. Please try again.';
      setError(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', bgcolor: 'background.default' }}>
      <Box
        sx={{
          position: 'relative',
          overflow: 'hidden',
          width: '46%',
          display: { xs: 'none', md: 'flex' },
          flexDirection: 'column',
          justifyContent: 'space-between',
          p: 6,
          bgcolor: 'sidebar.bg',
          color: 'sidebar.text',
        }}
      >
        <Box
          sx={{
            position: 'absolute',
            width: 320,
            height: 320,
            borderRadius: '50%',
            bgcolor: 'rgba(250,204,21,0.10)',
            filter: 'blur(70px)',
            top: -60,
            right: -80,
          }}
        />
        <Box
          sx={{
            position: 'absolute',
            width: 260,
            height: 260,
            borderRadius: '50%',
            bgcolor: 'rgba(250,204,21,0.08)',
            filter: 'blur(70px)',
            bottom: 40,
            left: -60,
          }}
        />

        <Box sx={{ position: 'relative', display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 44,
              height: 44,
              borderRadius: 14,
              bgcolor: 'primary.main',
              color: 'primary.contrastText',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 800,
              fontSize: 18,
              boxShadow: '0 4px 14px rgba(250,204,21,0.35)',
            }}
          >
            S
          </Box>
          <Box>
            <Typography sx={{ fontWeight: 700, lineHeight: 1.15 }}>
              Secure AI Knowledge Hub
            </Typography>
            <Typography variant="caption" sx={{ color: 'sidebar.muted' }}>
              Company knowledge platform
            </Typography>
          </Box>
        </Box>

        <Box sx={{ position: 'relative', mb: 2 }}>
          <Typography variant="h3" sx={{ fontWeight: 700, letterSpacing: '-0.03em', mb: 2 }}>
            Answers grounded in{' '}
            <Box component="span" sx={{ color: 'rgba(250,204,21,1)' }}>
              your documents
            </Box>
            .
          </Typography>
          <Typography sx={{ color: 'sidebar.muted', maxWidth: 420, mb: 4 }}>
            Securely search, ask, and retrieve knowledge across your organization with full access
            control.
          </Typography>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            {BRAND_PILLS.map((pill) => (
              <Box
                key={pill.label}
                sx={{
                  px: 1.5,
                  py: 0.75,
                  borderRadius: 999,
                  bgcolor: 'sidebar.soft',
                  border: '1px solid',
                  borderColor: 'sidebar.border',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                  color: 'rgba(250,204,21,1)',
                }}
              >
                {pill.icon}
                <Typography variant="caption" sx={{ fontWeight: 600, color: 'sidebar.text' }}>
                  {pill.label}
                </Typography>
              </Box>
            ))}
          </Box>
        </Box>

        <Typography variant="caption" sx={{ color: 'sidebar.muted', position: 'relative' }}>
          &copy; {new Date().getFullYear()} Secure AI Knowledge Hub
        </Typography>
      </Box>

      <Box
        sx={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          p: 3,
        }}
      >
        <Paper elevation={1} sx={{ p: 4, borderRadius: 3, width: '100%', maxWidth: 400 }}>
          <Box sx={{ textAlign: 'center', mb: 3 }}>
            <Box
              sx={{
                width: 52,
                height: 52,
                borderRadius: 16,
                bgcolor: 'primary.main',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                mx: 'auto',
                mb: 2,
                color: 'primary.contrastText',
                fontWeight: 700,
                fontSize: 22,
                boxShadow: '0 4px 14px rgba(250,204,21,0.35)',
              }}
            >
              S
            </Box>
            <Typography variant="h5" sx={{ fontWeight: 700 }}>
              Welcome back
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              Sign in to your account
            </Typography>
          </Box>

          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

          {backendDown && (
            <Alert severity="warning" sx={{ mb: 2 }} action={
              <Button color="inherit" size="small" onClick={retry}>Retry</Button>
            }>
              Cannot reach the knowledge service. The app will retry automatically.
            </Alert>
          )}

          {backendConnecting && !backendDown && (
            <Alert severity="info" sx={{ mb: 2 }} icon={<CircularProgress size={14} />}>
              Checking connection to the knowledge service...
            </Alert>
          )}

          <Box component="form" onSubmit={handleSubmit}>
            <TextField
              fullWidth
              label="Email"
              name="email"
              type="email"
              value={form.email}
              onChange={handleChange}
              margin="normal"
              required
              disabled={submitting}
            />
            <TextField
              fullWidth
              label="Password"
              name="password"
              type="password"
              value={form.password}
              onChange={handleChange}
              margin="normal"
              required
              disabled={submitting}
            />
            <Button
              type="submit"
              fullWidth
              variant="contained"
              size="large"
              disabled={submitting}
              sx={{ mt: 3, py: 1.2, borderRadius: 999 }}
            >
              {submitting ? <CircularProgress size={24} color="inherit" /> : 'Sign In'}
            </Button>
          </Box>
        </Paper>
      </Box>
    </Box>
  );
}