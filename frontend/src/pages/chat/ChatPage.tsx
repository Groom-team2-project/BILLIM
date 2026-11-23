import { useSearchParams } from "react-router-dom";
import { ChatPreviewPage } from "@/pages/chat/ChatPreviewPage";
import { LiveChatPage } from "@/pages/chat/LiveChatPage";

export function ChatPage() {
  const [params] = useSearchParams();
  return params.has("state") ? <ChatPreviewPage /> : <LiveChatPage />;
}
