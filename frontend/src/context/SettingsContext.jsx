import { createContext, useContext, useCallback, useMemo, useState } from 'react';

const SettingsContext = createContext(null);

const KEYS = {
  toastsEnabled: 'sakh.settings.toastsEnabled',
  outageAlertsEnabled: 'sakh.settings.outageAlertsEnabled',
};

function readPreference(key, fallback) {
  try {
    const value = localStorage.getItem(key);
    return value === null ? fallback : value === 'true';
  } catch {
    return fallback;
  }
}

function persist(key, value) {
  try {
    localStorage.setItem(key, String(value));
  } catch {
    // Storage unavailable — preference is session-only.
  }
}

/**
 * Client-side user preferences. Persisted in localStorage; no backend involved.
 */
export function SettingsProvider({ children }) {
  const [toastsEnabled, setToastsEnabledState] = useState(() => readPreference(KEYS.toastsEnabled, true));
  const [outageAlertsEnabled, setOutageAlertsEnabledState] = useState(() =>
    readPreference(KEYS.outageAlertsEnabled, true)
  );

  const setToastsEnabled = useCallback((value) => {
    setToastsEnabledState(value);
    persist(KEYS.toastsEnabled, value);
  }, []);

  const setOutageAlertsEnabled = useCallback((value) => {
    setOutageAlertsEnabledState(value);
    persist(KEYS.outageAlertsEnabled, value);
  }, []);

  const value = useMemo(
    () => ({ toastsEnabled, outageAlertsEnabled, setToastsEnabled, setOutageAlertsEnabled }),
    [toastsEnabled, outageAlertsEnabled, setToastsEnabled, setOutageAlertsEnabled]
  );

  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>;
}

export function useSettings() {
  const context = useContext(SettingsContext);
  if (!context) {
    throw new Error('useSettings must be used within a SettingsProvider');
  }
  return context;
}