import api from './api';

export const getAll = (params) => api.get('/users', { params });

export const getById = (id) => api.get(`/users/${id}`);

export const update = (id, data) => api.put(`/users/${id}`, data);

export const updateStatus = (id, data) => api.patch(`/users/${id}/status`, data);
