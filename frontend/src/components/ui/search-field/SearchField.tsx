import type { KeyboardEvent, RefObject } from "react";
import { Icon } from "@/components/ui/icon";
import styles from "@/components/ui/search-field/SearchField.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type SearchFieldProps = {
  placeholder?: string;
  className?: string;
  /** 검색어를 화면이 들고 있을 때만 넘긴다. 없으면 기존처럼 비제어 입력 */
  value?: string;
  onChange?: (value: string) => void;
  /** Enter 입력. 지정하면 입력을 form으로 감싼다 */
  onSubmit?: () => void;
  /** 스크린리더용 라벨. 물건 검색이 아닌 화면에서 바꿔 쓴다 */
  label?: string;
  /** 입력창에 포커스를 돌려줘야 하는 화면에서만 넘긴다 */
  inputRef?: RefObject<HTMLInputElement | null>;
  /** 화살표로 후보 목록에 들어가는 등 화면별 키 처리 */
  onKeyDown?: (e: KeyboardEvent<HTMLInputElement>) => void;
};

export function SearchField({
  placeholder = "전동드릴, 캐리어, 캠핑 의자…",
  className,
  value,
  onChange,
  onSubmit,
  label = "물건 검색",
  inputRef,
  onKeyDown,
}: SearchFieldProps) {
  const field = (
    <label className={c(`search-field${className ? ` ${className}` : ""}`)}>
      <Icon name="search" size={18} />
      <span className={c("sr-only")}>{label}</span>
      <input
        ref={inputRef}
        type="search"
        placeholder={placeholder}
        value={value}
        onChange={onChange ? (e) => onChange(e.target.value) : undefined}
        onKeyDown={(e) => {
          // 한글 조합 중 Enter는 글자를 확정하는 입력이다. 검색으로 받으면 안 된다
          if (e.key === "Enter" && e.nativeEvent.isComposing) {
            e.preventDefault();
            return;
          }
          onKeyDown?.(e);
        }}
      />
    </label>
  );

  if (!onSubmit) return field;

  return (
    <form
      role="search"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit();
      }}
    >
      {field}
    </form>
  );
}
