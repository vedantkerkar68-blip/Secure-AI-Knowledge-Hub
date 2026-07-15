import { useState, useEffect, useRef } from 'react';
import {
  Box,
  Paper,
  Typography,
  TextField,
  IconButton,
  Button,
  List,
  ListItemButton,
  ListItemText,
  Divider,
  Chip,
  Collapse,
  Card,
  CardContent,
  CircularProgress,
  Avatar,
  Tooltip,
} from '@mui/material';
import { toast } from 'react-toastify';
import SendIcon from '@mui/icons-material/Send';
import AddIcon from '@mui/icons-material/Add';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import PersonIcon from '@mui/icons-material/Person';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ArticleIcon from '@mui/icons-material/Article';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import * as chatService from '../services/chatService';

const SIDEBAR_WIDTH = 280;

function truncate(str, len) {
  if (!str || str.length <= len) return str;
  return str.substring(0, len) + '...';
}

export default function Chat() {
  const [sessions, setSessions] = useState(() => {
    try {
      const stored = localStorage.getItem('sakh_chat_sessions');
      return stored ? JSON.parse(stored) : [];
    } catch { return []; }
  });
  const [activeSessionId, setActiveSessionId] = useState(null);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const messagesEndRef = useRef(null);

  const activeSession = sessions.find((s) => s.id === activeSessionId) || null;
  const messages = activeSession?.messages || [];

  useEffect(() => {
    localStorage.setItem('sakh_chat_sessions', JSON.stringify(sessions));
  }, [sessions]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending]);

  const persistMessages = (sessionId, updater) => {
    setSessions((prev) =>
      prev.map((s) => (s.id === sessionId ? { ...s, messages: updater(s.messages) } : s))
    );
  };

  const createNewSession = async () => {
    try {
      const res = await chatService.createSession({ title: 'New Chat' });
      const newSession = {
        id: res.data.id,
        title: 'New Chat',
        createdAt: res.data.createdAt,
        messages: [],
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

    const userMsg = {
      id: Date.now(),
      role: 'user',
      content: question,
      timestamp: new Date().toISOString(),
    };
    persistMessages(activeSessionId, (msgs) => [...msgs, userMsg]);
    setSending(true);

    try {
      const res = await chatService.sendMessage({ sessionId: activeSessionId, question });
      const { answer, confidence, citations } = res.data;

      setSessions((prev) =>
        prev.map((s) =>
          s.id === activeSessionId && s.title === 'New Chat'
            ? { ...s, title: truncate(question, 60) }
            : s
        )
      );

      const assistantMsg = {
        id: Date.now() + 1,
        role: 'assistant',
        content: answer,
        confidence: confidence ?? 0,
        citations: citations ?? [],
        timestamp: new Date().toISOString(),
      };
      persistMessages(activeSessionId, (msgs) => [...msgs, assistantMsg]);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to get AI response');
    } finally {
      setSending(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const selectSession = (id) => setActiveSessionId(id);

  const formatConfidence = (val) => {
    const pct = Math.round((val ?? 0) * 100);
    let color = 'error';
    if (pct >= 70) color = 'success';
    else if (pct >= 40) color = 'warning';
    return { pct, color };
  };

  return (
    <Box
      sx={{
        display: 'flex',
        height: `calc(100vh - 64px - 48px)`,
        mx: -3,
        mt: -3,
      }}
    >
      <Paper
        elevation={0}
        sx={{
          width: SIDEBAR_WIDTH,
          minWidth: SIDEBAR_WIDTH,
          borderRadius: 0,
          borderRight: '1px solid',
          borderColor: 'divider',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Box sx={{ p: 2 }}>
          <Button
            variant="contained"
            fullWidth
            startIcon={<AddIcon />}
            onClick={createNewSession}
            sx={{ py: 1 }}
          >
            New Chat
          </Button>
        </Box>
        <Divider />
        <Box sx={{ flexGrow: 1, overflow: 'auto' }}>
          <List disablePadding>
            {sessions.map((session) => (
              <ListItemButton
                key={session.id}
                selected={session.id === activeSessionId}
                onClick={() => selectSession(session.id)}
                sx={{
                  px: 2,
                  py: 1.5,
                  '&.Mui-selected': { bgcolor: 'primary.light', color: 'primary.contrastText', '&:hover': { bgcolor: 'primary.light' } },
                }}
              >
                <ListItemText
                  primary={truncate(session.title, 30)}
                  secondary={session.messages?.length > 0 ? `${session.messages.length} messages` : null}
                  primaryTypographyProps={{ variant: 'body2', noWrap: true, fontWeight: session.id === activeSessionId ? 600 : 400 }}
                  secondaryTypographyProps={{ variant: 'caption', sx: { color: session.id === activeSessionId ? 'rgba(255,255,255,0.7)' : 'text.secondary' } }}
                  sx={{ flexGrow: 1, minWidth: 0 }}
                />
                <IconButton
                  size="small"
                  onClick={(e) => deleteSession(e, session.id)}
                  sx={{
                    opacity: 0.5,
                    '&:hover': { opacity: 1 },
                    color: session.id === activeSessionId ? 'inherit' : undefined,
                  }}
                >
                  <DeleteOutlineIcon fontSize="small" />
                </IconButton>
              </ListItemButton>
            ))}
            {sessions.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 4, px: 2 }}>
                No conversations yet. Start a new chat.
              </Typography>
            )}
          </List>
        </Box>
      </Paper>

      <Box
        sx={{
          flexGrow: 1,
          display: 'flex',
          flexDirection: 'column',
          bgcolor: '#f5f7fa',
        }}
      >
        {!activeSession ? (
          <Box
            sx={{
              flexGrow: 1,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 2,
            }}
          >
            <Avatar sx={{ width: 64, height: 64, bgcolor: 'primary.main' }}>
              <AutoAwesomeIcon sx={{ fontSize: 32 }} />
            </Avatar>
            <Typography variant="h5" sx={{ fontWeight: 600 }}>
              Secure AI Knowledge Hub
            </Typography>
            <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
              Ask questions about your organization's documents
            </Typography>
            <Button variant="contained" size="large" startIcon={<AddIcon />} onClick={createNewSession}>
              Start New Chat
            </Button>
          </Box>
        ) : (
          <>
            <Box
              sx={{
                flexGrow: 1,
                overflow: 'auto',
                px: { xs: 2, md: 6 },
                py: 3,
              }}
            >
              {messages.length === 0 && (
                <Box sx={{ textAlign: 'center', py: 6 }}>
                  <SmartToyIcon sx={{ fontSize: 48, color: 'primary.main', opacity: 0.5, mb: 2 }} />
                  <Typography variant="h6" color="text.secondary">
                    {activeSession.title}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Ask a question to get started
                  </Typography>
                </Box>
              )}
              {messages.map((msg) => (
                <Box key={msg.id} sx={{ mb: 2, display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }}>
                  <Box sx={{ maxWidth: '75%', minWidth: 0 }}>
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, flexDirection: msg.role === 'user' ? 'row-reverse' : 'row' }}>
                      <Avatar
                        sx={{
                          width: 34,
                          height: 34,
                          bgcolor: msg.role === 'user' ? 'primary.main' : 'grey.300',
                          color: msg.role === 'user' ? '#fff' : 'text.primary',
                        }}
                      >
                        {msg.role === 'user' ? <PersonIcon sx={{ fontSize: 20 }} /> : <SmartToyIcon sx={{ fontSize: 20 }} />}
                      </Avatar>
                      <Paper
                        elevation={0}
                        sx={{
                          px: 2.5,
                          py: 1.5,
                          borderRadius: 2,
                          bgcolor: msg.role === 'user' ? 'primary.main' : '#ffffff',
                          color: msg.role === 'user' ? '#fff' : 'text.primary',
                          border: msg.role === 'user' ? 'none' : '1px solid',
                          borderColor: 'divider',
                          wordBreak: 'break-word',
                          whiteSpace: 'pre-wrap',
                        }}
                      >
                        <Typography variant="body2" sx={{ lineHeight: 1.7 }}>
                          {msg.content}
                        </Typography>
                      </Paper>
                    </Box>

                    {msg.role === 'assistant' && (
                      <Box sx={{ ml: 6, mt: 0.5, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                        <ConfidenceBadge value={msg.confidence} />
                        {msg.citations?.length > 0 && (
                          <CitationsBlock citations={msg.citations} />
                        )}
                      </Box>
                    )}
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
                <TextField
                  fullWidth
                  multiline
                  maxRows={6}
                  placeholder="Ask a question..."
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={handleKeyDown}
                  disabled={sending}
                  variant="standard"
                  slotProps={{ input: { disableUnderline: true, sx: { py: 1 } } }}
                />
                <IconButton
                  color="primary"
                  onClick={handleSend}
                  disabled={!input.trim() || sending}
                  sx={{ mb: 0.5 }}
                >
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
    </Box>
  );
}

function ConfidenceBadge({ value }) {
  const { pct, color } = formatConfidence(value);
  return (
    <Tooltip title={`Confidence: ${pct}%`}>
      <Chip
        icon={<AutoAwesomeIcon sx={{ fontSize: 14 }} />}
        label={`${pct}%`}
        size="small"
        color={color}
        variant="outlined"
        sx={{ height: 22, '& .MuiChip-label': { fontSize: 11, px: 0.5 } }}
      />
    </Tooltip>
  );
}

function formatConfidence(val) {
  const pct = Math.round((val ?? 0) * 100);
  let color = 'error';
  if (pct >= 70) color = 'success';
  else if (pct >= 40) color = 'warning';
  return { pct, color };
}

function CitationsBlock({ citations }) {
  const [open, setOpen] = useState(false);

  return (
    <Box>
      <Chip
        icon={<ArticleIcon sx={{ fontSize: 14 }} />}
        label={`${citations.length} source${citations.length > 1 ? 's' : ''}`}
        size="small"
        variant="outlined"
        onClick={() => setOpen(!open)}
        sx={{ height: 22, cursor: 'pointer', '& .MuiChip-label': { fontSize: 11, px: 0.5 } }}
        deleteIcon={open ? <ExpandLessIcon /> : <ExpandMoreIcon />}
      />
      <Collapse in={open} sx={{ mt: 1 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {citations.map((cit, i) => (
            <CitationCard key={i} citation={cit} />
          ))}
        </Box>
      </Collapse>
    </Box>
  );
}

function CitationCard({ citation }) {
  const { pct, color } = formatConfidence(citation.similarityScore);
  return (
    <Card variant="outlined" sx={{ borderRadius: 2 }}>
      <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 } }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 0.5 }}>
          <Typography variant="caption" sx={{ fontWeight: 600, lineHeight: 1.3, flexGrow: 1, mr: 1 }}>
            {citation.documentTitle || 'Untitled'}
          </Typography>
          <Tooltip title={`Relevance: ${pct}%`}>
            <Chip label={`${pct}%`} size="small" color={color} variant="outlined" sx={{ height: 20, '& .MuiChip-label': { fontSize: 10, px: 0.5 } }} />
          </Tooltip>
        </Box>
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
          {citation.department && (
            <Typography variant="caption" color="text.secondary">
              Dept: {citation.department}
            </Typography>
          )}
          {citation.pageNumber != null && (
            <Typography variant="caption" color="text.secondary">
              Page: {citation.pageNumber}
            </Typography>
          )}
          {citation.sectionTitle && (
            <Typography variant="caption" color="text.secondary">
              Section: {citation.sectionTitle}
            </Typography>
          )}
          {citation.version != null && (
            <Typography variant="caption" color="text.secondary">
              v{citation.version}
            </Typography>
          )}
        </Box>
      </CardContent>
    </Card>
  );
}

function TypingDots() {
  return (
    <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center', py: 0.5 }}>
      {[0, 1, 2].map((i) => (
        <Box
          key={i}
          sx={{
            width: 8,
            height: 8,
            borderRadius: '50%',
            bgcolor: 'text.disabled',
            animation: 'bounce 1.4s infinite ease-in-out both',
            animationDelay: `${i * 0.16}s`,
            '@keyframes bounce': {
              '0%, 80%, 100%': { transform: 'scale(0)' },
              '40%': { transform: 'scale(1)' },
            },
          }}
        />
      ))}
    </Box>
  );
}

