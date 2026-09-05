import { useEffect } from 'react';
import LandingNavbar from '../components/landing/LandingNavbar.jsx';
import LandingHero from '../components/landing/LandingHero.jsx';
import LandingAbout from '../components/landing/LandingAbout.jsx';
import LandingProblemSolution from '../components/landing/LandingProblemSolution.jsx';
import LandingFeatures from '../components/landing/LandingFeatures.jsx';
import LandingSecurityQR from '../components/landing/LandingSecurityQR.jsx';
import LandingParcelsParking from '../components/landing/LandingParcelsParking.jsx';
import LandingAudience from '../components/landing/LandingAudience.jsx';
import LandingSecurity from '../components/landing/LandingSecurity.jsx';
import LandingPricing from '../components/landing/LandingPricing.jsx';
import LandingShowcase from '../components/landing/LandingShowcase.jsx';
import LandingFAQ from '../components/landing/LandingFAQ.jsx';
import LandingCTA from '../components/landing/LandingCTA.jsx';
import LandingFooter from '../components/landing/LandingFooter.jsx';

export default function LandingPage() {
  useEffect(() => {
    document.title = 'SAED 2.0 — Plataforma PropTech de Gestión y Seguridad para Copropiedades';
  }, []);

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col selection:bg-primary/20 selection:text-primary">
      {/* 1. Minimalist Translucent Sticky Navigation Header */}
      <LandingNavbar />

      {/* Main Editorial Storytelling Content */}
      <main className="flex-1">
        {/* 2. Hero Section with Monumental Typography & Live Grand Product Showcase */}
        <LandingHero />

        {/* 3. About & The Unified Architecture Equation */}
        <LandingAbout />

        {/* 4. Problem vs Solution Editorial Comparative Flow */}
        <LandingProblemSolution />

        {/* 5. Core Platform Features & Micro-Mockup Previews */}
        <LandingFeatures />

        {/* 6. Deep Dive: QR Security & Visitor Control Flow */}
        <LandingSecurityQR />

        {/* 7. Deep Dive: Parcels with PIN & Real-Time Visitor Parking */}
        <LandingParcelsParking />

        {/* 8. Target Audience Profiles & Strict Role Hierarchy */}
        <LandingAudience />

        {/* 9. Certified Architecture & Enterprise Security (Multi-Tenant RLS / Ley 675) */}
        <LandingSecurity />

        {/* 10. Commercial SaaS Pricing Tiers & Full Capability Matrix */}
        <LandingPricing />

        {/* 11. Certified Software Architecture & Platform Highlights */}
        <LandingShowcase />

        {/* 12. Interactive Accordion FAQ */}
        <LandingFAQ />

        {/* 13. Monumental Final CTA: Una propiedad. Una plataforma. SAED. */}
        <LandingCTA />
      </main>

      {/* 14. Comprehensive Enterprise Footer */}
      <LandingFooter />
    </div>
  );
}
