import { Link } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import type { ApiNotice } from "@/api/notices";
import styles from "@/components/custom/notices/NoticeBar.module.css";
import { classes } from "@/utils/classes";
import { noticeDate } from "@/utils/date";

const c = classes(styles);

/** 공지·고정 표시 배지. 홈 한 줄과 목록 화면 공용 */
export function NoticeTag({ children }: { children: string }) {
  return <span className={c("notice-bar-tag")}>{children}</span>;
}

/** 홈 상단 공지 한 줄. 고정 우선·최신순 첫 공지만, 누르면 동네 공지 목록 */
export function NoticeBar({ notice }: { notice: ApiNotice }) {
  return (
    <Link to={`/communities/${notice.communityId}/notices`} className={c("notice-bar")}>
      <NoticeTag>공지</NoticeTag>
      <span className={c("notice-bar-title")}>{notice.title}</span>
      <span className={c("t-caption notice-bar-date")}>{noticeDate(notice.createdAt)}</span>
      <Icon name="chevronR" size={16} />
    </Link>
  );
}
