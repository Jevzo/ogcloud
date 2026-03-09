import * as React from "react";
import {useState} from "react";
import {AnimatePresence, motion} from "motion/react";
import {MdAdd, MdRemove} from "react-icons/md";

interface FAQItem {
    question: string;
    answer: string;
}

const FAQ_ITEMS: FAQItem[] = [
    {
        question: "How do I set up OgCloud quickly?",
        answer:
            "Use the guided CLI: run `npx @ogcloud/setup`, generate a config, then deploy. The setup validates required tools, confirms your cluster context, downloads Helm charts automatically, and guides you through prompts.",
    },
    {
        question: "What do I need before running setup?",
        answer:
            "You need Node.js with npm/npx, kubectl configured to a cluster context, and Helm 3+. OgCloud setup checks these dependencies before it proceeds.",
    },
    {
        question: "What is the difference between deploy and deploy --without-backing?",
        answer:
            "`--deploy <network>` performs a clean deployment including infrastructure when configured. `--deploy <network> --without-backing` skips infra chart deployment and is intended for environments where backing services already exist and are healthy.",
    },
    {
        question: "Can I update a single component without full redeploy?",
        answer:
            "Yes. Use `npx @ogcloud/setup --update <network> <component> <tag>` for `api`, `controller`, `loadbalancer`, or `dashboard`. This updates the image tag and reapplies the relevant chart.",
    },
    {
        question: "Is OgCloud production-ready today?",
        answer:
            "OgCloud v1.0.0 is beta-prod ready. Core phases 1-6 are completed, Helm charts are completed, and the web dashboard is completed. Planned next are leader election and multi-version support per cluster without additional plugins.",
    },
    {
        question: "What is planned next after v1.0.0?",
        answer:
            "The near-term roadmap focuses on controller leader election for high availability and native support for multiple Minecraft versions in one cluster without requiring additional plugins.",
    },
];

const FAQItemRow: React.FC<{
    item: FAQItem;
    isOpen: boolean;
    onToggle: () => void;
}> = ({item, isOpen, onToggle}) => {
    return (
        <div className="border-b border-white/6 last:border-b-0">
            <button
                onClick={onToggle}
                className="group flex w-full cursor-pointer items-center justify-between gap-4 px-5 py-5 text-left"
                aria-expanded={isOpen}
            >
        <span className="text-base font-medium text-white transition-colors group-hover:text-primary">
          {item.question}
        </span>
                <span
                    className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-xl border transition-colors ${
                        isOpen
                            ? "border-primary/20 bg-primary/10 text-primary"
                            : "border-white/6 bg-white/[0.03] text-text-muted"
                    }`}
                >
          {isOpen ? <MdRemove className="h-4 w-4"/> : <MdAdd className="h-4 w-4"/>}
        </span>
            </button>
            <AnimatePresence initial={false}>
                {isOpen && (
                    <motion.div
                        initial={{height: 0, opacity: 0}}
                        animate={{height: "auto", opacity: 1}}
                        exit={{height: 0, opacity: 0}}
                        transition={{duration: 0.25, ease: "easeInOut"}}
                        className="overflow-hidden"
                    >
                        <p className="px-5 pb-5 text-sm leading-7 text-text-muted">
                            {item.answer}
                        </p>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
};

const FAQSection = () => {
    const [openIndex, setOpenIndex] = useState<number | null>(0);

    const handleToggle = (index: number) => {
        setOpenIndex((prev) => (prev === index ? null : index));
    };

    return (
        <section id="faq" className="px-5 py-24 sm:px-6 md:py-32 lg:px-8">
            <div className="mx-auto max-w-5xl">
                <motion.div
                    initial={{opacity: 0, y: 30}}
                    whileInView={{opacity: 1, y: 0}}
                    viewport={{once: true, margin: "-100px"}}
                    transition={{duration: 0.5}}
                    className="mx-auto mb-14 max-w-3xl text-center"
                >
          <span className="mb-4 block text-xs font-semibold uppercase tracking-[0.22em] text-primary">
            FAQ
          </span>
                    <h2 className="font-display text-3xl font-bold tracking-[-0.03em] text-white md:text-5xl">
                        Common questions
                    </h2>
                    <p className="mt-5 text-base leading-8 text-text-muted md:text-lg">
                        Clear answers for the migration path, operations model, and how
                        OgCloud differs from generic panel software.
                    </p>
                </motion.div>

                <motion.div
                    initial={{opacity: 0, y: 20}}
                    whileInView={{opacity: 1, y: 0}}
                    viewport={{once: true, margin: "-50px"}}
                    transition={{duration: 0.5, delay: 0.1}}
                    className="overflow-hidden rounded-[1.75rem] border border-white/8 bg-white/[0.04] shadow-[0_20px_60px_rgba(0,0,0,0.28)] backdrop-blur-xl"
                >
                    {FAQ_ITEMS.map((item, index) => (
                        <FAQItemRow
                            key={item.question}
                            item={item}
                            isOpen={openIndex === index}
                            onToggle={() => handleToggle(index)}
                        />
                    ))}
                </motion.div>
            </div>
        </section>
    );
};

export default FAQSection;
