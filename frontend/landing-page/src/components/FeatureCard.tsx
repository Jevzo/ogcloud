import {motion} from "motion/react";
import type {IconType} from "react-icons";
import * as React from "react";

interface FeatureCardProps {
    title: string;
    description: string;
    icon: IconType;
    iconColor: string;
    iconBg: string;
    index: number;
}

const FeatureCard: React.FC<FeatureCardProps> = ({
                                                     title,
                                                     description,
                                                     icon: Icon,
                                                     iconColor,
                                                     iconBg,
                                                     index,
                                                 }) => {
    return (
        <motion.article
            initial={{opacity: 0, y: 30}}
            whileInView={{opacity: 1, y: 0}}
            viewport={{once: true, margin: "-50px"}}
            transition={{duration: 0.5, delay: index * 0.06}}
            className="group relative overflow-hidden rounded-3xl border border-white/8 bg-white/4 p-6 backdrop-blur-xl transition-all duration-300 hover:-translate-y-1 hover:border-primary/25 hover:shadow-[0_24px_60px_rgba(0,0,0,0.25)]"
        >
            <div
                aria-hidden="true"
                className="absolute inset-x-10 top-0 h-24 rounded-full bg-primary/8 opacity-0 blur-3xl transition-opacity duration-300 group-hover:opacity-100"
            />
            <div
                className={`relative mb-5 flex h-14 w-14 items-center justify-center rounded-2xl border border-white/8 ${iconBg}`}
                aria-hidden="true"
            >
                <Icon className={`h-6 w-6 ${iconColor}`}/>
            </div>
            <h4 className="relative font-display text-xl font-semibold text-white">
                {title}
            </h4>
            <p className="relative mt-3 text-sm leading-7 text-text-muted">
                {description}
            </p>
        </motion.article>
    );
};

export default FeatureCard;
