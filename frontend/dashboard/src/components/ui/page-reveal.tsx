import { Children, isValidElement, type ReactNode } from "react";
import { m, useReducedMotion, type Variants } from "motion/react";

import { cn } from "@/lib/utils";

const ENTER_EASE = [0.22, 1, 0.36, 1] as const;

const pageVariants: Variants = {
    hidden: {
        opacity: 0.94,
        y: 6,
    },
    visible: {
        opacity: 1,
        y: 0,
        transition: {
            duration: 0.24,
            ease: ENTER_EASE,
        },
    },
};

const itemVariants: Variants = {
    hidden: {
        opacity: 0.9,
        y: 6,
    },
    visible: {
        opacity: 1,
        y: 0,
        transition: {
            duration: 0.22,
            ease: ENTER_EASE,
        },
    },
};

interface PageRevealProps {
    children: ReactNode;
    className?: string;
}

interface RevealGroupProps {
    children: ReactNode;
    className?: string;
    delayChildren?: number;
    itemClassName?: string;
    staggerChildren?: number;
}

export const PageReveal = ({ children, className }: PageRevealProps) => {
    const shouldReduceMotion = useReducedMotion();

    return (
        <m.div
            className={className}
            initial={shouldReduceMotion ? false : "hidden"}
            animate="visible"
            variants={pageVariants}
        >
            {children}
        </m.div>
    );
};

export const RevealGroup = ({
    children,
    className,
    delayChildren = 0,
    itemClassName,
    staggerChildren = 0.02,
}: RevealGroupProps) => {
    const shouldReduceMotion = useReducedMotion();

    return (
        <m.div
            className={className}
            initial={shouldReduceMotion ? false : "hidden"}
            animate="visible"
            variants={{
                hidden: {},
                visible: {
                    transition: {
                        delayChildren,
                        staggerChildren,
                    },
                },
            }}
        >
            {Children.toArray(children).map((child, index) => (
                <m.div
                    key={
                        isValidElement(child) && child.key != null
                            ? child.key
                            : `page-reveal-item-${index}`
                    }
                    className={cn(itemClassName)}
                    variants={itemVariants}
                >
                    {child}
                </m.div>
            ))}
        </m.div>
    );
};
