import type { ReactNode } from "react";
import styles from "@/components/ui/d-day/DDay.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 반납 D-day 표시 */
export function DDay({ late, children }: { late?: boolean; children: ReactNode }) {
  return <span className={c(`dday${late ? " dday--late" : ""}`)}>{children}</span>;
}
