import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useUi } from "@/components/overlay/UiContext";
import { getRental } from "@/api/rentals";
import { ApiError } from "@/api/client";
import { Icon } from "@/components/ui/icon";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, Loading, Skeleton } from "@/components/ui/state";
import {
  loadMoreNotifications,
  markAllRead,
  markOneRead,
  refreshNotifications,
  useNotificationState,
} from "@/stores/notifications";
import type { ApiNotification } from "@/types/notification";
import styles from "@/pages/notifications/NotificationsPage.module.css";
import { classes } from "@/utils/classes";
import { notificationAppearance, notificationDestination, notificationTargetPath, notificationTime } from "@/utils/notification-view";
import { PageHeader } from "@/components/layout/header";

const c = classes(styles);

export function NotificationsPage() {
  const navigate = useNavigate();
  const { toast } = useUi();
  const [openingId, setOpeningId] = useState<string | null>(null);
  const { items, unreadCount, hasMore, loading, loadingMore, readingAll, error, errorKind } = useNotificationState();

  useEffect(() => {
    void refreshNotifications();
  }, []);

  async function openNotification(notification: ApiNotification) {
    if (openingId) return;
    setOpeningId(notification.id);
    try {
      try {
        await markOneRead(notification.id);
      } catch {
        toast("알림을 읽음 처리하지 못했어요. 다시 시도해 주세요.");
      }
      const path = await notificationDestination(notification, getRental);
      if (!path) {
        toast("연결된 채팅방을 찾지 못했어요. 채팅 목록에서 확인해 주세요.");
        return;
      }
      navigate(path);
    } catch (error) {
      toast(error instanceof ApiError && (error.status === 403 || error.status === 404)
        ? "이 알림의 거래를 더는 볼 수 없어요. 내 대여에서 확인해 주세요."
        : "알림의 이동 대상을 확인하지 못했어요. 다시 시도해 주세요.");
    } finally {
      setOpeningId(null);
    }
  }

  async function readAll() {
    try {
      await markAllRead();
      toast("알림을 모두 읽음으로 표시했어요");
    } catch {
      toast("알림을 읽음 처리하지 못했어요. 다시 시도해 주세요.");
    }
  }

  return (
    <div className={c("notif page-window")}>
      <PageHeader
        title="알림"
        right={
          <button
            type="button"
            className={c("notif-read-all")}
            disabled={loading || readingAll || items.length === 0 || unreadCount === 0}
            onClick={() => void readAll()}
          >
            모두 읽음
          </button>
        }
      />

      {loading ? (
        <Loading>
          <div className={c("notif-list")}>
            {[0, 1, 2].map((key) => (
              <div key={key} className={c("notif-item")}>
                <Skeleton w={40} h={40} r="50%" />
                <span className={c("notif-body")}>
                  <Skeleton w="75%" h={20} />
                  <Skeleton w="45%" h={16} style={{ marginTop: 2 }} />
                </span>
              </div>
            ))}
          </div>
        </Loading>
      ) : error && items.length === 0 ? (
        <ErrorState onRetry={() => void refreshNotifications()} />
      ) : items.length === 0 ? (
        <EmptyState icon="bell" title="알림이 없어요">
          새 알림이 오면 여기에서 볼 수 있어요.
        </EmptyState>
      ) : (
        <div className={c("notif-list")}>
          {items.map((notification) => {
            const { tone, icon } = notificationAppearance(notification.type);
            const unread = !notification.readAt;
            const path = notificationTargetPath(notification);

            return (
              <Link
                key={notification.id}
                to={path}
                className={c(`notif-item${unread ? " notif-item--unread" : ""}`)}
                aria-disabled={openingId !== null}
                onClick={(event) => {
                  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
                  event.preventDefault();
                  void openNotification(notification);
                }}
              >
                <span className={c(`notif-icon notif-icon--${tone}`)}>
                  <Icon name={icon} size={20} />
                </span>
                <span className={c("notif-body")}>
                  <span className={c("notif-item-title")}>{notification.title}</span>
                  <span className={c("notif-item-sub")}>{notificationTime(notification.createdAt)}</span>
                </span>
                {unread ? <span className={c("notif-dot")} aria-label="안 읽음" /> : null}
              </Link>
            );
          })}

          {error ? (
            <ErrorState
              onRetry={() => void (errorKind === "more" ? loadMoreNotifications() : refreshNotifications())}
            />
          ) : null}
          {hasMore && !error ? (
            <div className={c("notif-more")}>
              <Button variant="secondary" disabled={loadingMore} onClick={() => void loadMoreNotifications()}>
                {loadingMore ? "불러오는 중" : "알림 더 보기"}
              </Button>
            </div>
          ) : null}
        </div>
      )}
    </div>
  );
}
