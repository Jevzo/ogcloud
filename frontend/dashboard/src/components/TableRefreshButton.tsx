import { FiRefreshCw } from "react-icons/fi";

interface TableRefreshButtonProps {
    onClick: () => void | Promise<void>;
    isRefreshing?: boolean;
    label?: string;
}

const TableRefreshButton = ({
    onClick,
    isRefreshing = false,
    label = "Refresh table",
}: TableRefreshButtonProps) => (
    <button
        type="button"
        onClick={onClick}
        disabled={isRefreshing}
        className="button-hover-lift button-shadow-neutral inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-700 bg-slate-950/70 text-slate-300 transition-colors hover:bg-slate-800 hover:text-primary disabled:cursor-not-allowed disabled:opacity-60"
        aria-label={label}
        title={label}
        aria-busy={isRefreshing}
    >
        <FiRefreshCw className={`h-4 w-4 ${isRefreshing ? "animate-spin" : ""}`} />
    </button>
);

export default TableRefreshButton;
