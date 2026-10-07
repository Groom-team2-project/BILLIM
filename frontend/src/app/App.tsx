import { createBrowserRouter, RouterProvider } from "react-router-dom";
import { AppLayout } from "@/components/layout/base";
import { UiProvider } from "@/components/overlay/UiProvider";
import { LoginPage } from "@/pages/onboarding";
import { AuthCompletePage } from "@/pages/onboarding";
import { JoinPage } from "@/pages/onboarding";
import { VerifyPage } from "@/pages/onboarding";
import { JoinDonePage } from "@/pages/onboarding";
import { HomePage } from "@/pages/home";
import { SearchPage } from "@/pages/search";
import { RegisterPage } from "@/pages/register";
import { ChatPage } from "@/pages/chat";
import { RentalsPage } from "@/pages/rentals";
import { NotificationsPage } from "@/pages/notifications";
import { ProfilePage } from "@/pages/profile";
import { ItemDetailPage, RequestPage } from "@/pages/items";
import { NeighborPage } from "@/pages/neighbors";
import { BlockedPage } from "@/pages/profile";
import { NeighborhoodsPage } from "@/pages/neighbors";
import { PolicyPage } from "@/pages/policy";
import { ReportsPage } from "@/pages/profile";
import { AdminLayout } from "@/pages/admin";
import { AdminDashboardPage } from "@/pages/admin";
import { AdminReportsPage } from "@/pages/admin";
import { AdminNoticesPage } from "@/pages/admin";

const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/auth/complete", element: <AuthCompletePage /> },
  { path: "/join", element: <JoinPage /> },
  { path: "/verify", element: <VerifyPage /> },
  { path: "/joindone", element: <JoinDonePage /> },
  {
    path: "/admin",
    element: <AdminLayout />,
    children: [
      { index: true, element: <AdminDashboardPage /> },
      { path: "reports", element: <AdminReportsPage /> },
      { path: "notices", element: <AdminNoticesPage /> },
    ],
  },
  {
    path: "/",
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "search", element: <SearchPage /> },
      { path: "items/:itemId", element: <ItemDetailPage /> },
      { path: "items/:itemId/request", element: <RequestPage /> },
      { path: "neighbors/:name", element: <NeighborPage /> },
      { path: "blocked", element: <BlockedPage /> },
      { path: "neighborhoods", element: <NeighborhoodsPage /> },
      { path: "policy/:key", element: <PolicyPage /> },
      { path: "reports", element: <ReportsPage /> },
      { path: "register", element: <RegisterPage /> },
      { path: "chat/:chatId?", element: <ChatPage /> },
      { path: "rentals", element: <RentalsPage /> },
      { path: "notifications", element: <NotificationsPage /> },
      { path: "profile", element: <ProfilePage /> },
    ],
  },
]);

export function App() {
  return (
    <UiProvider>
      <RouterProvider router={router} />
    </UiProvider>
  );
}
