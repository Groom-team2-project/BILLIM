import { Icon } from "@/components/ui/icon";
import { STATUS_UI, type RentalStatusView } from "@/components/custom/rentals/status";
import styles from "@/components/custom/rentals/StatusBadge.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type StatusBadgeProps = {
  status: RentalStatusView;
};

export function StatusBadge({ status }: StatusBadgeProps) {
  const s = STATUS_UI[status];
  return (
    <span className={c(`status-badge status-badge--${status}`)}>
      <Icon name={s.icon} size={14} />
      {s.label}
    </span>
  );
}
