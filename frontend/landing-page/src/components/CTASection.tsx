import {motion} from "motion/react";
import {FaDiscord, FaGithub} from "react-icons/fa6";

const CTASection = () => {
    return (
        <section className="px-5 py-24 sm:px-6 md:py-32 lg:px-8">
            <div className="mx-auto max-w-6xl">
                <div
                    className="relative overflow-hidden rounded-[2rem] border border-white/8 bg-white/[0.04] px-6 py-12 text-center shadow-[0_20px_70px_rgba(0,0,0,0.32)] backdrop-blur-xl sm:px-10 md:px-14">
                    <div
                        aria-hidden="true"
                        className="absolute inset-x-14 top-0 h-36 rounded-full bg-primary/12 blur-3xl"
                    />
                    <div
                        aria-hidden="true"
                        className="absolute -bottom-16 left-1/2 h-48 w-48 -translate-x-1/2 rounded-full bg-secondary/12 blur-3xl"
                    />

                    <motion.div
                        initial={{opacity: 0, y: 20}}
                        whileInView={{opacity: 1, y: 0}}
                        viewport={{once: true, margin: "-50px"}}
                        transition={{duration: 0.5}}
                        className="relative z-10"
                    >
            <span
                className="inline-flex rounded-full border border-primary/20 bg-primary/10 px-4 py-2 text-xs font-semibold uppercase tracking-[0.18em] text-primary">
              Ready to test OgCloud?
            </span>
                        <h3 className="mt-6 font-display text-3xl font-bold tracking-[-0.03em] text-white md:text-5xl">
                            Start with the cluster, not with another panel
                        </h3>
                        <p className="mx-auto mt-5 max-w-2xl text-base leading-8 text-text-muted md:text-lg">
                            Ship OgCloud into staging, validate the autoscaler, and join the
                            community while the dashboard and Helm tooling land.
                        </p>

                        <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
                            <a
                                href="https://discord.gg/8TgdaDMdY7"
                                className="inline-flex items-center gap-2 rounded-xl bg-linear-to-r from-primary to-primary-dark px-7 py-3.5 text-sm font-semibold text-slate-950 shadow-[0_0_24px_rgba(0,229,255,0.22)] transition-transform hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                            >
                                <FaDiscord className="h-5 w-5"/>
                                Join Discord
                            </a>
                            <a
                                href="https://github.com/Jevzo/ogcloud"
                                className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-white/5 px-7 py-3.5 text-sm font-semibold text-white transition-colors hover:border-primary/30 hover:bg-white/[0.08] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                            >
                                <FaGithub className="h-5 w-5"/>
                                View on GitHub
                            </a>
                        </div>
                    </motion.div>
                </div>
            </div>
        </section>
    );
};

export default CTASection;
