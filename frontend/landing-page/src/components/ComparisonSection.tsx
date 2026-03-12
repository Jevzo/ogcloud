import { motion } from "motion/react";
import { MdCheckCircle, MdWarning } from "react-icons/md";

interface StrengthCard {
    title: string;
    description: string;
}

interface CompetitorCard {
    name: string;
    summary: string;
    evidence: string;
    verdict: string;
}

const OGCLOUD_STRENGTHS: StrengthCard[] = [
    {
        title: "Control plane, not process babysitting",
        description:
            "Autoscaling windows, cooldown logic, drain handoff, heartbeat cleanup, and stale-server remediation are baseline behavior. Start/stop loops are not orchestration.",
    },
    {
        title: "API built for operators",
        description:
            "Networks, groups, servers, players, permissions, templates, command dispatch, auth, and audit logs are first-class. If it matters in operations, it is scriptable.",
    },
    {
        title: "Ingress that handles failure",
        description:
            "The Go load balancer tracks proxy health, ejects stale backends, supports routing strategy control, and drains cleanly instead of turning traffic into proxy roulette.",
    },
    {
        title: "Kubernetes as first principle",
        description:
            "Helm charts split by concern plus setup automation for generate, deploy, update, and destroy. Repeatable infrastructure, not pet-node rituals.",
    },
] as const;

const COMPETITOR_BREAKDOWN: CompetitorCard[] = [
    {
        name: "PoloCloud",
        summary: "Pre-release theater dressed up as a platform.",
        evidence:
            "Snapshot-track releases and split hosted-vs-self-host paths are a signal that you are still testing their roadmap in production.",
        verdict: "Interesting for experiments, weak for operators who need predictability.",
    },
    {
        name: "CloudNet",
        summary: "Legacy host-fleet architecture with refreshed branding.",
        evidence:
            "Node-centric clustering, manual host identity, and machine-first assumptions keep you in yesterday's operating model.",
        verdict: "Historical relevance is not modern control-plane architecture.",
    },
    {
        name: "Shulker",
        summary: "Strong primitives, incomplete platform story.",
        evidence:
            "Operator plus plugin plus agent plus addon composition is technically solid but still pushes core product assembly back onto your team.",
        verdict: "Good substrate. You still build the platform yourself.",
    },
    {
        name: "SimpleCloud",
        summary: "Marketing confidence, infrastructure lag.",
        evidence:
            "When Kubernetes is off the table and container support is still unfinished, you are looking at legacy process orchestration with cloud paint.",
        verdict: "Future-facing copy, past-tense operational model.",
    },
] as const;

