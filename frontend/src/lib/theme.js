import { useState, useEffect } from 'react';

// Tema claro/oscuro: aplica el atributo data-theme en <html> y persiste en
// localStorage. Por defecto se estrena la nueva identidad visual clara de SAED 2.0
// (Blanco #FFFFFF, Perla #F7F9FE, Índigo SAED #5267DA, Azul Tinta #1D2942).
const THEME_KEY = 'saed_theme';
const LANDING_THEME_KEY = 'saed_landing_theme';

export function getInitialTheme() {
  try {
    const savedLanding = localStorage.getItem(LANDING_THEME_KEY);
    if (savedLanding === 'dark' || savedLanding === 'light') return savedLanding;
    const saved = localStorage.getItem(THEME_KEY);
    if (saved === 'dark' || saved === 'light') return saved;
  } catch {
    /* almacenamiento no disponible */
  }
  return 'light';
}

export function applyTheme(theme) {
  if (typeof document === 'undefined') return;
  document.documentElement.setAttribute('data-theme', theme);
  if (theme === 'dark') {
    document.documentElement.classList.add('dark');
  } else {
    document.documentElement.classList.remove('dark');
  }
}

export function persistTheme(theme) {
  try {
    localStorage.setItem(THEME_KEY, theme);
    localStorage.setItem(LANDING_THEME_KEY, theme);
  } catch {
    /* almacenamiento no disponible */
  }
}

export function useSaedTheme() {
  const [theme, setTheme] = useState(() => getInitialTheme());

  useEffect(() => {
    applyTheme(theme);
    persistTheme(theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'));
  };

  return { theme, setTheme, toggleTheme };
}
