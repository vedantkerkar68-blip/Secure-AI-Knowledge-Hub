import { useState, useEffect, useRef } from 'react';
import {
  Box, Paper, Typography, TextField, IconButton, Button, List, ListItemButton,
  ListItemText, Divider, Chip, Drawer, Card, CardContent, CircularProgress,
  Avatar, Tooltip, Skeleton,
} from '@mui/material';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { toast } from 'react-toastify';
import SendIcon from '@mui/icons-material/Send';
import AddIcon from '@mui/icons-material/Add';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import PersonIcon from '@mui/icons-material/Person';
import ArticleIcon from '@mui/icons-material/Article';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import CloseIcon from '@mui/icons-material/Close';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { useAuth } from '../context/AuthContext';
import * as chatService from '../services/chatService';
import * as documentService from '../services/documentService';

const SIDEBAR_WIDTH = 280;
const DRAWER_WIDTH = 360;

function truncate(str, len) {
  if (!str || str.length <= len) return str;
  return str.substring(0, len) + '...';
}

function formatTime(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
}

function confidenceLabel(val) {
  const pct = Math.round((val ?? 0) * 100);
  if (pct >= 70) return { label: 'High', color: 'success' };
  if (pct >= 40) return { label: 'Medium', color: 'warning' };
  return { label: 'Low', color: 'error' };
}

const storageKey = (uid) => `sakh_chat_sessions_${uid}`;

