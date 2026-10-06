import { Link } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { useCategories } from "@/hooks/useCategories";
import { categoryIcon } from "@/utils/registerForm";
import styles from "@/components/custom/items/CategoryGrid.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 카테고리 그리드 (B_013) — 선택 시 해당 카테고리로 물건 찾기 이동 */
export function CategoryGrid() {
  const categories = useCategories();
  if (!categories || categories.length === 0) return null;
  return (
    <div className={c("category-grid")}>
      {categories.map((cat) => (
        <Link key={cat.id} to={`/search?categoryId=${cat.id}`} className={c("category-item")}>
          <span className={c("category-icon")}>
            <Icon name={categoryIcon(cat.code)} size={24} />
          </span>
          <span className={c("t-caption")}>{cat.name}</span>
        </Link>
      ))}
    </div>
  );
}
