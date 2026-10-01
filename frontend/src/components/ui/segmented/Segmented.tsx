import { Icon } from "@/components/ui/icon";
import styles from "@/components/ui/segmented/Segmented.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type SegmentedOption<T extends string> = {
  value: T;
  label: string;
  icon?: string;
};

type SegmentedProps<T extends string> = {
  label: string;
  value: T;
  options: SegmentedOption<T>[];
  onChange?: (value: T) => void;
};

export function Segmented<T extends string>({ label, value, options, onChange }: SegmentedProps<T>) {
  return (
    <div className={c("seg")} role="group" aria-label={label}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          className={c("seg-option")}
          aria-pressed={value === o.value ? "true" : "false"}
          onClick={() => onChange?.(o.value)}
        >
          {o.icon ? <Icon name={o.icon} size={15} /> : null}
          {o.label}
        </button>
      ))}
    </div>
  );
}
