import { useState } from "react";
import { Icon } from "@/components/ui/icon";
import { setTheme, useTheme, type ThemeName } from "@/stores/theme";
import styles from "@/components/ui/theme-menu/ThemeMenu.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 테마 5종 — 대표색 점은 각 팔레트의 배경색 */
const THEMES: { name: ThemeName; label: string; dot: string }[] = [
  { name: "white", label: "화이트", dot: "#f8f8f8" },
  { name: "cream", label: "크림", dot: "#f8f8dd" },
  { name: "blue", label: "블루", dot: "#dcf5f5" },
  { name: "red", label: "레드", dot: "#f5e6e6" },
  { name: "green", label: "그린", dot: "#dcf6e0" },
];

const DARK_DOT = "#10100e";

export function ThemeMenu() {
  const { theme, dark } = useTheme();
  const [open, setOpen] = useState(false);
  const cur = THEMES.find((t) => t.name === theme) ?? THEMES[1];

  const item = (dot: string, label: string, active: boolean, onClick: () => void) => (
    <button
      key={label}
      type="button"
      role="menuitemradio"
      aria-checked={active ? "true" : "false"}
      className={c(`theme-item${active ? " theme-item--on" : ""}`)}
      onClick={onClick}
    >
      <span className={c("theme-dot")} style={{ background: dot }} />
      <span className={c("theme-item-label")}>{label}</span>
      {active ? <span className={c("theme-check")}><Icon name="check" size={15} /></span> : null}
    </button>
  );

  return (
    <div className={c("theme-menu")}>
      <button
        type="button"
        className={c("theme-btn")}
        aria-label="테마 선택"
        aria-haspopup="menu"
        aria-expanded={open ? "true" : "false"}
        onClick={() => setOpen(!open)}
      >
        <span className={c("theme-dot")} style={{ background: dark ? DARK_DOT : cur.dot }} />
        <Icon name="chevronD" size={14} />
      </button>
      {open ? <div className={c("theme-back")} onClick={() => setOpen(false)} /> : null}
      {open ? (
        <div className={c("theme-pop")} role="menu">
          <div className={c("theme-pop-head")}>테마</div>
          {THEMES.map((t) =>
            item(t.dot, t.label, !dark && theme === t.name, () => {
              setTheme({ theme: t.name, dark: false });
              setOpen(false);
            }),
          )}
          <div className={c("theme-div")} />
          {item(DARK_DOT, "다크 모드", dark, () => {
            setTheme({ dark: !dark });
            setOpen(false);
          })}
        </div>
      ) : null}
    </div>
  );
}
