import { motion } from "motion/react";

type PhaseStatus = "complete" | "in-progress" | "planned";

interface Phase {
  period: string;
  name: string;
  description: string;
  status: PhaseStatus;
}

const PHASES: Phase[] = [
  {
    period: "Completed",
    name: "Phase 1 - Core Infrastructure",
    description:
      "Controller, API, template loader, Paper and Velocity plugins, Redis state management, and MongoDB audit logging.",
    status: "complete",
  },
  {
    period: "Completed",
    name: "Phase 2 - Load Balancer and Multi-Proxy",
    description:
      "Go TCP load balancer with Minecraft protocol parsing, PROXY protocol v2, proxy heartbeats, and network settings.",
    status: "complete",
  },
  {
    period: "Completed",
    name: "Phase 3 - Autoscaling and Game States",
    description:
      "Game-state-aware autoscaler, DRAINING with timeout, static groups with PVCs, and a template push-back sidecar.",
    status: "complete",
  },
  {
    period: "Completed",
    name: "Phase 4 - Permissions and Tablist",
    description:
      "Flat permission groups with timed assignments, a network tablist with header and footer, and chat formatting.",
    status: "complete",
  },
  {
    period: "Completed",
    name: "Phase 5 - In-Game Management",
    description:
      "Velocity commands for server, group, and player management directly from in-game.",
    status: "complete",
  },
  {
    period: "Completed",
    name: "Phase 6 - Plugin APIs",
    description:
      "Developer-facing APIs for Paper and Velocity plugins to integrate with OgCloud programmatically.",
    status: "complete",
  },
  {
    period: "Completed",
    name: "Helm Charts",
    description:
      "One-command deployment with configurable Helm charts for production Kubernetes clusters.",
    status: "complete",
  },
  {
    period: "In progress",
    name: "Web Dashboard",
    description:
      "Browser-based management UI for monitoring servers, groups, players, and network health.",
    status: "in-progress",
  },
  {
    period: "Planned",
    name: "Leader Election",
    description:
      "Lease-based high availability for the controller with automatic failover.",
    status: "planned",
  },
];

const CompletedDot: React.FC = () => (
  <div className="flex h-5 w-5 items-center justify-center rounded-full bg-success ring-4 ring-background-dark">
    <svg
      className="h-3 w-3 text-white"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={3}
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
    </svg>
  </div>
);

const InProgressDot: React.FC = () => (
  <div className="h-5 w-5 rounded-full bg-primary ring-4 ring-background-dark shadow-[0_0_18px_rgba(0,229,255,0.45)]" />
);

const PlannedDot: React.FC = () => (
  <div className="h-5 w-5 rounded-full bg-slate-600 ring-4 ring-background-dark" />
);

const RoadmapSection: React.FC = () => {
  return (
    <section id="roadmap" className="px-5 py-24 sm:px-6 md:py-32 lg:px-8">
      <div className="mx-auto max-w-5xl">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-100px" }}
          transition={{ duration: 0.5 }}
          className="mx-auto mb-14 max-w-3xl text-center"
        >
          <span className="mb-4 block text-xs font-semibold uppercase tracking-[0.22em] text-primary">
            Roadmap
          </span>
          <h2 className="font-display text-3xl font-bold tracking-[-0.03em] text-white md:text-5xl">
            Delivery roadmap
          </h2>
          <p className="mt-5 text-base leading-8 text-text-muted md:text-lg">
            The design shifts to the new template, but the roadmap still
            reflects what is shipped, what is underway, and what comes next.
          </p>
        </motion.div>

        <div className="relative rounded-[1.75rem] border border-white/8 bg-white/4 px-6 py-8 shadow-[0_20px_60px_rgba(0,0,0,0.28)] backdrop-blur-xl md:px-8">
          <div
            aria-hidden="true"
            className="absolute bottom-10 left-[2.05rem] top-10 w-px bg-linear-to-b from-success via-primary to-slate-600/50 md:left-[2.55rem]"
          />

          <div className="flex flex-col gap-6">
            {PHASES.map((phase, index) => (
              <motion.div
                key={phase.name}
                initial={{ opacity: 0, x: -16 }}
                whileInView={{ opacity: 1, x: 0 }}
                viewport={{ once: true, margin: "-50px" }}
                transition={{ duration: 0.4, delay: index * 0.06 }}
                className="relative flex items-start gap-4"
              >
                <div className="relative z-10 mt-0.5 shrink-0">
                  {phase.status === "complete" ? (
                    <CompletedDot />
                  ) : phase.status === "in-progress" ? (
                    <InProgressDot />
                  ) : (
                    <PlannedDot />
                  )}
                </div>

                <div className="flex-1 pb-1">
                  <div className="mb-2 flex flex-wrap items-center gap-2">
                    <span
                      className={`rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.14em] ${
                        phase.status === "complete"
                          ? "bg-success/10 text-success"
                          : phase.status === "in-progress"
                            ? "bg-primary/10 text-primary"
                            : "bg-slate-700/50 text-slate-400"
                      }`}
                    >
                      {phase.period}
                    </span>
                    <h4 className="font-display text-lg font-semibold text-white">
                      {phase.name}
                    </h4>
                  </div>
                  <p className="text-sm leading-7 text-text-muted">
                    {phase.description}
                  </p>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
};

export default RoadmapSection;
