import api from './api';

export const getDashboard = () => api.get('/admin/dashboard');

export const getRagMetrics = () => api.get('/admin/rag/metrics');

export const getRecentFailures = (page = 0, size = 5) =>
  api.get('/documents', { params: { status: 'FAILED', page, size, sort: 'uploadedAt,desc' } });

export const getRecentActivity = (page = 0, size = 6) =>
  api.get('/admin/activity', { params: { page, size, sort: 'createdAt,desc' } });

export const getActivityLogs = (params) =>
  api.get('/admin/activity', { params });

export const deleteOldActivity = (olderThanDays) =>
  api.delete('/admin/activity', { params: { olderThanDays } });