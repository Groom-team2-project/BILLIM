import type { ReactNode } from "react";
import { Icon } from "@/components/ui/icon";
import styles from "@/components/ui/meta/Meta.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 아이콘 + 한 줄 정보 (장소, 날짜 등) */
export function Meta({ icon, strong, children }: { icon: string; strong?: boolean; children: ReactNode }) {
  return (
    <div className={c(`meta${strong ? " meta--strong" : ""}`)}>
      <Icon name={icon} size={15} />
      <span>{children}</span>
    </div>
  );
}
