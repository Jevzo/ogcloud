import { useEffect, useMemo, useRef, useState } from "react";
import { FiCheck, FiChevronDown } from "react-icons/fi";
import FieldHintLabel from "@/components/FieldHintLabel";

interface AppCreatableSelectProps {
    id?: string;
    label?: string;
    labelHint?: string;
    value: string;
    onChangeValue: (value: string) => void;
    options: string[];
    disabled?: boolean;
    placeholder?: string;
    hint?: string;
    emptyLabel?: string;
}

const AppCreatableSelect = ({
    id,
    label,
    labelHint,
    value,
    onChangeValue,
    options,
    disabled = false,
    placeholder = "Select an option",
    hint,
    emptyLabel = "No options available",
}: AppCreatableSelectProps) => {
    const rootRef = useRef<HTMLDivElement | null>(null);
    const inputRef = useRef<HTMLInputElement | null>(null);
    const [isOpen, setIsOpen] = useState(false);
    const listboxId = id ? `${id}-options` : undefined;

    const normalizedValue = value.trim();
    const isDropdownOpen = isOpen && !disabled;
    const filteredOptions = useMemo(() => {
        const normalizedFilter = value.trim().toLowerCase();

        if (!normalizedFilter) {
            return options;
        }

        return options.filter((option) => option.toLowerCase().includes(normalizedFilter));
    }, [options, value]);

    const hasExactMatch = useMemo(
        () => options.some((option) => option.toLowerCase() === normalizedValue.toLowerCase()),
        [options, normalizedValue],
    );

    useEffect(() => {
        if (!isDropdownOpen) {
            return;
        }

        const handlePointerDown = (event: MouseEvent) => {
            if (!rootRef.current?.contains(event.target as Node)) {
                setIsOpen(false);
            }
        };

        const handleEscape = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                setIsOpen(false);
            }
        };

        window.addEventListener("mousedown", handlePointerDown);
        window.addEventListener("keydown", handleEscape);

        return () => {
            window.removeEventListener("mousedown", handlePointerDown);
            window.removeEventListener("keydown", handleEscape);
        };
    }, [isDropdownOpen]);

    const chooseValue = (nextValue: string) => {
        onChangeValue(nextValue);
        setIsOpen(false);
    };

    const useCustomValue = () => {
        onChangeValue(normalizedValue);
        setIsOpen(false);
    };

    return (
        <div ref={rootRef} className="app-field-stack">
            {label &&
                (labelHint ? (
                    <label htmlFor={id}>
                        <FieldHintLabel label={label} hint={labelHint} />
                    </label>
                ) : (
                    <label htmlFor={id} className="app-field-label">
                        {label}
                    </label>
                ))}

            <div className="relative">
                <div
                    className={`app-select-field relative flex items-center ${
                        disabled ? "cursor-not-allowed opacity-60" : ""
                    }`}
                >
                    <input
                        id={id}
                        ref={inputRef}
                        type="text"
                        value={value}
                        onChange={(event) => {
                            onChangeValue(event.target.value);
                            setIsOpen(true);
                        }}
                        onFocus={() => setIsOpen(true)}
                        onKeyDown={(event) => {
                            if (!disabled && event.key === "ArrowDown" && !isDropdownOpen) {
                                setIsOpen(true);
                            }

                            if (event.key === "Enter" && normalizedValue) {
                                onChangeValue(normalizedValue);
                                setIsOpen(false);
                            }
                        }}
                        disabled={disabled}
                        aria-haspopup="listbox"
                        aria-expanded={isDropdownOpen}
                        aria-controls={listboxId}
                        className={`h-full w-full bg-transparent p-0 text-sm outline-none placeholder:text-slate-500 ${
                            disabled ? "text-slate-500" : "text-slate-100"
                        }`}
                        placeholder={placeholder}
                    />
                    <button
                        type="button"
                        tabIndex={-1}
                        aria-label="Toggle options"
                        disabled={disabled}
                        onClick={() => {
                            setIsOpen((currentValue) => {
                                const nextIsOpen = !currentValue;

                                if (nextIsOpen) {
                                    inputRef.current?.focus();
                                }

                                return nextIsOpen;
                            });
                        }}
                        className="absolute right-0 top-0 inline-flex h-full items-center px-3 text-slate-500"
                    >
                        <FiChevronDown
                            className={`h-4 w-4 transition-transform duration-150 ${isDropdownOpen ? "rotate-180" : ""}`}
                        />
                    </button>
                </div>

                {isDropdownOpen && (
                    <div
                        id={listboxId}
                        role="listbox"
                        className="absolute left-0 right-0 top-[calc(100%+0.5rem)] z-30 overflow-hidden rounded-xl border border-slate-700/90 bg-slate-950 shadow-[0_18px_40px_rgba(2,8,23,0.6)]"
                    >
                        <div className="max-h-64 space-y-1.5 overflow-y-auto p-2">
                            {normalizedValue && !hasExactMatch ? (
                                <button
                                    type="button"
                                    onClick={useCustomValue}
                                    className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm text-slate-200 transition-colors hover:bg-slate-800 hover:text-white"
                                >
                                    <span className="min-w-0 flex-1">Use "{normalizedValue}"</span>
                                </button>
                            ) : null}

                            {filteredOptions.map((option) => {
                                const isSelected = option === normalizedValue;

                                return (
                                    <button
                                        key={option}
                                        type="button"
                                        role="option"
                                        aria-selected={isSelected}
                                        onClick={() => chooseValue(option)}
                                        className={`flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition-colors ${
                                            isSelected
                                                ? "bg-primary/12 text-primary"
                                                : "text-slate-200 hover:bg-slate-800 hover:text-white"
                                        }`}
                                    >
                                        <span className="min-w-0 flex-1">{option}</span>
                                        {isSelected && (
                                            <FiCheck className="ml-auto h-4 w-4 shrink-0" />
                                        )}
                                    </button>
                                );
                            })}

                            {filteredOptions.length === 0 &&
                            !(normalizedValue && !hasExactMatch) ? (
                                <p className="rounded-lg px-3 py-2.5 text-sm text-slate-500">
                                    {emptyLabel}
                                </p>
                            ) : null}
                        </div>
                    </div>
                )}
            </div>

            {hint ? <span className="text-xs text-slate-500">{hint}</span> : null}
        </div>
    );
};

export default AppCreatableSelect;
