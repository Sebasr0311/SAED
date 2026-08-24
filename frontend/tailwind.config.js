/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx,ts,tsx}'],
  darkMode: ['selector', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        // Brand palette (SAED v4)
        navy: {
          900: '#0A1628',
          800: '#0F2044',
          700: '#163060',
          600: '#1E4080',
          500: '#2855A0',
          400: '#3D6BBF',
          300: '#6B93D6',
          200: '#A8C4EC',
          100: '#D6E5F7',
          50: '#EDF4FC',
        },
        amber: {
          700: '#92400E',
          600: '#B45309',
          500: '#D97706',
          400: '#F59E0B',
          300: '#FBB84E',
          200: '#FDE68A',
          100: '#FEF3C7',
          50: '#FFFBEB',
        },
        slate: {
          900: '#0F172A',
          800: '#1E293B',
          700: '#334155',
          600: '#475569',
          500: '#64748B',
          400: '#94A3B8',
          300: '#CBD5E1',
          200: '#E2E8F0',
          100: '#F1F5F9',
          50: '#F8FAFC',
        },
        success: {
          700: '#065F46',
          600: '#047857',
          500: '#059669',
          400: '#10B981',
          100: '#D1FAE5',
          50: '#ECFDF5',
        },
        danger: {
          700: '#9F1239',
          600: '#BE123C',
          500: '#E11D48',
          400: '#F43F5E',
          100: '#FFE4E6',
          50: '#FFF1F2',
        },
        warn: {
          700: '#92400E',
          600: '#B45309',
          500: '#D97706',
          400: '#F59E0B',
          100: '#FEF3C7',
          50: '#FFFBEB',
        },
        info: {
          600: '#0369A1',
          500: '#0284C7',
          400: '#38BDF8',
          100: '#E0F2FE',
          50: '#F0F9FF',
        },
        purple: {
          600: '#7C3AED',
          500: '#8B5CF6',
          100: '#EDE9FE',
          50: '#F5F3FF',
        },
        teal: {
          600: '#0F766E',
          500: '#14B8A6',
          100: '#CCFBF1',
          50: '#F0FDFA',
        },
        // Semantic aliases
        // Semantic aliases (vinculados a :root / [data-theme=dark] en index.css)
        surface: 'var(--surface)',
        'surface-dim': 'var(--surface-dim)',
        'surface-container': 'var(--surface-container)',
        'surface-muted': 'var(--surface-dim)',
    'surface-selected': 'var(--surface-selected)',
    'preview-bg': 'var(--preview-bg)',
        primary: {
          DEFAULT: 'var(--primary)',
          hover: 'var(--primary-hover)',
        },
        'on-primary': 'var(--on-primary)',
        accent: {
          DEFAULT: 'var(--warn)',
          hover: 'var(--btn-warn-hover)',
        },
        // Material-token aliases used by the React kit (Button/Form/Modal/Toast).
        // Kept in sync with index.css :root so inline `var(--x)` styles resolve
        // to the same values as Tailwind utilities (single source of truth).
        'on-surface': 'var(--on-surface)',
        'on-surface-variant': 'var(--on-surface-variant)',
        'outline-variant': 'var(--border)',
        'on-error': '#FFFFFF',
        'error-container': 'var(--error-container)',
        'accent-green': 'var(--accent-green)',
        'accent-green-bg': 'var(--accent-green-bg)',
        'warn-amber': 'var(--warn)',
        'warn-amber-bg': 'var(--warn-amber-bg)',
        // Botones solidos: profundos en ambos modos (texto blanco AA).
        'btn-accent': 'var(--btn-accent)',
        'btn-accent-hover': 'var(--btn-accent-hover)',
        'btn-danger': 'var(--btn-danger)',
        'btn-danger-hover': 'var(--btn-danger-hover)',
        'btn-warn': 'var(--btn-warn)',
        'btn-warn-hover': 'var(--btn-warn-hover)',
        'btn-success': 'var(--btn-success)',
        error: {
          DEFAULT: 'var(--error)',
          container: 'var(--error-container)',
        },
        info: {
          DEFAULT: 'var(--info)',
          container: 'var(--info-bg)',
        },
        'success-strong': 'var(--success-strong)',
        background: 'var(--background)',
        'on-background': 'var(--on-background)',
        'text-secondary': 'var(--text-secondary)',
        'text-muted': 'var(--text-muted)',
        'text-hint': 'var(--text-hint)',
        border: { DEFAULT: 'var(--border)', focus: 'var(--border-focus)', subtle: 'var(--border-subtle)' },
        'border-focus': 'var(--border-focus)',
        'border-subtle': 'var(--border-subtle)',
        // Anillos de foco con alpha fijo (RGB triplets).
        'ring-primary': 'rgb(var(--ring-primary) / 0.25)',
        'ring-error': 'rgb(var(--ring-error) / 0.25)',
        // ---- Tokens shadcn/ui (mapeados a la paleta SAED existente) ----
        // Los componentes shadcn usan estos nombres; resuelven a las mismas
        // variables que el kit propio (single source of truth en index.css).
        foreground: 'var(--on-background)',
        card: {
          DEFAULT: 'var(--surface)',
          foreground: 'var(--on-background)',
        },
        'primary-foreground': 'var(--on-primary)',
        secondary: {
          DEFAULT: 'var(--surface-dim)',
          foreground: 'var(--on-surface)',
        },
        muted: {
          DEFAULT: 'var(--surface-dim)',
          foreground: 'var(--text-muted)',
        },
        'accent-foreground': 'var(--on-surface)',
        destructive: {
          DEFAULT: 'var(--btn-danger)',
          foreground: 'var(--on-primary)',
        },
        input: 'var(--outline-variant)',
        ring: 'var(--border-focus)',
        popover: {
          DEFAULT: 'var(--surface)',
          foreground: 'var(--on-background)',
        },
        'chart-1': 'var(--chart-1)',
        'chart-2': 'var(--chart-2)',
        'chart-3': 'var(--chart-3)',
        'chart-4': 'var(--chart-4)',
        'chart-5': 'var(--chart-5)',
      },
      borderRadius: {
        xs: '4px',
        sm: '8px',
        DEFAULT: '12px',
        md: '14px',
        lg: '18px',
        xl: '24px',
      },
      boxShadow: {
        xs: '0 1px 2px rgba(15,23,42,0.04)',
        sm: '0 2px 8px rgba(15,23,42,0.06), 0 0 0 1px rgba(15,23,42,0.03)',
        DEFAULT: '0 4px 20px rgba(0, 0, 0, 0.03)',
        md: '0 4px 16px rgba(15,23,42,0.08), 0 0 0 1px rgba(15,23,42,0.03)',
        lg: '0 12px 40px rgba(15,23,42,0.12), 0 0 0 1px rgba(15,23,42,0.04)',
        xl: '0 24px 64px rgba(15,23,42,0.16)',
        soft: '0px 4px 20px rgba(0, 0, 0, 0.03)',
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'DM Sans', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
      transitionTimingFunction: {
        DEFAULT: 'cubic-bezier(0.4, 0, 0.2, 1)',
      },
      transitionDuration: {
        DEFAULT: '180ms',
        slow: '280ms',
      },
      spacing: {
        sidebar: '72px',
        'sidebar-open': '240px',
        topbar: '64px',
      },
      keyframes: {
        'fade-in': {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        'slide-up': {
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'slide-in-right': {
          '0%': { opacity: '0', transform: 'translateX(20px)' },
          '100%': { opacity: '1', transform: 'translateX(0)' },
        },
        pulse: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.5' },
        },
      },
      animation: {
        'fade-in': 'fade-in 0.2s ease-out',
        'slide-up': 'slide-up 0.3s ease-out',
        'slide-in-right': 'slide-in-right 0.3s ease-out',
      },
    },
  },
  plugins: [],
};
