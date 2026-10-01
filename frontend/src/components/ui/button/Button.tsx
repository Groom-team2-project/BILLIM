import type { ReactNode } from "react";
import { Icon } from "@/components/ui/icon";
import styles from "@/components/ui/button/Button.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type ButtonProps = {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md" | "lg";
  icon?: string;
  block?: boolean;
  disabled?: boolean;
  onClick?: () => void;
  children: ReactNode;
};

export function Button({
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
      type="button"
      className={c(`btn btn--${variant}${size === "md" ? "" : ` btn--${size}`}${block ? " btn--block" : ""}`)}
      disabled={disabled}
      onClick={onClick}
    >
      {icon ? <Icon name={icon} size={size === "sm" ? 16 : 18} /> : null}
      {children}
    </button>
  );
}
