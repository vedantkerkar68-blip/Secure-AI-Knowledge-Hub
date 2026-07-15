import { useState, useEffect, useCallback, useRef } from 'react';
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
  LinearProgress,
  InputAdornment,
  Tooltip,
  Grid,
} from '@mui/material';
import { toast } from 'react-toastify';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import SearchIcon from '@mui/icons-material/Search';
import DownloadIcon from '@mui/icons-material/Download';
import VisibilityIcon from '@mui/icons-material/Visibility';
import HistoryIcon from '@mui/icons-material/History';
import RefreshIcon from '@mui/icons-material/Refresh';
import InsertDriveFileIcon from '@mui/icons-material/InsertDriveFile';
import * as documentService from '../services/documentService';
import * as departmentService from '../services/departmentService';

const STATUS_COLORS = {
  PENDING: 'default',
  PROCESSING: 'info',
  READY: 'success',
  FAILED: 'error',
  ARCHIVED: 'warning',
};

const FILE_TYPE_COLORS = {
  pdf: 'error',
  docx: 'primary',
  txt: 'success',
  md: 'secondary',
};

const STATUS_OPTIONS = ['', 'PENDING', 'PROCESSING', 'READY', 'FAILED', 'ARCHIVED'];

function formatDate(iso) {
  if (!iso) return '-';
  return new Date(iso).toLocaleDateString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
  });
}

