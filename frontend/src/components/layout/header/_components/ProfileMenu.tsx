import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { logout, useSession } from "@/stores/session";
import { useUi } from "@/components/overlay/UiContext";
import { Avatar } from "@/components/ui/avatar";
import styles from "@/components/layout/header/_components/ProfileMenu.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 헤더 계정 메뉴 — 관리자 콘솔은 관리자에게만 노출 */
export function ProfileMenu() {
  const { admin } = useSession();
  const { confirm, toast } = useUi();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  const go = (to: string) => {
    setOpen(false);
    navigate(to);
  };

  return (
    <div className={c("pmenu")}>
      <button
        type="button"
        className={c("pmenu-btn")}
        aria-label="내 계정"
        aria-haspopup="menu"
        aria-expanded={open ? "true" : "false"}
        onClick={() => setOpen(!open)}
      >
        <Avatar name="나" size={30} />
      </button>
      {open ? <div className={c("pmenu-back")} onClick={() => setOpen(false)} /> : null}
      {open ? (
        <div className={c("pmenu-pop")} role="menu">
          <button type="button" className={c("pmenu-item")} role="menuitem" onClick={() => go("/profile")}>
            내 프로필
          </button>
          {admin ? (
            <button type="button" className={c("pmenu-item")} role="menuitem" onClick={() => go("/admin")}>
              관리자 콘솔
            </button>
          ) : null}
          <div className={c("pmenu-div")} />
          <button
            type="button"
            className={c("pmenu-item")}
            role="menuitem"
            onClick={() => {
              setOpen(false);
              confirm({
                title: "로그아웃할까요?",
                body: "다시 로그인하면 이어서 이용할 수 있어요.",
                confirm: "로그아웃",
                onConfirm: () => {
                  logout();
                  toast("로그아웃되었어요");
                  navigate("/login");
                },
              });
            }}
          >
            로그아웃
          </button>
        </div>
      ) : null}
    </div>
  );
}
