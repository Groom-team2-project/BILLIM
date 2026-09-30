import { NavLink, useNavigate } from "react-router-dom";
import { MoreMenu, type MoreMenuItem } from "@/components/ui/more-menu";
import { SearchField } from "@/components/ui/search-field";
import { ThemeMenu } from "@/components/ui/theme-menu";
import { useUnreadCount } from "@/stores/notifications";
import { ProfileMenu } from "@/components/layout/header/_components/ProfileMenu";
import styles from "@/components/layout/header/Header.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type HeaderProps = {
  /** 하위 화면 — 모바일에서 뒤로+제목+더보기 */
  sub: boolean;
  title?: string;
  /** 홈은 큰 검색창이 있어 헤더 검색은 자리만 유지 (프로토타입 hideSearch) */
  searchHidden?: boolean;
  moreItems?: MoreMenuItem[];
  onBack?: () => void;
};

/** 상단 헤더 — 워드마크·내비·검색·등록·테마·알림·계정 (§7) */
export function Header({ sub, title, searchHidden, moreItems = [], onBack }: HeaderProps) {
  const navigate = useNavigate();
  const unreadNotifs = useUnreadCount();
  return (
    <header className={c(`shell-header${sub ? " shell-header--sub" : ""}`)}>
      <div className={c("shell-header-inner")}>
        {sub ? (
          <button type="button" className={c("shell-back")} aria-label="뒤로" onClick={onBack}>
            <BackIcon />
          </button>
        ) : null}
        <NavLink
          to="/"
          className={c(`shell-wordmark${title || sub ? " shell-wordmark--desk-only" : ""}`)}
          aria-label="BILLIM 홈"
        >
          BILLIM
        </NavLink>
        {title ? <span className={c("shell-title t-title")}>{title}</span> : null}
        {sub && moreItems.length > 0 ? <MoreMenu label="더보기" items={moreItems} className={c("shell-more")} /> : null}

        <nav className={c("shell-desktop-nav")} aria-label="주 메뉴">
          <NavLink to="/" end className={({ isActive }) => c(`shell-nav-link${isActive ? " shell-nav-link--on" : ""}`)}>홈</NavLink>
          <NavLink to="/search" className={({ isActive }) => c(`shell-nav-link${isActive ? " shell-nav-link--on" : ""}`)}>물건 찾기</NavLink>
          <NavLink to="/chat" className={({ isActive }) => c(`shell-nav-link${isActive ? " shell-nav-link--on" : ""}`)}>채팅</NavLink>
          <NavLink to="/rentals" className={({ isActive }) => c(`shell-nav-link${isActive ? " shell-nav-link--on" : ""}`)}>내 대여</NavLink>
        </nav>

        <form
          className={c(`shell-header-search${searchHidden ? " shell-header-search--home" : ""}`)}
          onSubmit={(e) => {
            e.preventDefault();
            navigate("/search");
          }}
        >
          <SearchField placeholder="물건 검색" className={c("shell-search-input")} />
        </form>

        <div className={c("shell-header-actions")}>
          <NavLink to="/register" className={c("shell-register-button t-label")}>
            <RegisterPlusIcon />
            물건 등록
          </NavLink>
          <ThemeMenu />
          <NavLink to="/notifications" className={c("shell-icon-button")} aria-label={`알림 ${unreadNotifs}개`}>
            <BellIcon />
            {unreadNotifs > 0 ? <span className={c("shell-icon-badge")}>{unreadNotifs}</span> : null}
          </NavLink>
          <ProfileMenu />
        </div>
      </div>
    </header>
  );
}

function icon(path: React.ReactNode) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {path}
    </svg>
  );
}

const BackIcon = () => icon(<path d="m15 6-6 6 6 6" />);
const BellIcon = () => icon(<path d="M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15zM10 20.5a2 2 0 0 0 4 0" />);
const RegisterPlusIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 5v14M5 12h14" />
  </svg>
);
