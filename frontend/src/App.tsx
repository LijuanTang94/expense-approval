import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth";
import { Layout, ProtectedRoute } from "./ui";
import { Login } from "./pages/Login";
import { RequestList } from "./pages/RequestList";
import { NewRequest } from "./pages/NewRequest";
import { RequestDetail } from "./pages/RequestDetail";

function Protected({ element }: { element: React.ReactNode }) {
  return (
    <ProtectedRoute>
      <Layout>{element}</Layout>
    </ProtectedRoute>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Protected element={<RequestList />} />} />
          <Route path="/requests/new" element={<Protected element={<NewRequest />} />} />
          <Route path="/requests/:id" element={<Protected element={<RequestDetail />} />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
