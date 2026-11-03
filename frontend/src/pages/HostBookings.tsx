import { useState, useEffect } from 'react';
import { apiFetch } from '../api/client';
import ChatModal from '../components/ChatModal';
import {
    Check,
    X,
    CheckCheck,
    Calendar,
    AlertCircle,
    User,
    DollarSign,
    MessageSquare
} from 'lucide-react';

export default function HostBookings() {
    const [filter, setFilter] = useState<'PENDING' | 'ALL'>('PENDING');
    const [bookings, setBookings] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);
    const [rejectingId, setRejectingId] = useState<string | null>(null);
    const [rejectReason, setRejectReason] = useState('');

    // Estados para el Chat con el Huésped
    const [activeChatPropertyId, setActiveChatPropertyId] = useState<string | null>(null);
    const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
    const [showChatModal, setShowChatModal] = useState(false);

    const fetchBookings = async () => {
        setLoading(true);
        setErrorMsg(null);
        try {
            const endpoint =
                filter === 'PENDING'
                    ? '/api/v1/me/hosting/bookings?status=PENDING'
                    : '/api/v1/me/hosting/bookings';
            const data: any = await apiFetch(endpoint);
            const list = Array.isArray(data) ? data : data?.content || [];
            setBookings(list);
        } catch (err: any) {
            setErrorMsg(err.message || 'Error al obtener solicitudes de reserva.');
            setBookings([]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchBookings();
    }, [filter]);

    const handleConfirm = async (id: string) => {
        try {
            await apiFetch(`/api/v1/bookings/${id}/confirm`, { method: 'PATCH' });
            fetchBookings();
        } catch (err: any) {
            alert(err.message || 'Error al confirmar la reserva.');
        }
    };

    const handleReject = async (id: string) => {
        try {
            await apiFetch(`/api/v1/bookings/${id}/reject`, {
                method: 'PATCH',
                body: JSON.stringify({ reason: rejectReason.trim() || 'No disponible para esas fechas' }),
            });
            setRejectingId(null);
            setRejectReason('');
            fetchBookings();
        } catch (err: any) {
            alert(err.message || 'Error al rechazar la reserva.');
        }
    };

    const handleComplete = async (id: string) => {
        try {
            await apiFetch(`/api/v1/bookings/${id}/complete`, { method: 'PATCH' });
            fetchBookings();
        } catch (err: any) {
            alert(err.message || 'Error al marcar la estancia como completada.');
        }
    };

    // Abrir chat directamente con el huésped
    const handleOpenChat = async (booking: any) => {
        const propId = booking.propertyId || booking.property?.id;
        const convId = booking.conversationId || booking.conversation?.id || null;

        setActiveChatPropertyId(propId || null);
        setActiveConversationId(convId);
        setShowChatModal(true);
    };

    return (
        <div className="max-w-4xl mx-auto space-y-6 pb-16 animate-fadeIn">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between border-b border-[#E8E2D8] pb-4 gap-4">
                <div>
                    <h1 className="text-2xl font-serif font-bold text-[#1E1C1A]">Gestión de Reservas (Anfitrión)</h1>
                    <p className="text-xs text-[#736E65] mt-1">Aprobación, contacto y seguimiento de huéspedes</p>
                </div>

                <div className="flex bg-[#E8E2D8]/40 p-1 rounded-xl text-xs font-semibold">
                    <button
                        onClick={() => setFilter('PENDING')}
                        className={`px-3 py-1.5 rounded-lg transition ${filter === 'PENDING' ? 'bg-[#E05A47] text-white shadow-sm' : 'text-[#5C574F]'
                            }`}
                    >
                        Pendientes
                    </button>
                    <button
                        onClick={() => setFilter('ALL')}
                        className={`px-3 py-1.5 rounded-lg transition ${filter === 'ALL' ? 'bg-[#E05A47] text-white shadow-sm' : 'text-[#5C574F]'
                            }`}
                    >
                        Todas
                    </button>
                </div>
            </div>

            {errorMsg && (
                <div className="bg-[#FEEAE6] border border-[#F5C2B8] text-[#C84937] p-4 rounded-2xl flex items-center gap-3 text-xs">
                    <AlertCircle className="w-5 h-5 shrink-0" />
                    <span>{errorMsg}</span>
                </div>
            )}

            {loading ? (
                <p className="text-center py-12 text-xs text-[#736E65]">Consultando solicitudes...</p>
            ) : bookings.length === 0 ? (
                <div className="bg-white border border-[#E8E2D8] rounded-3xl p-12 text-center text-xs text-[#736E65]">
                    No se encontraron solicitudes en este filtro.
                </div>
            ) : (
                <div className="space-y-4">
                    {bookings.map((b) => {
                        const propertyName = b.property?.title || b.propertyTitle || 'Alojamiento en Xela';

                        // Extracción exhaustiva del nombre o identificador del huésped
                        const guest =
                            b.guest?.fullName ||
                            b.guestUser?.fullName ||
                            (b.guest?.firstName && `${b.guest.firstName} ${b.guest.lastName || ''}`.trim()) ||
                            (b.guestUser?.firstName && `${b.guestUser.firstName} ${b.guestUser.lastName || ''}`.trim()) ||
                            b.guestName ||
                            b.guestEmail ||
                            b.guest?.email ||
                            b.guestUser?.email ||
                            b.user?.email ||
                            b.userName ||
                            'Viajero';

                        const checkIn = b.checkInDate || b.checkIn || b.startDate || 'Sin definir';
                        const checkOut = b.checkOutDate || b.checkOut || b.endDate || 'Sin definir';
                        const total = b.totalPrice ?? b.totalAmount ?? b.total ?? b.price ?? 0;

                        return (
                            <div key={b.id} className="bg-white border border-[#E8E2D8] rounded-2xl p-5 shadow-sm space-y-3">
                                <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#F0EBE1] pb-3">
                                    <div>
                                        <h3 className="font-serif font-bold text-sm text-[#1E1C1A]">{propertyName}</h3>
                                        <p className="text-[11px] text-[#736E65] flex items-center gap-1.5 mt-0.5">
                                            <User className="w-3.5 h-3.5 text-[#E05A47]" />
                                            <span>Huésped: <strong className="text-[#1E1C1A]">{guest}</strong></span>
                                        </p>
                                    </div>
                                    <span
                                        className={`text-[10px] font-bold px-2.5 py-1 rounded-full uppercase tracking-wider ${b.status === 'PENDING'
                                                ? 'bg-amber-100 text-amber-800'
                                                : b.status === 'CONFIRMED'
                                                    ? 'bg-emerald-100 text-emerald-800'
                                                    : b.status === 'COMPLETED'
                                                        ? 'bg-blue-100 text-blue-800'
                                                        : 'bg-rose-100 text-rose-800'
                                            }`}
                                    >
                                        {b.status}
                                    </span>
                                </div>

                                <div className="flex flex-wrap items-center justify-between text-xs text-[#5C574F] gap-2">
                                    <div className="flex items-center gap-2">
                                        <Calendar className="w-4 h-4 text-[#E05A47]" />
                                        <span>{checkIn} ➔ {checkOut}</span>
                                    </div>
                                    <div className="font-bold text-[#1E1C1A] flex items-center gap-1">
                                        <DollarSign className="w-3.5 h-3.5 text-[#3A833A]" />
                                        <span>Total: Q{total}</span>
                                    </div>
                                </div>

                                {/* Acciones */}
                                <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-[#F0EBE1]">
                                    {/* Botón de Chat para el Anfitrión */}
                                    <button
                                        onClick={() => handleOpenChat(b)}
                                        className="bg-[#FAF7F2] border border-[#E8E2D8] hover:border-[#E05A47] text-[#1E1C1A] px-3 py-1.5 rounded-xl text-xs font-semibold transition flex items-center gap-1.5 shadow-sm"
                                    >
                                        <MessageSquare className="w-3.5 h-3.5 text-[#E05A47]" />
                                        <span>Contactar Huésped</span>
                                    </button>

                                    <div className="flex items-center gap-2">
                                        {b.status === 'PENDING' && (
                                            <>
                                                <button
                                                    onClick={() => handleConfirm(b.id)}
                                                    className="bg-[#3A833A] hover:bg-[#2F6D2F] text-white px-3.5 py-1.5 rounded-xl text-xs font-bold transition flex items-center gap-1.5 shadow-sm"
                                                >
                                                    <Check className="w-3.5 h-3.5" /> Confirmar
                                                </button>
                                                <button
                                                    onClick={() => setRejectingId(b.id)}
                                                    className="bg-[#C84937] hover:bg-[#A83827] text-white px-3.5 py-1.5 rounded-xl text-xs font-bold transition flex items-center gap-1.5 shadow-sm"
                                                >
                                                    <X className="w-3.5 h-3.5" /> Rechazar
                                                </button>
                                            </>
                                        )}

                                        {b.status === 'CONFIRMED' && (
                                            <button
                                                onClick={() => handleComplete(b.id)}
                                                className="bg-[#1E1C1A] hover:bg-black text-white px-3.5 py-1.5 rounded-xl text-xs font-bold transition flex items-center gap-1.5 shadow-sm"
                                            >
                                                <CheckCheck className="w-3.5 h-3.5" /> Marcar Estadía Completada
                                            </button>
                                        )}
                                    </div>
                                </div>

                                {rejectingId === b.id && (
                                    <div className="mt-3 p-3 bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl space-y-2">
                                        <label className="block text-[11px] font-semibold text-[#1E1C1A]">Motivo de rechazo:</label>
                                        <input
                                            type="text"
                                            value={rejectReason}
                                            onChange={(e) => setRejectReason(e.target.value)}
                                            placeholder="Ej. Fechas no disponibles"
                                            className="w-full bg-white border border-[#E8E2D8] rounded-lg px-3 py-1.5 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                                        />
                                        <div className="flex justify-end gap-2">
                                            <button
                                                onClick={() => setRejectingId(null)}
                                                className="px-3 py-1 rounded-lg text-xs font-semibold text-[#736E65]"
                                            >
                                                Cancelar
                                            </button>
                                            <button
                                                onClick={() => handleReject(b.id)}
                                                className="bg-[#C84937] text-white px-3 py-1 rounded-lg text-xs font-bold"
                                            >
                                                Confirmar Rechazo
                                            </button>
                                        </div>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}

            {/* Modal de Chat entre Anfitrión y Huésped */}
            <ChatModal
                isOpen={showChatModal}
                onClose={() => setShowChatModal(false)}
                propertyId={activeChatPropertyId || undefined}
                conversationId={activeConversationId}
            />
        </div>
    );
}