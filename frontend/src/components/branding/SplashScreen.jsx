import { Box, Typography, Button, Chip, CircularProgress } from '@mui/material';
import AppLogo from './AppLogo';
import { useBackendHealth, HEALTH_STATUS } from '../../context/BackendHealthContext';

const FEATURES = [
  'Secure, role-based access',
  'Answers backed by your documents',
  'Persistent chat history',
  'Documents stay within their department',
];

function ConnectionChip() {
  const { status, retry } = useBackendHealth();

  if (status === HEALTH_STATUS.READY) {
    return (
      <Chip
        icon={<span style={{ color: 'success.main', fontSize: 12 }}>●</span>}
        label="Knowledge Hub ready"
        size="small"
        sx={{ bgcolor: 'success.soft', color: 'success.dark', border: '1px solid', borderColor: 'success.light' }}
      />
    );
  }

  if (status === HEALTH_STATUS.UNAVAILABLE) {
    return (
      <Chip
        icon={<span style={{ color: 'error.main', fontSize: 12 }}>●</span>}
        label="Knowledge service is temporarily unavailable"
        size="small"
        sx={{ bgcolor: 'error.soft', color: 'error.dark', border: '1px solid', borderColor: 'error.light' }}
      />
    );
  }

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <CircularProgress size={14} thickness={5} />
      <Typography variant="caption">Connecting to Knowledge Hub...</Typography>
    </Box>
  );
}

export default function SplashScreen() {
  const { status, retry } = useBackendHealth();

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'background.default',
        px: 2,
      }}
    >
      <Box sx={{ textAlign: 'center', maxWidth: 460 }}>
        <Box sx={{ display: 'flex', justifyContent: 'center', mb: 3 }}>
          <AppLogo size={64} showWordmark={false} />
        </Box>
        <Typography variant="h4" sx={{ mb: 1 }}>
          Secure AI Knowledge Hub
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Securely store, search, and query your organization's knowledge with AI.
        </Typography>

        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: 1,
            mb: 3,
            textAlign: 'left',
          }}
        >
          {FEATURES.map((feature) => (
            <Box
              key={feature}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
                px: 1.5,
                py: 1,
                bgcolor: 'background.paper',
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 2,
              }}
            >
              <Typography sx={{ color: 'success.main', fontSize: 14 }}>✓</Typography>
              <Typography variant="caption">{feature}</Typography>
            </Box>
          ))}
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 1.5, flexWrap: 'wrap' }}>
          <ConnectionChip />
          {status === HEALTH_STATUS.UNAVAILABLE && (
            <Button size="small" variant="outlined" onClick={retry}>
              Retry
            </Button>
          )}
        </Box>
        {status === HEALTH_STATUS.CONNECTING && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
            The first load can take up to a minute while the knowledge service wakes up.
          </Typography>
        )}
        {status === HEALTH_STATUS.UNAVAILABLE && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
            Still unreachable? The service may be cold-starting. We keep retrying automatically.
          </Typography>
        )}
      </Box>
    </Box>
  );
}