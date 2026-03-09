import { useEffect } from "react";
import Header from "@/components/Header";
import Hero from "@/components/Hero";
import FeaturesSection from "@/components/FeaturesSection";
import ComparisonSection from "@/components/ComparisonSection";
import RoadmapSection from "@/components/RoadmapSection";
import FAQSection from "@/components/FAQSection";
import CTASection from "@/components/CTASection";
import Footer from "@/components/Footer";

const LandingPage = () => {
  useEffect(() => {
    document.title = "OgCloud - Kubernetes-Native Minecraft Cloud";
  }, []);

  return (
    <div className="relative min-h-screen overflow-x-hidden bg-background-dark text-text-main">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 overflow-hidden"
      >
        <div className="absolute -left-40 -top-56 h-144 w-xl rounded-full bg-primary/10 blur-[140px]" />
        <div className="absolute -right-32 top-32 h-112 w-md rounded-full bg-secondary/10 blur-[140px]" />
        <div className="absolute bottom-0 left-1/3 h-120 w-120 rounded-full bg-accent/6 blur-[160px]" />
      </div>

      <Header />
      <main className="relative z-10">
        <Hero />
        <FeaturesSection />
        <ComparisonSection />
        <RoadmapSection />
        <FAQSection />
        <CTASection />
      </main>
      <Footer />
    </div>
  );
};

export default LandingPage;
