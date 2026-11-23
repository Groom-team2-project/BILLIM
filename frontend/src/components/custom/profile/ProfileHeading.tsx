import type { ReactNode } from "react";
import { Avatar } from "@/components/ui/avatar";
import { Loading, Skeleton } from "@/components/ui/state";
import styles from "@/components/custom/profile/profileBlocks.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 아바타·이름·요약 한 줄 — 내 프로필·이웃 프로필 공용 */
export function ProfileHeading({
  name,
  summary,
  action,
}: {
  name: string;
  summary: string;
  /** 내 프로필의 이름 수정처럼 오른쪽에 붙는 조작 */
  action?: ReactNode;
}) {
  return (
    <div className={c("profile-me")}>
      <Avatar name={name} size={56} />
      <div>
        <div className={c("t-title-lg")}>{name}</div>
        <div className={c("t-caption profile-muted")}>{summary}</div>
      </div>
      {action ? <span className={c("profile-me-action")}>{action}</span> : null}
    </div>
  );
}

export function ProfileHeadingSkeleton() {
  return (
    <Loading label="프로필 불러오는 중">
      <div className={c("profile-me")}>
        <Skeleton w={56} h={56} r={28} />
        <div>
          <Skeleton w={120} h={20} />
          <Skeleton w={200} h={14} />
        </div>
      </div>
    </Loading>
  );
}
