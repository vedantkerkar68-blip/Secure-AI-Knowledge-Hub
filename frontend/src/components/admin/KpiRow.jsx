import { useNavigate } from 'react-router-dom';
import { Paper, Typography, Box } from '@mui/material';
import PeopleIcon from '@mui/icons-material/People';
import BusinessIcon from '@mui/icons-material/Business';
import ForumIcon from '@mui/icons-material/Forum';
import ChatIcon from '@mui/icons-material/Chat';
import useCountUp from '../../hooks/useCountUp';

const formatInteger = (v) => Math.round(v).toLocaleString('en-US');

function KpiTile({ icon, label, value, sub, path, onClick }) {
  const animated = useCountUp(value, { duration: 600 });
  const clickable = Boolean(path);

  return (
    <Paper
      elevation={1}
      onClick={onClick}
      sx={{
        borderRadius: 2,
        p: 2.5,
        display: 'flex',
        flexDirection: 'column',
        gap: 1.5,
        minWidth: 0,
        cursor: clickable ? 'pointer' : 'default',
        transition: 'transform 150ms ease, box-shadow 150ms ease',
        '&:hover': {
          transform: clickable ? 'translateY(-2px)' : 'none',
          boxShadow: (theme) => (clickable ? theme.shadows[4] : undefined),
        },
      }}
    >
      <Box
        sx={{
          width: 38,
          height: 38,
          borderRadius: 1.25,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: 'primary.soft',
          color: 'primary.dark',
        }}
      >
        {icon}
      </Box>
      <Box>
        <Typography sx={{ fontSize: 28, fontWeight: 800, lineHeight: 1.1, fontVariantNumeric: 'tabular-nums' }}>
          {formatInteger(animated)}
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary', fontWeight: 600, mt: 0.5 }}>
          {label}
        </Typography>
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
          {sub}
        </Typography>
      </Box>
    </Paper>
  );
}

export default function KpiTiles({ data, withNavigation = false }) {
  const navigate = useNavigate();

  const tiles = [
    { icon: <PeopleIcon fontSize="small" />, label: 'Users', value: data?.users ?? 0, sub: 'registered accounts', path: '/users' },
    { icon: <BusinessIcon fontSize="small" />, label: 'Departments', value: data?.departments ?? 0, sub: 'organizational units', path: '/departments' },
    { icon: <ForumIcon fontSize="small" />, label: 'Chat Sessions', value: data?.chatSessions ?? 0, sub: 'conversations', path: '/chat' },
    { icon: <ChatIcon fontSize="small" />, label: 'Chat Messages', value: data?.chatMessages ?? 0, sub: 'AI responses generated', path: '/chat' },
  ];

  return (
    <Box
      sx={{
        display: 'grid',
        gap: 2,
        gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' },
      }}
    >
      {tiles.map((tile) => (
        <KpiTile
          key={tile.label}
          {...tile}
          onClick={withNavigation ? () => navigate(tile.path) : undefined}
        />
      ))}
    </Box>
  );
}