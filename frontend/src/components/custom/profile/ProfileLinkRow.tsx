import { Icon } from "@/components/ui/icon";
import styles from "@/components/custom/profile/profileBlocks.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 다음 화면으로 넘기는 행 — 프로필의 차단·신고 내역 진입 */
export function ProfileLinkRow({
  icon,
  title,
  trailing,
  onClick,
}: {
  icon: string;
  title: string;
  trailing?: string;
  onClick: () => void;
}) {
  return (
    <button type="button" className={c("profile-row")} onClick={onClick}>
      <Icon name={icon} size={20} />
      <span className={c("profile-row-body")}>
        <span className={c("profile-row-title")}>{title}</span>
      </span>
      {trailing ? <span className={c("t-caption profile-muted")}>{trailing}</span> : null}
      <Icon name="chevronR" size={18} />
    </button>
  );
}
