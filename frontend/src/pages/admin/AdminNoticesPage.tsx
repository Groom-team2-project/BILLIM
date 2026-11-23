import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Alert } from "@/components/ui/alert";
import { useUi } from "@/components/overlay/UiContext";
import { ErrorState, Loading, SkeletonItem } from "@/components/ui/state";
import { RegionPicker } from "@/components/custom/community/RegionPicker";
import { ApiError } from "@/api/client";
import type { ApiCommunity } from "@/api/communities";
import { type ApiNotice, createNotice, deleteNotice, getAdminNotices } from "@/api/notices";
import adminStyles from "@/components/custom/admin/admin.module.css";
import { classes } from "@/utils/classes";

const c = classes(adminStyles);

type List =
  | { state: "loading" }
  | { state: "error"; message: string }
  | { state: "ok"; rows: ApiNotice[] };

function dateLabel(iso: string): string {
  const at = new Date(iso);
  return `${at.getMonth() + 1}/${at.getDate()}`;
}

export function AdminNoticesPage() {
  const { confirm, toast } = useUi();
  const [list, setList] = useState<List>({ state: "loading" });
  const [retry, setRetry] = useState(0);

  const [open, setOpen] = useState(false);
  const [target, setTarget] = useState<ApiCommunity | null>(null);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let alive = true;
    getAdminNotices()
      .then((page) => alive && setList({ state: "ok", rows: page.items }))
      .catch((e: unknown) => {
        if (!alive) return;
        setList({
          state: "error",
          message: e instanceof ApiError ? e.message : "공지를 불러오지 못했어요.",
        });
      });
    return () => {
      alive = false;
    };
  }, [retry]);

  const closeCompose = () => {
    setOpen(false);
    setTitle("");
    setBody("");
  };

  const submit = async () => {
    if (!title.trim() || !body.trim() || !target) return;
    setSaving(true);
    try {
      await createNotice({
        communityId: target.id,
        title: title.trim(),
        body: body.trim(),
        pinned: false,
      });
      closeCompose();
      toast("공지를 등록했어요");
      setList({ state: "loading" });
      setRetry((n) => n + 1);
    } catch (e: unknown) {
      toast(e instanceof ApiError ? e.message : "공지를 등록하지 못했어요");
    } finally {
      setSaving(false);
    }
  };

  const onDelete = (notice: ApiNotice) => {
    confirm({
      title: "공지를 삭제할까요?",
      body: `"${notice.title}" 공지가 목록에서 사라져요.`,
      confirm: "삭제",
      danger: true,
      onConfirm: async () => {
        try {
          await deleteNotice(notice.id, notice.version);
          setList((prev) =>
            prev.state === "ok"
              ? { state: "ok", rows: prev.rows.filter((r) => r.id !== notice.id) }
              : prev,
          );
          toast("공지를 삭제했어요");
        } catch (e: unknown) {
          if (e instanceof ApiError && e.code === "VERSION_CONFLICT") {
            toast("다른 곳에서 먼저 바뀌었어요. 목록을 새로 불러왔어요");
            setList({ state: "loading" });
            setRetry((n) => n + 1);
            return;
          }
          toast(e instanceof ApiError ? e.message : "공지를 삭제하지 못했어요");
        }
      },
    });
  };

  const canSubmit = Boolean(title.trim() && body.trim() && target) && !saving;

  return (
    <div className={c("adm-body")}>
      <Alert tone="info" title="공지사항은 관리자만 작성해요">
        등록한 공지는 이웃들의 알림과 공지 목록에 노출돼요. 일반 이웃은 볼 수만 있어요.
      </Alert>

      {open ? (
        <div className={c("adm-card adm-compose")}>
          <RegionPicker
            selected={target}
            onPick={setTarget}
            title="공지 대상 동네"
            hint="선택한 동네의 이웃에게만 보여요."
          />
          <input
            className={c("adm-input")}
            placeholder="공지 제목을 입력하세요"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <textarea
            className={c("adm-input")}
            rows={4}
            placeholder="공지 본문을 입력하세요"
            value={body}
            onChange={(e) => setBody(e.target.value)}
          />
          <div className={c("adm-compose-actions")}>
            <Button variant="secondary" onClick={closeCompose}>
              취소
            </Button>
            <Button disabled={!canSubmit} onClick={submit}>
              {saving ? "등록 중…" : "등록"}
            </Button>
          </div>
        </div>
      ) : (
        <Button block icon="plus" onClick={() => setOpen(true)}>새 공지 작성</Button>
      )}

      {list.state === "loading" && (
        <Loading label="공지를 불러오는 중">
          <div className={c("adm-card adm-card--list")}>
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
        <div className={c("adm-card adm-card--list")}>
          {list.rows.length === 0 ? (
            <p className={c("t-caption adm-muted adm-empty")}>등록한 공지가 없어요.</p>
          ) : (
            list.rows.map((notice) => (
              <div key={notice.id} className={c("adm-row")}>
                <div className={c("adm-row-body")}>
                  <div className={c("adm-row-head")}>
                    {notice.pinned ? <span className={c("adm-pill adm-pill--pending")}>고정</span> : null}
                    <span className={c("t-body adm-row-name")}>{notice.title}</span>
                  </div>
                  <div className={c("t-caption adm-muted adm-row-sub")}>{dateLabel(notice.createdAt)}</div>
                </div>
                <button
                  type="button"
                  aria-label="삭제"
                  className={c("adm-del")}
                  onClick={() => onDelete(notice)}
                >
                  <Icon name="x" size={15} />
                </button>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
