import { motion } from "motion/react";
import { MdCheckCircle, MdWarning } from "react-icons/md";

interface FeatureRow {
    feature: string;
    subtitle: string;
    ogcloud: string;
    competitors: string;
}

const FEATURE_ROWS: FeatureRow[] = [
    {
        feature: "Kubernetes Native",
        subtitle: "Built for K8s from the ground up",
        ogcloud: "Purpose-built around pods, controllers, and cluster-native lifecycle.",
        competitors:
            "Usually Docker-first, partial, or missing native Kubernetes support entirely.",
    },
    {
        feature: "Architecture",
        subtitle: "How the platform is composed",
        ogcloud: "Microservices split between Kotlin control plane and Go networking.",
        competitors: "Mostly monolithic Java nodes or a narrower Agones-centric deployment model.",
    },
    {
        feature: "Autoscaling",
        subtitle: "How capacity is adjusted",
        ogcloud: "Game-state-aware scaling that protects active matches.",
        competitors: "Typically fixed min/max rules, player-count heuristics, or fleet buffers.",
    },
    {
        feature: "Load Balancer",
        subtitle: "Player traffic routing",
        ogcloud: "Dedicated Go TCP load balancer with protocol-aware routing.",
        competitors: "Usually absent, proxy-only, or delegated to generic Kubernetes services.",
    },
    {
        feature: "Permissions",
        subtitle: "Network-wide player permissions",
        ogcloud: "Built-in timed groups, wildcard support, and synchronized updates.",
        competitors: "Often extra modules, basic plugin support, or bring-your-own tooling.",
    },
    {
        feature: "Maintenance Mode",
        subtitle: "Operational safety controls",
        ogcloud: "Network-wide and per-group maintenance with bypass and MOTD handling.",
        competitors: "Usually proxy-only, limited, or missing as a first-class feature.",
    },
    {
        feature: "Self-Healing",
        subtitle: "Failure recovery model",
        ogcloud: "Kubernetes-native recovery with automated restarts and replacement.",
        competitors: "Usually limited watchdog behavior or no built-in failure recovery.",
    },
] as const;

const ComparisonSection = () => {
    return (
        <section id="comparison" className="px-5 py-24 sm:px-6 md:py-32 lg:px-8">
            <div className="mx-auto max-w-6xl">
                <motion.div
                    initial={{ opacity: 0, y: 30 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true, margin: "-100px" }}
                    transition={{ duration: 0.5 }}
                    className="mx-auto mb-12 max-w-3xl text-center"
                >
                    <span className="mb-4 block text-xs font-semibold uppercase tracking-[0.22em] text-primary">
                        Why OgCloud?
                    </span>
                    <h2 className="font-display text-3xl font-bold tracking-[-0.03em] text-white md:text-5xl">
                        The OgCloud difference
                    </h2>
                    <p className="mt-5 text-base leading-8 text-text-muted md:text-lg">
                        This is a single aggregated view across CloudNet, SimpleCloud, Shulker, and
                        TimoCloud, focused on the seven capability gaps that matter most.
                    </p>
                </motion.div>

                <motion.div
                    initial={{ opacity: 0, y: 8 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true, margin: "-60px" }}
                    transition={{ duration: 0.25 }}
                    className="overflow-hidden rounded-[1.75rem] border border-white/8 bg-white/[0.04] shadow-[0_24px_80px_rgba(0,0,0,0.38)] backdrop-blur-xl"
                >
                    <div className="border-b border-white/6 bg-amber-500/10 px-5 py-3 text-sm text-amber-200">
                        <span className="inline-flex items-center gap-2 font-medium">
                            <MdWarning className="h-5 w-5 shrink-0" />
                            Competitor column summarizes the common pattern across the major
                            alternatives above.
                        </span>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-white/6 bg-white/[0.02]">
                                    <th className="w-1/3 px-5 py-4 text-left text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
                                        Feature
                                    </th>
                                    <th className="w-1/3 bg-primary/[0.06] px-5 py-4 text-left text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                                        OgCloud
                                    </th>
                                    <th className="w-1/3 px-5 py-4 text-left text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
                                        Other competitors
                                    </th>
                                </tr>
                            </thead>
                            <tbody>
                                {FEATURE_ROWS.map((row) => (
                                    <tr
                                        key={row.feature}
                                        className="border-b border-white/6 transition-colors hover:bg-white/[0.02]"
                                    >
                                        <td className="px-5 py-3">
                                            <div className="font-medium text-white">
                                                {row.feature}
                                            </div>
                                            <div className="mt-1 text-xs text-text-muted">
                                                {row.subtitle}
                                            </div>
                                        </td>
                                        <td className="bg-primary/[0.06] px-5 py-3">
                                            <span className="flex items-start gap-2 font-medium text-white">
                                                <MdCheckCircle className="mt-0.5 h-4 w-4 shrink-0 text-success" />
                                                <span>{row.ogcloud}</span>
                                            </span>
                                        </td>
                                        <td className="px-5 py-3">
                                            <span className="flex items-start gap-2 text-text-muted">
                                                <MdWarning className="mt-0.5 h-4 w-4 shrink-0 text-amber-300/80" />
                                                <span>{row.competitors}</span>
                                            </span>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </motion.div>
            </div>
        </section>
    );
};

export default ComparisonSection;
