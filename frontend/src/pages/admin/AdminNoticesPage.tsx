import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Alert } from "@/components/ui/alert";
import { useUi } from "@/components/overlay/UiContext";
import { addNotice, removeNotice, useNotices } from "@/stores/admin";
import adminStyles from "@/components/custom/admin/admin.module.css";
import { classes } from "@/utils/classes";

const c = classes(adminStyles);

export function AdminNoticesPage() {
  const { toast } = useUi();
  const notices = useNotices();
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("");

  const submit = () => {
    if (!title.trim()) return;
    addNotice(title.trim());
    setTitle("");
    setOpen(false);
    toast("공지를 등록했어요");
  };

  return (
    <div className={c("adm-body")}>
      <Alert tone="info" title="공지사항은 관리자만 작성해요">
        등록한 공지는 이웃들의 알림과 공지 목록에 노출돼요. 일반 이웃은 볼 수만 있어요.
      </Alert>

      {open ? (
        <div className={c("adm-card adm-compose")}>
          <input
            className={c("adm-input")}
            placeholder="공지 제목을 입력하세요"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <div className={c("adm-compose-actions")}>
            <Button
              variant="secondary"
              onClick={() => {
                setOpen(false);
                setTitle("");
              }}
            >
              취소
            </Button>
            <Button disabled={!title.trim()} onClick={submit}>등록</Button>
          </div>
        </div>
      ) : (
        <Button block icon="plus" onClick={() => setOpen(true)}>새 공지 작성</Button>
      )}

      <div className={c("adm-card adm-card--list")}>
        {notices.map((n) => (
          <div key={n.id} className={c("adm-row")}>
            <div className={c("adm-row-body")}>
              <div className={c("adm-row-head")}>
                {n.pinned ? <span className={c("adm-pill adm-pill--pending")}>고정</span> : null}
                <span className={c("t-body adm-row-name")}>{n.title}</span>
              </div>
              <div className={c("t-caption adm-muted adm-row-sub")}>{n.date}</div>
            </div>
            <button
              type="button"
              aria-label="삭제"
              className={c("adm-del")}
              onClick={() => {
                removeNotice(n.id);
                toast("공지를 삭제했어요");
              }}
            >
              <Icon name="x" size={15} />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
