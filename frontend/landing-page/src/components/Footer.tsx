import { Link } from "react-router";
import { FaDiscord, FaGithub } from "react-icons/fa6";

const CURRENT_YEAR = new Date().getFullYear();

const PRODUCT_LINKS = [
  { label: "Features", href: "#features" },
  { label: "Comparison", href: "#comparison" },
  { label: "Roadmap", href: "#roadmap" },
] as const;

const RESOURCE_LINKS = [
  { label: "GitHub", href: "https://github.com/Jevzo/ogcloud" },
  { label: "Discord", href: "https://discord.gg/8TgdaDMdY7" },
  { label: "FAQ", href: "#faq" },
] as const;

const LEGAL_LINKS = [
  { label: "Imprint", href: "/imprint" },
  { label: "Privacy", href: "/privacy" },
  { label: "Terms", href: "/terms" },
] as const;

const Footer = () => {
  return (
    <footer className="relative z-10 border-t border-white/5 px-5 pb-10 pt-20 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <div className="grid gap-10 border-b border-white/6 pb-10 md:grid-cols-4">
          <div className="md:col-span-2">
            <a
              href="/"
              aria-label="OgCloud home"
              className="inline-flex items-center gap-3 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <span className="flex h-10 w-10 items-center justify-center rounded-2xl border border-white/10 bg-white/5">
                <img
                  src="/static/logo.webp"
                  alt=""
                  className="h-7 w-auto"
                  aria-hidden="true"
                />
              </span>
              <span className="font-display text-lg font-bold tracking-[-0.02em] text-white">
                OgCloud
              </span>
            </a>
            <p className="mt-5 max-w-xs text-sm leading-7 text-text-muted">
              The original gangster cloud for Kubernetes-native Minecraft
              networks that need ruthless reliability and fast operations.
            </p>
          </div>

          <nav aria-label="Product links">
            <h2 className="text-sm font-semibold uppercase tracking-[0.18em] text-text-soft">
              Product
            </h2>
            <div className="mt-4 flex flex-col gap-3">
              {PRODUCT_LINKS.map((link) => (
                <a
                  key={link.href}
                  href={link.href}
                  className="text-sm text-text-muted transition-colors hover:text-white"
                >
                  {link.label}
                </a>
              ))}
            </div>
          </nav>

          <div className="grid gap-10 sm:grid-cols-2 md:grid-cols-1">
            <nav aria-label="Resource links">
              <h2 className="text-sm font-semibold uppercase tracking-[0.18em] text-text-soft">
                Resources
              </h2>
              <div className="mt-4 flex flex-col gap-3">
                {RESOURCE_LINKS.map((link) => (
                  <a
                    key={link.label}
                    href={link.href}
                    className="text-sm text-text-muted transition-colors hover:text-white"
                  >
                    {link.label}
                  </a>
                ))}
              </div>
            </nav>

            <nav aria-label="Legal links">
              <h2 className="text-sm font-semibold uppercase tracking-[0.18em] text-text-soft">
                Legal
              </h2>
              <div className="mt-4 flex flex-col gap-3">
                {LEGAL_LINKS.map((link) => (
                  <Link
                    key={link.href}
                    to={link.href}
                    className="text-sm text-text-muted transition-colors hover:text-white"
                  >
                    {link.label}
                  </Link>
                ))}
              </div>
            </nav>
          </div>
        </div>

        <div className="flex flex-col gap-5 pt-6 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-text-muted">OgCloud &copy; {CURRENT_YEAR}</p>

          <div className="flex items-center gap-3">
            <a
              href="https://discord.gg/8TgdaDMdY7"
              target="_blank"
              rel="noopener noreferrer"
              aria-label="Discord"
              className="flex h-10 w-10 items-center justify-center rounded-xl border border-white/10 bg-white/4 text-text-muted transition-colors hover:border-primary/30 hover:text-white"
            >
              <FaDiscord className="h-4 w-4" />
            </a>
            <a
              href="https://github.com/Jevzo/ogcloud"
              target="_blank"
              rel="noopener noreferrer"
              aria-label="GitHub"
              className="flex h-10 w-10 items-center justify-center rounded-xl border border-white/10 bg-white/4 text-text-muted transition-colors hover:border-primary/30 hover:text-white"
            >
              <FaGithub className="h-4 w-4" />
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
