import { useId } from "react";
import styles from "@/components/ui/text-field/TextField.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type TextFieldProps = {
  label: string;
  optional?: boolean;
  multiline?: boolean;
  placeholder?: string;
  defaultValue?: string;
  hint?: string;
};

export function TextField({ label, optional, multiline, placeholder, defaultValue, hint }: TextFieldProps) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  return (
    <div className={c("text-field")}>
      <label htmlFor={id} className={c("t-label")}>
        {label}
        {optional ? <span className={c("text-field-optional t-caption")}> (선택)</span> : null}
      </label>
      {multiline ? (
        <textarea id={id} className={c("text-field-input t-body")} rows={4} placeholder={placeholder} defaultValue={defaultValue} aria-describedby={hintId} />
      ) : (
        <input id={id} type="text" className={c("text-field-input t-body")} placeholder={placeholder} defaultValue={defaultValue} aria-describedby={hintId} />
      )}
      {hint ? (
        <span id={hintId} className={c("text-field-hint t-caption")}>{hint}</span>
      ) : null}
    </div>
  );
}
