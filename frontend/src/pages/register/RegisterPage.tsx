import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { ApiError, newIdempotencyKey } from "@/api/client";
import {
  type ApiCategory,
  type ApiItemDetail,
  type ApiPlace,
  createItem,
  deleteMedia,
  getCategories,
  getItem,
  getMyCommunityPlaces,
  updateItem,
  uploadMedia,
} from "@/api/items";
import { EmptyState, Loading, Skeleton } from "@/components/ui/state";
import { parseIsoDate } from "@/utils/itemView";
import { useUi } from "@/components/overlay/UiContext";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Calendar, type DayRange } from "@/components/ui/calendar";
import { Chip } from "@/components/ui/chip";
import { Icon } from "@/components/ui/icon";
import { TextField } from "@/components/ui/text-field";
import styles from "@/pages/register/RegisterPage.module.css";
import { classes } from "@/utils/classes";
import {
  MAX_PHOTOS,
  categoryIcon,
  checkPhotoFile,
  movePhoto,
  toCreateBody,
  validateDraft,
} from "@/utils/registerForm";
import { PageHeader } from "@/components/layout/header";

const c = classes(styles);

type Photo = {
  key: string;
  previewUrl: string;
  status: "uploading" | "done" | "error";
  mediaId?: string;
  error?: string;
  /** 수정 모드에서 불러온 기존 사진 (ATTACHED) — 지워도 서버 삭제 API를 부르지 않는다 */
  existing?: boolean;
};

type Load<T> = { state: "loading" } | { state: "error"; message: string } | { state: "ok"; data: T };

function messageOf(e: unknown): string {
  return e instanceof ApiError ? e.message : "요청을 처리하지 못했어요.";
}

