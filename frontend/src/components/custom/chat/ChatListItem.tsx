import { Link } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { StatusBadge } from "@/components/custom/rentals/StatusBadge";
import { Avatar } from "@/components/ui/avatar";
import { Photo } from "@/components/custom/items/ItemCard";
import type { Chat } from "@/data/chat";
import styles from "@/components/custom/chat/ChatListItem.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

export function ChatListItem({ chat, selected }: { chat: Chat; selected?: boolean }) {
  return (
    <Link
      to={`/chat/${chat.id}`}
      className={c("chat-row")}
      aria-current={selected ? "true" : undefined}
      aria-label={`${chat.name} 님과의 채팅, ${chat.item}${chat.unread ? `, 안 읽은 메시지 ${chat.unread}개` : ""}`}
    >
      <Avatar name={chat.name} size={44} />
      <span className={c("chat-row-body")}>
        <span className={c("chat-row-top")}>
          <strong>{chat.name}</strong>
          <span className={c("t-micro chat-row-muted")}>{chat.time}</span>
        </span>
        <span className={c("chat-row-last")}>{chat.last}</span>
        <span className={c("chat-row-meta t-caption")}>
          <StatusBadge status={chat.status} />
          {chat.appt ? (
            <span className={c("chat-row-appt")}>
              <Icon name="clock" size={13} />
              {chat.appt}
            </span>
          ) : (
            <span className={c("chat-row-muted")}>{chat.item}</span>
          )}
        </span>
      </span>
      <span className={c("chat-row-side")}>
        <Photo cat={chat.cat} iconSize={18} className={c("chat-row-thumb")} />
        {chat.unread ? <span className={c("chat-row-unread")}>{chat.unread}</span> : null}
      </span>
    </Link>
  );
}
