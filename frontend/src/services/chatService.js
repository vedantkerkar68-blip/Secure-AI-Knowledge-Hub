import api from './api';

export const createSession = (data) => api.post('/chat/sessions', data);

export const listSessions = () => api.get('/chat/sessions');

export const getSession = (sessionId) => api.get(`/chat/sessions/${sessionId}`);

export const deleteSession = (sessionId) => api.delete(`/chat/sessions/${sessionId}`);

export const sendMessage = (data) => api.post('/chat', data);