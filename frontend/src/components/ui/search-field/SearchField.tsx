import { Icon } from "@/components/ui/icon";
import styles from "@/components/ui/search-field/SearchField.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type SearchFieldProps = {
  placeholder?: string;
  className?: string;
};

export function SearchField({ placeholder = "전동드릴, 캐리어, 캠핑 의자…", className }: SearchFieldProps) {
  return (
    <label className={c(`search-field${className ? ` ${className}` : ""}`)}>
      <Icon name="search" size={18} />
      <span className={c("sr-only")}>물건 검색</span>
      <input type="search" placeholder={placeholder} />
    </label>
  );
}