export default function Chat() {
  const { user } = useAuth();
  const userId = user?.id;

  const [sessions, setSessions] = useState([]);
  const [sessionsLoading, setSessionsLoading] = useState(true);
  const [activeSessionId, setActiveSessionId] = useState(null);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [sourcesOpen, setSourcesOpen] = useState(false);
  const [sourcesData, setSourcesData] = useState([]);
  const messagesEndRef = useRef(null);

  const activeSession = sessions.find((s) => s.id === activeSessionId) || null;
  const messages = activeSession?.messages || [];

  useEffect(() => {
    setSessions([]);
    setActiveSessionId(null);
    if (!userId) { setSessionsLoading(false); return; }
    setSessionsLoading(true);
    Promise.resolve().then(() => {
      try {
        const raw = localStorage.getItem(storageKey(userId));
        setSessions(raw ? JSON.parse(raw) : []);
      } catch { setSessions([]); }
      setSessionsLoading(false);
    });
  }, [userId]);

  useEffect(() => {
    if (!userId) return;
    localStorage.setItem(storageKey(userId), JSON.stringify(sessions));
  }, [sessions, userId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending]);

  const createNewSession = async () => {
    try {
      const res = await chatService.createSession({ title: 'New Chat' });
      const newSession = {
        id: res.data.id, title: 'New Chat', createdAt: res.data.createdAt, messages: [],
      };
      setSessions((prev) => [newSession, ...prev]);
      setActiveSessionId(newSession.id);
    } catch {
      toast.error('Failed to create chat session');
    }
  };

  const deleteSession = (e, id) => {
    e.stopPropagation();
    setSessions((prev) => prev.filter((s) => s.id !== id));
    if (activeSessionId === id) setActiveSessionId(null);
  };

  const handleSend = async () => {
    const question = input.trim();
    if (!question || !activeSessionId || sending) return;
    setInput('');
    setSending(true);

    const userMsg = {
      id: Date.now(), role: 'user', content: question, timestamp: new Date().toISOString(),
    };

    setSessions((prev) =>
      prev.map((s) => (s.id === activeSessionId ? { ...s, messages: [...s.messages, userMsg] } : s))
    );

    try {
      const res = await chatService.sendMessage({ sessionId: activeSessionId, question });
      const { answer, confidence, citations } = res.data;

      const assistantMsg = {
        id: Date.now() + 1, role: 'assistant', content: answer,
        confidence: confidence ?? 0, citations: citations ?? [],
        timestamp: new Date().toISOString(),
      };

      setSessions((prev) =>
        prev.map((s) => {
          if (s.id !== activeSessionId) return s;
          return {
            ...s,
            title: s.title === 'New Chat' ? truncate(question, 60) : s.title,
            messages: [...s.messages, assistantMsg],
          };
        })
      );
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to get AI response');
    } finally {
      setSending(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); }
  };

  const openSourcesDrawer = (citations) => {
    setSourcesData(citations || []);
    setSourcesOpen(true);
  };

  const handleOpenDocument = async (docId) => {
    if (!docId) return;
    try {
      const res = await documentService.download(docId);
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `document-${docId}`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch {
      toast.error('Failed to download document');
    }
  };

  return (
    <Box sx={{ display: 'flex', height: `calc(100vh - 64px - 48px)`, mx: -3, mt: -3 }}>
      <Paper elevation={0} sx={{
        width: SIDEBAR_WIDTH, minWidth: SIDEBAR_WIDTH, borderRadius: 0,
        borderRight: '1px solid', borderColor: 'divider', display: 'flex', flexDirection: 'column',
      }}>
        <Box sx={{ p: 2 }}>
          <Button variant="contained" fullWidth startIcon={<AddIcon />} onClick={createNewSession} sx={{ py: 1 }}>New Chat</Button>
        </Box>
        <Divider />
        <Box sx={{ flexGrow: 1, overflow: 'auto' }}>
          {sessionsLoading ? (
            <Box sx={{ p: 2 }}>
              {[1, 2, 3, 4, 5].map((i) => (
                <Box key={i} sx={{ mb: 1.5 }}>
                  <Skeleton variant="rounded" height={44} sx={{ borderRadius: 2 }} />
                </Box>
              ))}
            </Box>
          ) : (
            <List disablePadding>
              {sessions.map((session) => (
                <ListItemButton key={session.id} selected={session.id === activeSessionId}
                  onClick={() => setActiveSessionId(session.id)}
                  sx={{ px: 2, py: 1.5, '&.Mui-selected': { bgcolor: 'primary.light', color: 'primary.contrastText', '&:hover': { bgcolor: 'primary.light' } } }}>
                  <ListItemText primary={truncate(session.title, 30)}
                    secondary={session.messages?.length > 0 ? `${session.messages.length} messages` : null}
                    primaryTypographyProps={{ variant: 'body2', noWrap: true, fontWeight: session.id === activeSessionId ? 600 : 400 }}
                    secondaryTypographyProps={{ variant: 'caption', sx: { color: session.id === activeSessionId ? 'rgba(255,255,255,0.7)' : 'text.secondary' } }}
                    sx={{ flexGrow: 1, minWidth: 0 }} />
                  <IconButton size="small" onClick={(e) => deleteSession(e, session.id)}
                    sx={{ opacity: 0.5, '&:hover': { opacity: 1 }, color: session.id === activeSessionId ? 'inherit' : undefined }}>
                    <DeleteOutlineIcon fontSize="small" />
                  </IconButton>
                </ListItemButton>
              ))}
              {!sessionsLoading && sessions.length === 0 && (
                <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 4, px: 2 }}>
                  No conversations yet. Start a new chat.
                </Typography>
              )}
            </List>
          )}
        </Box>
      </Paper>

      <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', bgcolor: '#f5f7fa' }}>
        {!activeSession ? (
          <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 2 }}>
            <Avatar sx={{ width: 64, height: 64, bgcolor: 'primary.main' }}>
              <AutoAwesomeIcon sx={{ fontSize: 32 }} />
            </Avatar>
            <Typography variant="h5" sx={{ fontWeight: 600 }}>Secure AI Knowledge Hub</Typography>
            <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
              Ask questions about your organization's documents
            </Typography>
            <Button variant="contained" size="large" startIcon={<AddIcon />} onClick={createNewSession}>
              Start New Chat
            </Button>
          </Box>
        ) : (
          <>
            <Box sx={{ flexGrow: 1, overflow: 'auto', px: { xs: 2, md: 6 }, py: 3 }}>
              {messages.length === 0 && (
                <Box sx={{ textAlign: 'center', py: 6 }}>
                  <SmartToyIcon sx={{ fontSize: 48, color: 'primary.main', opacity: 0.5, mb: 2 }} />
                  <Typography variant="h6" color="text.secondary">{activeSession.title}</Typography>
                  <Typography variant="body2" color="text.secondary">Ask a question to get started</Typography>
                </Box>
              )}
              {messages.map((msg) => (
                <Box key={msg.id} sx={{ mb: 3, display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }}>
                  <Box sx={{ maxWidth: msg.role === 'user' ? '55%' : '72%', minWidth: 0 }}>
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, flexDirection: msg.role === 'user' ? 'row-reverse' : 'row' }}>
                      <Avatar sx={{ width: 34, height: 34, bgcolor: msg.role === 'user' ? 'primary.main' : 'grey.300', color: msg.role === 'user' ? '#fff' : 'text.primary' }}>
                        {msg.role === 'user' ? <PersonIcon sx={{ fontSize: 20 }} /> : <SmartToyIcon sx={{ fontSize: 20 }} />}
                      </Avatar>
                      <Paper elevation={0} sx={{
                        px: 2.5, py: 1.5, borderRadius: 2,
                        bgcolor: msg.role === 'user' ? 'primary.main' : '#ffffff',
                        color: msg.role === 'user' ? '#fff' : 'text.primary',
                        border: msg.role === 'user' ? 'none' : '1px solid',
                        borderColor: 'divider',
                      }}>
                        {msg.role === 'user' ? (
                          <Typography variant="body2" sx={{ lineHeight: 1.7, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{msg.content}</Typography>
                        ) : (
                          <Box sx={{
                            '& p': { my: 0.5, lineHeight: 1.6 },
                            '& ul, & ol': { pl: 2.5, my: 0.5 },
                            '& li': { my: 0.25 },
                            '& code': { bgcolor: 'grey.100', px: 0.5, borderRadius: 0.5, fontSize: '0.85em' },
                            '& pre': { bgcolor: 'grey.100', p: 1.5, borderRadius: 1, overflow: 'auto', fontSize: '0.85em' },
                            '& table': { borderCollapse: 'collapse', width: '100%', my: 1, fontSize: '0.85em' },
                            '& th, & td': { border: '1px solid', borderColor: 'divider', p: 0.75, textAlign: 'left' },
                            '& th': { bgcolor: 'grey.100', fontWeight: 600 },
                            '& blockquote': { borderLeft: '3px solid', borderColor: 'primary.main', pl: 1.5, ml: 0, my: 1, color: 'text.secondary' },
                            '& a': { color: 'primary.main' },
                            '& h1, & h2, & h3, & h4, & h5, & h6': { my: 1, fontWeight: 600 },
                            '& img': { maxWidth: '100%', borderRadius: 1 },
                          }}>
                            <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
                          </Box>
                        )}
                      </Paper>
                    </Box>
                    <Box sx={{ ml: 6, mt: 0.5, display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
                      {msg.role === 'assistant' && msg.confidence != null && (
                        <Chip
                          icon={<AutoAwesomeIcon sx={{ fontSize: 13 }} />}
                          label={confidenceLabel(msg.confidence).label}
                          size="small"
                          color={confidenceLabel(msg.confidence).color}
                          variant="outlined"
                          sx={{ height: 22, '& .MuiChip-label': { fontSize: 11, px: 0.5 } }}
                        />
                      )}
                      {msg.role === 'assistant' && msg.citations?.length > 0 && (
                        <Chip
                          icon={<ArticleIcon sx={{ fontSize: 13 }} />}
                          label={`${msg.citations.length} source${msg.citations.length > 1 ? 's' : ''}`}
                          size="small"
                          variant="outlined"
                          color="primary"
                          onClick={() => openSourcesDrawer(msg.citations)}
                          sx={{ height: 22, cursor: 'pointer', '& .MuiChip-label': { fontSize: 11, px: 0.5 } }}
                        />
                      )}
                      {msg.timestamp && (
                        <Typography variant="caption" color="text.disabled" sx={{ fontSize: 10 }}>{formatTime(msg.timestamp)}</Typography>
                      )}
                    </Box>
                  </Box>
                </Box>
              ))}
              {sending && (
                <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, mb: 2 }}>
                  <Avatar sx={{ width: 34, height: 34, bgcolor: 'grey.300' }}>
                    <SmartToyIcon sx={{ fontSize: 20, color: 'text.primary' }} />
                  </Avatar>
                  <Paper elevation={0} sx={{ px: 3, py: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider', bgcolor: '#fff' }}>
                    <TypingDots />
                  </Paper>
                </Box>
              )}
              <div ref={messagesEndRef} />
            </Box>
            <Box sx={{ px: { xs: 2, md: 6 }, py: 2, bgcolor: '#ffffff', borderTop: '1px solid', borderColor: 'divider' }}>
              <Paper elevation={0} variant="outlined" sx={{ borderRadius: 3, px: 2, py: 0.5, display: 'flex', alignItems: 'flex-end', gap: 1 }}>
                <TextField fullWidth multiline maxRows={6} placeholder="Ask a question..." value={input}
                  onChange={(e) => setInput(e.target.value)} onKeyDown={handleKeyDown}
                  disabled={sending} variant="standard"
                  slotProps={{ input: { disableUnderline: true, sx: { py: 1 } } }} />
                <IconButton color="primary" onClick={handleSend} disabled={!input.trim() || sending} sx={{ mb: 0.5 }}>
                  {sending ? <CircularProgress size={22} /> : <SendIcon />}
                </IconButton>
              </Paper>
              <Typography variant="caption" color="text.disabled" sx={{ display: 'block', textAlign: 'center', mt: 0.5 }}>
                AI responses are generated from your organization's documents
              </Typography>
            </Box>
          </>
        )}
      </Box>

      <Drawer anchor="right" open={sourcesOpen} onClose={() => setSourcesOpen(false)}
        sx={{ '& .MuiDrawer-paper': { width: DRAWER_WIDTH, p: 2 } }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
          <Typography variant="h6">Sources ({sourcesData.length})</Typography>
          <IconButton onClick={() => setSourcesOpen(false)} size="small"><CloseIcon /></IconButton>
        </Box>
        <Divider sx={{ mb: 2 }} />
        {sourcesData.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>No sources available</Typography>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            {sourcesData.map((cit, i) => (
              <SourceCard key={i} citation={cit} onOpenDocument={handleOpenDocument} />
            ))}
          </Box>
        )}
      </Drawer>
    </Box>
  );
}

