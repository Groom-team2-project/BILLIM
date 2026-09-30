import styles from "@/components/ui/avatar/Avatar.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 이름 첫 글자 아바타 */
export function Avatar({ name, size = 36 }: { name: string; size?: number }) {
  return (
    <span
      className={c("avatar")}
      style={{ width: size, height: size, fontSize: Math.round(size * 0.4) }}
      aria-hidden="true"
    >
      {name.slice(0, 1)}
    </span>
  );
}
