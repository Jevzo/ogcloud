import { useEffect } from "react";
import Header from "@/components/Header";
import Hero from "@/components/Hero";
import FeaturesSection from "@/components/FeaturesSection";
import ComparisonSection from "@/components/ComparisonSection";
import RoadmapSection from "@/components/RoadmapSection";
import FAQSection from "@/components/FAQSection";
import CTASection from "@/components/CTASection";
import Footer from "@/components/Footer";

const LandingPage: React.FC = () => {
  useEffect(() => {
    document.title = "OgCloud - Kubernetes-Native Minecraft Cloud";
  }, []);

  return (
    <div className="relative min-h-screen overflow-x-hidden bg-background-dark text-text-main">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 overflow-hidden"
      >
        <div className="absolute -left-40 top-[-14rem] h-[36rem] w-[36rem] rounded-full bg-primary/10 blur-[140px]" />
        <div className="absolute -right-32 top-32 h-[28rem] w-[28rem] rounded-full bg-secondary/10 blur-[140px]" />
        <div className="absolute bottom-0 left-1/3 h-[30rem] w-[30rem] rounded-full bg-accent/[0.06] blur-[160px]" />
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
