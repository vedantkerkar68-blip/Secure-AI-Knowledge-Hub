import { useEffect, useState, useCallback } from 'react';
import {
  Box,
  Typography,
  Chip,
  Button,
  Skeleton,
  Stack,
  Paper,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  CircularProgress,
  InputAdornment,
} from '@mui/material';
import HistoryIcon from '@mui/icons-material/History';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import { toast } from 'react-toastify';
import { getActivityLogs, deleteOldActivity } from '../../services/adminService';

const ACTION_COLORS = {
  LOGIN: 'info',
  LOGOUT: 'default',
  CREATE: 'success',
  UPDATE: 'warning',
  DELETE: 'error',
  UPLOAD: 'primary',
  DOWNLOAD: 'primary',
  CHAT: 'secondary',
  REPROCESS: 'warning',
};

const RETENTION_OPTIONS = [
  { label: '30 days', days: 30 },
  { label: '6 months', days: 180 },
  { label: '1 year', days: 365 },
  { label: 'Never', days: 0 },
];

const PAGE_SIZE = 10;

function relativeTime(iso) {
  if (!iso) return '';
  const seconds = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return 'just now';
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

function ActivityRowSkeleton() {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, py: 1 }}>
      <Skeleton variant="rounded" width={70} height={24} sx={{ borderRadius: 999, flexShrink: 0 }} />
      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <Skeleton width="60%" height={13} />
        <Skeleton width="35%" height={11} sx={{ mt: 0.5 }} />
      </Box>
      <Skeleton width={44} height={12} />
    </Box>
  );
}

export default function ActivityFeed() {
  const [action, setAction] = useState('');
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [page, setPage] = useState(0);
  const [logs, setLogs] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const [retention, setRetention] = useState(180);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const retentionLabel = RETENTION_OPTIONS.find((o) => o.days === retention)?.label ?? '';

  const fetchData = useCallback(async (targetPage, append) => {
    setLoading(true);
    setError(false);
    try {
      const params = { page: targetPage, size: PAGE_SIZE, sort: 'createdAt,desc' };
      if (action) params.action = action;
      if (search.trim()) params.search = search.trim();
      const res = await getActivityLogs(params);
      const content = res.data.content ?? [];
      setTotalElements(res.data.totalElements ?? 0);
      setLogs((prev) => (append ? [...prev, ...content] : content));
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [action, search]);

  useEffect(() => {
    setPage(0);
    fetchData(0, false);
  }, [fetchData]);

  const clearFilters = () => {
    setAction('');
    setSearch('');
    setSearchInput('');
  };

  const loadMore = () => {
    const next = page + 1;
    setPage(next);
    fetchData(next, true);
  };

  const confirmDelete = async () => {
    setDeleting(true);
    try {
      const res = await deleteOldActivity(retention);
      const count = res.data?.deleted ?? 0;
      toast.success(`Deleted ${count} activity log${count === 1 ? '' : 's'} older than ${retentionLabel.toLowerCase()}.`);
      setDeleteOpen(false);
      setPage(0);
      fetchData(0, false);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to delete activity logs');
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Paper elevation={1} sx={{ borderRadius: 2, p: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap', mb: 2 }}>
        <TextField
          size="small"
          placeholder="Search by user or resource..."
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') setSearch(searchInput); }}
          slotProps={{
            input: {
              startAdornment: <InputAdornment position="start"><SearchIcon sx={{ color: 'text.secondary' }} /></InputAdornment>,
            },
          }}
          sx={{ flexGrow: 1, minWidth: 180 }}
        />
        <FormControl size="small" sx={{ minWidth: 130 }}>
          <InputLabel>Action</InputLabel>
          <Select
            value={action}
            label="Action"
            onChange={(e) => setAction(e.target.value)}
          >
            <MenuItem value="">All actions</MenuItem>
            {Object.keys(ACTION_COLORS).map((a) => (
              <MenuItem key={a} value={a}>{a}</MenuItem>
            ))}
          </Select>
        </FormControl>
        {(action || search) && (
          <Button size="small" startIcon={<ClearIcon />} onClick={clearFilters}>
            Clear
          </Button>
        )}
        <Box sx={{ flexGrow: 1 }} />
        <FormControl size="small" sx={{ minWidth: 130 }}>
          <InputLabel>Retention</InputLabel>
          <Select
            value={retention}
            label="Retention"
            onChange={(e) => setRetention(e.target.value)}
          >
            {RETENTION_OPTIONS.map((o) => (
              <MenuItem key={o.days} value={o.days}>{o.label}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <Button
          color="error"
          variant="outlined"
          startIcon={<DeleteOutlineIcon />}
          disabled={retention === 0 || deleting}
          onClick={() => setDeleteOpen(true)}
        >
          Delete old activity
        </Button>
      </Box>

      {loading && page === 0 ? (
        <Box>
          {[0, 1, 2, 3, 4, 5].map((i) => <ActivityRowSkeleton key={i} />)}
        </Box>
      ) : error ? (
        <Stack spacing={1.5} alignItems="center" sx={{ py: 4, textAlign: 'center' }}>
          <ErrorOutlineIcon color="error" />
          <Typography variant="body2" color="text.secondary">
            Failed to load recent activity.
          </Typography>
          <Button size="small" variant="outlined" onClick={() => fetchData(0, false)}>
            Retry
          </Button>
        </Stack>
      ) : logs.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 5 }}>
          <HistoryIcon sx={{ fontSize: 40, color: 'text.disabled', mb: 1 }} />
          <Typography variant="body2" color="text.secondary">
            No activity recorded yet.
          </Typography>
        </Box>
      ) : (
        <>
          <Stack spacing={1}>
            {logs.map((log) => (
              <Box
                key={log.id}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1.5,
                  py: 1,
                  '&:not(:last-child)': { borderBottom: '1px solid', borderColor: 'divider' },
                }}
              >
                <Chip
                  label={log.action}
                  size="small"
                  color={ACTION_COLORS[log.action] || 'default'}
                  variant="outlined"
                  sx={{ flexShrink: 0, minWidth: 84 }}
                />
                <Box sx={{ minWidth: 0, flexGrow: 1 }}>
                  <Typography
                    variant="body2"
                    sx={{ fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                  >
                    {log.resource || log.userEmail || '—'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {log.userEmail}
                  </Typography>
                </Box>
                <Typography variant="caption" color="text.disabled" sx={{ flexShrink: 0 }}>
                  {relativeTime(log.createdAt)}
                </Typography>
              </Box>
            ))}
          </Stack>
          {logs.length < totalElements && (
            <Box sx={{ textAlign: 'center', pt: 2 }}>
              <Button size="small" variant="outlined" onClick={loadMore} disabled={loading}>
                {loading ? <CircularProgress size={16} /> : `Show more (${totalElements - logs.length} more)`}
              </Button>
            </Box>
          )}
        </>
      )}

      <Dialog open={deleteOpen} onClose={() => !deleting && setDeleteOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Delete Old Activity</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            Permanently delete all activity logs older than{' '}
            <strong>{retentionLabel.toLowerCase()}</strong>? This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteOpen(false)} disabled={deleting}>Cancel</Button>
          <Button color="error" variant="contained" onClick={confirmDelete} disabled={deleting}>
            {deleting ? 'Deleting...' : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  );
}