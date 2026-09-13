import type { ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import MainLayout from './layouts/MainLayout';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import OpenAppPage from './pages/OpenAppPage';
import KnowledgeBaseListPage from './pages/KnowledgeBaseListPage';
import KnowledgeBaseDetailPage from './pages/KnowledgeBaseDetailPage';
import ChatPage from './pages/ChatPage';
import VideoCanvasPage from './pages/VideoCanvasPage';

function RequireAuth({ children }: { children: ReactNode }) {
  const { token } = useAuth();
  if (!token) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          path="/"
          element={
            <RequireAuth>
              <MainLayout />
            </RequireAuth>
          }
        >
          <Route index element={<Navigate to="/open/apps" replace />} />
          <Route path="open/apps" element={<OpenAppPage />} />
          <Route path="knowledge/bases" element={<KnowledgeBaseListPage />} />
          <Route path="knowledge/bases/:id" element={<KnowledgeBaseDetailPage />} />
          <Route path="chat" element={<ChatPage />} />
          <Route path="chat/:conversationId" element={<ChatPage />} />
          <Route path="video/canvas" element={<VideoCanvasPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
