import {FiInfo} from "react-icons/fi";

interface FieldHintLabelProps {
    label: string;
    hint: string;
    className?: string;
}

const FieldHintLabel = ({label, hint, className = ""}: FieldHintLabelProps) => (
    <span className={`app-field-label inline-flex items-center ${className}`}>
    <span>{label}</span>
    <span
        className="ml-1 inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-slate-800/80 text-slate-400"
        title={hint}
        aria-label={hint}
    >
      <FiInfo className="h-2.5 w-2.5 align-middle"/>
    </span>
  </span>
);

export default FieldHintLabel;
