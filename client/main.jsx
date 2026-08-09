import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.jsx';
import './index.css';
import { getInitialTheme, applyTheme } from './lib/theme.js';

const basename = import.meta.env.BASE_URL.replace(/\/$/, '');

// Tema antes del primer render (sin flash): aplica la preferencia guardada o
// la del sistema sobre <html> para que tokens CSS y variantes dark: resuelvan.
applyTheme(getInitialTheme());

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter basename={basename}>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