const BOTTOM_LINE = {
    headline: "For serious Minecraft networks, this is not a close race.",
    support:
        "OgCloud is built around orchestration, declarative deployment, and platform behavior as first principles. Most alternatives lose for the same reason: too old, too fragmented, or too incomplete to operate like a modern control plane.",
    kicker: "Stop choosing stacks that only prove they can start; choose one that proves it can scale and survive failure.",
} as const;

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
                        Why OgCloud wins
                    </h2>
                    <p className="mt-5 text-base leading-8 text-text-muted md:text-lg">
                        Blunt version: most Minecraft cloud stacks are old architecture, incomplete
                        infrastructure sold as a platform, or legacy process orchestration with new
                        branding. OgCloud is a real control plane with real orchestration, a real
                        API, and a real ingress layer built for traffic under load.
                    </p>
                </motion.div>

                <motion.div
                    initial={{ opacity: 0, y: 8 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true, margin: "-60px" }}
                    transition={{ duration: 0.25 }}
                    className="overflow-hidden rounded-[1.75rem] border border-white/8 bg-white/[0.04] p-6 shadow-[0_24px_80px_rgba(0,0,0,0.38)] backdrop-blur-xl md:p-8 lg:p-9"
                >
                    <div className="mb-7 rounded-2xl border border-primary/30 bg-primary/10 px-4 py-4 md:px-5 md:py-5">
                        <div className="flex items-start gap-3 text-sm leading-7 text-primary">
                            <MdCheckCircle className="mt-1 h-5 w-5 shrink-0 text-success" />
                            <p>
                                OgCloud is not trying to be another recycled "cloud system." It is a
                                platform stack designed for scale, churn, and failure handling.
                            </p>
                        </div>
                    </div>

                    <div className="mb-8 grid gap-4 md:grid-cols-2">
                        {OGCLOUD_STRENGTHS.map((strength) => (
                            <article
                                key={strength.title}
                                className="rounded-2xl border border-white/8 bg-white/[0.03] p-5 md:p-6"
                            >
                                <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-primary/80">
                                    OgCloud Strength
                                </p>
                                <h3 className="mt-3 font-display text-xl font-semibold tracking-[-0.02em] text-white">
                                    {strength.title}
                                </h3>
                                <p className="mt-3 text-[15px] leading-7 text-text-muted">
                                    {strength.description}
                                </p>
                            </article>
                        ))}
                    </div>

                    <div className="rounded-2xl border border-amber-500/20 bg-amber-500/[0.06] p-5 md:p-6">
                        <div className="mb-6 space-y-2">
                            <div className="inline-flex items-center gap-2 text-sm font-semibold uppercase tracking-[0.18em] text-amber-200">
                                <MdWarning className="h-5 w-5 shrink-0" />
                                Why the alternatives lose
                            </div>
                            <p className="max-w-3xl text-sm leading-7 text-amber-100/90">
                                Once you need an opinionated, self-owned stack that already ships
                                control-plane behavior, the gap stops being subtle.
                            </p>
                        </div>
                        <div className="grid gap-4 md:grid-cols-2">
                            {COMPETITOR_BREAKDOWN.map((competitor) => (
                                <article
                                    key={competitor.name}
                                    className="rounded-xl border border-white/8 bg-black/20 p-4 md:p-5"
                                >
                                    <h3 className="font-display text-2xl font-semibold tracking-[-0.02em] text-white">
                                        {competitor.name}
                                    </h3>
                                    <p className="mt-4 text-[11px] font-semibold uppercase tracking-[0.16em] text-amber-200/80">
                                        Market pitch
                                    </p>
                                    <p className="mt-1 text-[15px] leading-7 text-text-soft/90">
                                        {competitor.summary}
                                    </p>
                                    <p className="mt-4 text-[11px] font-semibold uppercase tracking-[0.16em] text-amber-200/80">
                                        Operational reality
                                    </p>
                                    <p className="mt-1 text-[15px] leading-7 text-text-soft/90">
                                        {competitor.evidence}
                                    </p>
                                    <p className="mt-4 text-[11px] font-semibold uppercase tracking-[0.16em] text-amber-200/80">
                                        Verdict
                                    </p>
                                    <p className="mt-1 text-sm font-semibold leading-7 text-amber-100">
                                        {competitor.verdict}
                                    </p>
                                </article>
                            ))}
                        </div>
                    </div>

                    <div className="mt-8 overflow-hidden rounded-2xl border border-primary/25 bg-linear-to-r from-primary/[0.1] via-cyan-400/[0.05] to-amber-500/[0.08] px-5 py-6 md:px-6">
                        <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-primary">
                            Bottom line
                        </p>
                        <p className="mt-2 font-display text-2xl font-semibold leading-[1.1] tracking-[-0.02em] text-white md:text-[1.7rem]">
                            {BOTTOM_LINE.headline}
                        </p>
                        <p className="mt-3 text-sm leading-7 text-text-soft/90">
                            {BOTTOM_LINE.kicker}
                        </p>
                        <p className="mt-4 text-[15px] leading-7 text-text-muted">
                            {BOTTOM_LINE.support}
                        </p>
                    </div>
                </motion.div>
            </div>
        </section>
    );
};

export default ComparisonSection;
