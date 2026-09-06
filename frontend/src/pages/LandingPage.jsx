import { useEffect } from 'react';
import LandingNavbar from '../components/landing/LandingNavbar.jsx';
import LandingHero from '../components/landing/LandingHero.jsx';
import LandingTrust from '../components/landing/LandingTrust.jsx';
import LandingProblemSolution from '../components/landing/LandingProblemSolution.jsx';
import LandingAccess from '../components/landing/LandingAccess.jsx';
import LandingPackages from '../components/landing/LandingPackages.jsx';
import LandingParking from '../components/landing/LandingParking.jsx';
import LandingFinance from '../components/landing/LandingFinance.jsx';
import LandingSecurity from '../components/landing/LandingSecurity.jsx';
import LandingFAQ from '../components/landing/LandingFAQ.jsx';
import LandingFooter from '../components/landing/LandingFooter.jsx';

export default function LandingPage() {
  useEffect(() => {
    document.title = 'SAED 2.0 | Administración inteligente para propiedad horizontal';

    const metaDescription = document.querySelector('meta[name="description"]');
    if (metaDescription) {
      metaDescription.setAttribute(
        'content',
        'SAED 2.0 conecta administración, residentes, portería, cartera y operación en una sola plataforma PropTech.'
      );
    }
  }, []);

  return (
    <div className="min-h-screen bg-[#070B14] text-slate-100 flex flex-col selection:bg-sky-500/25 selection:text-sky-300">
      {/* 1. Minimalist Translucent Sticky Navigation Header */}
      <LandingNavbar />

      {/* Main Editorial Storytelling Content */}
      <main className="flex-1">
        {/* 2. Monumental Editorial Hero */}
        <LandingHero />

        {/* 2. Trust & Hierarchy (Una plataforma. Cuatro perfiles. Una operación conectada.) */}
        <LandingTrust />

        {/* 3. Problem vs Solution Narrative & The Unified Equation */}
        <LandingProblemSolution />

        {/* 4. Product Deep Dive 01: Access & QR Security HUD */}
        <LandingAccess />

        {/* 5. Product Deep Dive 02: Package Custody with Cryptographic PIN */}
        <LandingPackages />

        {/* 6. Product Deep Dive 03: Real-Time Visitor Parking Bays */}
        <LandingParking />

        {/* 7. Financial Operations: Debt, Aging, and Wompi Gateway */}
        <LandingFinance />

        {/* 8. Architectural Security, ROI & Ley 675 */}
        <LandingSecurity />

        {/* 9. Accessible FAQ Accordion */}
        <LandingFAQ />
      </main>

      {/* 14. Minimalist Enterprise Footer */}
      <LandingFooter />
    </div>
  );
}
