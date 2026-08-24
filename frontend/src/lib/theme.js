// Tema claro/oscuro: aplica el atributo data-theme en <html> y persiste en
// localStorage. Se ejecuta temprano (main.jsx) para evitar el flash y que la
// login también respete la preferencia del sistema.
const THEME_KEY = 'saed_theme';

export function getInitialTheme() {
  try {
    const saved = localStorage.getItem(THEME_KEY);
    if (saved === 'dark') return 'dark';
    if (saved === 'light') return 'light';
  } catch {
    /* almacenamiento no disponible */
  }
  return typeof window !== 'undefined' &&
    window.matchMedia?.('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light';
}

export function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
}

export function persistTheme(theme) {
  try {
    localStorage.setItem(THEME_KEY, theme);
  } catch {
    /* almacenamiento no disponible */
  }
}