function formatDateTime(iso) {
  if (!iso) return '-';
  return new Date(iso).toLocaleString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

function formatBytes(bytes) {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

export default function Documents() {
  const [documents, setDocuments] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [departmentFilter, setDepartmentFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [departments, setDepartments] = useState([]);

  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploadDeptId, setUploadDeptId] = useState('');
  const [dragOver, setDragOver] = useState(false);

  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewData, setPreviewData] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  const [versionsOpen, setVersionsOpen] = useState(false);
  const [versionsData, setVersionsData] = useState([]);
  const [versionsTitle, setVersionsTitle] = useState('');
  const [versionsLoading, setVersionsLoading] = useState(false);

  const pollingRef = useRef(new Set());
  const refreshRef = useRef(null);

  const fetchDocuments = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size: rowsPerPage, sort: 'id,desc' };
      if (search.trim()) params.search = search.trim();
      if (departmentFilter) params.department = departmentFilter;
      if (statusFilter) params.status = statusFilter;
      const res = await documentService.getAll(params);
      setDocuments(res.data.content ?? []);
      setTotalElements(res.data.totalElements ?? 0);
    } catch {
      toast.error('Failed to load documents');
    } finally {
      setLoading(false);
    }
  }, [search, departmentFilter, statusFilter, page, rowsPerPage]);

  refreshRef.current = fetchDocuments;

  useEffect(() => {
    fetchDocuments();
  }, [fetchDocuments]);

  useEffect(() => {
    departmentService
      .getAll({ page: 0, size: 1000, sort: 'name,asc' })
      .then((res) => setDepartments(res.data.content ?? []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (pollingRef.current.size === 0) return;
    const interval = setInterval(async () => {
      const ids = Array.from(pollingRef.current);
      for (const id of ids) {
        try {
          const res = await documentService.getStatus(id);
          const status = res.data.status;
          if (status === 'READY' || status === 'FAILED') {
            pollingRef.current.delete(id);
            if (status === 'READY') {
              toast.success(`Document #${id} processed successfully`);
            } else {
              toast.error(`Document #${id} processing failed`);
            }
            refreshRef.current?.();
          }
        } catch {
          pollingRef.current.delete(id);
        }
      }
    }, 2500);
    return () => clearInterval(interval);
  }, []);

  const handleSearch = () => {
    setSearch(searchInput);
    setPage(0);
  };

  const openUpload = () => {
    setSelectedFile(null);
    setUploadDeptId('');
    setUploadProgress(0);
    setUploadOpen(true);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files[0];
    if (file) setSelectedFile(file);
  };

  const handleFileSelect = (e) => {
    const file = e.target.files[0];
    if (file) setSelectedFile(file);
  };

  const handleUpload = async () => {
    if (!selectedFile) return;
    const formData = new FormData();
    formData.append('file', selectedFile);
    if (uploadDeptId) formData.append('departmentId', uploadDeptId);
    setUploading(true);
    setUploadProgress(0);
    try {
      const res = await documentService.upload(formData, (pe) => {
        const pct = Math.round((pe.loaded * 100) / pe.total);
        setUploadProgress(pct);
      });
      const doc = res.data;
      pollingRef.current.add(doc.id);
      setUploadOpen(false);
      toast.success('Document uploaded. Processing has started.');
      fetchDocuments();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Upload failed');
    } finally {
      setUploading(false);
      setSelectedFile(null);
    }
  };

  const handleDownload = async (doc) => {
    try {
      const res = await documentService.download(doc.id);
      const blobUrl = URL.createObjectURL(res.data);
      const a = document.createElement('a');
      a.href = blobUrl;
      a.download = doc.originalFilename || `doc-${doc.id}`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(blobUrl);
      toast.success('Download started');
    } catch {
      toast.error('Download failed');
    }
  };

  const handlePreview = async (doc) => {
    setPreviewLoading(true);
    setPreviewOpen(true);
    try {
      const res = await documentService.getPreview(doc.id);
      setPreviewData(res.data);
    } catch {
      toast.error('Failed to load document preview');
      setPreviewOpen(false);
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleVersions = async (doc) => {
    setVersionsLoading(true);
    setVersionsOpen(true);
    setVersionsTitle(doc.title);
    try {
      const res = await documentService.getVersions(doc.id);
      setVersionsData(res.data ?? []);
    } catch {
      toast.error('Failed to load version history');
      setVersionsOpen(false);
    } finally {
      setVersionsLoading(false);
    }
  };

  const handleReprocess = async (doc) => {
    try {
      const res = await documentService.reprocess(doc.id);
      pollingRef.current.add(res.data.id);
      toast.success('Document reprocessing started');
      fetchDocuments();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Reprocess failed');
    }
  };

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 3 }}>Documents</Typography>

      <Paper elevation={2} sx={{ borderRadius: 3, p: 2, mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
          <TextField
            size="small"
            placeholder="Search documents..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSearch(); }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start"><SearchIcon sx={{ color: 'text.secondary' }} /></InputAdornment>
                ),
              },
            }}
            sx={{ flexGrow: 1, minWidth: 200 }}
          />
          <FormControl size="small" sx={{ minWidth: 160 }}>
            <InputLabel>Department</InputLabel>
            <Select
              value={departmentFilter}
              label="Department"
              onChange={(e) => { setDepartmentFilter(e.target.value); setPage(0); }}
            >
              <MenuItem value="">All</MenuItem>
              {departments.map((d) => (
                <MenuItem key={d.id} value={d.name}>{d.name}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 140 }}>
            <InputLabel>Status</InputLabel>
            <Select
              value={statusFilter}
              label="Status"
              onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
            >
              {STATUS_OPTIONS.map((s) => (
                <MenuItem key={s} value={s}>{s || 'All'}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <Button variant="contained" startIcon={<CloudUploadIcon />} onClick={openUpload}>
            Upload
          </Button>
        </Box>
      </Paper>

      <Paper elevation={2} sx={{ borderRadius: 3, p: 2 }}>
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
                    <TableCell sx={{ fontWeight: 600 }}>Title</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Type</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Department</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Uploaded By</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Status</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Created</TableCell>
                    <TableCell sx={{ fontWeight: 600 }} align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {documents.map((doc) => (
                    <TableRow key={doc.id} hover>
                      <TableCell sx={{ maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        <Tooltip title={doc.title}><span>{doc.title}</span></Tooltip>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={doc.fileType?.toUpperCase()}
                          size="small"
                          color={FILE_TYPE_COLORS[doc.fileType] || 'default'}
                          variant="outlined"
                        />
                      </TableCell>
                      <TableCell sx={{ color: 'text.secondary' }}>{doc.department || '—'}</TableCell>
                      <TableCell sx={{ color: 'text.secondary' }}>{doc.uploadedBy || '—'}</TableCell>
                      <TableCell>
                        <Chip
                          label={doc.status}
                          size="small"
                          color={STATUS_COLORS[doc.status] || 'default'}
                        />
                      </TableCell>
                      <TableCell>{formatDate(doc.uploadedAt)}</TableCell>
                      <TableCell align="right">
                        <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
                          <Tooltip title="Download">
                            <IconButton size="small" onClick={() => handleDownload(doc)}>
                              <DownloadIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Preview Metadata">
                            <IconButton size="small" onClick={() => handlePreview(doc)}>
                              <VisibilityIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="View Versions">
                            <IconButton size="small" onClick={() => handleVersions(doc)}>
                              <HistoryIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Reprocess">
                            <IconButton size="small" onClick={() => handleReprocess(doc)}>
                              <RefreshIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))}
                  {documents.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={7} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                        <InsertDriveFileIcon sx={{ fontSize: 40, mb: 1, opacity: 0.4 }} />
                        <Typography variant="body2">No documents found</Typography>
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
              onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
              rowsPerPageOptions={[5, 10, 25, 50]}
            />
          </>
        )}
      </Paper>

      <Dialog open={uploadOpen} onClose={() => !uploading && setUploadOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Upload Document</DialogTitle>
        <DialogContent>
          <Box
            sx={{
              border: '2px dashed',
              borderColor: dragOver ? 'primary.main' : 'grey.300',
              borderRadius: 2,
              p: 4,
              textAlign: 'center',
              bgcolor: dragOver ? 'action.hover' : 'transparent',
              transition: 'all 0.2s',
              cursor: 'pointer',
              mb: 2,
            }}
            onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
            onDragLeave={() => setDragOver(false)}
            onDrop={handleDrop}
            onClick={() => document.getElementById('file-input').click()}
          >
            <input id="file-input" type="file" hidden onChange={handleFileSelect} accept=".pdf,.docx,.md,.txt" />
            <CloudUploadIcon sx={{ fontSize: 48, color: 'primary.main', mb: 1 }} />
            <Typography variant="body1" sx={{ fontWeight: 500 }}>
              {selectedFile ? selectedFile.name : 'Drop a file here or click to browse'}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Supported: PDF, DOCX, MD, TXT
            </Typography>
          </Box>
          <FormControl fullWidth size="small">
            <InputLabel>Department (optional)</InputLabel>
            <Select
              value={uploadDeptId}
              label="Department (optional)"
              onChange={(e) => setUploadDeptId(e.target.value)}
            >
              <MenuItem value=""><em>None</em></MenuItem>
              {departments.map((d) => (
                <MenuItem key={d.id} value={d.id}>{d.name}</MenuItem>
              ))}
            </Select>
          </FormControl>
          {uploading && (
            <Box sx={{ mt: 2 }}>
              <Typography variant="body2" sx={{ mb: 0.5 }}>Uploading... {uploadProgress}%</Typography>
              <LinearProgress variant="determinate" value={uploadProgress} />
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setUploadOpen(false)} disabled={uploading}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleUpload}
            disabled={!selectedFile || uploading}
            startIcon={uploading ? <CircularProgress size={16} /> : <CloudUploadIcon />}
          >
            {uploading ? 'Uploading...' : 'Upload'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={previewOpen} onClose={() => setPreviewOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Document Preview</DialogTitle>
        <DialogContent>
          {previewLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}><CircularProgress /></Box>
          ) : previewData ? (
            <Box>
              <Typography variant="h6" gutterBottom>{previewData.title}</Typography>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary" display="block">Author</Typography>
                  <Typography variant="body2">{previewData.author || '—'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary" display="block">Department</Typography>
                  <Typography variant="body2">{previewData.department || '—'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary" display="block">Language</Typography>
                  <Typography variant="body2">{previewData.language || '—'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary" display="block">Pages</Typography>
                  <Typography variant="body2">{previewData.pageCount ?? '—'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary" display="block">Version</Typography>
                  <Typography variant="body2">{previewData.version || '—'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary" display="block">Uploaded By</Typography>
                  <Typography variant="body2">{previewData.uploadedBy || '—'}</Typography>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="caption" color="text.secondary" display="block">Created</Typography>
                  <Typography variant="body2">{formatDateTime(previewData.createdAt)}</Typography>
                </Grid>
                {previewData.tags?.length > 0 && (
                  <Grid item xs={12}>
                    <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.5 }}>Tags</Typography>
                    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                      {previewData.tags.map((tag, i) => (
                        <Chip key={i} label={tag} size="small" variant="outlined" />
                      ))}
                    </Box>
                  </Grid>
                )}
                {previewData.summary && (
                  <Grid item xs={12}>
                    <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.5 }}>Summary</Typography>
                    <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 1, bgcolor: 'grey.50' }}>
                      <Typography variant="body2">{previewData.summary}</Typography>
                    </Paper>
                  </Grid>
                )}
              </Grid>
            </Box>
          ) : (
            <Typography color="text.secondary">No preview available</Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPreviewOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={versionsOpen} onClose={() => setVersionsOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Version History — {versionsTitle}</DialogTitle>
        <DialogContent>
          {versionsLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}><CircularProgress /></Box>
          ) : versionsData.length === 0 ? (
            <Typography color="text.secondary">No version history available</Typography>
          ) : (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
              {versionsData.map((v) => (
                <Paper key={v.id} variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <Box>
                      <Typography variant="subtitle2">
                        Version {v.version}
                        {v.isLatest && (
                          <Chip label="Latest" size="small" color="primary" sx={{ ml: 1, height: 20 }} />
                        )}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" display="block">
                        {v.originalFilename}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {v.fileType?.toUpperCase()} — {formatBytes(v.fileSize)} — {formatDateTime(v.createdAt)}
                      </Typography>
                    </Box>
                    <IconButton
                      size="small"
                      onClick={() => handleDownload({ id: v.id, originalFilename: v.originalFilename })}
                    >
                      <DownloadIcon fontSize="small" />
                    </IconButton>
                  </Box>
                </Paper>
              ))}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setVersionsOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
