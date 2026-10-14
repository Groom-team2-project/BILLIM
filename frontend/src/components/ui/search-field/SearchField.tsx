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
};

export function SearchField({
  placeholder = "전동드릴, 캐리어, 캠핑 의자…",
  className,
  value,
  onChange,
  onSubmit,
  label = "물건 검색",
}: SearchFieldProps) {
  const field = (
    <label className={c(`search-field${className ? ` ${className}` : ""}`)}>
      <Icon name="search" size={18} />
      <span className={c("sr-only")}>{label}</span>
      <input
        type="search"
        placeholder={placeholder}
        value={value}
        onChange={onChange ? (e) => onChange(e.target.value) : undefined}
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
