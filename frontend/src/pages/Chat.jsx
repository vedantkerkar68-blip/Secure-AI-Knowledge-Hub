import { useState, useEffect, useRef, useCallback } from 'react';
import {
  Box, Paper, Typography, TextField, IconButton, Button, List, ListItem, ListItemButton,
  ListItemText, Divider, Chip, Drawer, Card, CardContent, CircularProgress,
  Avatar, Tooltip, Skeleton, Alert, Dialog, DialogTitle, DialogContent, DialogActions,
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
import AttachFileIcon from '@mui/icons-material/AttachFile';
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

function formatSessionTime(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const now = new Date();
  const sameDay = d.toDateString() === now.toDateString();
  if (sameDay) {
    return d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
  }
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function confidenceLabel(val) {
  const pct = Math.round((val ?? 0) * 100);
  if (pct >= 70) return { label: 'High', color: 'success' };
  if (pct >= 40) return { label: 'Medium', color: 'warning' };
  return { label: 'Low', color: 'error' };
}

function dedupeSources(citations) {
  const seen = new Set();
  return (citations || []).filter((c) => {
    const key = c.documentId ?? c.documentTitle ?? '';
    if (!key || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function normalizeMessage(m) {
  return {
    id: m.id,
    role: m.role,
    content: m.content,
    confidence: m.confidence,
    citations: dedupeSources(m.citations),
    timestamp: m.createdAt,
  };
}

function normalizeSession(s) {
  return {
    id: s.id,
    title: s.title,
    createdAt: s.createdAt,
    updatedAt: s.updatedAt,
    messages: [],
  };
}

export default function Chat() {
  const { user } = useAuth();
  const userId = user?.id;

  const [sessions, setSessions] = useState([]);
  const [sessionsLoading, setSessionsLoading] = useState(true);
  const [sessionsError, setSessionsError] = useState(false);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [activeSessionId, setActiveSessionId] = useState(null);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [attachOpen, setAttachOpen] = useState(false);
  const [attachDocs, setAttachDocs] = useState([]);
  const [attachLoading, setAttachLoading] = useState(false);
  const [attachedDoc, setAttachedDoc] = useState(null);
  const [sourcesOpen, setSourcesOpen] = useState(false);
  const [sourcesData, setSourcesData] = useState([]);
  const messagesEndRef = useRef(null);

  const activeSession = sessions.find((s) => s.id === activeSessionId) || null;
  const messages = activeSession?.messages || [];

  useEffect(() => {
    setSessions([]);
    setActiveSessionId(null);
    if (!userId) { setSessionsLoading(false); return; }
    let cancelled = false;
    setSessionsLoading(true);
    setSessionsError(false);
    chatService.listSessions()
      .then((res) => { if (!cancelled) setSessions(res.data.map(normalizeSession)); })
      .catch(() => { if (!cancelled) setSessionsError(true); })
      .finally(() => { if (!cancelled) setSessionsLoading(false); });
    return () => { cancelled = true; };
  }, [userId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending]);

  const selectSession = useCallback(async (id) => {
    setActiveSessionId(id);
    setMessagesLoading(true);
    try {
      const res = await chatService.getSession(id);
      setSessions((prev) => prev.map((s) => (
        s.id === id
          ? { ...s, title: res.data.title, messages: res.data.messages.map(normalizeMessage) }
          : s
      )));
    } catch {
      toast.error('Failed to load conversation');
    } finally {
      setMessagesLoading(false);
    }
  }, []);

  const createNewSession = async () => {
    try {
      const res = await chatService.createSession({ title: 'New Chat' });
      const newSession = normalizeSession(res.data);
      setSessions((prev) => [newSession, ...prev]);
      setActiveSessionId(newSession.id);
    } catch {
      toast.error('Failed to create chat session');
    }
  };

  const handleDeleteSession = async (e, id) => {
    e.stopPropagation();
    try {
      await chatService.deleteSession(id);
      setSessions((prev) => prev.filter((s) => s.id !== id));
      if (activeSessionId === id) setActiveSessionId(null);
    } catch {
      toast.error('Failed to delete conversation');
    }
  };

  const openAttachDialog = async () => {
    setAttachOpen(true);
    if (attachDocs.length > 0) return;
    setAttachLoading(true);
    try {
      const res = await documentService.getAll({ page: 0, size: 200, sort: 'id,desc' });
      const docs = (res.data?.content ?? []).filter((d) => d.status === 'READY');
      setAttachDocs(docs);
    } catch {
      toast.error('Failed to load documents');
    } finally {
      setAttachLoading(false);
    }
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
      const res = await chatService.sendMessage({
        sessionId: activeSessionId,
        question,
        documentId: attachedDoc?.id ?? null,
      });
      setAttachedDoc(null);
      const { answer, confidence, citations } = res.data;

      const assistantMsg = {
        id: Date.now() + 1, role: 'assistant', content: answer,
        confidence: confidence ?? 0, citations: dedupeSources(citations),
        timestamp: new Date().toISOString(),
      };

      setSessions((prev) =>
        prev.map((s) => {
          if (s.id !== activeSessionId) return s;
          return {
            ...s,
            title: s.title === 'New Chat' ? truncate(question, 60) : s.title,
            updatedAt: new Date().toISOString(),
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
    <Box sx={{ display: 'flex', height: { xs: 'calc(100vh - 56px)', md: 'calc(100vh - 64px)' } }}>
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
          ) : sessionsError ? (
            <Box sx={{ p: 2 }}>
              <Alert severity="error" sx={{ mb: 1.5 }}>Could not load conversations.</Alert>
              <Button variant="outlined" size="small" fullWidth onClick={() => { setSessionsError(false); setSessionsLoading(true); chatService.listSessions().then((res) => setSessions(res.data.map(normalizeSession))).catch(() => setSessionsError(true)).finally(() => setSessionsLoading(false)); }}>
                Retry
              </Button>
            </Box>
          ) : (
            <List disablePadding>
              {sessions.map((session) => (
                <ListItemButton key={session.id} selected={session.id === activeSessionId}
                  onClick={() => selectSession(session.id)}
                  sx={{
                    mx: 1, mb: 0.5, px: 1.5, py: 1.25, borderRadius: 2,
                    '&.Mui-selected': {
                      bgcolor: 'primary.soft',
                      color: 'primary.dark',
                      '&:hover': { bgcolor: 'primary.soft' },
                    },
                    '&:hover': { bgcolor: 'action.hover' },
                  }}>
                  <ListItemText
                    primary={truncate(session.title, 30)}
                    secondary={
                      session.messages?.length > 0
                        ? `${session.messages.length} messages`
                        : (session.updatedAt ? formatSessionTime(session.updatedAt) : null)
                    }
                    primaryTypographyProps={{ variant: 'body2', noWrap: true, fontWeight: session.id === activeSessionId ? 700 : 500, color: session.id === activeSessionId ? 'primary.dark' : undefined }}
                    secondaryTypographyProps={{ variant: 'caption', sx: { color: session.id === activeSessionId ? 'rgba(24,108,90,0.75)' : 'text.secondary' } }}
                    sx={{ flexGrow: 1, minWidth: 0 }} />
                  <IconButton size="small" onClick={(e) => handleDeleteSession(e, session.id)}
                    sx={{ opacity: 0.4, '&:hover': { opacity: 1 }, color: session.id === activeSessionId ? 'primary.dark' : 'inherit' }}>
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

      <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
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
              {messagesLoading ? (
                <Box sx={{ py: 4, display: 'flex', justifyContent: 'center' }}>
                  <CircularProgress size={28} />
                </Box>
              ) : messages.length === 0 ? (
                <Box sx={{ textAlign: 'center', py: 6 }}>
                  <SmartToyIcon sx={{ fontSize: 48, color: 'primary.main', opacity: 0.5, mb: 2 }} />
                  <Typography variant="h6" color="text.secondary">{activeSession.title}</Typography>
                  <Typography variant="body2" color="text.secondary">Ask a question to get started</Typography>
                </Box>
              ) : (
                messages.map((msg) => (
                  <Box key={msg.id} sx={{ mb: 3, display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }}>
                    <Box sx={{ maxWidth: msg.role === 'user' ? '55%' : '72%', minWidth: 0 }}>
                      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, flexDirection: msg.role === 'user' ? 'row-reverse' : 'row' }}>
                        <Avatar sx={{ width: 34, height: 34, bgcolor: msg.role === 'user' ? 'primary.main' : 'action.hover', color: msg.role === 'user' ? 'primary.contrastText' : 'text.primary' }}>
                          {msg.role === 'user' ? <PersonIcon sx={{ fontSize: 20 }} /> : <SmartToyIcon sx={{ fontSize: 20 }} />}
                        </Avatar>
                        <Paper elevation={0} sx={{
                          px: 2.5, py: 1.5, borderRadius: 2,
                          bgcolor: msg.role === 'user' ? 'primary.main' : 'background.paper',
                          color: msg.role === 'user' ? 'primary.contrastText' : 'text.primary',
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
                              '& code': { bgcolor: 'action.hover', px: 0.5, borderRadius: 0.5, fontSize: '0.85em' },
                              '& pre': { bgcolor: 'action.hover', p: 1.5, borderRadius: 1, overflow: 'auto', fontSize: '0.85em' },
                              '& table': { borderCollapse: 'collapse', width: '100%', my: 1, fontSize: '0.85em' },
                              '& th, & td': { border: '1px solid', borderColor: 'divider', p: 0.75, textAlign: 'left' },
                              '& th': { bgcolor: 'action.hover', fontWeight: 600 },
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
                ))
              )}
              {sending && (
                <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, mb: 2 }}>
                  <Avatar sx={{ width: 34, height: 34, bgcolor: 'action.hover' }}>
                    <SmartToyIcon sx={{ fontSize: 20, color: 'text.primary' }} />
                  </Avatar>
                  <Paper elevation={0} sx={{ px: 3, py: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider', bgcolor: 'background.paper' }}>
                    <TypingDots />
                  </Paper>
                </Box>
              )}
              <div ref={messagesEndRef} />
            </Box>
            <Box sx={{ px: { xs: 2, md: 6 }, py: 2, bgcolor: 'background.paper', borderTop: '1px solid', borderColor: 'divider' }}>
              {attachedDoc && (
                <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 1 }}>
                  <Chip
                    icon={<ArticleIcon sx={{ fontSize: 15 }} />}
                    label={`Answering within: ${truncate(attachedDoc.title, 40)}`}
                    onDelete={() => setAttachedDoc(null)}
                    color="primary"
                    size="small"
                    variant="outlined"
                    sx={{ maxWidth: '100%' }}
                  />
                </Box>
              )}
              <Paper elevation={0} variant="outlined" sx={{ borderRadius: 3, px: 1, py: 0.5, display: 'flex', alignItems: 'flex-end', gap: 0.5 }}>
                <Tooltip title={attachedDoc ? 'Change attached document' : 'Attach a document'}>
                  <span>
                    <IconButton onClick={openAttachDialog} disabled={sending}
                      color={attachedDoc ? 'primary' : 'default'} sx={{ mb: 0.5 }}>
                      <AttachFileIcon />
                    </IconButton>
                  </span>
                </Tooltip>
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

      <Dialog open={attachOpen} onClose={() => setAttachOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Attach a document</DialogTitle>
        <DialogContent dividers sx={{ p: 0 }}>
          {attachLoading ? (
            <Box sx={{ py: 4, display: 'flex', justifyContent: 'center' }}>
              <CircularProgress size={28} />
            </Box>
          ) : attachDocs.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
              No ready documents available to attach.
            </Typography>
          ) : (
            <List disablePadding>
              {attachDocs.map((doc) => (
                <ListItem key={doc.id} disablePadding divider>
                  <ListItemButton
                    selected={attachedDoc?.id === doc.id}
                    onClick={() => { setAttachedDoc(doc); setAttachOpen(false); }}>
                    <ListItemText
                      primary={truncate(doc.title, 90)}
                      secondary={`${doc.fileType?.toUpperCase() || 'FILE'} · ${doc.department || 'General'}`}
                      primaryTypographyProps={{ variant: 'body2', noWrap: true }}
                      secondaryTypographyProps={{ variant: 'caption' }}
                    />
                  </ListItemButton>
                </ListItem>
              ))}
            </List>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAttachOpen(false)}>Cancel</Button>
        </DialogActions>
      </Dialog>
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
            display: 'block', mt: 0.5, p: 0.75, bgcolor: 'action.hover', borderRadius: 1,
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
          '@media (prefers-reduced-motion: reduce)': { animation: 'none', transform: 'scale(1)' },
        }} />
      ))}
    </Box>
  );
}