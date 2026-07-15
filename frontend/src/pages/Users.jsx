import { useState, useEffect, useCallback } from 'react';
import { useForm, Controller } from 'react-hook-form';
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
  Chip,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  FormHelperText,
  InputAdornment,
  Skeleton,
} from '@mui/material';
import { toast } from 'react-toastify';
import EditIcon from '@mui/icons-material/Edit';
import SearchIcon from '@mui/icons-material/Search';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import LockOpenIcon from '@mui/icons-material/LockOpen';
import LockIcon from '@mui/icons-material/Lock';
import { useAuth } from '../context/AuthContext';
import * as userService from '../services/userService';
import * as departmentService from '../services/departmentService';
import * as authService from '../services/authService';

const STATUS_COLORS = { ACTIVE: 'success', INACTIVE: 'default', LOCKED: 'error' };
const STATUS_OPTIONS = ['', 'ACTIVE', 'INACTIVE'];
const ROLE_OPTIONS = ['', 'ADMIN', 'MANAGER', 'EMPLOYEE'];

const EMPTY_FORM = { firstName: '', lastName: '', email: '', password: '', departmentId: '', roleId: '', status: 'ACTIVE' };

export default function Users() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [deptFilter, setDeptFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [departments, setDepartments] = useState([]);

  const [openAdd, setOpenAdd] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);
  const [editUser, setEditUser] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const isOwnProfile = (userId) => currentUser?.id === userId;

  const {
    register: reg,
    handleSubmit: handleSubmitForm,
    control,
    formState: { errors },
    reset,
    setError,
  } = useForm({ defaultValues: EMPTY_FORM });

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size: rowsPerPage, sort: 'id,asc' };
      if (search.trim()) params.search = search.trim();
      if (roleFilter) params.role = roleFilter;
      if (deptFilter) params.department = deptFilter;
      if (statusFilter) params.status = statusFilter;
      const res = await userService.getAll(params);
      setUsers(res.data.content ?? []);
      setTotalElements(res.data.totalElements ?? 0);
    } catch {
      toast.error('Failed to load users');
    } finally {
      setLoading(false);
    }
  }, [search, roleFilter, deptFilter, statusFilter, page, rowsPerPage]);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);

  useEffect(() => {
    departmentService.getAll({ page: 0, size: 1000, sort: 'name,asc' })
      .then((r) => setDepartments(r.data.content ?? []))
      .catch(() => {});
  }, []);

  const handleAddOpen = () => {
    reset(EMPTY_FORM);
    setOpenAdd(true);
  };

  const handleAddSubmit = async (data) => {
    setSubmitting(true);
    try {
      await authService.register({
        firstName: data.firstName.trim(),
        lastName: data.lastName.trim(),
        email: data.email.trim(),
        password: data.password,
        roleId: parseInt(data.roleId, 10),
        departmentId: data.departmentId ? parseInt(data.departmentId, 10) : null,
      });
      toast.success('Employee added successfully');
      setOpenAdd(false);
      fetchUsers();
    } catch (err) {
      const apiErr = err.response?.data;
      if (apiErr?.fields) {
        apiErr.fields.forEach((f) => setError(f.field, { message: f.message }));
      }
      toast.error(apiErr?.message || 'Failed to add employee');
    } finally {
      setSubmitting(false);
    }
  };

  const handleEditOpen = (target) => {
    setEditUser(target);
    reset({
      firstName: target.firstName ?? '',
      lastName: target.lastName ?? '',
      departmentId: target.departmentId ?? '',
      status: target.status ?? '',
    });
    setOpenEdit(true);
  };

  const handleEditSubmit = async (data) => {
    setSubmitting(true);
    try {
      await userService.update(editUser.id, {
        firstName: data.firstName.trim(),
        lastName: data.lastName.trim(),
        departmentId: data.departmentId || null,
        status: isOwnProfile(editUser.id) ? undefined : data.status,
      });
      toast.success('User updated');
      setOpenEdit(false);
      fetchUsers();
    } catch (err) {
      const apiErr = err.response?.data;
      if (apiErr?.fields) {
        apiErr.fields.forEach((f) => setError(f.field, { message: f.message }));
      }
      toast.error(apiErr?.message || 'Update failed');
    } finally {
      setSubmitting(false);
    }
  };

  const toggleUserStatus = async (target) => {
    const newStatus = target.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    try {
      await userService.updateStatus(target.id, { status: newStatus });
      toast.success(`User ${newStatus === 'ACTIVE' ? 'enabled' : 'disabled'}`);
      fetchUsers();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update status');
    }
  };

  const searchSubmit = () => { setSearch(searchInput); setPage(0); };

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 3 }}>Users</Typography>

      <Paper elevation={2} sx={{ borderRadius: 3, p: 2, mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
          <TextField size="small" placeholder="Search by name or email..." value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') searchSubmit(); }}
            slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon sx={{ color: 'text.secondary' }} /></InputAdornment> } }}
            sx={{ flexGrow: 1, minWidth: 180 }}
          />
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Role</InputLabel>
            <Select value={roleFilter} label="Role" onChange={(e) => { setRoleFilter(e.target.value); setPage(0); }}>
              <MenuItem value="">All</MenuItem>
              {ROLE_OPTIONS.filter(Boolean).map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 140 }}>
            <InputLabel>Department</InputLabel>
            <Select value={deptFilter} label="Department" onChange={(e) => { setDeptFilter(e.target.value); setPage(0); }}>
              <MenuItem value="">All</MenuItem>
              {departments.map((d) => <MenuItem key={d.id} value={d.name}>{d.name}</MenuItem>)}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Status</InputLabel>
            <Select value={statusFilter} label="Status" onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}>
              <MenuItem value="">All</MenuItem>
              {STATUS_OPTIONS.filter(Boolean).map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
            </Select>
          </FormControl>
          <Button variant="contained" startIcon={<PersonAddIcon />} onClick={handleAddOpen}>Add Employee</Button>
        </Box>
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
                    <TableCell sx={{ fontWeight: 600 }}>Name</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Email</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Role</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Department</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Status</TableCell>
                    <TableCell sx={{ fontWeight: 600 }} align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {users.map((u) => (
                    <TableRow key={u.id} hover>
                      <TableCell>{`${u.firstName} ${u.lastName}`}</TableCell>
                      <TableCell>{u.email}</TableCell>
                      <TableCell><Chip label={u.role} size="small" color="primary" variant="outlined" /></TableCell>
                      <TableCell sx={{ color: 'text.secondary' }}>{u.department || '—'}</TableCell>
                      <TableCell><Chip label={u.status} size="small" color={STATUS_COLORS[u.status] || 'default'} /></TableCell>
                      <TableCell align="right">
                        <IconButton onClick={() => handleEditOpen(u)} color="primary" size="small" title="Edit"><EditIcon /></IconButton>
                        <IconButton onClick={() => toggleUserStatus(u)} color={u.status === 'ACTIVE' ? 'warning' : 'success'} size="small"
                          title={u.status === 'ACTIVE' ? 'Disable' : 'Enable'}>
                          {u.status === 'ACTIVE' ? <LockIcon /> : <LockOpenIcon />}
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
                  {users.length === 0 && (
                    <TableRow><TableCell colSpan={6} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                      {search || roleFilter || deptFilter || statusFilter ? 'No users match your filters' : 'No users found'}
                    </TableCell></TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination component="div" count={totalElements} page={page}
              onPageChange={(_, p) => setPage(p)} rowsPerPage={rowsPerPage}
              onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
              rowsPerPageOptions={[5, 10, 25, 50]} />
          </>
        )}
      </Paper>

      <Dialog open={openAdd} onClose={() => !submitting && setOpenAdd(false)} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmitForm(handleAddSubmit)}>
          <DialogTitle>Add Employee</DialogTitle>
          <DialogContent>
            <TextField fullWidth label="First Name" {...reg('firstName', { required: 'Required' })} margin="dense" error={!!errors.firstName} helperText={errors.firstName?.message} disabled={submitting} />
            <TextField fullWidth label="Last Name" {...reg('lastName', { required: 'Required' })} margin="dense" error={!!errors.lastName} helperText={errors.lastName?.message} disabled={submitting} />
            <TextField fullWidth label="Email" type="email" {...reg('email', { required: 'Required' })} margin="dense" error={!!errors.email} helperText={errors.email?.message} disabled={submitting} />
            <TextField fullWidth label="Password" type="password" {...reg('password', { required: 'Required', minLength: { value: 8, message: 'Min 8 characters' } })} margin="dense" error={!!errors.password} helperText={errors.password?.message} disabled={submitting} />
            <FormControl fullWidth margin="dense" error={!!errors.departmentId}>
              <InputLabel>Department</InputLabel>
              <Controller name="departmentId" control={control} render={({ field }) => (
                <Select {...field} label="Department" disabled={submitting}>
                  <MenuItem value=""><em>None</em></MenuItem>
                  {departments.map((d) => <MenuItem key={d.id} value={d.id}>{d.name}</MenuItem>)}
                </Select>
              )} />
              {errors.departmentId && <FormHelperText>{errors.departmentId.message}</FormHelperText>}
            </FormControl>
            <FormControl fullWidth margin="dense" error={!!errors.roleId}>
              <InputLabel>Role *</InputLabel>
              <Controller name="roleId" control={control} rules={{ required: 'Role is required' }} render={({ field }) => (
                <Select {...field} label="Role *" disabled={submitting}>
                  <MenuItem value="1">ADMIN</MenuItem>
                  <MenuItem value="2">MANAGER</MenuItem>
                  <MenuItem value="3">EMPLOYEE</MenuItem>
                </Select>
              )} />
              {errors.roleId && <FormHelperText>{errors.roleId.message}</FormHelperText>}
            </FormControl>
            <FormControl fullWidth margin="dense">
              <InputLabel>Status</InputLabel>
              <Controller name="status" control={control} render={({ field }) => (
                <Select {...field} label="Status" disabled={submitting}>
                  <MenuItem value="ACTIVE">ACTIVE</MenuItem>
                  <MenuItem value="INACTIVE">INACTIVE</MenuItem>
                </Select>
              )} />
            </FormControl>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setOpenAdd(false)} disabled={submitting}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={submitting}>{submitting ? <CircularProgress size={20} /> : 'Add'}</Button>
          </DialogActions>
        </form>
      </Dialog>

      <Dialog open={openEdit} onClose={() => !submitting && setOpenEdit(false)} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmitForm(handleEditSubmit)}>
          <DialogTitle>Edit User — {editUser ? `${editUser.firstName} ${editUser.lastName}` : ''}</DialogTitle>
          <DialogContent>
            <TextField fullWidth label="First Name" {...reg('firstName', { required: 'Required' })} margin="dense" error={!!errors.firstName} helperText={errors.firstName?.message} disabled={submitting} />
            <TextField fullWidth label="Last Name" {...reg('lastName', { required: 'Required' })} margin="dense" error={!!errors.lastName} helperText={errors.lastName?.message} disabled={submitting} />
            <FormControl fullWidth margin="dense" error={!!errors.departmentId}>
              <InputLabel>Department</InputLabel>
              <Controller name="departmentId" control={control} render={({ field }) => (
                <Select {...field} label="Department" disabled={submitting}>
                  <MenuItem value=""><em>None</em></MenuItem>
                  {departments.map((d) => <MenuItem key={d.id} value={d.id}>{d.name}</MenuItem>)}
                </Select>
              )} />
              {errors.departmentId && <FormHelperText>{errors.departmentId.message}</FormHelperText>}
            </FormControl>
            <TextField fullWidth label="Role" value={editUser?.role ?? ''} margin="dense" disabled slotProps={{ input: { readOnly: true } }} />
            <FormControl fullWidth margin="dense" error={!!errors.status}>
              <InputLabel>Status</InputLabel>
              <Controller name="status" control={control} rules={{ required: 'Required' }} render={({ field }) => (
                <Select {...field} label="Status" disabled={submitting || isOwnProfile(editUser?.id)}>
                  {STATUS_OPTIONS.filter(Boolean).map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
                </Select>
              )} />
              {errors.status && <FormHelperText>{errors.status.message}</FormHelperText>}
              {isOwnProfile(editUser?.id) && <FormHelperText>You cannot change your own status</FormHelperText>}
            </FormControl>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setOpenEdit(false)} disabled={submitting}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={submitting}>{submitting ? <CircularProgress size={20} /> : 'Save'}</Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
}
