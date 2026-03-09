import { motion } from "motion/react";
import { FaDiscord, FaGithub } from "react-icons/fa6";

const NAV_LINKS = [
    { label: "Features", target: "features" },
    { label: "Comparison", target: "comparison" },
    { label: "Roadmap", target: "roadmap" },
    { label: "FAQ", target: "faq" },
] as const;

const Header = () => {
    const scrollTo = (id: string) => {
        document.getElementById(id)?.scrollIntoView({ behavior: "smooth" });
    };

    return (
        <motion.header
            initial={{ y: -20, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ duration: 0.5, ease: "easeOut" }}
            className="fixed inset-x-0 top-4 z-50 px-5 sm:px-6 lg:px-8"
        >
            <div className="mx-auto flex h-16 max-w-7xl items-center justify-between rounded-2xl border border-white/10 bg-white/5 px-5 shadow-[0_18px_50px_rgba(0,0,0,0.28)] backdrop-blur-2xl sm:px-6 lg:px-8">
                <a
                    href="/"
                    aria-label="OgCloud home"
                    className="flex items-center gap-3 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                    <span className="flex h-11 w-11 items-center justify-center bg-transparent">
                        <img
                            src="/static/logo.webp"
                            alt=""
                            className="h-8 w-auto"
                            aria-hidden="true"
                        />
                    </span>
                    <span className="font-display text-lg font-bold tracking-[-0.02em] text-white">
                        OgCloud
                    </span>
                </a>

                <nav aria-label="Main navigation" className="hidden items-center gap-8 md:flex">
                    {NAV_LINKS.map((link) => (
                        <button
                            key={link.target}
                            onClick={() => scrollTo(link.target)}
                            className="cursor-pointer text-sm font-medium text-text-muted transition-colors hover:text-white focus-visible:outline-none focus-visible:text-white"
                        >
                            {link.label}
                        </button>
                    ))}
                </nav>

                <div className="flex items-center gap-2 sm:gap-3">
                    <a
                        href="https://github.com/Jevzo/ogcloud"
                        aria-label="View OgCloud on GitHub"
                        className="inline-flex h-11 w-11 items-center justify-center rounded-xl border border-white/10 bg-white/5 text-text-muted transition-colors hover:border-primary/40 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                    >
                        <FaGithub className="h-4 w-4" />
                    </a>
                    <a
                        href="https://discord.gg/8TgdaDMdY7"
                        className="inline-flex items-center gap-2 rounded-xl bg-linear-to-r from-primary to-primary-dark px-4 py-2.5 text-sm font-semibold text-slate-950 shadow-[0_8px_30px_rgba(0,229,255,0.18)] transition-transform hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                    >
                        <FaDiscord className="h-4 w-4" />
                        <span className="hidden sm:inline">Join Discord</span>
                    </a>
                </div>
            </div>
        </motion.header>
    );
};

export default Header;
