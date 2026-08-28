import { createTheme } from '@mui/material/styles';

// SAKH design system — warm cream + gold accent + charcoal, soft UI.
// Centralized so pages never duplicate global design rules.
// `createAppTheme(mode)` returns a light or dark theme.

const SHADOW_KEYS = [
  'none',
  '0 1px 2px rgba(30,32,34,0.05), 0 1px 3px rgba(30,32,34,0.06)',
  '0 1px 2px rgba(30,32,34,0.06), 0 2px 4px rgba(30,32,34,0.05)',
  '0 4px 10px rgba(30,32,34,0.05)',
  '0 10px 30px rgba(30,32,34,0.06)',
  '0 14px 40px rgba(30,32,34,0.09)',
  '0 20px 50px rgba(30,32,34,0.12)',
];

function buildPalette(mode) {
  const light = mode === 'light';

  return {
    mode,
    primary: {
      main: '#FACC15',
      light: '#FFDB58',
      dark: '#E3B800',
      soft: 'rgba(250,204,21,0.16)',
      contrastText: '#1E2022',
    },
    secondary: {
      main: light ? '#2D3033' : '#3F4145',
      light: '#4A4D50',
      dark: '#1E2022',
      soft: light ? 'rgba(45,48,51,0.08)' : 'rgba(255,255,255,0.08)',
      contrastText: '#FFFFFF',
    },
    success: { main: '#16A34A', light: '#DCFCE7', dark: '#15803D', soft: light ? '#F0FDF4' : 'rgba(22,163,74,0.14)', contrastText: '#FFFFFF' },
    warning: { main: '#D97706', light: '#FEF3C7', dark: '#B45309', soft: light ? '#FFFBEB' : 'rgba(217,119,6,0.14)', contrastText: '#FFFFFF' },
    error: { main: '#DC2626', light: '#FEE2E2', dark: '#B91C1C', soft: light ? '#FEF2F2' : 'rgba(220,38,38,0.14)', contrastText: '#FFFFFF' },
    info: { main: '#0E7490', light: '#CFFAFE', dark: '#155E75', soft: light ? '#ECFEFF' : 'rgba(14,116,144,0.14)', contrastText: '#FFFFFF' },
    background: {
      default: light ? '#F7F5EC' : '#141518',
      paper: light ? '#FFFFFF' : '#1C1E20',
    },
    text: {
      primary: light ? '#1E2022' : '#F4F4F5',
      secondary: light ? '#6B7280' : '#A1A1AA',
      disabled: light ? '#9CA3AF' : '#71717A',
    },
    divider: light ? '#E9E6DD' : 'rgba(255,255,255,0.08)',
    action: {
      hover: light ? '#F3F1E8' : 'rgba(255,255,255,0.06)',
      selected: light ? 'rgba(250,204,21,0.14)' : 'rgba(250,204,21,0.16)',
    },
    sidebar: {
      bg: light ? '#1E2022' : '#101113',
      text: '#FFFFFF',
      muted: 'rgba(255,255,255,0.55)',
      soft: 'rgba(255,255,255,0.06)',
      border: 'rgba(255,255,255,0.08)',
      activeBg: 'rgba(250,204,21,0.16)',
      activeText: '#FACC15',
      hoverBg: 'rgba(255,255,255,0.08)',
    },
  };
}

const typography = {
  fontFamily: '"Plus Jakarta Sans", "Inter", "Roboto", "Helvetica", "Arial", sans-serif',
  fontSize: 14,
  h1: { fontWeight: 700, letterSpacing: '-0.03em' },
  h2: { fontWeight: 700, letterSpacing: '-0.025em' },
  h3: { fontWeight: 700, letterSpacing: '-0.02em' },
  h4: { fontWeight: 700, letterSpacing: '-0.02em' },
  h5: { fontWeight: 600, letterSpacing: '-0.015em' },
  h6: { fontWeight: 600, letterSpacing: '-0.01em' },
  subtitle1: { fontWeight: 600 },
  subtitle2: { fontWeight: 600 },
  button: { textTransform: 'none', fontWeight: 600 },
  caption: {},
  overline: { fontWeight: 600, letterSpacing: '0.08em', fontSize: 11 },
};

