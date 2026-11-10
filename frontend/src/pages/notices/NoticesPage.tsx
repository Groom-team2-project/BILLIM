import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { EmptyState, ErrorState, Loading, SkeletonItem } from "@/components/ui/state";
import { PageHeader } from "@/components/layout/header";
import { NoticeTag } from "@/components/custom/notices";
import { ApiError } from "@/api/client";
import { getCommunity } from "@/api/communities";
import { type ApiNotice, getCommunityNotices } from "@/api/notices";
import styles from "@/pages/notices/NoticesPage.module.css";
import { classes } from "@/utils/classes";
import { noticeDate } from "@/utils/date";

const c = classes(styles);

type List =
  | { state: "loading" }
  | { state: "error"; message: string }
  | { state: "ok"; rows: ApiNotice[] };

/** 응답이 어느 동네 것인지. 경로의 동네와 다르면 로딩 — 이전 동네 목록·이름 잔존 방지 */
type Loaded<T> = { communityId: string; value: T };

/**
 * 동네 공지 목록 (E_063). 관리자가 등록한 공지를 이웃이 보는 곳.
 * 고정 우선·최신순 서버 정렬 그대로, 첫 공지만 펼친 채 시작.
 */
export function NoticesPage() {
  const { communityId = "" } = useParams();
  const navigate = useNavigate();
  const [loaded, setLoaded] = useState<Loaded<List> | null>(null);
  const [named, setNamed] = useState<Loaded<string | null> | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let alive = true;
    getCommunityNotices(communityId, 0, 50)
      .then((page) => {
        if (!alive) return;
        setLoaded({ communityId, value: { state: "ok", rows: page.items } });
        setOpen(page.items[0]?.id ?? null);
      })
      .catch((e: unknown) => {
        if (!alive) return;
        if (e instanceof ApiError && e.unauthenticated) {
          navigate("/login");
          return;
        }
        setLoaded({
          communityId,
          value: { state: "error", message: e instanceof ApiError ? e.message : "공지를 불러오지 못했어요." },
        });
      });
    return () => {
      alive = false;
    };
  }, [communityId, retry, navigate]);

  // 동네 이름 배지. 실패하면 배지만 생략
  useEffect(() => {
    let alive = true;
    getCommunity(communityId)
      .then((community) => alive && setNamed({ communityId, value: community.name }))
      .catch(() => alive && setNamed({ communityId, value: null }));
    return () => {
      alive = false;
    };
  }, [communityId]);

  const list: List = loaded?.communityId === communityId ? loaded.value : { state: "loading" };
  const communityName = named?.communityId === communityId ? named.value : null;

  return (
    <div className={c("notices page-window")}>
      <PageHeader title="동네 공지" />
      {communityName ? <span className={c("notices-community")}>{communityName}</span> : null}
      <p className={c("t-caption notices-muted")}>운영팀이 우리 동네 이웃에게 알리는 소식이에요.</p>

      {list.state === "loading" && (
        <Loading label="공지 불러오는 중">
          <div className={c("notices-card")}>
            <SkeletonItem layout="row" />
            <SkeletonItem layout="row" />
          </div>
        </Loading>
      )}

      {list.state === "error" && (
        <ErrorState
          title="공지를 불러오지 못했어요"
          onRetry={() => {
            setLoaded(null);
            setRetry((n) => n + 1);
          }}
        >
          {list.message}
        </ErrorState>
      )}

      {list.state === "ok" &&
        (list.rows.length === 0 ? (
          <div className={c("notices-card notices-card--empty")}>
            <EmptyState icon="info" title="아직 공지가 없어요">
              운영팀이 공지를 올리면 여기와 홈에 보여요.
            </EmptyState>
          </div>
        ) : (
          <div className={c("notices-card")}>
            {list.rows.map((notice) => {
              const on = open === notice.id;
              return (
                <div key={notice.id} className={c(`notices-item${on ? " notices-item--on" : ""}`)}>
                  <button
                    type="button"
                    className={c("notices-head")}
                    aria-expanded={on}
                    onClick={() => setOpen(on ? null : notice.id)}
                  >
                    {notice.pinned ? <NoticeTag>고정</NoticeTag> : null}
                    <span className={c("notices-title")}>{notice.title}</span>
                    <span className={c("t-caption notices-muted notices-date")}>{noticeDate(notice.createdAt)}</span>
                    <Icon name="chevronD" size={16} />
                  </button>
                  {/* 관리자 입력 본문 — 일반 텍스트로만 렌더링, 줄바꿈 유지 */}
                  {on ? <p className={c("t-body notices-body")}>{notice.body}</p> : null}
                </div>
              );
            })}
          </div>
        ))}
    </div>
  );
}
