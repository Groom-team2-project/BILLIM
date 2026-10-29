import { Link } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { StatusBadge } from "@/components/custom/rentals/StatusBadge";
import { Avatar } from "@/components/ui/avatar";
import { Photo } from "@/components/custom/items/ItemCard";
import type { Chat } from "@/data/chat";
import type { ApiChatRoom } from "@/types/chat-api";
import { chatRentalStatus, chatTimeLabel } from "@/utils/chat-view";
import styles from "@/components/custom/chat/ChatListItem.module.css";
import { classes } from "@/utils/classes";
import { ChatItemThumbnail } from "@/components/custom/chat/ChatItemThumbnail";

const c = classes(styles);

export function ChatListItem({ chat, room, selected, appointment }: {
  chat?: Chat;
  room?: ApiChatRoom;
  selected?: boolean;
  appointment?: string;
}) {
  if (room) {
    const rental = room.rentals[0];
    const status = rental ? chatRentalStatus(rental) : null;
    return (
      <Link to={`/chat/${room.id}`} state={{ chatName: room.otherMember.displayName }}
        className={c("chat-row")} aria-current={selected ? "true" : undefined}
        aria-label={`${room.otherMember.displayName} 님과의 채팅, ${room.itemTitle}${appointment ? `, 확정 약속 ${appointment}` : ""}${room.unreadCount ? `, 안 읽은 메시지 ${room.unreadCount}개` : ""}`}>
        <Avatar name={room.otherMember.displayName} size={44} />
        <span className={c("chat-row-body")}>
          <span className={c("chat-row-top")}><strong>{room.otherMember.displayName}</strong>
            <span className={c("t-micro chat-row-muted")}>{chatTimeLabel(room.lastMessageAt)}</span></span>
          <span className={c("chat-row-last")}>{room.lastMessagePreview ?? "대화를 시작해 보세요"}</span>
          <span className={c("chat-row-meta t-caption")}>
            {status ? <StatusBadge status={status} /> : rental ? <span>기간 만료</span> : null}
            {appointment ? <span className={c("chat-row-appt")}>
              <Icon name="clock" size={13} />{appointment}
            </span> : <span className={c("chat-row-muted")}>{room.itemTitle}</span>}
          </span>
        </span>
        <span className={c("chat-row-side")}>
          <ChatItemThumbnail key={room.itemThumbnailUrl ?? ""}
            src={room.itemThumbnailUrl} title={room.itemTitle}
            className={c("chat-row-thumb")} placeholderClassName={c("chat-row-placeholder")} />
          {room.unreadCount ? <span className={c("chat-row-unread")}>{room.unreadCount}</span> : null}
        </span>
      </Link>
    );
  }
  if (!chat) return null;
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
