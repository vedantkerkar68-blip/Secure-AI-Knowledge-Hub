import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import api from '../services/api';

export const HEALTH_STATUS = {
  CONNECTING: 'connecting',
  READY: 'ready',
  UNAVAILABLE: 'unavailable',
};

const POLL_INTERVAL_MS = 15000;
const FIRST_CHECK_TIMEOUT_MS = 45000;
const POLL_TIMEOUT_MS = 8000;

const BackendHealthContext = createContext(null);

export function BackendHealthProvider({ children }) {
  const [status, setStatus] = useState(HEALTH_STATUS.CONNECTING);
  const [lastChecked, setLastChecked] = useState(null);
  const firstCheckDone = useRef(false);

  const check = useCallback(async (silent = false) => {
    if (!silent) {
      setStatus(HEALTH_STATUS.CONNECTING);
    }
    const timeout = firstCheckDone.current ? POLL_TIMEOUT_MS : FIRST_CHECK_TIMEOUT_MS;
    try {
      const res = await api.get('/health', { timeout });
      setStatus(res.data?.status === 'UP' ? HEALTH_STATUS.READY : HEALTH_STATUS.UNAVAILABLE);
    } catch {
      setStatus(HEALTH_STATUS.UNAVAILABLE);
    } finally {
      firstCheckDone.current = true;
      setLastChecked(new Date());
    }
  }, []);

  useEffect(() => {
    check();
    const timer = setInterval(() => check(true), POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [check]);

  const retry = useCallback(() => check(false), [check]);

  return (
    <BackendHealthContext.Provider value={{ status, lastChecked, retry }}>
      {children}
    </BackendHealthContext.Provider>
  );
}

export function useBackendHealth() {
  const context = useContext(BackendHealthContext);
  if (!context) {
    throw new Error('useBackendHealth must be used within a BackendHealthProvider');
  }
  return context;
}