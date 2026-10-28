import type { ReactNode } from "react";
import { Icon } from "@/components/ui/icon";
import styles from "@/components/ui/button/Button.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type ButtonProps = {
  /** 폼 제출 버튼만 submit. 기본 button — 폼 안 일반 버튼의 의도치 않은 제출 방지 */
  type?: "button" | "submit";
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md" | "lg";
  icon?: string;
  block?: boolean;
  disabled?: boolean;
  onClick?: () => void;
  children: ReactNode;
};

export function Button({
  type = "button",
  variant = "primary",
  size = "md",
  icon,
  block,
  disabled,
  onClick,
  children,
}: ButtonProps) {
  return (
    <button
      type={type}
      className={c(`btn btn--${variant}${size === "md" ? "" : ` btn--${size}`}${block ? " btn--block" : ""}`)}
      disabled={disabled}
      onClick={onClick}
    >
      {icon ? <Icon name={icon} size={size === "sm" ? 16 : 18} /> : null}
      {children}
    </button>
  );
}
