import { Box, Paper, Typography } from '@mui/material';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';

export default function QuickActionsCard({ actions }) {
  return (
    <Paper
      elevation={1}
      sx={{
        borderRadius: 2,
        p: 2.5,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1.5 }}>
        Quick Actions
      </Typography>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, flexGrow: 1 }}>
        {actions.map((action) => (
          <Box
            key={action.label}
            component="button"
            type="button"
            onClick={action.onClick}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1.5,
              p: 1.25,
              borderRadius: 1.5,
              bgcolor: 'transparent',
              border: 'none',
              textAlign: 'left',
              cursor: 'pointer',
              fontFamily: 'inherit',
              transition: 'background-color 150ms ease',
              '&:hover': { bgcolor: 'action.hover' },
            }}
          >
            <Box
              sx={{
                width: 36,
                height: 36,
                borderRadius: 1.25,
                bgcolor: 'primary.soft',
                color: 'primary.dark',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              {action.icon}
            </Box>
            <Typography variant="body2" sx={{ fontWeight: 600, flexGrow: 1 }}>
              {action.label}
            </Typography>
            <ChevronRightIcon fontSize="small" sx={{ color: 'text.disabled' }} />
          </Box>
        ))}
      </Box>
    </Paper>
  );
}