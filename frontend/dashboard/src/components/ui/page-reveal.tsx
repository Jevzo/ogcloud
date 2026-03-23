import { Children, isValidElement, type ReactNode } from "react";
import { m, useReducedMotion, type Variants } from "motion/react";

import { cn } from "@/lib/utils";

const ENTER_EASE = [0.22, 1, 0.36, 1] as const;

const itemVariants: Variants = {
    hidden: {
        opacity: 0,
        y: 18,
        scale: 0.985,
    },
    visible: {
        opacity: 1,
        y: 0,
        scale: 1,
        transition: {
            duration: 0.48,
            ease: ENTER_EASE,
        },
    },
};

interface RevealContainerProps {
    children: ReactNode;
    className?: string;
    delayChildren?: number;
    itemClassName?: string;
    staggerChildren?: number;
}

const RevealContainer = ({
    children,
    className,
    delayChildren = 0,
    itemClassName,
    staggerChildren = 0.08,
}: RevealContainerProps) => {
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

export const PageReveal = (props: Omit<RevealContainerProps, "delayChildren">) => (
    <RevealContainer {...props} delayChildren={0.05} staggerChildren={0.09} />
);

export const RevealGroup = (props: Omit<RevealContainerProps, "delayChildren">) => (
    <RevealContainer {...props} delayChildren={0.12} staggerChildren={0.07} />
);
