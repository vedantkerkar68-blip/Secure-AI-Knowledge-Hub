import api from './api';

export const getAll = (params) => api.get('/departments', { params });

export const getById = (id) => api.get(`/departments/${id}`);

export const create = (data) => api.post('/departments', data);

export const update = (id, data) => api.put(`/departments/${id}`, data);

export const remove = (id) => api.delete(`/departments/${id}`);
