import { Link } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { CATEGORIES } from "@/data/items";
import styles from "@/components/custom/items/CategoryGrid.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 카테고리 10종 그리드 — 선택 시 해당 카테고리로 물건 찾기 이동 */
export function CategoryGrid() {
  return (
    <div className={c("category-grid")}>
      {CATEGORIES.map((cat) => (
        <Link key={cat.id} to={`/search?cat=${cat.id}`} className={c("category-item")}>
          <span className={c("category-icon")}>
            <Icon name={cat.icon} size={24} />
          </span>
          <span className={c("t-caption")}>{cat.label}</span>
        </Link>
      ))}
    </div>
  );
}
