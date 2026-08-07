/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './client/**/*.{js,jsx}',
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        surface: '#FFFFFF',
        'surface-dim': '#dadada',
        'surface-bright': '#f9f9f9',
        'surface-container-lowest': '#ffffff',
        'surface-container-low': '#f3f3f3',
        'surface-container': '#eeeeee',
        'surface-container-high': '#e8e8e8',
        'surface-container-highest': '#e2e2e2',
        'on-surface': '#1b1b1b',
        'on-surface-variant': '#4c4546',
        'outline-variant': '#cfc4c5',
        primary: '#0F2044',
        'on-primary': '#ffffff',
        'primary-container': '#1b1b1b',
        'on-primary-container': '#848484',
        'on-secondary': '#ffffff',
        secondary: '#5d5f5f',
        'secondary-container': '#dfe0e0',
        'on-secondary-container': '#616363',
        background: '#EEF2F8',
        'on-background': '#0F172A',
        error: '#ba1a1a',
        'on-error': '#ffffff',
        'error-container': '#ffdad6',
        'on-error-container': '#93000a',
        'accent-green': '#065F46',
        'accent-green-bg': '#D1FAE5',
        'warn-amber': '#92400E',
        'warn-amber-bg': '#FEF3C7',
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'DM Sans', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        soft: '0px 4px 20px rgba(0, 0, 0, 0.03)',
      },
      borderRadius: {
        xl: '0.75rem',
        '2xl': '1.5rem',
      },
    },
  },
  plugins: [],
};
