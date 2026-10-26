import { useState, useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import LandingNavbar from '../components/landing/LandingNavbar.jsx';
import LandingHero from '../components/landing/LandingHero.jsx';
import LandingParts from '../components/landing/LandingParts.jsx';
import LandingAssembly from '../components/landing/LandingAssembly.jsx';
import LandingFilm from '../components/landing/LandingFilm.jsx';
import LandingSpecs from '../components/landing/LandingSpecs.jsx';
import LandingFAQ from '../components/landing/LandingFAQ.jsx';
import LandingCTA from '../components/landing/LandingCTA.jsx';
import LandingFooter from '../components/landing/LandingFooter.jsx';
import { UNITS_DEFAULT } from '../components/landing/manualModel.js';
import useManualReveal from '../components/landing/useManualReveal.js';
import { applyTheme, persistTheme } from '../lib/theme.js';
import '../components/landing/manual.css';

const LANDING_THEME_KEY = 'saed_landing_theme';

export default function LandingPage() {
  const { hash } = useLocation();
  const [units, setUnits] = useState(UNITS_DEFAULT);
  const mainRef = useRef(null);
  useManualReveal(mainRef);
  const [theme, setTheme] = useState(() => {
    try {
      const saved = localStorage.getItem(LANDING_THEME_KEY);
      if (saved === 'dark' || saved === 'light') return saved;
    } catch {
      /* ignore storage errors */
    }
    return 'light';
  });

  useEffect(() => {
    document.title = 'SAED 2.0 | Administración para propiedad horizontal';

    const metaDescription = document.querySelector('meta[name="description"]');
    if (metaDescription) {
      metaDescription.setAttribute(
        'content',
        'SAED 2.0 conecta administración, portería, residentes y cartera de tu conjunto sobre un mismo registro.'
      );
    }
  }, []);

  // Footer links from other routes arrive as "/#section".
  useEffect(() => {
    if (!hash) return;
    const target = document.getElementById(hash.slice(1));
    if (target) target.scrollIntoView();
  }, [hash]);

  useEffect(() => {
    applyTheme(theme);
    persistTheme(theme);
    try {
      localStorage.setItem(LANDING_THEME_KEY, theme);
    } catch {
      /* ignore storage errors */
    }
  }, [theme]);

  const handleToggleTheme = () => {
    setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'));
  };

  return (
    <div className={`saed-manual m-root ${theme === 'dark' ? 'dark' : ''}`}>
      <a href="#m-main" className="m-skip">
        Saltar al contenido
      </a>
      <LandingNavbar theme={theme} onToggleTheme={handleToggleTheme} />

      <main id="m-main" ref={mainRef} className="m-main">
        <LandingFilm />
        <LandingHero units={units} onUnitsChange={setUnits} />
        <LandingParts />
        <LandingAssembly units={units} />
        <LandingSpecs />
        <LandingFAQ />
        <LandingCTA units={units} />
      </main>

      <LandingFooter />
    </div>
  );
}
