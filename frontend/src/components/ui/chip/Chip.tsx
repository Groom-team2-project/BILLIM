import type { ReactNode } from "react";
import { Icon } from "@/components/ui/icon";
import styles from "@/components/ui/chip/Chip.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type ChipProps = {
  icon?: string;
  selected?: boolean;
  caret?: boolean;
  count?: number;
  onClick?: () => void;
  children: ReactNode;
};

export function Chip({ icon, selected, caret, count, onClick, children }: ChipProps) {
  return (
    <button type="button" className={c("chip")} aria-pressed={selected ? "true" : "false"} onClick={onClick}>
      {icon ? <Icon name={icon} size={16} /> : null}
      {children}
      {count != null ? <span className={c("chip-count")}>{count}</span> : null}
      {caret ? <Icon name="chevronD" size={14} /> : null}
    </button>
  );
}
