import { motion } from "motion/react";
import { FaDiscord, FaGithub } from "react-icons/fa6";
import { MdArrowForward, MdTerminal } from "react-icons/md";

const HERO_PILLS = [
  "Game-state-aware autoscaling",
  "Built-in TCP load balancing",
] as const;

const TERMINAL_LINES = [
  "$ helm repo add ogcloud https://charts.ogcloud.dev",
  "$ helm upgrade --install ogcloud ogcloud/platform -n ogcloud --create-namespace",
  "$ kubectl get pods -n ogcloud",
  "controller-7f6d8d9bcb-rx2ht      1/1   Running",
  "loadbalancer-6cb84b6dbf-j8jz7    1/1   Running",
  "api-5f4db4d47b-lr2pn       1/1   Running",
  "$ kubectl get svc -n ogcloud",
  "ogcloud-lb   LoadBalancer   65.109.xx.xx:25565",
] as const;

const Hero: React.FC = () => {
  return (
    <section className="px-5 pb-24 pt-36 sm:px-6 md:pb-32 md:pt-48 lg:px-8">
      <div className="mx-auto grid max-w-7xl gap-10 lg:gap-14 lg:grid-cols-[minmax(0,1fr)_minmax(0,35rem)] lg:items-center">
        <div className="text-center lg:text-left">
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.45 }}
            className="mb-8 inline-flex items-center gap-3 rounded-full border border-primary/20 bg-primary/10 px-4 py-2"
          >
            <span className="relative flex h-2.5 w-2.5">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary opacity-75" />
              <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-primary" />
            </span>
            <span className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
              Phases 1-6 are live
            </span>
          </motion.div>

          <motion.h1
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.55, delay: 0.08 }}
            className="font-display text-5xl font-bold leading-[0.98] tracking-[-0.04em] text-white sm:text-6xl md:text-7xl"
          >
            Run Minecraft servers at the{" "}
            <span className="bg-linear-to-r from-primary to-primary-dark bg-clip-text text-transparent">
              cluster level.
            </span>
          </motion.h1>

          <motion.p
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.55, delay: 0.16 }}
            className="mx-auto mt-6 max-w-3xl text-base leading-8 text-text-muted sm:text-lg lg:mx-0"
          >
            OgCloud is the Kubernetes-native control plane for Minecraft
            networks: deploy, scale, drain, and heal servers with game-state
            awareness, built-in permissions, and a dedicated TCP load balancer.
          </motion.p>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.55, delay: 0.24 }}
            className="mt-10 flex flex-col items-center gap-3 sm:flex-row lg:items-start"
          >
            <a
              href="https://discord.gg/8TgdaDMdY7#"
              className="inline-flex items-center gap-2 rounded-xl bg-linear-to-r from-primary to-primary-dark px-6 py-3.5 text-sm font-semibold text-slate-950 shadow-[0_0_24px_rgba(0,229,255,0.25)] transition-transform hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <FaDiscord className="h-5 w-5" />
              Join the Discord
              <MdArrowForward className="h-5 w-5" />
            </a>
            <a
              href="https://github.com/Jevzo/ogcloud"
              className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-white/5 px-6 py-3.5 text-sm font-semibold text-white transition-colors hover:border-primary/30 hover:bg-white/8 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <FaGithub className="h-5 w-5" />
              View on GitHub
            </a>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.55, delay: 0.32 }}
            className="mt-8 flex flex-wrap items-center justify-center gap-3 lg:justify-start"
          >
            {HERO_PILLS.map((pill) => (
              <span
                key={pill}
                className="rounded-full border border-white/8 bg-white/4 px-4 py-2 text-xs font-medium uppercase tracking-[0.14em] text-text-soft/80"
              >
                {pill}
              </span>
            ))}
          </motion.div>
        </div>

        <motion.aside
          initial={{ opacity: 0, scale: 0.96 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.7, delay: 0.18 }}
          className="relative overflow-hidden rounded-3xl border border-white/8 bg-white/4 p-6 shadow-[0_22px_70px_rgba(0,0,0,0.45)] backdrop-blur-xl"
        >
          <div
            aria-hidden="true"
            className="absolute inset-x-8 top-0 h-32 rounded-full bg-primary/10 blur-3xl"
          />

          <div className="relative">
            <div className="flex items-center justify-between px-2 pb-4">
              <div className="flex items-center gap-2">
                <span className="h-2.5 w-2.5 rounded-full bg-rose-400/80" />
                <span className="h-2.5 w-2.5 rounded-full bg-amber-300/80" />
                <span className="h-2.5 w-2.5 rounded-full bg-emerald-400/80" />
              </div>
              <span className="inline-flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-primary">
                <MdTerminal className="h-4 w-4" />
                Deploy preview
              </span>
            </div>

            <div className="rounded-2xl border border-white/6 bg-slate-950/70 p-4 font-mono text-[11px] leading-6 text-slate-300">
              {TERMINAL_LINES.map((line) => {
                const isCommand = line.startsWith("$");
                const isService = line.includes("LoadBalancer");

                return (
                  <div
                    key={line}
                    className={`whitespace-pre ${
                      isCommand
                        ? "text-primary"
                        : isService
                          ? "text-success"
                          : "text-slate-300"
                    }`}
                  >
                    {line}
                  </div>
                );
              })}
            </div>

            <div className="mt-4 grid grid-cols-2 gap-3">
              <div className="rounded-2xl border border-white/6 bg-white/3 px-4 py-3">
                <p className="text-[11px] uppercase tracking-[0.14em] text-text-muted">
                  Install path
                </p>
                <p className="mt-2 text-sm font-semibold text-white">
                  Helm first
                </p>
              </div>
              <div className="rounded-2xl border border-white/6 bg-white/3 px-4 py-3">
                <p className="text-[11px] uppercase tracking-[0.14em] text-text-muted">
                  Runtime
                </p>
                <p className="mt-2 text-sm font-semibold text-white">
                  kubectl visible
                </p>
              </div>
            </div>
          </div>
        </motion.aside>
      </div>
    </section>
  );
};

export default Hero;
