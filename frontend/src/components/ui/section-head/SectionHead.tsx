import { Link } from "react-router-dom";
import styles from "@/components/ui/section-head/SectionHead.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 섹션 제목 + 오른쪽 링크 */
export function SectionHead({ title, more, moreTo, id }: { title: string; more?: string; moreTo?: string; id?: string }) {
  return (
    <div className={c("section-head")}>
      <h2 id={id} className={c("t-title")}>{title}</h2>
      {more && moreTo ? (
        <Link to={moreTo} className={c("section-head-more")}>{more}</Link>
      ) : more ? (
        <button type="button" className={c("section-head-more")}>{more}</button>
      ) : null}
    </div>
  );
}
