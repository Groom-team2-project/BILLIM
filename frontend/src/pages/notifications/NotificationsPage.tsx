import { Link } from "react-router-dom";
import { useUi } from "@/components/overlay/UiContext";
import { Icon } from "@/components/ui/icon";
import { markAllRead, useNotifications } from "@/stores/notifications";
import styles from "@/pages/notifications/NotificationsPage.module.css";
import { classes } from "@/utils/classes";
import { PageHeader } from "@/components/layout/header";

const c = classes(styles);

export function NotificationsPage() {
  const { toast } = useUi();
  const items = useNotifications();

  return (
    <div className={c("notif page-window")}>
      <PageHeader
        title="알림"
        right={
          <button
            type="button"
            className={c("notif-read-all")}
            onClick={() => {
              markAllRead();
              toast("알림을 모두 읽음으로 표시했어요");
            }}
          >
            모두 읽음
          </button>
        }
      />

      <div className={c("notif-list")}>
        {items.map((n, i) => (
          <Link key={i} to={n.to} className={c(`notif-item${n.unread ? " notif-item--unread" : ""}`)}>
            <span className={c(`notif-icon notif-icon--${n.tone}`)}>
              <Icon name={n.icon} size={20} />
            </span>
            <span className={c("notif-body")}>
              <span className={c("notif-item-title")}>{n.title}</span>
              <span className={c("notif-item-sub")}>{n.subtitle}</span>
            </span>
            {n.unread ? <span className={c("notif-dot")} aria-label="안 읽음" /> : null}
          </Link>
        ))}
      </div>
    </div>
  );
}