export default function createAppTheme(mode) {
  const palette = buildPalette(mode);

  return createTheme({
    palette,
    typography,
    shape: { borderRadius: 10 },
    spacing: 8,
    shadows: [
      ...SHADOW_KEYS,
      ...Array(19).fill('none'),
    ],
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            backgroundColor: palette.background.default,
            color: palette.text.primary,
            WebkitFontSmoothing: 'antialiased',
            MozOsxFontSmoothing: 'grayscale',
            backgroundImage: mode === 'light'
              ? 'radial-gradient(1200px 600px at 100% 0%, rgba(250,204,21,0.10), transparent 60%), radial-gradient(900px 500px at 0% 100%, rgba(250,204,21,0.06), transparent 55%)'
              : 'radial-gradient(1200px 600px at 100% 0%, rgba(250,204,21,0.06), transparent 60%)',
            backgroundAttachment: 'fixed',
          },
        },
      },
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: {
            textTransform: 'none',
            fontWeight: 600,
            borderRadius: 999,
            '&:focus-visible': { outline: '2px solid', outlineColor: palette.primary.main, outlineOffset: 2 },
          },
          sizeSmall: { minHeight: 30, paddingInline: 14 },
          sizeMedium: { minHeight: 38, paddingInline: 18 },
          sizeLarge: { minHeight: 46, paddingInline: 24 },
          containedPrimary: {
            boxShadow: '0 2px 8px rgba(250,204,21,0.35)',
            '&:hover': { boxShadow: '0 4px 12px rgba(250,204,21,0.4)' },
          },
          containedSecondary: {
            color: '#FFFFFF',
            '&:hover': { backgroundColor: mode === 'light' ? '#24262A' : '#4A4D50' },
          },
          outlined: { borderWidth: 1.5 },
        },
      },
      MuiPaper: {
        defaultProps: { elevation: 0 },
        styleOverrides: {
          root: {
            backgroundImage: 'none',
            border: '1px solid',
            borderColor: palette.divider,
          },
          rounded: { borderRadius: 20 },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: { borderRadius: 20, overflow: 'hidden' },
        },
      },
      MuiTextField: {
        defaultProps: { size: 'small' },
      },
      MuiInputBase: {
        styleOverrides: {
          root: { '&:focus-visible': { outline: 'none' } },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: 14,
            backgroundColor: mode === 'light' ? '#FFFFFF' : 'rgba(255,255,255,0.02)',
            '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: palette.text.disabled },
            '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: palette.primary.dark, borderWidth: 1.5 },
          },
        },
      },
      MuiFormLabel: {
        styleOverrides: {
          root: { '&.Mui-focused': { color: palette.text.primary } },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: { fontWeight: 600, borderRadius: 999 },
          sizeSmall: { height: 24, fontSize: 11.5 },
        },
      },
      MuiTableCell: {
        styleOverrides: {
          root: { borderColor: palette.divider, paddingTop: 10, paddingBottom: 10 },
          head: {
            fontWeight: 600,
            color: palette.text.secondary,
            fontSize: 11.5,
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
            backgroundColor: mode === 'light' ? '#FBFAF4' : 'rgba(255,255,255,0.02)',
          },
        },
      },
      MuiTableRow: {
        styleOverrides: {
          root: {
            '&:last-child .MuiTableCell-root': { borderBottom: 'none' },
            '&.MuiTableRow-hover:hover': { backgroundColor: palette.action.hover },
          },
        },
      },
      MuiTablePagination: {
        styleOverrides: {
          root: { borderTop: `1px solid ${palette.divider}` },
        },
      },
      MuiDialog: {
        defaultProps: { fullWidth: true, maxWidth: 'sm' },
        styleOverrides: {
          paper: { borderRadius: 24 },
        },
      },
      MuiDialogTitle: {
        styleOverrides: { root: { fontSize: 18, fontWeight: 700, padding: '24px 28px 8px' } },
      },
      MuiDialogContent: { styleOverrides: { root: { padding: '16px 28px' } } },
      MuiDialogActions: { styleOverrides: { root: { padding: '12px 28px 20px' } } },
      MuiDrawer: {
        styleOverrides: { paper: { borderRight: 'none' } },
      },
      MuiAppBar: {
        styleOverrides: {
          root: { boxShadow: 'none', borderBottom: `1px solid ${palette.divider}` },
        },
      },
      MuiToolbar: {
        styleOverrides: { root: { minHeight: 64 } },
      },
      MuiListItemButton: {
        styleOverrides: {
          root: {
            borderRadius: 999,
            '&.Mui-selected': {
              backgroundColor: palette.action.selected,
              '&:hover': { backgroundColor: palette.action.selected },
            },
          },
        },
      },
      MuiAvatar: {
        styleOverrides: { root: { fontWeight: 600 } },
      },
      MuiTooltip: {
        styleOverrides: { tooltip: { fontSize: 12, borderRadius: 10 } },
      },
      MuiLinearProgress: {
        styleOverrides: {
          root: { borderRadius: 999, backgroundColor: mode === 'light' ? '#EFEDE3' : 'rgba(255,255,255,0.1)' },
          bar: { borderRadius: 999 },
        },
      },
      MuiSkeleton: {
        defaultProps: { animation: 'pulse' },
        styleOverrides: {
          root: { backgroundColor: mode === 'light' ? 'rgba(30,32,34,0.08)' : 'rgba(255,255,255,0.08)' },
        },
      },
      MuiMenu: {
        styleOverrides: {
          paper: { borderRadius: 16, boxShadow: '0 10px 40px rgba(30,32,34,0.12)' },
        },
      },
      MuiAlert: {
        styleOverrides: { root: { borderRadius: 14 } },
      },
      MuiSnackbarContent: { styleOverrides: { root: { borderRadius: 14 } } },
      MuiDivider: { styleOverrides: { root: { borderColor: palette.divider } } },
      MuiTabs: {
        styleOverrides: { indicator: { height: 3, borderRadius: '3px 3px 0 0' } },
      },
    },
  });
}