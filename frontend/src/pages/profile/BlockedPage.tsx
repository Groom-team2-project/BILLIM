import { useState } from "react";
import { useUi } from "@/components/overlay/UiContext";
import { Button } from "@/components/ui/button";
import { Avatar } from "@/components/ui/avatar";
import styles from "@/pages/profile/BlockedPage.module.css";
import { classes } from "@/utils/classes";
import { PageHeader } from "@/components/layout/header";

const c = classes(styles);

/** 프로토타입 목데이터 — 차단 목록. API 연동 시 교체 */
const BLOCKED = [
  { name: "민수", date: "2026년 9월 18일 차단" },
  { name: "하은", date: "2026년 8월 2일 차단" },
];

export function BlockedPage() {
  const { confirm, toast } = useUi();
  const [rows, setRows] = useState(BLOCKED);

  return (
    <div className={c("blocked page-window")}>
      <PageHeader title="차단한 이웃" backTo="/profile" />
      <p className={c("t-body blocked-muted")}>
        차단한 이웃과는 서로 대화할 수 없고, 물건도 보이지 않아요. 차단을 해제하면 다시 볼 수 있어요.
      </p>
      <div className={c("blocked-card")}>
        {rows.length === 0 ? (
          <p className={c("t-caption blocked-muted blocked-empty")}>차단한 이웃이 없어요.</p>
        ) : (
          rows.map((r) => (
            <div key={r.name} className={c("blocked-row")}>
              <Avatar name={r.name} size={44} />
              <span className={c("blocked-row-body")}>
                <span className={c("t-body blocked-row-name")}>{r.name} 님</span>
                <span className={c("t-caption blocked-muted")}>{r.date}</span>
              </span>
              <Button
                variant="secondary"
                size="sm"
                onClick={() =>
                  confirm({
                    title: `${r.name} 님 차단을 해제할까요?`,
                    body: "해제하면 다시 서로의 물건을 보고 대화할 수 있어요.",
                    confirm: "차단 해제",
                    onConfirm: () => {
                      setRows((prev) => prev.filter((x) => x.name !== r.name));
                      toast("차단을 해제했어요");
                    },
                  })
                }
              >
                차단 해제
              </Button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
