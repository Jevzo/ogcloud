import { motion } from "motion/react";

interface RoadmapPoint {
    status: "completed" | "planned";
    text: string;
}

const ROADMAP_POINTS: RoadmapPoint[] = [
    {
        status: "completed",
        text: "Phases 1-6 completed: Core Infrastructure, Load Balancer and Multi-Proxy, Autoscaling and Game States, Permissions and Tablist, In-Game Management, and Plugin APIs.",
    },
    {
        status: "completed",
        text: "Helm Charts completed for one-command Kubernetes deployment.",
    },
    {
        status: "completed",
        text: "Web Dashboard completed for browser-based network management.",
    },
    {
        status: "planned",
        text: "Leader election planned for controller high availability with automatic failover.",
    },
    {
        status: "planned",
        text: "Multi-version Minecraft support planned per cluster without additional plugins.",
    },
];

const RoadmapSection = () => {
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
                        v1.0.0 overview and next steps
                    </h2>
                    <p className="mt-5 text-base leading-8 text-text-muted md:text-lg">
                        Quick overview of completed delivery and the next planned milestones.
                    </p>
                </motion.div>

                <motion.div
                    initial={{ opacity: 0, y: 20 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true, margin: "-50px" }}
                    transition={{ duration: 0.45 }}
                    className="relative rounded-3xl border border-white/10 bg-white/4 p-6 shadow-[0_20px_60px_rgba(0,0,0,0.28)] backdrop-blur-xl md:p-8"
                >
                    <div
                        aria-hidden="true"
                        className="absolute bottom-8 left-7 top-8 w-px bg-linear-to-b from-success/70 via-primary/50 to-slate-500/60 md:left-9"
                    />

                    <ul className="relative z-10">
                        {ROADMAP_POINTS.map((point) => (
                            <li key={point.text} className="flex items-start gap-3 py-3">
                                <span
                                    className={`mt-1 block h-2 w-2 shrink-0 rounded-full ring-4 ring-background-dark ${
                                        point.status === "completed" ? "bg-success" : "bg-slate-400"
                                    }`}
                                    aria-hidden="true"
                                />
                                <div className="flex flex-1 flex-col items-start gap-1">
                                    <span
                                        className={`inline-flex self-start rounded-full px-2 py-0.5 text-[9px] font-semibold uppercase tracking-[0.12em] ${
                                            point.status === "completed"
                                                ? "bg-success/10 text-success"
                                                : "bg-slate-700/60 text-slate-300"
                                        }`}
                                    >
                                        {point.status}
                                    </span>
                                    <span className="block text-sm leading-7 text-text-soft md:text-base">
                                        {point.text}
                                    </span>
                                </div>
                            </li>
                        ))}
                    </ul>
                </motion.div>
            </div>
        </section>
    );
};

export default RoadmapSection;
