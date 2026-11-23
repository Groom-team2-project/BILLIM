import { Navigate, NavLink, Outlet } from "react-router-dom";
import { useSession } from "@/stores/session";
import { PageHeader } from "@/components/layout/header";
import adminStyles from "@/components/custom/admin/admin.module.css";
import { classes } from "@/utils/classes";

const c = classes(adminStyles);

const TABS = [
  { to: "/admin", label: "대시보드" },
  { to: "/admin/reports", label: "신고 관리" },
  { to: "/admin/notices", label: "공지 관리" },
];

/** 관리자 콘솔 — 서버 권한 검사로 진입 제한 (도메인 가이드 '결정: 관리자 진입') */
export function AdminLayout() {
  const { loggedIn, admin } = useSession();
  if (!admin) return <Navigate to={loggedIn ? "/" : "/login"} replace />;
  return (
    <main className={c("admin")}>
      <PageHeader title="관리자 콘솔" backTo="/" standalone />
      <nav className={c("adm-nav")} aria-label="관리자 메뉴">
        {TABS.map((t) => (
          <NavLink
            key={t.to}
            to={t.to}
            end={t.to === "/admin"}
            className={({ isActive }) => c(`adm-nav-b${isActive ? " adm-nav-b--on" : ""}`)}
          >
            {t.label}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </main>
  );
}
