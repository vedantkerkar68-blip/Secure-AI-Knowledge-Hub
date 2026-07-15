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
} from '@mui/material';
import { toast } from 'react-toastify';
import EditIcon from '@mui/icons-material/Edit';
import SearchIcon from '@mui/icons-material/Search';
import { useAuth } from '../context/AuthContext';
import * as userService from '../services/userService';
import * as departmentService from '../services/departmentService';

const STATUS_COLORS = {
  ACTIVE: 'success',
  INACTIVE: 'default',
  LOCKED: 'error',
};

const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE', 'LOCKED'];

const EMPTY_FORM = { firstName: '', lastName: '', departmentId: '', status: '' };

export default function Users() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [openDialog, setOpenDialog] = useState(false);
  const [editUser, setEditUser] = useState(null);
  const [departments, setDepartments] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit: formSubmit,
    control,
    formState: { errors },
    reset,
    setValue,
  } = useForm({ defaultValues: EMPTY_FORM });

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size: rowsPerPage, sort: 'id,asc' };
      if (search.trim()) params.search = search.trim();
      const response = await userService.getAll(params);
      setUsers(response.data.content ?? []);
      setTotalElements(response.data.totalElements ?? 0);
    } catch {
      toast.error('Failed to load users');
    } finally {
      setLoading(false);
    }
  }, [page, rowsPerPage, search]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleSearch = () => {
    setSearch(searchInput);
    setPage(0);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') handleSearch();
  };

  const isOwnProfile = (userId) => currentUser?.id === userId;

  const handleOpenEdit = async (target) => {
    setEditUser(target);
    reset({
      firstName: target.firstName ?? '',
      lastName: target.lastName ?? '',
      departmentId: target.departmentId ?? '',
      status: target.status ?? '',
    });
    setOpenDialog(true);

    if (departments.length === 0) {
      try {
        const res = await departmentService.getAll({ page: 0, size: 1000, sort: 'name,asc' });
        setDepartments(res.data.content ?? []);
      } catch {
        // silently fail — department field will just be empty
      }
    }
  };

  const handleCloseDialog = () => {
    setOpenDialog(false);
    setEditUser(null);
  };

  const onSubmit = async (data) => {
    setSubmitting(true);
    try {
      const payload = {
        firstName: data.firstName.trim(),
        lastName: data.lastName.trim(),
        departmentId: data.departmentId || null,
        status: isOwnProfile(editUser.id) ? undefined : data.status,
      };
      await userService.update(editUser.id, payload);
      toast.success('User updated successfully');
      handleCloseDialog();
      await fetchUsers();
    } catch (err) {
      const apiError = err.response?.data;
      if (apiError?.fields) {
        apiError.fields.forEach((f) => {
          setValue(f.field, editUser[f.field] ?? '');
        });
        apiError.fields.forEach((f) => {
          setError(f.field, { message: f.message });
        });
      }
      toast.error(apiError?.message || 'Failed to update user');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 3 }}>Users</Typography>

      <Paper elevation={2} sx={{ borderRadius: 3, p: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
          <TextField
            size="small"
            placeholder="Search by name or email..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={handleKeyDown}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
            sx={{ flexGrow: 1, minWidth: 240 }}
          />
          <Button variant="contained" onClick={handleSearch}>
            Search
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
                      <TableCell>
                        <Chip label={u.role} size="small" color="primary" variant="outlined" />
                      </TableCell>
                      <TableCell sx={{ color: 'text.secondary' }}>{u.department || '—'}</TableCell>
                      <TableCell>
                        <Chip
                          label={u.status}
                          size="small"
                          color={STATUS_COLORS[u.status] || 'default'}
                        />
                      </TableCell>
                      <TableCell align="right">
                        <IconButton
                          onClick={() => handleOpenEdit(u)}
                          color="primary"
                          size="small"
                          title="Edit user"
                        >
                          <EditIcon />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
                  {users.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={6} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                        {search ? 'No users match your search' : 'No users found'}
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              component="div"
              count={totalElements}
              page={page}
              onPageChange={(_, p) => setPage(p)}
              rowsPerPage={rowsPerPage}
              onRowsPerPageChange={(e) => {
                setRowsPerPage(parseInt(e.target.value, 10));
                setPage(0);
              }}
              rowsPerPageOptions={[5, 10, 25, 50]}
            />
          </>
        )}
      </Paper>

      <Dialog open={openDialog} onClose={handleCloseDialog} maxWidth="sm" fullWidth>
        <form onSubmit={formSubmit(onSubmit)}>
          <DialogTitle>Edit User — {editUser ? `${editUser.firstName} ${editUser.lastName}` : ''}</DialogTitle>
          <DialogContent>
            <TextField
              fullWidth
              label="First Name"
              {...register('firstName', {
                required: 'First name is required',
                maxLength: { value: 100, message: 'First name must be 100 characters or less' },
              })}
              margin="normal"
              error={!!errors.firstName}
              helperText={errors.firstName?.message}
              disabled={submitting}
            />
            <TextField
              fullWidth
              label="Last Name"
              {...register('lastName', {
                required: 'Last name is required',
                maxLength: { value: 100, message: 'Last name must be 100 characters or less' },
              })}
              margin="normal"
              error={!!errors.lastName}
              helperText={errors.lastName?.message}
              disabled={submitting}
            />

            <FormControl fullWidth margin="normal" error={!!errors.departmentId}>
              <InputLabel>Department</InputLabel>
              <Controller
                name="departmentId"
                control={control}
                render={({ field }) => (
                  <Select {...field} label="Department" disabled={submitting}>
                    <MenuItem value="">
                      <em>None</em>
                    </MenuItem>
                    {departments.map((d) => (
                      <MenuItem key={d.id} value={d.id}>
                        {d.name}
                      </MenuItem>
                    ))}
                  </Select>
                )}
              />
              {errors.departmentId && (
                <FormHelperText>{errors.departmentId.message}</FormHelperText>
              )}
            </FormControl>

            <TextField
              fullWidth
              label="Role"
              value={editUser?.role ?? ''}
              margin="normal"
              disabled
              slotProps={{ input: { readOnly: true } }}
            />

            <FormControl fullWidth margin="normal" error={!!errors.status}>
              <InputLabel>Status</InputLabel>
              <Controller
                name="status"
                control={control}
                rules={{ required: 'Status is required' }}
                render={({ field }) => (
                  <Select
                    {...field}
                    label="Status"
                    disabled={submitting || isOwnProfile(editUser?.id)}
                  >
                    {STATUS_OPTIONS.map((s) => (
                      <MenuItem key={s} value={s}>
                        {s}
                      </MenuItem>
                    ))}
                  </Select>
                )}
              />
              {errors.status && (
                <FormHelperText>{errors.status.message}</FormHelperText>
              )}
              {isOwnProfile(editUser?.id) && (
                <FormHelperText>You cannot change your own status</FormHelperText>
              )}
            </FormControl>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleCloseDialog} disabled={submitting}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={submitting}>
              {submitting ? <CircularProgress size={20} /> : 'Save'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
}
