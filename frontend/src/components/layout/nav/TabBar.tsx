import { useEffect } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { refreshChatUnreadCount, useChatUnreadCount } from "@/stores/chat-unread";
import { TAB_MENU } from "@/components/layout/nav/constants/tab-menu-list";
import styles from "@/components/layout/nav/TabBar.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 하단 탭 (모바일) — 데스크톱·하위 화면에서는 렌더되지 않음 */
export function TabBar() {
  const { pathname } = useLocation();
  const unreadChats = useChatUnreadCount();

  useEffect(() => {
    const refresh = () => { void refreshChatUnreadCount(); };
    refresh();
    window.addEventListener("focus", refresh);
    return () => window.removeEventListener("focus", refresh);
  }, [pathname]);

  return (
    <nav className={c("shell-tabbar")} aria-label="주 메뉴">
      {TAB_MENU.map((t) =>
        t.register ? (
          <NavLink
            key={t.to}
            to={t.to}
            className={({ isActive }) => c(`shell-tab shell-tab-register${isActive ? " shell-tab--on" : ""}`)}
            aria-label="물건 등록"
          >
            {t.icon}
          </NavLink>
        ) : (
          <NavLink
            key={t.to}
            to={t.to}
            end={t.end}
            className={({ isActive }) => c(`shell-tab${isActive ? " shell-tab--on" : ""}`)}
          >
            {t.icon}
            <span className={c("t-micro")}>{t.label}</span>
            {t.chatDot && unreadChats > 0 ? (
              <span className={c("shell-tab-dot")} aria-label={`안 읽은 채팅 ${unreadChats}개`}>{unreadChats}</span>
            ) : null}
          </NavLink>
        ),
      )}
    </nav>
  );
}
