import { Box, Typography, Button } from '@mui/material';
import { useNavigate } from 'react-router-dom';

export default function NotFound() {
  const navigate = useNavigate();

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: '#f5f7fa',
      }}
    >
      <Typography variant="h1" sx={{ fontWeight: 700, color: '#1565c0', mb: 1 }}>
        404
      </Typography>
      <Typography variant="h5" sx={{ mb: 1, color: '#333' }}>
        Page Not Found
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
        The page you are looking for does not exist.
      </Typography>
      <Button variant="contained" onClick={() => navigate('/dashboard')}>
        Go to Dashboard
      </Button>
    </Box>
  );
}
