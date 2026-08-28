import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';

const MARK_SIZE = 36;

export default function AppLogo({ size = MARK_SIZE, showWordmark = true, variant = 'dark', compact = false }) {
  const theme = useTheme();
  const mark = Math.max(size, 28);
  const wordColor = variant === 'light' ? '#ffffff' : theme.palette.text.primary;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: compact ? 0.75 : 1.5 }}>
      <Box
        aria-hidden
        sx={{
          width: mark,
          height: mark,
          borderRadius: compact ? 1 : 1.5,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontWeight: 800,
          fontSize: mark * 0.46,
          color: theme.palette.primary.contrastText,
          letterSpacing: 0,
          background: `linear-gradient(135deg, ${theme.palette.primary.main} 0%, ${theme.palette.primary.dark} 100%)`,
          boxShadow: '0 2px 8px rgba(250,204,21,0.4)',
        }}
      >
        S
      </Box>
      {showWordmark && (
        <Box sx={{ lineHeight: 1, color: wordColor }}>
          <Box
            component="span"
            sx={{
              display: 'block',
              fontWeight: 700,
              fontSize: compact ? 15 : 18,
              letterSpacing: '-0.01em',
              color: 'inherit',
            }}
          >
            Secure AI
          </Box>
          {!compact && (
            <Box
              component="span"
              sx={{
                display: 'block',
                fontSize: 10.5,
                fontWeight: 500,
                letterSpacing: '0.08em',
                textTransform: 'uppercase',
                opacity: 0.7,
                mt: 0.25,
                color: 'inherit',
              }}
            >
              Knowledge Hub
            </Box>
          )}
        </Box>
      )}
    </Box>
  );
}