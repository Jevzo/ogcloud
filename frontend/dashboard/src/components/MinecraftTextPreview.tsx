import { hasMeaningfulVisibleContent, parseMinecraftSegments } from "@/lib/minecraft-text";

interface MinecraftTextPreviewProps {
    value?: string | null;
    fallback?: string;
    emptyFallback?: string;
    className?: string;
    useFallbackForFormatOnly?: boolean;
}

const MinecraftTextPreview = ({
    value,
    fallback = "--",
    emptyFallback,
    className = "",
    useFallbackForFormatOnly = false,
}: MinecraftTextPreviewProps) => {
    const rawValue = value ?? "";
    const trimmedValue = rawValue.trim();

    if (!trimmedValue) {
        return (
            <p className={`break-words text-sm text-slate-500 ${className}`}>
                {emptyFallback ?? fallback}
            </p>
        );
    }

    if (useFallbackForFormatOnly && !hasMeaningfulVisibleContent(rawValue)) {
        const fallbackSegments = parseMinecraftSegments(fallback);

        return (
            <p className={`break-words whitespace-pre-wrap text-sm leading-relaxed ${className}`}>
                {fallbackSegments.map((segment, index) => (
                    <span key={`${index}-${segment.text}`} style={segment.style}>
                        {segment.text}
                    </span>
                ))}
            </p>
        );
    }

    const segments = parseMinecraftSegments(rawValue);

    return (
        <p className={`break-words whitespace-pre-wrap text-sm leading-relaxed ${className}`}>
            {segments.map((segment, index) => (
                <span key={`${index}-${segment.text}`} style={segment.style}>
                    {segment.text}
                </span>
            ))}
        </p>
    );
};

export default MinecraftTextPreview;
