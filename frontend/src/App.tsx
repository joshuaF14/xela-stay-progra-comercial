import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Navbar from './components/Navbar';
import Catalog from './pages/Catalog';
import Login from './pages/login';
import PropertyDetail from './pages/PropertyDetail';
import HostDashboard from './pages/HostDashboard';
import Reports from './pages/Reports';

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-[#FAF7F2] text-[#1E1C1A] flex flex-col font-sans antialiased relative overflow-x-hidden selection:bg-[#FEEAE6] selection:text-[#E05A47]">
        {/* Luces y formas orgánicas de fondo (Gradientes sutiles) */}
        <div className="fixed top-0 left-1/4 w-96 h-96 bg-gradient-to-br from-[#FEEAE6] to-[#F5D5C8] rounded-full blur-3xl opacity-60 -z-10 pointer-events-none animate-pulse"></div>
        <div className="fixed top-1/3 -right-20 w-80 h-80 bg-gradient-to-tr from-[#FCE8DC] to-[#F3DFD1] rounded-full blur-3xl opacity-50 -z-10 pointer-events-none"></div>
        <div className="fixed -bottom-20 left-10 w-96 h-96 bg-gradient-to-tr from-[#FEEAE6]/40 to-[#EADBC8]/40 rounded-full blur-3xl opacity-40 -z-10 pointer-events-none"></div>

        <Navbar />

        <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 relative z-0">
          <Routes>
            <Route path="/" element={<Catalog />} />
            <Route path="/login" element={<Login />} />
            <Route path="/properties/:id" element={<PropertyDetail />} />
            <Route path="/host" element={<HostDashboard />} />
            <Route path="/reports" element={<Reports />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}