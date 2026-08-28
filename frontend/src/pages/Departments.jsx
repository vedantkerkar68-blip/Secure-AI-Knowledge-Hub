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
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  FormHelperText,
} from '@mui/material';
import { toast } from 'react-toastify';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import SearchIcon from '@mui/icons-material/Search';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import * as departmentService from '../services/departmentService';

const INITIAL_FORM = { name: '', description: '', parentId: '' };

function buildTree(departments) {
  const byId = new Map(departments.map((d) => [d.id, d]));
  const childrenMap = new Map();
  departments.forEach((d) => {
    const pid = d.parentId ?? null;
    if (!childrenMap.has(pid)) childrenMap.set(pid, []);
    childrenMap.get(pid).push(d);
  });
  const ordered = [];
  const depth = new Map();
  const walk = (pid, level) => {
    for (const child of childrenMap.get(pid) ?? []) {
      depth.set(child.id, level);
      ordered.push(child);
      walk(child.id, level + 1);
    }
  };
  walk(null, 0);
  return { byId, ordered, depth };
}

export default function Departments({ embedded = false }) {
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

  const tree = useMemo(() => buildTree(departments), [departments]);

  const excludedParentIds = useMemo(() => {
    if (!editTarget) return new Set();
    const childrenMap = new Map();
    departments.forEach((d) => {
      const pid = d.parentId ?? null;
      if (!childrenMap.has(pid)) childrenMap.set(pid, []);
      childrenMap.get(pid).push(d);
    });
    const set = new Set([editTarget.id]);
    const queue = [editTarget.id];
    while (queue.length) {
      for (const child of childrenMap.get(queue.shift()) ?? []) {
        set.add(child.id);
        queue.push(child.id);
      }
    }
    return set;
  }, [editTarget, departments]);

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
    if (!search.trim()) return tree.ordered;
    const q = search.toLowerCase();
    return tree.ordered.filter(
      (d) =>
        d.name?.toLowerCase().includes(q) ||
        (d.description ?? '').toLowerCase().includes(q)
    );
  }, [tree.ordered, search]);

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
    setFormData({ name: dept.name, description: dept.description ?? '', parentId: dept.parentId ?? '' });
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
        parentId: formData.parentId ? Number(formData.parentId) : null,
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
      {!embedded && <Typography variant="h4" sx={{ mb: 3 }}>Departments</Typography>}

      <Paper elevation={1} sx={{ borderRadius: 2, p: 2 }}>
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
                    <TableCell sx={{ fontWeight: 600 }}>Parent</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Description</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Created Date</TableCell>
                    <TableCell sx={{ fontWeight: 600 }} align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {paginated.map((dept) => {
                    const level = tree.depth.get(dept.id) ?? 0;
                    const parentName = dept.parentId ? tree.byId.get(dept.parentId)?.name : null;
                    return (
                      <TableRow key={dept.id} hover>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, pl: level * 3 }}>
                            {level === 0 ? (
                              <AccountTreeIcon sx={{ fontSize: 16, color: 'primary.main', flexShrink: 0 }} />
                            ) : (
                              <ChevronRightIcon sx={{ fontSize: 16, color: 'text.disabled', flexShrink: 0 }} />
                            )}
                            <Typography variant="body2" sx={{ fontWeight: level === 0 ? 600 : 400 }}>
                              {dept.name}
                            </Typography>
                          </Box>
                        </TableCell>
                        <TableCell sx={{ color: 'text.secondary' }}>{parentName || '—'}</TableCell>
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
                    );
                  })}
                  {paginated.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} align="center" sx={{ py: 4, color: 'text.secondary' }}>
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
          <FormControl fullWidth size="small" margin="dense">
            <InputLabel>Parent department</InputLabel>
            <Select
              label="Parent department"
              value={formData.parentId}
              onChange={(e) => setFormData({ ...formData, parentId: e.target.value })}
              disabled={submitting}
            >
              <MenuItem value=""><em>None — top-level department</em></MenuItem>
              {departments
                .filter((d) => !excludedParentIds.has(d.id))
                .map((d) => (
                  <MenuItem key={d.id} value={d.id} sx={{ pl: (tree.depth.get(d.id) ?? 0) * 2 }}>
                    {d.name}
                  </MenuItem>
                ))}
            </Select>
            <FormHelperText>
              Sub-departments are visible to everyone in this department and the ones above it.
            </FormHelperText>
          </FormControl>
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
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            Departments that still have sub-departments cannot be deleted.
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