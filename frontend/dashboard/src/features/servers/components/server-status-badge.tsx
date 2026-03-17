import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

const getServerStateBadgeClassName = (state: string) => {
    const normalizedState = state.toUpperCase();

    if (normalizedState === "RUNNING") {
        return "border-emerald-500/30 bg-emerald-500/10 text-emerald-300";
    }

    if (normalizedState === "PREPARING" || normalizedState === "STARTING") {
        return "border-sky-500/30 bg-sky-500/10 text-sky-300";
    }

    if (normalizedState === "DRAINING" || normalizedState === "STOPPING") {
        return "border-amber-500/30 bg-amber-500/10 text-amber-300";
    }

    if (normalizedState === "STOPPED") {
        return "border-border/80 bg-muted/60 text-muted-foreground";
    }

    return "border-red-500/30 bg-red-500/10 text-red-300";
};

interface ServerStatusBadgeProps {
    className?: string;
    state: string;
}

const ServerStatusBadge = ({ className, state }: ServerStatusBadgeProps) => (
    <Badge variant="outline" className={cn("gap-1.5 rounded-full", getServerStateBadgeClassName(state), className)}>
        <span className="size-1.5 rounded-full bg-current" aria-hidden="true" />
        {state}
    </Badge>
);

export default ServerStatusBadge;
