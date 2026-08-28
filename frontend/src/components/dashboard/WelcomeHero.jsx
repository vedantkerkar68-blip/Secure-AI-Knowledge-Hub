import { Box, Typography, Button } from '@mui/material';
import ForumIcon from '@mui/icons-material/Forum';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';

export default function WelcomeHero({ user, onStartChat, onUpload }) {
  return (
    <Box
      sx={{
        position: 'relative',
        overflow: 'hidden',
        borderRadius: 3,
        p: 4,
        minHeight: 264,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        bgcolor: 'sidebar.bg',
        color: 'sidebar.text',
        boxShadow: (theme) => theme.shadows[4],
      }}
    >
      <Box
        sx={{
          position: 'absolute',
          width: 260,
          height: 260,
          borderRadius: '50%',
          bgcolor: 'rgba(250,204,21,0.12)',
          filter: 'blur(60px)',
          top: -90,
          right: -40,
        }}
      />
      <Box sx={{ position: 'relative' }}>
        <Typography
          variant="body2"
          sx={{
            color: 'sidebar.muted',
            fontWeight: 600,
            textTransform: 'uppercase',
            letterSpacing: '0.08em',
            fontSize: 11,
            mb: 1,
          }}
        >
          Welcome back{user?.firstName ? `, ${user.firstName}` : ''}
        </Typography>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1, letterSpacing: '-0.02em' }}>
          Your knowledge,{' '}
          <Box component="span" sx={{ color: 'rgba(250,204,21,1)' }}>
            instantly accessible
          </Box>
          .
        </Typography>
        <Typography sx={{ color: 'sidebar.muted', mb: 3, maxWidth: 480 }}>
          Ask questions and get answers grounded in your organization&rsquo;s documents — securely, with full access
          control.
        </Typography>
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
          <Button
            variant="contained"
            startIcon={<ForumIcon />}
            onClick={onStartChat}
            sx={{ borderRadius: 999, px: 3 }}
          >
            Start a conversation
          </Button>
          <Button
            variant="outlined"
            startIcon={<CloudUploadIcon />}
            onClick={onUpload}
            sx={{
              borderRadius: 999,
              px: 3,
              color: '#fff',
              borderColor: 'rgba(255,255,255,0.3)',
              '&:hover': { borderColor: '#fff', bgcolor: 'rgba(255,255,255,0.06)' },
            }}
          >
            Upload a document
          </Button>
        </Box>
      </Box>
    </Box>
  );
}