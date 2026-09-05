import { useEffect } from 'react';
import LandingNavbar from '../components/landing/LandingNavbar.jsx';
import LandingHero from '../components/landing/LandingHero.jsx';
import LandingAbout from '../components/landing/LandingAbout.jsx';
import LandingProblemSolution from '../components/landing/LandingProblemSolution.jsx';
import LandingFeatures from '../components/landing/LandingFeatures.jsx';
import LandingSecurityQR from '../components/landing/LandingSecurityQR.jsx';
import LandingParcelsParking from '../components/landing/LandingParcelsParking.jsx';
import LandingAudience from '../components/landing/LandingAudience.jsx';
import LandingBenefits from '../components/landing/LandingBenefits.jsx';
import LandingPricing from '../components/landing/LandingPricing.jsx';
import LandingShowcase from '../components/landing/LandingShowcase.jsx';
import LandingFAQ from '../components/landing/LandingFAQ.jsx';
import LandingFooter from '../components/landing/LandingFooter.jsx';

export default function LandingPage() {
  useEffect(() => {
    document.title = 'SAED 2.0 — Plataforma SaaS de Gestión y Seguridad para Copropiedades';
  }, []);

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col selection:bg-primary/20 selection:text-primary">
      {/* 1. Sticky Navigation Header */}
      <LandingNavbar />

      {/* Main Content Sections */}
      <main className="flex-1">
        {/* 2. Hero Section with Interactive Live Dashboard Mockup */}
        <LandingHero />

        {/* 3. About & Mathematical Formula Formula */}
        <LandingAbout />

        {/* 4. Problem vs Solution Comparative Grid */}
        <LandingProblemSolution />

        {/* 5. Core Platform Features Grid */}
        <LandingFeatures />

        {/* 6. Deep Dive: QR Security & Visitor Control */}
        <LandingSecurityQR />

        {/* 7. Deep Dive: Parcels with PIN & Visitor Parking Flow */}
        <LandingParcelsParking />

        {/* 8. Target Audience Profiles (Admin, Guard, Resident, Multi-Building) */}
        <LandingAudience />

        {/* 9. Strategic Benefits & Regulatory Compliance */}
        <LandingBenefits />

        {/* 10. Commercial SaaS Pricing Tiers & Comparison Matrix */}
        <LandingPricing />

        {/* 11. Immersive Metrics & Conversion Showcase */}
        <LandingShowcase />

        {/* 12. Interactive Accordion FAQ */}
        <LandingFAQ />
      </main>

      {/* 13. Comprehensive Enterprise Footer */}
      <LandingFooter />
    </div>
  );
}
