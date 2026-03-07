import {
  Children,
  isValidElement,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { FiCheck, FiChevronDown } from "react-icons/fi";
import FieldHintLabel from "@/components/FieldHintLabel";

interface AppSelectProps {
  id?: string;
  label?: string;
  labelHint?: string;
  value: string;
  onChangeValue: (value: string) => void;
  disabled?: boolean;
  children: ReactNode;
}

interface SelectOption {
  value: string;
  label: string;
  disabled: boolean;
}

interface OptionElementProps {
  value?: unknown;
  children?: ReactNode;
  disabled?: boolean;
}

const AppSelect = ({
  id,
  label,
  labelHint,
  value,
  onChangeValue,
  disabled = false,
  children,
}: AppSelectProps) => {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  const listboxId = id ? `${id}-options` : undefined;
  const isDropdownOpen = isOpen && !disabled;

  const options = useMemo<SelectOption[]>(() => {
    const nextOptions: SelectOption[] = [];

    for (const child of Children.toArray(children)) {
      if (!isValidElement<OptionElementProps>(child)) {
        continue;
      }

      const optionValueRaw = child.props.value;
      if (optionValueRaw === undefined || optionValueRaw === null) {
        continue;
      }

      const optionValue =
        typeof optionValueRaw === "string" ? optionValueRaw : String(optionValueRaw);
      const optionLabel =
        typeof child.props.children === "string" || typeof child.props.children === "number"
          ? String(child.props.children)
          : String(child.props.children ?? optionValue);

      nextOptions.push({
        value: optionValue,
        label: optionLabel,
        disabled: Boolean(child.props.disabled),
      });
    }

    return nextOptions;
  }, [children]);

  const selectedOption = options.find((option) => option.value === value) ?? null;
  const displayLabel = selectedOption?.label ?? (value || "Select an option");

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

  return (
    <div ref={rootRef} className="app-field-stack">
      {label && (
        labelHint ? (
          <label htmlFor={id}>
            <FieldHintLabel label={label} hint={labelHint} />
          </label>
        ) : (
          <label htmlFor={id} className="app-field-label">
            {label}
          </label>
        )
      )}

      <div className="relative">
        <button
          id={id}
          type="button"
          disabled={disabled}
          aria-haspopup="listbox"
          aria-expanded={isDropdownOpen}
          aria-controls={listboxId}
          onClick={() => setIsOpen((currentValue) => !currentValue)}
          className="app-select-field relative flex items-center text-left"
        >
          <span
            className={`min-w-0 flex-1 ${
              disabled
                ? "text-slate-500"
                : selectedOption
                  ? "text-slate-100"
                  : "text-slate-500"
            }`}
          >
            {displayLabel}
          </span>
          <FiChevronDown
            className={`pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 transition-transform duration-150 ${
              disabled ? "text-slate-600" : "text-slate-500"
            } ${
              isDropdownOpen ? "rotate-180" : ""
            }`}
          />
        </button>

        {isDropdownOpen && (
          <div
            id={listboxId}
            role="listbox"
            className="absolute left-0 right-0 top-[calc(100%+0.5rem)] z-30 overflow-hidden rounded-xl border border-slate-700/90 bg-slate-950 shadow-[0_18px_40px_rgba(2,8,23,0.6)]"
          >
            <div className="max-h-64 space-y-1.5 overflow-y-auto p-2">
              {options.length === 0 ? (
                <p className="rounded-lg px-3 py-2.5 text-sm text-slate-500">
                  No options available
                </p>
              ) : null}
              {options.map((option) => {
                const isSelected = option.value === value;

                return (
                  <button
                    key={option.value}
                    type="button"
                    role="option"
                    aria-selected={isSelected}
                    disabled={option.disabled}
                    onClick={() => {
                      if (option.disabled) {
                        return;
                      }

                      onChangeValue(option.value);
                      setIsOpen(false);
                    }}
                    className={`flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition-colors disabled:cursor-not-allowed ${
                      option.disabled
                        ? "bg-slate-950/70 text-slate-600"
                        : isSelected
                          ? "bg-primary/12 text-primary"
                          : "text-slate-200 hover:bg-slate-800 hover:text-white"
                    }`}
                  >
                    <span className="min-w-0 flex-1">{option.label}</span>
                    {isSelected && <FiCheck className="ml-auto h-4 w-4 shrink-0" />}
                  </button>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default AppSelect;
