import api from './api';

export const login = (credentials) => api.post('/auth/login', credentials);

export const register = (data) => api.post('/auth/register', data);

export const getProfile = () => api.get('/users/me');
