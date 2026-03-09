import { motion } from "motion/react";
import {
  MdBolt,
  MdPublic,
  MdLayers,
  MdShield,
  MdCode,
  MdViewList,
} from "react-icons/md";
import FeatureCard from "./FeatureCard";

const FEATURES = [
  {
    title: "Game-State-Aware Autoscaling",
    description:
      "Scales from LOBBY, INGAME, and ENDING states instead of blunt player counts, so active matches stay alive.",
    icon: MdBolt,
    iconColor: "text-primary",
    iconBg: "bg-primary/10",
  },
  {
    title: "Go TCP Load Balancer",
    description:
      "Dedicated TCP load balancing with Minecraft protocol parsing, least-connections routing, and PROXY protocol v2.",
    icon: MdPublic,
    iconColor: "text-secondary",
    iconBg: "bg-secondary/10",
  },
  {
    title: "Dynamic and Static Groups",
    description:
      "Dynamic groups scale on demand. Static groups keep persistent volumes and template push-back via sidecar.",
    icon: MdLayers,
    iconColor: "text-success",
    iconBg: "bg-success/10",
  },
  {
    title: "Built-In Permissions",
    description:
      "Flat permission groups with timed assignments, wildcard support, and real-time sync across the network.",
    icon: MdShield,
    iconColor: "text-primary",
    iconBg: "bg-primary/10",
  },
  {
    title: "Network UX Controls",
    description:
      "Maintenance mode, custom MOTD handling, and a built-in network tablist keep player-facing behavior consistent.",
    icon: MdViewList,
    iconColor: "text-secondary",
    iconBg: "bg-secondary/10",
  },
  {
    title: "Operator Control Surface",
    description:
      "In-game management and full REST APIs cover groups, servers, players, permissions, tablist, and network settings.",
    icon: MdCode,
    iconColor: "text-primary",
    iconBg: "bg-primary/10",
  },
] as const;

const FeaturesSection = () => {
  return (
    <section id="features" className="px-5 py-24 sm:px-6 md:py-32 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-100px" }}
          transition={{ duration: 0.5 }}
          className="mx-auto mb-14 max-w-3xl text-center"
        >
          <span className="mb-4 block text-xs font-semibold uppercase tracking-[0.22em] text-primary">
            Platform Features
          </span>
          <h2 className="font-display text-3xl font-bold tracking-[-0.03em] text-white md:text-5xl">
            Core capabilities for Minecraft operations
          </h2>
          <p className="mt-5 text-base leading-8 text-text-muted md:text-lg">
            The template changes, but the promise stays the same: remove
            operational drag from scaling, routing, and managing a distributed
            Minecraft network.
          </p>
        </motion.div>

        <div className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((feature, index) => (
            <FeatureCard
              key={feature.title}
              title={feature.title}
              description={feature.description}
              icon={feature.icon}
              iconColor={feature.iconColor}
              iconBg={feature.iconBg}
              index={index}
            />
          ))}
        </div>
      </div>
    </section>
  );
};

export default FeaturesSection;
