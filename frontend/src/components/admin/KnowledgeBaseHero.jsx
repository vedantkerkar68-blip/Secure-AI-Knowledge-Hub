import { Box, Typography, CircularProgress } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import useCountUp from '../../hooks/useCountUp';

function StatusLegend({ items }) {
  return (
    <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
      {items.map((item) => (
        <Box key={item.label} sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
          <Box sx={{ width: 8, height: 8, borderRadius: 999, bgcolor: item.color }} />
          <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.7)' }}>
            {item.label} <strong style={{ color: '#ffffff' }}>{item.count}</strong>
          </Typography>
        </Box>
      ))}
    </Box>
  );
}

function SegmentedBar({ segments }) {
  return (
    <Box
      sx={{
        display: 'flex',
        gap: 0.5,
        height: 10,
        borderRadius: 999,
        overflow: 'hidden',
        bgcolor: 'rgba(255,255,255,0.08)',
      }}
    >
      {segments.map((seg) =>
        seg.percent > 0 ? (
          <Box
            key={seg.label}
            sx={{ width: `${seg.percent}%`, bgcolor: seg.color, borderRadius: 999, minWidth: 4 }}
          />
        ) : null
      )}
    </Box>
  );
}

export default function KnowledgeBaseHero({ data }) {
  const theme = useTheme();
  const total = data?.documents ?? 0;
  const ready = data?.processedDocuments ?? 0;
  const failed = data?.failedDocuments ?? 0;
  const pending = Math.max(total - ready - failed, 0);

  const readyPct = total > 0 ? Math.round((ready / total) * 100) : 0;
  const failedPct = total > 0 ? Math.round((failed / total) * 100) : 0;
  const pendingPct = total > 0 ? Math.max(100 - readyPct - failedPct, 0) : 0;

  const totalCount = useCountUp(total, { duration: 600 });
  const readyCount = useCountUp(ready, { duration: 600 });

  const ringPct = readyPct;
  const gold = theme.palette.primary.main;

  return (
    <Box
      sx={{
        borderRadius: 3,
        p: { xs: 3, md: 4 },
        bgcolor: 'secondary.main',
        color: '#ffffff',
        display: 'flex',
        flexDirection: 'column',
        gap: 3,
        minHeight: 264,
        boxShadow: (t) => t.shadows[4],
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 3, flexWrap: 'wrap' }}>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="overline" sx={{ color: 'rgba(255,255,255,0.55)', mb: 0.5, display: 'block' }}>
            Knowledge Base
          </Typography>
          <Typography sx={{ fontSize: 44, fontWeight: 800, lineHeight: 1.1, fontVariantNumeric: 'tabular-nums' }}>
            {totalCount.toLocaleString('en-US')}
          </Typography>
          <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.7)', mt: 0.5 }}>
            {readyCount.toLocaleString('en-US')} ready · {failed} need attention
          </Typography>
        </Box>

        <Box sx={{ position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <CircularProgress
            variant="determinate"
            value={ringPct}
            size={128}
            thickness={3}
            sx={{
              color: gold,
              '& .MuiCircularProgress-circle': { strokeLinecap: 'round' },
              '& .MuiCircularProgress-svg': { filter: `drop-shadow(0 0 6px ${gold}55)` },
            }}
          />
          <Box sx={{ position: 'absolute', textAlign: 'center' }}>
            <Typography sx={{ fontSize: 26, fontWeight: 800, lineHeight: 1 }}>{ringPct}%</Typography>
            <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.6)', display: 'block', mt: 0.25 }}>
              ready
            </Typography>
          </Box>
        </Box>
      </Box>

      <SegmentedBar
        segments={[
          { label: 'Ready', percent: readyPct, color: gold },
          { label: 'Pending', percent: pendingPct, color: 'rgba(255,255,255,0.25)' },
          { label: 'Failed', percent: failedPct, color: theme.palette.error.main },
        ]}
      />

      <StatusLegend
        items={[
          { label: 'Ready', count: ready, color: gold },
          { label: 'Pending', count: pending, color: 'rgba(255,255,255,0.25)' },
          { label: 'Failed', count: failed, color: theme.palette.error.main },
        ]}
      />
    </Box>
  );
}