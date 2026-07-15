import api from './api';

export const createSession = (data) => api.post('/chat/sessions', data);

export const sendMessage = (data) => api.post('/chat', data);
