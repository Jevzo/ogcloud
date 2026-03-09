import {FiChevronDown, FiChevronUp} from "react-icons/fi";

interface AppNumberInputProps {
    id?: string;
    value: string;
    onChangeValue: (value: string) => void;
    min?: number;
    max?: number;
    step?: number;
    disabled?: boolean;
    placeholder?: string;
}

const DEFAULT_STEP = 1;

const clampValue = (value: number, min?: number, max?: number) => {
    let nextValue = value;

    if (typeof min === "number") {
        nextValue = Math.max(min, nextValue);
    }

    if (typeof max === "number") {
        nextValue = Math.min(max, nextValue);
    }

    return nextValue;
};

const normalizeStep = (step: number) =>
    Number.isFinite(step) && step > 0 ? step : DEFAULT_STEP;

const AppNumberInput = ({
                            id,
                            value,
                            onChangeValue,
                            min,
                            max,
                            step = DEFAULT_STEP,
                            disabled = false,
                            placeholder,
                        }: AppNumberInputProps) => {
    const effectiveStep = normalizeStep(step);

    const nudgeValue = (direction: 1 | -1) => {
        const parsed = Number.parseFloat(value);
        const baseValue = Number.isFinite(parsed)
            ? parsed
            : typeof min === "number"
                ? min
                : 0;
        const nextValue = clampValue(baseValue + direction * effectiveStep, min, max);
        onChangeValue(String(nextValue));
    };

    return (
        <div className="relative">
            <input
                id={id}
                type="number"
                value={value}
                min={min}
                max={max}
                step={effectiveStep}
                placeholder={placeholder}
                disabled={disabled}
                onChange={(event) => onChangeValue(event.target.value)}
                onBlur={() => {
                    const parsed = Number.parseFloat(value);
                    if (!Number.isFinite(parsed)) {
                        return;
                    }

                    onChangeValue(String(clampValue(parsed, min, max)));
                }}
                className="app-input-field block w-full px-3 pr-12"
            />
            <div
                className="absolute inset-y-1.5 right-1.5 flex w-9 flex-col overflow-hidden rounded-md border border-slate-700/90 bg-slate-950/80">
                <button
                    type="button"
                    disabled={disabled}
                    onClick={() => nudgeValue(1)}
                    className="flex flex-1 items-center justify-center text-slate-300 transition-colors hover:bg-slate-800/90 hover:text-primary disabled:cursor-not-allowed disabled:text-slate-500"
                    aria-label="Increase value"
                >
                    <FiChevronUp className="h-3.5 w-3.5"/>
                </button>
                <div className="h-px bg-slate-700"/>
                <button
                    type="button"
                    disabled={disabled}
                    onClick={() => nudgeValue(-1)}
                    className="flex flex-1 items-center justify-center text-slate-300 transition-colors hover:bg-slate-800/90 hover:text-primary disabled:cursor-not-allowed disabled:text-slate-500"
                    aria-label="Decrease value"
                >
                    <FiChevronDown className="h-3.5 w-3.5"/>
                </button>
            </div>
        </div>
    );
};

export default AppNumberInput;
