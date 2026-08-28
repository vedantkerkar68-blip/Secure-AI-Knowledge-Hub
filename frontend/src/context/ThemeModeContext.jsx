import { createContext, useContext, useEffect, useMemo, useState } from 'react';

const ThemeModeContext = createContext(null);

const STORAGE_KEY = 'sakh.themeMode';

function systemPrefersDark() {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function initialMode() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'light' || stored === 'dark') return stored;
  } catch {
    // fall through to system preference
  }
  return systemPrefersDark() ? 'dark' : 'light';
}

/**
 * Light/dark mode. First visit follows the OS preference; toggling
 * overrides and persists the choice in localStorage.
 */
export function ThemeModeProvider({ children }) {
  const [mode, setMode] = useState(initialMode);

  const toggleMode = () => {
    setMode((prev) => {
      const next = prev === 'light' ? 'dark' : 'light';
      try {
        localStorage.setItem(STORAGE_KEY, next);
      } catch {
        // storage unavailable — session-only
      }
      return next;
    });
  };

  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => {
      try {
        if (!localStorage.getItem(STORAGE_KEY)) {
          setMode(media.matches ? 'dark' : 'light');
        }
      } catch {
        setMode(media.matches ? 'dark' : 'light');
      }
    };
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, []);

  const value = useMemo(() => ({ mode, toggleMode }), [mode]);

  return <ThemeModeContext.Provider value={value}>{children}</ThemeModeContext.Provider>;
}

export function useThemeMode() {
  const context = useContext(ThemeModeContext);
  if (!context) {
    throw new Error('useThemeMode must be used within a ThemeModeProvider');
  }
  return context;
}