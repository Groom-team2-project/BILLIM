import { Icon } from "@/components/ui/icon";
import { fmtDate, rangeDays } from "@/utils/date";
import styles from "@/components/ui/date-range/DateRange.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 대여 기간 표기: 9/26(토) – 9/27(일) · 2일 */
export function DateRange({
  start,
  end,
  icon = true,
  len = true,
  className,
}: {
  start: number[];
  end: number[];
  icon?: boolean;
  /** "· N일" 길이 표시 */
  len?: boolean;
  className?: string;
}) {
  return (
    <span className={c(`date-range t-body-lg${className ? ` ${className}` : ""}`)}>
      {icon ? <Icon name="calendar" size={16} /> : null}
      {`${fmtDate(start)} – ${fmtDate(end)}`}
      {len ? <span className={c("date-range-len t-caption")}>· {rangeDays(start, end)}일</span> : null}
    </span>
  );
}
