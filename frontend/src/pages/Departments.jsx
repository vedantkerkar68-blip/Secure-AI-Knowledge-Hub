import { useState, useEffect, useMemo } from 'react';
import {
  Box,
  Typography,
  Paper,
  TextField,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  CircularProgress,
} from '@mui/material';
import { toast } from 'react-toastify';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import SearchIcon from '@mui/icons-material/Search';
import * as departmentService from '../services/departmentService';

const INITIAL_FORM = { name: '', description: '' };

export default function Departments() {
  const [departments, setDepartments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [openDialog, setOpenDialog] = useState(false);
  const [editTarget, setEditTarget] = useState(null);
  const [formData, setFormData] = useState(INITIAL_FORM);
  const [formErrors, setFormErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);

  const fetchDepartments = async () => {
    setLoading(true);
    try {
      const response = await departmentService.getAll({ page: 0, size: 1000, sort: 'id,asc' });
      setDepartments(response.data.content ?? []);
    } catch {
      toast.error('Failed to load departments');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDepartments();
  }, []);

  const filtered = useMemo(() => {
    if (!search.trim()) return departments;
    const q = search.toLowerCase();
    return departments.filter(
      (d) =>
        d.name?.toLowerCase().includes(q) ||
        (d.description ?? '').toLowerCase().includes(q)
    );
  }, [departments, search]);

  const paginated = useMemo(
    () => filtered.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage),
    [filtered, page, rowsPerPage]
  );

  const handleOpenCreate = () => {
    setEditTarget(null);
    setFormData(INITIAL_FORM);
    setFormErrors({});
    setOpenDialog(true);
  };

  const handleOpenEdit = (dept) => {
    setEditTarget(dept);
    setFormData({ name: dept.name, description: dept.description ?? '' });
    setFormErrors({});
    setOpenDialog(true);
  };

  const handleCloseDialog = () => {
    setOpenDialog(false);
    setEditTarget(null);
    setFormErrors({});
  };

  const handleSubmit = async () => {
    const errors = {};
    if (!formData.name.trim()) errors.name = 'Name is required';
    if (formData.name.length > 100) errors.name = 'Name must be 100 characters or less';
    setFormErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      const payload = {
        name: formData.name.trim(),
        description: formData.description.trim() || null,
      };
      if (editTarget) {
        await departmentService.update(editTarget.id, payload);
        toast.success('Department updated successfully');
      } else {
        await departmentService.create(payload);
        toast.success('Department created successfully');
      }
      handleCloseDialog();
      await fetchDepartments();
    } catch (err) {
      const data = err.response?.data;
      if (data?.fields) {
        const fieldErrors = {};
        data.fields.forEach((f) => { fieldErrors[f.field] = f.message; });
        setFormErrors(fieldErrors);
      }
      toast.error(data?.message || 'An unexpected error occurred');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await departmentService.remove(deleteTarget.id);
      toast.success('Department deleted successfully');
      setDeleteTarget(null);
      await fetchDepartments();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to delete department');
    }
  };

  const formatDate = (iso) => {
    if (!iso) return '-';
    return new Date(iso).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  };

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 3 }}>Departments</Typography>

      <Paper elevation={2} sx={{ borderRadius: 3, p: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
          <TextField
            size="small"
            placeholder="Search departments..."
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            slotProps={{ input: { startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} /> } }}
            sx={{ flexGrow: 1, minWidth: 240 }}
          />
          <Button variant="contained" startIcon={<AddIcon />} onClick={handleOpenCreate}>
            Create Department
          </Button>
        </Box>

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
            <CircularProgress />
          </Box>
        ) : (
          <>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 600 }}>Name</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Description</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Created Date</TableCell>
                    <TableCell sx={{ fontWeight: 600 }} align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {paginated.map((dept) => (
                    <TableRow key={dept.id} hover>
                      <TableCell>{dept.name}</TableCell>
                      <TableCell sx={{ color: 'text.secondary' }}>{dept.description || '—'}</TableCell>
                      <TableCell>{formatDate(dept.createdAt)}</TableCell>
                      <TableCell align="right">
                        <IconButton onClick={() => handleOpenEdit(dept)} color="primary" size="small" title="Edit">
                          <EditIcon />
                        </IconButton>
                        <IconButton onClick={() => setDeleteTarget(dept)} color="error" size="small" title="Delete">
                          <DeleteIcon />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
                  {paginated.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={4} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                        {search ? 'No departments match your search' : 'No departments found'}
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              component="div"
              count={filtered.length}
              page={page}
              onPageChange={(_, p) => setPage(p)}
              rowsPerPage={rowsPerPage}
              onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
              rowsPerPageOptions={[5, 10, 25, 50]}
            />
          </>
        )}
      </Paper>

      <Dialog open={openDialog} onClose={handleCloseDialog} maxWidth="sm" fullWidth>
        <DialogTitle>{editTarget ? 'Edit Department' : 'Create Department'}</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label="Name"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            margin="normal"
            required
            error={!!formErrors.name}
            helperText={formErrors.name}
            disabled={submitting}
          />
          <TextField
            fullWidth
            label="Description"
            value={formData.description}
            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            margin="normal"
            multiline
            rows={3}
            error={!!formErrors.description}
            helperText={formErrors.description}
            disabled={submitting}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog} disabled={submitting}>Cancel</Button>
          <Button onClick={handleSubmit} variant="contained" disabled={submitting}>
            {submitting ? <CircularProgress size={20} /> : (editTarget ? 'Update' : 'Create')}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={!!deleteTarget} onClose={() => setDeleteTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Delete Department</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete <strong>{deleteTarget?.name}</strong>? This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>Cancel</Button>
          <Button onClick={handleDelete} color="error" variant="contained">Delete</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
