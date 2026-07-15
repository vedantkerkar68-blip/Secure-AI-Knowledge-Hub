import api from './api';

export const getDashboard = () => api.get('/admin/dashboard');
