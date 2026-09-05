import { useEffect } from 'react';
import LandingNavbar from '../components/landing/LandingNavbar.jsx';
import LandingHero from '../components/landing/LandingHero.jsx';
import LandingProductShowcase from '../components/landing/LandingProductShowcase.jsx';
import LandingTrust from '../components/landing/LandingTrust.jsx';
import LandingProblemSolution from '../components/landing/LandingProblemSolution.jsx';
import LandingAccess from '../components/landing/LandingAccess.jsx';
import LandingPackages from '../components/landing/LandingPackages.jsx';
import LandingParking from '../components/landing/LandingParking.jsx';
import LandingFinance from '../components/landing/LandingFinance.jsx';
import LandingSecurity from '../components/landing/LandingSecurity.jsx';
import LandingPricing from '../components/landing/LandingPricing.jsx';
import LandingFAQ from '../components/landing/LandingFAQ.jsx';
import LandingCTA from '../components/landing/LandingCTA.jsx';
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
    <div className="min-h-screen bg-[#0A1628] text-white flex flex-col selection:bg-emerald-500/20 selection:text-emerald-400">
      {/* 1. Minimalist Translucent Sticky Navigation Header */}
      <LandingNavbar />

      {/* Main Editorial Storytelling Content */}
      <main className="flex-1">
        {/* 2. Monumental Editorial Hero */}
        <LandingHero />

        {/* 3. Grand Product Showcase (Interactive 3-Tab Browser Frame) */}
        <LandingProductShowcase />

        {/* 4. Trust & Hierarchy (Una plataforma. Cinco roles. Una operación conectada.) */}
        <LandingTrust />

        {/* 5. Problem vs Solution Narrative & The Unified Equation */}
        <LandingProblemSolution />

        {/* 6. Product Deep Dive 01: Access & QR Security HUD */}
        <LandingAccess />

        {/* 7. Product Deep Dive 02: Package Custody with Cryptographic PIN */}
        <LandingPackages />

        {/* 8. Product Deep Dive 03: Real-Time Visitor Parking Bays */}
        <LandingParking />

        {/* 9. Financial Operations: Debt, Aging, and Wompi Gateway */}
        <LandingFinance />

        {/* 10. Architectural Security, Isolation Pipeline & Ley 675 */}
        <LandingSecurity />

        {/* 11. Transparent Pricing & Expandable Capability Matrix */}
        <LandingPricing />

        {/* 12. Accessible FAQ Accordion */}
        <LandingFAQ />

        {/* 13. Monumental Final CTA */}
        <LandingCTA />
      </main>

      {/* 14. Minimalist Enterprise Footer */}
      <LandingFooter />
    </div>
  );
}
