import { useState, useEffect, useCallback } from 'react';
import {
  Box, Typography, Paper, Button, Table, TableBody, TableCell, TableContainer, TableHead,
  TableRow, TablePagination, Chip, Skeleton, TextField, InputAdornment,
} from '@mui/material';
import { toast } from 'react-toastify';
import SearchIcon from '@mui/icons-material/Search';
import HistoryIcon from '@mui/icons-material/History';
import * as activityLogService from '../services/activityLogService';

function formatDateTime(iso) {
  if (!iso) return '-';
  return new Date(iso).toLocaleString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

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

export default function ActivityLogs() {
  const [logs, setLogs] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);
  const [fetchError, setFetchError] = useState(false);

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    setFetchError(false);
    try {
      const params = { page, size: rowsPerPage, sort: 'id,desc' };
      if (search.trim()) params.search = search.trim();
      const res = await activityLogService.getAll(params);
      setLogs(res.data.content ?? []);
      setTotalElements(res.data.totalElements ?? 0);
    } catch (err) {
      if (err.response?.status === 403) {
        setFetchError(true);
      } else {
        toast.error('Failed to load activity logs');
      }
    } finally {
      setLoading(false);
    }
  }, [search, page, rowsPerPage]);

  useEffect(() => { fetchLogs(); }, [fetchLogs]);

  const handleSearch = () => { setSearch(searchInput); setPage(0); };

  if (fetchError) {
    return (
      <Box>
        <Typography variant="h4" sx={{ mb: 3 }}>Activity Logs</Typography>
        <Paper elevation={2} sx={{ borderRadius: 3, p: 6, textAlign: 'center' }}>
          <HistoryIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
          <Typography variant="h6" color="text.secondary" sx={{ mb: 1 }}>Access Restricted</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            You do not have permission to view activity logs. Only administrators can access this page.
          </Typography>
          <Button variant="outlined" onClick={fetchLogs}>Retry</Button>
        </Paper>
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 3 }}>Activity Logs</Typography>

      <Paper elevation={2} sx={{ borderRadius: 3, p: 2, mb: 3 }}>
        <TextField size="small" placeholder="Search by user, action, or details..." value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') handleSearch(); }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon sx={{ color: 'text.secondary' }} /></InputAdornment> } }}
          sx={{ width: 400, maxWidth: '100%' }} />
      </Paper>

      <Paper elevation={2} sx={{ borderRadius: 3, p: 2 }}>
        {loading ? (
          <Box sx={{ p: 2 }}>{[1, 2, 3, 4, 5].map((i) => <Skeleton key={i} height={48} sx={{ mb: 1 }} />)}</Box>
        ) : (
          <>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 600 }}>Timestamp</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>User</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Action</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Details</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {logs.map((log) => (
                    <TableRow key={log.id} hover>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(log.createdAt)}</TableCell>
                      <TableCell>{log.userEmail || '-'}</TableCell>
                      <TableCell>
                        <Chip label={log.action} size="small" color={ACTION_COLORS[log.action] || 'default'} variant="outlined" />
                      </TableCell>
                      <TableCell sx={{ color: 'text.secondary', maxWidth: 400, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {log.resource || '-'}
                      </TableCell>
                    </TableRow>
                  ))}
                  {logs.length === 0 && (
                    <TableRow><TableCell colSpan={4} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                      <HistoryIcon sx={{ fontSize: 32, opacity: 0.4, mb: 1 }} />
                      <Typography variant="body2">{search ? 'No logs match your search' : 'No activity logs found'}</Typography>
                    </TableCell></TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination component="div" count={totalElements} page={page}
              onPageChange={(_, p) => setPage(p)} rowsPerPage={rowsPerPage}
              onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
              rowsPerPageOptions={[10, 25, 50, 100]} />
          </>
        )}
      </Paper>
    </Box>
  );
}
