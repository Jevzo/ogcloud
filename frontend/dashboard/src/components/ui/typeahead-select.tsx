"use client";

import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { CheckIcon, ChevronDownIcon } from "lucide-react";

import { cn } from "@/lib/utils";
import {
    InputGroup,
    InputGroupAddon,
    InputGroupButton,
    InputGroupInput,
} from "@/components/ui/input-group";

interface TypeaheadSelectProps {
    createOptionIcon?: ReactNode;
    createOptionLabel?: (query: string) => string | null;
    disabled?: boolean;
    emptyMessage: string;
    id?: string;
    inputIcon?: ReactNode;
    invalid?: boolean;
    maxVisibleOptions?: number;
    onValueChange: (value: string) => void;
    options: string[];
    placeholder: string;
    renderOptionIcon?: (option: string, selected: boolean) => ReactNode;
    value: string;
}

function TypeaheadSelect({
    createOptionIcon,
    createOptionLabel,
    disabled = false,
    emptyMessage,
    id,
    inputIcon,
    invalid = false,
    maxVisibleOptions = 6,
    onValueChange,
    options,
    placeholder,
    renderOptionIcon,
    value,
}: TypeaheadSelectProps) {
    const rootRef = useRef<HTMLDivElement | null>(null);
    const [isOpen, setIsOpen] = useState(false);
    const [highlightedIndex, setHighlightedIndex] = useState(-1);

    const normalizedOptions = [
        ...new Set(options.map((option) => option.trim()).filter(Boolean)),
    ].sort();
    const trimmedValue = value.trim();
    const normalizedQuery = trimmedValue.toLowerCase();
    const visibleOptions =
        normalizedQuery === ""
            ? normalizedOptions.slice(0, maxVisibleOptions)
            : normalizedOptions
                  .filter((option) => option.toLowerCase().includes(normalizedQuery))
                  .slice(0, maxVisibleOptions);
    const createLabel = createOptionLabel?.(trimmedValue) ?? null;
    const items = [
        ...(createLabel
            ? [{ kind: "create" as const, label: createLabel, value: trimmedValue }]
            : []),
        ...visibleOptions.map((option) => ({
            kind: "option" as const,
            label: option,
            value: option,
        })),
    ];

    useEffect(() => {
        const handlePointerDown = (event: PointerEvent) => {
            if (!rootRef.current?.contains(event.target as Node)) {
                setIsOpen(false);
                setHighlightedIndex(-1);
            }
        };

        document.addEventListener("pointerdown", handlePointerDown);

        return () => {
            document.removeEventListener("pointerdown", handlePointerDown);
        };
    }, []);

    const commitValue = (nextValue: string) => {
        onValueChange(nextValue);
        setIsOpen(false);
        setHighlightedIndex(-1);
    };

    const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
        if (disabled) {
            return;
        }

        if (event.key === "ArrowDown") {
            event.preventDefault();
            setIsOpen(true);
            setHighlightedIndex((currentValue) =>
                items.length === 0 ? -1 : Math.min(items.length - 1, currentValue + 1),
            );
            return;
        }

        if (event.key === "ArrowUp") {
            event.preventDefault();
            setIsOpen(true);
            setHighlightedIndex((currentValue) =>
                items.length === 0 ? -1 : currentValue <= 0 ? 0 : currentValue - 1,
            );
            return;
        }

        if (event.key === "Enter" && isOpen && highlightedIndex >= 0 && items[highlightedIndex]) {
            event.preventDefault();
            commitValue(items[highlightedIndex].value);
            return;
        }

        if (event.key === "Escape") {
            setIsOpen(false);
            setHighlightedIndex(-1);
        }
    };

    return (
        <div ref={rootRef} className="relative">
            <InputGroup>
                {inputIcon ? <InputGroupAddon>{inputIcon}</InputGroupAddon> : null}
                <InputGroupInput
                    id={id}
                    aria-invalid={invalid ? "true" : "false"}
                    autoComplete="off"
                    disabled={disabled}
                    placeholder={placeholder}
                    value={value}
                    onFocus={() => {
                        setIsOpen(true);
                        setHighlightedIndex(-1);
                    }}
                    onKeyDown={handleKeyDown}
                    onChange={(event) => {
                        onValueChange(event.target.value);
                        setIsOpen(true);
                        setHighlightedIndex(-1);
                    }}
                />
                <InputGroupAddon align="inline-end">
                    <InputGroupButton
                        aria-expanded={isOpen}
                        className="data-[state=open]:bg-muted"
                        data-state={isOpen ? "open" : "closed"}
                        disabled={disabled}
                        onClick={() => {
                            setIsOpen((currentValue) => !currentValue);
                            setHighlightedIndex(-1);
                        }}
                        size="icon-xs"
                        variant="ghost"
                    >
                        <ChevronDownIcon
                            className={cn("size-4 transition-transform", isOpen && "rotate-180")}
                        />
                    </InputGroupButton>
                </InputGroupAddon>
            </InputGroup>

            {isOpen ? (
                <div className="absolute inset-x-0 top-full z-50 mt-2 overflow-hidden rounded-lg bg-popover text-popover-foreground shadow-md ring-1 ring-foreground/10">
                    <div className="max-h-72 overflow-y-auto p-1">
                        {items.length > 0 ? (
                            items.map((item, index) => {
                                const selected =
                                    item.kind === "option" &&
                                    item.value.toLowerCase() === normalizedQuery;

                                return (
                                    <button
                                        key={`${item.kind}:${item.value}`}
                                        type="button"
                                        className={cn(
                                            "relative flex w-full items-center gap-2 rounded-md py-2 pr-9 pl-2 text-left text-sm outline-hidden transition-colors",
                                            highlightedIndex === index
                                                ? "bg-accent text-accent-foreground"
                                                : "hover:bg-accent hover:text-accent-foreground",
                                        )}
                                        disabled={disabled}
                                        onMouseEnter={() => setHighlightedIndex(index)}
                                        onPointerDown={(event) => {
                                            event.preventDefault();
                                            commitValue(item.value);
                                        }}
                                    >
                                        {item.kind === "create"
                                            ? createOptionIcon
                                            : renderOptionIcon?.(item.value, selected)}
                                        <span className="truncate">{item.label}</span>
                                        {selected ? (
                                            <span className="absolute right-2 flex size-4 items-center justify-center">
                                                <CheckIcon className="size-4 text-primary" />
                                            </span>
                                        ) : null}
                                    </button>
                                );
                            })
                        ) : (
                            <div className="px-2 py-6 text-center text-sm text-muted-foreground">
                                {emptyMessage}
                            </div>
                        )}
                    </div>
                </div>
            ) : null}
        </div>
    );
}

export { TypeaheadSelect };
