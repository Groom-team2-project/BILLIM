import { useState } from "react";
import { Icon } from "@/components/ui/icon";
import styles from "@/components/ui/more-menu/MoreMenu.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

export type MoreMenuItem = {
  label: string;
  icon?: string;
  danger?: boolean;
  onSelect: () => void;
};

type MoreMenuProps = {
  /** 버튼 aria-label */
  label: string;
  items: MoreMenuItem[];
  className?: string;
};

/** 더보기(⋮) 드롭다운 — 하위 화면 헤더·채팅방 공용 */
export function MoreMenu({ label, items, className }: MoreMenuProps) {
  const [open, setOpen] = useState(false);
  return (
    <div className={c(`mmenu${className ? ` ${className}` : ""}`)}>
      <button
        type="button"
        className={c("mmenu-btn")}
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open ? "true" : "false"}
        onClick={() => setOpen(!open)}
      >
        <Icon name="dots" size={20} />
      </button>
      {open ? <div className={c("mmenu-back")} onClick={() => setOpen(false)} /> : null}
      {open ? (
        <div className={c("mmenu-pop")} role="menu">
          {items.map((it) => (
            <button
              key={it.label}
              type="button"
              role="menuitem"
              className={c(`mmenu-item${it.danger ? " mmenu-item--danger" : ""}`)}
              onClick={() => {
                setOpen(false);
                it.onSelect();
              }}
            >
              {it.icon ? <Icon name={it.icon} size={16} /> : null}
              {it.label}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
