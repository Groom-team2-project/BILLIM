import { useEffect, useState } from "react";
import { useUi } from "@/components/overlay/UiContext";
import { Button } from "@/components/ui/button";
import { Avatar } from "@/components/ui/avatar";
import { ErrorState, Loading, SkeletonItem } from "@/components/ui/state";
import { ApiError } from "@/api/client";
import { type ApiBlock, getMyBlocks, unblockMember } from "@/api/blocks";
import styles from "@/pages/profile/BlockedPage.module.css";
import { classes } from "@/utils/classes";
import { PageHeader } from "@/components/layout/header";

const c = classes(styles);

type List =
  | { state: "loading" }
  | { state: "error"; message: string }
  | { state: "ok"; rows: ApiBlock[] };

/** 프로토타입 문구: "2026년 9월 18일 차단" */
function blockedLabel(iso: string): string {
  const at = new Date(iso);
  return `${at.getFullYear()}년 ${at.getMonth() + 1}월 ${at.getDate()}일 차단`;
}

export function BlockedPage() {
  const { confirm, toast } = useUi();
  const [list, setList] = useState<List>({ state: "loading" });
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let alive = true;
    getMyBlocks()
      .then((page) => alive && setList({ state: "ok", rows: page.items }))
      .catch((e: unknown) => {
        if (!alive) return;
        setList({
          state: "error",
          message: e instanceof ApiError ? e.message : "차단 목록을 불러오지 못했어요.",
        });
      });
    return () => {
      alive = false;
    };
  }, [retry]);

  const onUnblock = (block: ApiBlock) => {
    const name = block.member.displayName;
    confirm({
      title: `${name} 님 차단을 해제할까요?`,
      body: "해제하면 다시 서로의 물건을 보고 대화할 수 있어요.",
      confirm: "차단 해제",
      onConfirm: async () => {
        try {
          await unblockMember(block.member.id);
          setList((prev) =>
            prev.state === "ok"
              ? { state: "ok", rows: prev.rows.filter((r) => r.member.id !== block.member.id) }
              : prev,
          );
          toast("차단을 해제했어요");
        } catch (e: unknown) {
          toast(e instanceof ApiError ? e.message : "차단을 해제하지 못했어요");
        }
      },
    });
  };

  return (
    <div className={c("blocked page-window")}>
      <PageHeader title="차단한 이웃" backTo="/profile" />
      <p className={c("t-body blocked-muted")}>
        차단한 이웃과는 서로 대화할 수 없고, 물건도 보이지 않아요. 차단을 해제하면 다시 볼 수 있어요.
      </p>

      {list.state === "loading" && (
        <Loading label="차단 목록을 불러오는 중">
          <div className={c("blocked-card")}>
            <SkeletonItem layout="row" />
            <SkeletonItem layout="row" />
          </div>
        </Loading>
      )}

      {list.state === "error" && (
        <ErrorState
          onRetry={() => {
            setList({ state: "loading" });
            setRetry((n) => n + 1);
          }}
        >
          {list.message}
        </ErrorState>
      )}

      {list.state === "ok" && (
        <div className={c("blocked-card")}>
          {list.rows.length === 0 ? (
            <p className={c("t-caption blocked-muted blocked-empty")}>차단한 이웃이 없어요.</p>
          ) : (
            list.rows.map((block) => (
              <div key={block.member.id} className={c("blocked-row")}>
                <Avatar name={block.member.displayName} size={44} />
                <span className={c("blocked-row-body")}>
                  <span className={c("t-body blocked-row-name")}>{block.member.displayName} 님</span>
                  <span className={c("t-caption blocked-muted")}>{blockedLabel(block.blockedAt)}</span>
                </span>
                <Button variant="secondary" size="sm" onClick={() => onUnblock(block)}>
                  차단 해제
                </Button>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
