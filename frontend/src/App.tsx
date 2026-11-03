import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Navbar from './components/Navbar';
import Catalog from './pages/Catalog';
import Login from './pages/Login';
import PropertyDetail from './pages/PropertyDetail';
import HostDashboard from './pages/HostDashboard';
import Reports from './pages/Reports';
import MyBookings from './pages/MyBookings';
import AiAssistantWidget from './components/AiAssistantWidget';

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-[#FAF7F2] text-[#1E1C1A] flex flex-col font-sans antialiased relative overflow-x-hidden selection:bg-[#FEEAE6] selection:text-[#E05A47]">
        <Navbar />
        <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 relative z-0">
          <Routes>
            <Route path="/" element={<Catalog />} />
            <Route path="/login" element={<Login />} />
            <Route path="/properties/:id" element={<PropertyDetail />} />
            <Route path="/host" element={<HostDashboard />} />
            <Route path="/reports" element={<Reports />} />
            <Route path="/my-bookings" element={<MyBookings />} />
          </Routes>
        </main>
        <AiAssistantWidget />
      </div>
    </BrowserRouter>
  );
}