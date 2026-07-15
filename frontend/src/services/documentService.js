import api from './api';

export const getAll = (params) => api.get('/documents', { params });

export const getById = (id) => api.get(`/documents/${id}`);

export const getStatus = (id) => api.get(`/documents/${id}/status`);

export const getPreview = (id) => api.get(`/documents/${id}/preview`);

export const getVersions = (id) => api.get(`/documents/${id}/versions`);

export const upload = (formData, onProgress) =>
  api.post('/documents', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress,
  });

export const download = (id) =>
  api.get(`/documents/${id}/download`, { responseType: 'blob' });

export const reprocess = (id) => api.post(`/documents/${id}/reprocess`);