export function RegisterPage() {
  const { toast } = useUi();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  /** ?itemId= 가 있으면 수정 모드 (B_021) */
  const editId = params.get("itemId");
  const [editing, setEditing] = useState<Load<ApiItemDetail> | null>(editId ? { state: "loading" } : null);

  const [categories, setCategories] = useState<Load<ApiCategory[]>>({ state: "loading" });
  const [places, setPlaces] = useState<Load<ApiPlace[]>>({ state: "loading" });
  const [reload, setReload] = useState(0);

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [placeId, setPlaceId] = useState<string | null>(null);
  const [range, setRange] = useState<DayRange | null>(null);
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [dragFrom, setDragFrom] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [today] = useState(() => new Date());

  const fileInput = useRef<HTMLInputElement>(null);
  const photosRef = useRef<Photo[]>([]);
  useEffect(() => {
    photosRef.current = photos;
  }, [photos]);
  /** 같은 내용 재시도는 같은 Idempotency-Key로 보낸다 */
  const attempt = useRef<{ body: string; key: string } | null>(null);

  useEffect(() => {
    let alive = true;
    getCategories()
      .then((data) => alive && setCategories({ state: "ok", data }))
      .catch((e) => alive && setCategories({ state: "error", message: messageOf(e) }));
    getMyCommunityPlaces()
      .then((data) => alive && setPlaces({ state: "ok", data }))
      .catch((e) => alive && setPlaces({ state: "error", message: messageOf(e) }));
    return () => {
      alive = false;
    };
  }, [reload]);

  // 수정 모드: 기존 값을 불러와 폼을 채운다. 사진은 서버 순서(sortOrder) 그대로, 첫 번째가 대표
  useEffect(() => {
    if (!editId) return;
    let alive = true;
    getItem(editId)
      .then((item) => {
        if (!alive) return;
        setTitle(item.title);
        setDescription(item.description ?? "");
        setCategoryId(item.category.id);
        setPlaceId(item.place.id);
        setRange([parseIsoDate(item.availableStartDate), parseIsoDate(item.availableEndDate)]);
        setPhotos(
          [...item.images]
            .sort((a, b) => a.sortOrder - b.sortOrder)
            .map((img) => ({ key: img.mediaId, previewUrl: img.contentUrl, status: "done", mediaId: img.mediaId, existing: true })),
        );
        setEditing({ state: "ok", data: item });
      })
      .catch((e: unknown) => {
        if (!alive) return;
        if (e instanceof ApiError && e.unauthenticated) {
          navigate("/login");
          return;
        }
        setEditing({ state: "error", message: messageOf(e) });
      });
    return () => {
      alive = false;
    };
  }, [editId, navigate]);

  // 화면을 떠날 때 미리보기 주소 해제 (기존 사진은 서버 주소라 해제 대상 아님)
  useEffect(
    () => () => {
      for (const p of photosRef.current) if (!p.existing) URL.revokeObjectURL(p.previewUrl);
    },
    [],
  );

  const patchPhoto = (key: string, patch: Partial<Photo>) =>
    setPhotos((list) => list.map((p) => (p.key === key ? { ...p, ...patch } : p)));

  const addFiles = async (files: File[]) => {
    setError(null);
    const room = MAX_PHOTOS - photosRef.current.length;
    if (files.length > room) setError(`사진은 최대 ${MAX_PHOTOS}장까지 등록할 수 있어요.`);
    const accepted: Photo[] = [];
    const uploads: { key: string; file: File }[] = [];
    for (const file of files.slice(0, Math.max(room, 0))) {
      const problem = checkPhotoFile(file);
      if (problem) {
        setError(problem);
        continue;
      }
      const key = crypto.randomUUID();
      accepted.push({ key, previewUrl: URL.createObjectURL(file), status: "uploading" });
      uploads.push({ key, file });
    }
    if (accepted.length === 0) return;
    setPhotos((list) => [...list, ...accepted]);
    for (const { key, file } of uploads) {
      try {
        const media = await uploadMedia(file);
        patchPhoto(key, { status: "done", mediaId: media.id });
      } catch (e) {
        patchPhoto(key, { status: "error", error: messageOf(e) });
        setError(messageOf(e));
      }
    }
  };

  const removePhoto = (index: number) => {
    const target = photos[index];
    setPhotos((list) => list.filter((_, i) => i !== index));
    // 기존 사진은 수정 저장 때 목록에서 빠지는 것으로 처리한다 (단독 삭제는 409 MEDIA_IN_USE)
    if (target.existing) return;
    URL.revokeObjectURL(target.previewUrl);
    // 이번에 올린 TEMP 사진은 바로 지운다 (실패해도 24시간 뒤 만료)
    if (target.mediaId) void deleteMedia(target.mediaId).catch(() => undefined);
  };

  const reorder = (from: number, to: number) => setPhotos((list) => movePhoto(list, from, to));

  const submit = async () => {
    const draft = {
      title,
      description,
      categoryId,
      placeId,
      range,
      photos: photos.map((p) => ({ mediaId: p.mediaId, status: p.status })),
    };
    const problem = validateDraft(draft);
    if (problem) {
      setError(problem);
      return;
    }
    const body = toCreateBody(draft);

    setError(null);
    setSubmitting(true);
    try {
      if (editId && editing?.state === "ok") {
        // 수정(B_021): 마지막으로 불러온 version으로 낙관적 잠금. imageIds 순서가 새 표시 순서
        await updateItem(editId, { ...body, expectedVersion: editing.data.version });
        toast("물건을 수정했어요");
        navigate(`/items/${editId}`);
        return;
      }
      const serialized = JSON.stringify(body);
      if (attempt.current?.body !== serialized) attempt.current = { body: serialized, key: newIdempotencyKey() };
      await createItem(body, attempt.current.key);
      toast("물건을 등록했어요");
      navigate("/");
    } catch (e) {
      if (e instanceof ApiError && e.unauthenticated) {
        // 인증은 기존 로그인 흐름에 맡긴다
        navigate("/login");
        return;
      }
      setError(messageOf(e));
    } finally {
      setSubmitting(false);
    }
  };

  const busy = submitting || photos.some((p) => p.status === "uploading");
  const pageTitle = editId ? "물건 수정" : "물건 등록";
  const backTo = editId ? `/items/${editId}` : "/";

  if (editing?.state === "loading") {
    return (
      <div className={c("register page-window")}>
        <PageHeader title={pageTitle} backTo={backTo} />
        <Loading label="물건 정보 불러오는 중">
          <Skeleton h={84} r={12} />
          <Skeleton h={48} />
          <Skeleton h={120} r={12} />
        </Loading>
      </div>
    );
  }
  if (editing?.state === "error") {
    return (
      <div className={c("register page-window")}>
        <PageHeader title={pageTitle} backTo={backTo} />
        <EmptyState icon="grid" title="이 물건을 수정할 수 없어요">
          {editing.message}
        </EmptyState>
      </div>
    );
  }

  return (
    <div className={c("register page-window")}>
      <PageHeader title={pageTitle} backTo={backTo} />

      <div className={c("register-field")}>
        <span className={c("t-label")}>
          사진 <span className={c("register-muted t-caption")}>최대 5장</span>
        </span>
        <div className={c("register-photos")}>
          <button
            type="button"
            className={c("register-photo-tile t-caption")}
            disabled={photos.length >= MAX_PHOTOS}
            onClick={() => fileInput.current?.click()}
          >
            <Icon name="camera" size={24} />
            {photos.length} / {MAX_PHOTOS}
          </button>
          <input
            ref={fileInput}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            hidden
            onChange={(e) => {
              const files = Array.from(e.target.files ?? []);
              e.target.value = "";
              void addFiles(files);
            }}
          />
          {photos.map((p, i) => (
            <div
              key={p.key}
              className={c(`register-photo-item${dragFrom === i ? " register-photo-item--drag" : ""}`)}
              draggable
              tabIndex={0}
              aria-label={`사진 ${i + 1}${i === 0 ? " (대표)" : ""}. 좌우 방향키로 순서를 바꿀 수 있어요`}
              onDragStart={() => setDragFrom(i)}
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                e.preventDefault();
                if (dragFrom !== null) reorder(dragFrom, i);
                setDragFrom(null);
              }}
              onDragEnd={() => setDragFrom(null)}
              onKeyDown={(e) => {
                if (e.key === "ArrowLeft" && i > 0) reorder(i, i - 1);
                if (e.key === "ArrowRight" && i < photos.length - 1) reorder(i, i + 1);
              }}
            >
              <img src={p.previewUrl} alt="" className={c("register-photo-img")} draggable={false} />
              {i === 0 ? <span className={c("register-photo-badge t-micro")}>대표</span> : null}
              {p.status === "uploading" ? <span className={c("register-photo-state t-micro")}>올리는 중</span> : null}
              {p.status === "error" ? <span className={c("register-photo-state t-micro")}>실패</span> : null}
              <button
                type="button"
                className={c("register-photo-remove")}
                aria-label={`사진 ${i + 1} 삭제`}
                onClick={() => removePhoto(i)}
              >
                <Icon name="x" size={14} />
              </button>
            </div>
          ))}
        </div>
        {photos.length > 1 ? (
          <span className={c("t-caption register-muted")}>끌어서 순서를 바꿀 수 있어요. 첫 번째 사진이 대표 사진이에요.</span>
        ) : null}
      </div>

      <div onInput={(e) => setTitle((e.target as HTMLInputElement).value)}>
        <TextField label="제목" hint="물건 이름과 규격을 적어 주세요" defaultValue={title} />
      </div>

      <div className={c("register-field")}>
        <span className={c("t-label")}>카테고리</span>
        {categories.state === "ok" ? (
          <div className={c("register-chips")}>
            {categories.data.map((cat) => (
              <Chip
                key={cat.id}
                icon={categoryIcon(cat.code)}
                selected={cat.id === categoryId}
                onClick={() => setCategoryId(cat.id)}
              >
                {cat.name}
              </Chip>
            ))}
          </div>
        ) : categories.state === "error" ? (
          <span className={c("t-caption register-muted")}>{categories.message}</span>
        ) : (
          <span className={c("t-caption register-muted")}>불러오는 중이에요</span>
        )}
      </div>

      <div onInput={(e) => setDescription((e.target as HTMLTextAreaElement).value)}>
        <TextField
          label="설명"
          optional
          multiline
          placeholder="함께 빌려주는 구성품, 사용할 때 주의할 점"
          defaultValue={description}
        />
      </div>

      <div className={c("register-field")}>
        <span className={c("t-label")}>거래 장소</span>
        <span className={c("t-caption register-muted")}>
          집 주소 대신 커뮤니티 공용 장소에서 만나요. 주소는 서로 공유되지 않아요.
        </span>
        {places.state === "ok" ? (
          places.data.length === 0 ? (
            <span className={c("t-caption register-muted")}>동네 인증 후 거래 장소를 고를 수 있어요.</span>
          ) : (
            <div className={c("register-places")} role="radiogroup" aria-label="거래 장소">
              {places.data.map((place) => (
                <label key={place.id} className={c("register-place t-body")}>
                  <input
                    type="radio"
                    name="place"
                    checked={placeId === place.id}
                    onChange={() => setPlaceId(place.id)}
                  />
                  {place.name}
                </label>
              ))}
            </div>
          )
        ) : places.state === "error" ? (
          <span className={c("t-caption register-muted")}>{places.message}</span>
        ) : (
          <span className={c("t-caption register-muted")}>불러오는 중이에요</span>
        )}
        {categories.state === "error" || places.state === "error" ? (
          <Button
            variant="secondary"
            size="sm"
            icon="refresh"
            onClick={() => {
              setCategories({ state: "loading" });
              setPlaces({ state: "loading" });
              setReload((n) => n + 1);
            }}
          >
            다시 불러오기
          </Button>
        ) : null}
      </div>

      <div className={c("register-field")}>
        <span className={c("t-label")}>대여 가능 기간</span>
        <div className={c("register-cal")}>
          <Calendar today={today} value={range} onChange={setRange} hideLegend />
        </div>
        <span className={c("t-caption register-muted")}>
          빌려줄 수 있는 기간을 정해요. 이웃은 이 안에서만 날짜를 고를 수 있어요.
        </span>
      </div>

      {error ? (
        <Alert tone="danger" title="등록하지 못했어요">
          {error}
        </Alert>
      ) : null}

      <Button size="lg" block disabled={busy} onClick={() => void submit()}>
        {editId ? (submitting ? "수정하는 중…" : "수정하기") : submitting ? "등록하는 중…" : "등록하기"}
      </Button>
    </div>
  );
}
