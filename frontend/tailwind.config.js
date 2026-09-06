function withOpacity(variableName, fallback) {
  return ({ opacityValue }) => {
    if (opacityValue !== undefined) {
      return `color-mix(in srgb, var(${variableName}${fallback ? `, ${fallback}` : ''}) calc(${opacityValue} * 100%), transparent)`;
    }
    return `var(${variableName}${fallback ? `, ${fallback}` : ''})`;
  };
}

/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx,ts,tsx}'],
  darkMode: ['selector', '[data-theme="dark"], .dark'],
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
        // Semantic aliases (vinculados a :root / [data-theme=dark] en index.css)
        surface: withOpacity('--surface'),
        'surface-dim': withOpacity('--surface-dim'),
        'surface-container': withOpacity('--surface-container'),
        'surface-muted': withOpacity('--surface-dim'),
        'surface-selected': withOpacity('--surface-selected'),
        'preview-bg': withOpacity('--preview-bg'),
        primary: {
          DEFAULT: withOpacity('--primary'),
          hover: withOpacity('--primary-hover'),
          foreground: withOpacity('--on-primary'),
        },
        'on-primary': withOpacity('--on-primary'),
        accent: {
          DEFAULT: withOpacity('--warn'),
          hover: withOpacity('--btn-warn-hover'),
        },
        // Material-token aliases used by the React kit (Button/Form/Modal/Toast).
        // Kept in sync with index.css :root so inline `var(--x)` styles resolve
        // to the same values as Tailwind utilities (single source of truth).
        'on-surface': withOpacity('--on-surface'),
        'on-surface-variant': withOpacity('--on-surface-variant'),
        'outline-variant': withOpacity('--outline-variant', '#1E293B'),
        'on-error': '#FFFFFF',
        'error-container': withOpacity('--error-container'),
        'accent-green': withOpacity('--accent-green'),
        'accent-green-bg': withOpacity('--accent-green-bg'),
        'warn-amber': withOpacity('--warn'),
        'warn-amber-bg': withOpacity('--warn-amber-bg'),
        // Botones solidos: profundos en ambos modos (texto blanco AA).
        'btn-accent': withOpacity('--btn-accent'),
        'btn-accent-hover': withOpacity('--btn-accent-hover'),
        'btn-danger': withOpacity('--btn-danger'),
        'btn-danger-hover': withOpacity('--btn-danger-hover'),
        'btn-warn': withOpacity('--btn-warn'),
        'btn-warn-hover': withOpacity('--btn-warn-hover'),
        'btn-success': withOpacity('--btn-success'),
        error: {
          DEFAULT: withOpacity('--error'),
          container: withOpacity('--error-container'),
        },
        info: {
          DEFAULT: withOpacity('--info'),
          container: withOpacity('--info-bg'),
        },
        'success-strong': withOpacity('--success-strong'),
        background: withOpacity('--background'),
        'background-subtle': withOpacity('--background-subtle', '#0B1120'),
        'on-background': withOpacity('--on-background'),
        'text-secondary': withOpacity('--text-secondary'),
        'text-muted': withOpacity('--text-muted'),
        'text-hint': withOpacity('--text-hint'),
        border: {
          DEFAULT: withOpacity('--border'),
          focus: withOpacity('--border-focus'),
          subtle: withOpacity('--border-subtle'),
        },
        'border-focus': withOpacity('--border-focus'),
        'border-subtle': withOpacity('--border-subtle'),
        // Anillos de foco con alpha
        'ring-primary': withOpacity('--primary'),
        'ring-error': withOpacity('--error'),
        warning: {
          DEFAULT: withOpacity('--warn'),
          50: '#FFFBEB',
          100: '#FEF3C7',
          500: '#D97706',
          600: '#B45309',
          700: '#92400E',
        },
        // ---- Tokens shadcn/ui (mapeados a la paleta SAED existente) ----
        foreground: withOpacity('--on-background'),
        card: {
          DEFAULT: withOpacity('--surface'),
          foreground: withOpacity('--on-background'),
        },
        'primary-foreground': withOpacity('--on-primary'),
        secondary: {
          DEFAULT: withOpacity('--surface-dim'),
          foreground: withOpacity('--on-surface'),
        },
        muted: {
          DEFAULT: withOpacity('--surface-dim'),
          foreground: withOpacity('--text-muted'),
        },
        'muted-foreground': withOpacity('--text-muted'),
        'accent-foreground': withOpacity('--on-surface'),
        destructive: {
          DEFAULT: withOpacity('--btn-danger'),
          foreground: withOpacity('--on-primary'),
        },
        'destructive-foreground': withOpacity('--on-primary'),
        input: withOpacity('--outline-variant', '#1E293B'),
        ring: withOpacity('--border-focus'),
        popover: {
          DEFAULT: withOpacity('--surface'),
          foreground: withOpacity('--on-background'),
        },
        'chart-1': withOpacity('--chart-1'),
        'chart-2': withOpacity('--chart-2'),
        'chart-3': withOpacity('--chart-3'),
        'chart-4': withOpacity('--chart-4'),
        'chart-5': withOpacity('--chart-5'),
      },
      screens: {
        xs: '360px',
        mobile: '390px',
        tablet: '768px',
        desktop: '1024px',
        wide: '1440px',
      },
      fontSize: {
        display: ['2.25rem', { lineHeight: '2.75rem', fontWeight: '800' }],
        h1: ['1.875rem', { lineHeight: '2.25rem', fontWeight: '700' }],
        h2: ['1.5rem', { lineHeight: '2rem', fontWeight: '700' }],
        h3: ['1.25rem', { lineHeight: '1.75rem', fontWeight: '600' }],
        body: ['0.875rem', { lineHeight: '1.375rem', fontWeight: '400' }],
        'body-sm': ['0.8125rem', { lineHeight: '1.25rem', fontWeight: '400' }],
        label: ['0.75rem', { lineHeight: '1rem', fontWeight: '600' }],
        caption: ['0.6875rem', { lineHeight: '0.875rem', fontWeight: '500' }],
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
        fast: '150ms',
        DEFAULT: '180ms',
        normal: '200ms',
        slow: '300ms',
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