function SourceCard({ citation, onOpenDocument }) {
  const pct = Math.round((citation.similarityScore ?? 0) * 100);
  return (
    <Card variant="outlined" sx={{ borderRadius: 2 }}>
      <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 } }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 0.5 }}>
          <Typography variant="caption" sx={{ fontWeight: 600, flexGrow: 1, mr: 1 }}>
            {citation.documentTitle || 'Untitled'}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <Chip label={`${pct}%`} size="small" variant="outlined"
              color={pct >= 70 ? 'success' : pct >= 40 ? 'warning' : 'error'}
              sx={{ height: 20, '& .MuiChip-label': { fontSize: 10, px: 0.5 } }} />
            {citation.documentId && (
              <Tooltip title="Open Document">
                <IconButton size="small" onClick={() => onOpenDocument(citation.documentId)} sx={{ p: 0.3 }}>
                  <OpenInNewIcon sx={{ fontSize: 14 }} />
                </IconButton>
              </Tooltip>
            )}
          </Box>
        </Box>
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', mb: 0.5 }}>
          {citation.department && <Typography variant="caption" color="text.secondary">Dept: {citation.department}</Typography>}
          {citation.pageNumber != null && <Typography variant="caption" color="text.secondary">Page: {citation.pageNumber}</Typography>}
          {citation.sectionTitle && <Typography variant="caption" color="text.secondary">Section: {citation.sectionTitle}</Typography>}
          {citation.version != null && <Typography variant="caption" color="text.secondary">v{citation.version}</Typography>}
        </Box>
        {citation.chunkContent && (
          <Typography variant="caption" color="text.secondary" sx={{
            display: 'block', mt: 0.5, p: 0.75, bgcolor: 'grey.50', borderRadius: 1,
            fontStyle: 'italic', lineHeight: 1.4, maxHeight: 60, overflow: 'hidden',
          }}>
            &ldquo;{truncateChunk(citation.chunkContent, 120)}&rdquo;
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

function truncateChunk(text, maxLen) {
  if (!text || text.length <= maxLen) return text || '';
  return text.substring(0, maxLen) + '...';
}

function TypingDots() {
  return (
    <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center', py: 0.5 }}>
      {[0, 1, 2].map((i) => (
        <Box key={i} sx={{
          width: 8, height: 8, borderRadius: '50%', bgcolor: 'text.disabled',
          animation: 'bounce 1.4s infinite ease-in-out both',
          animationDelay: `${i * 0.16}s`,
          '@keyframes bounce': {
            '0%, 80%, 100%': { transform: 'scale(0)' },
            '40%': { transform: 'scale(1)' },
          },
        }} />
      ))}
    </Box>
  );
}
