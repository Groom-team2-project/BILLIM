import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { ThemeMenu } from "@/components/ui/theme-menu";
import styles from "@/components/layout/header/PageHeader.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type PageHeaderProps = {
  title: string;
  /** 지정하면 해당 경로로, 없으면 히스토리 뒤로 */
  backTo?: string;
  /** 제목 오른쪽 액션 (예: 모두 읽음) */
  right?: ReactNode;
  /** AppLayout 밖의 독립 화면(온보딩·관리자) — 본문 여백 상쇄 안 함 */
  standalone?: boolean;
};

/** 하위 화면 헤더 — 프로토타입 .hdr (뒤로 + 제목 + 액션 + 테마, 하단 보더) */
export function PageHeader({ title, backTo, right, standalone }: PageHeaderProps) {
  const navigate = useNavigate();
  return (
    <header className={c(`page-header${standalone ? " page-header--standalone" : ""}`)}>
      <button
        type="button"
        className={c("page-header-back")}
        aria-label="뒤로"
        onClick={() => (backTo ? navigate(backTo) : navigate(-1))}
      >
        <Icon name="chevronL" size={24} />
      </button>
      <span className={c("page-header-grow")}>
        <span className={c("page-header-title")}>{title}</span>
      </span>
      {right ?? null}
      {/* 테마 버튼은 화면당 하나 — 앱 헤더가 없는 독립 화면에서만 */}
      {standalone ? <ThemeMenu /> : null}
    </header>
  );
}
