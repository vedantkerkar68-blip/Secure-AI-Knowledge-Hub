import api from './api';

export const getAll = (params) => api.get('/admin/activity', { params });
