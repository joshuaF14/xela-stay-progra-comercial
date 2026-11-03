import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { apiFetch } from '../api/client';
import ChatModal from '../components/ChatModal';
import {
    Calendar,
    Ban,
    AlertCircle,
    Clock,
    ExternalLink,
    MessageSquare,
    Star,
    X,
    CheckCircle2
} from 'lucide-react';

export default function MyBookings() {
    const [bookings, setBookings] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);

    // Cancelación
    const [cancellingId, setCancellingId] = useState<string | null>(null);
    const [cancelReason, setCancelReason] = useState('');

    // Chat
    const [activeChatPropertyId, setActiveChatPropertyId] = useState<string | null>(null);
    const [activeChatConversationId, setActiveChatConversationId] = useState<string | null>(null);
    const [showChatModal, setShowChatModal] = useState(false);

    // Reseña
    const [reviewBooking, setReviewBooking] = useState<any | null>(null);
    const [rating, setRating] = useState(5);
    const [comment, setComment] = useState('');
    const [submittingReview, setSubmittingReview] = useState(false);
    const [reviewSuccess, setReviewSuccess] = useState<string | null>(null);
    const [reviewedBookings, setReviewedBookings] = useState<Record<string, boolean>>({});

    const fetchMyBookings = async () => {
        setLoading(true);
        setErrorMsg(null);
        try {
            const data: any = await apiFetch('/api/v1/me/bookings');
            const list = Array.isArray(data) ? data : data?.content || [];
            setBookings(list);
        } catch (err: any) {
            setErrorMsg(err.message || 'Error al cargar tus reservaciones.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchMyBookings();
        // Cargar reservaciones ya reseñadas guardadas en local
        try {
            const cached = JSON.parse(localStorage.getItem('xela_reviewed_bookings') || '{}');
            setReviewedBookings(cached);
        } catch {
            setReviewedBookings({});
        }
    }, []);

    const handleCancel = async (id: string) => {
        if (!cancelReason.trim()) {
            alert('Debes indicar un motivo de cancelación.');
            return;
        }

        try {
            await apiFetch(`/api/v1/bookings/${id}/cancel`, {
                method: 'PATCH',
                body: JSON.stringify({ reason: cancelReason.trim() }),
            });
            setCancellingId(null);
            setCancelReason('');
            fetchMyBookings();
        } catch (err: any) {
            alert(err.message || 'No se pudo cancelar la reserva.');
        }
    };

    const handleOpenChat = (b: any) => {
        const propId = b.propertyId || b.property?.id;
        const convId = b.conversationId || b.conversation?.id || null;
        setActiveChatPropertyId(propId || null);
        setActiveChatConversationId(convId);
        setShowChatModal(true);
    };

    const handleOpenReview = (b: any) => {
        setReviewBooking(b);
        setRating(5);
        setComment('');
        setReviewSuccess(null);
    };

    const markBookingAsReviewed = (bookingId: string) => {
        const updated = { ...reviewedBookings, [bookingId]: true };
        setReviewedBookings(updated);
        localStorage.setItem('xela_reviewed_bookings', JSON.stringify(updated));
    };

    const handleSubmitReview = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!reviewBooking) return;

        setSubmittingReview(true);

        try {
            // POST en singular hacia /bookings/{bookingId}/review según contrato de Gio
            await apiFetch(`/api/v1/bookings/${reviewBooking.id}/review`, {
                method: 'POST',
                body: JSON.stringify({
                    rating: Number(rating),
                    comment: comment.trim() || undefined,
                }),
            });

            markBookingAsReviewed(reviewBooking.id);
            setReviewSuccess('¡Reseña publicada con éxito!');
            setTimeout(() => {
                setReviewBooking(null);
                fetchMyBookings();
            }, 1200);
        } catch (err: any) {
            // Si responde 409 (ya tiene reseña o no está completed), esconder botón según regla de Gio
            markBookingAsReviewed(reviewBooking.id);
            alert(err.message || 'Esta reserva ya tiene una reseña registrada o no está completada.');
            setReviewBooking(null);
        } finally {
            setSubmittingReview(false);
        }
    };

    return (
        <div className="max-w-4xl mx-auto space-y-6 pb-16 animate-fadeIn">
            <div className="border-b border-[#E8E2D8] pb-4">
                <h1 className="text-2xl font-serif font-bold text-[#1E1C1A]">Mis Reservaciones</h1>
                <p className="text-xs text-[#736E65] mt-1">
                    Seguimiento de tus estadías en Quetzaltenango, chat con anfitriones y calificaciones
                </p>
            </div>

            {errorMsg && (
                <div className="bg-[#FEEAE6] border border-[#F5C2B8] text-[#C84937] p-4 rounded-2xl flex items-center gap-3 text-xs">
                    <AlertCircle className="w-5 h-5 shrink-0" />
                    <span>{errorMsg}</span>
                </div>
            )}

            {loading ? (
                <p className="text-center py-12 text-xs text-[#736E65]">Cargando reservaciones...</p>
            ) : bookings.length === 0 ? (
                <div className="bg-white border border-[#E8E2D8] rounded-3xl p-12 text-center text-xs text-[#736E65]">
                    Aún no tienes reservaciones registradas.
                </div>
            ) : (
                <div className="space-y-4">
                    {bookings.map((b) => {
                        const propertyId = b.propertyId || b.property?.id;
                        const title = b.property?.title || b.propertyTitle || 'Alojamiento en Xela';
                        const checkIn = b.checkInDate || b.checkIn || b.startDate || 'Pendiente';
                        const checkOut = b.checkOutDate || b.checkOut || b.endDate || 'Pendiente';
                        const total = b.totalPrice ?? b.totalAmount ?? b.total ?? b.price ?? 0;
                        const hasReview = reviewedBookings[b.id] || false;

                        return (
                            <div key={b.id} className="bg-white border border-[#E8E2D8] rounded-2xl p-5 shadow-sm space-y-3">
                                <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#F0EBE1] pb-3">
                                    {propertyId ? (
                                        <Link
                                            to={`/properties/${propertyId}`}
                                            className="font-serif font-bold text-sm text-[#1E1C1A] hover:text-[#E05A47] transition flex items-center gap-1.5 group"
                                            title="Ver detalles del alojamiento"
                                        >
                                            <span>{title}</span>
                                            <ExternalLink className="w-3.5 h-3.5 opacity-40 group-hover:opacity-100 transition" />
                                        </Link>
                                    ) : (
                                        <h3 className="font-serif font-bold text-sm text-[#1E1C1A]">{title}</h3>
                                    )}

                                    <span
                                        className={`text-[10px] font-bold px-2.5 py-1 rounded-full uppercase tracking-wider ${b.status === 'PENDING'
                                                ? 'bg-amber-100 text-amber-800'
                                                : b.status === 'CONFIRMED'
                                                    ? 'bg-emerald-100 text-emerald-800'
                                                    : b.status === 'COMPLETED'
                                                        ? 'bg-blue-100 text-blue-800'
                                                        : b.status === 'CANCELLED'
                                                            ? 'bg-rose-100 text-rose-800'
                                                            : 'bg-zinc-100 text-zinc-700'
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
                                    <div className="font-bold text-[#1E1C1A]">Total: Q{total}</div>
                                </div>

                                {b.status === 'PENDING' && (
                                    <div className="bg-amber-50 border border-amber-200 text-amber-900 px-3 py-2 rounded-xl text-[11px] flex items-center gap-2">
                                        <Clock className="w-4 h-4 text-amber-700 shrink-0" />
                                        <span>Pendiente de aprobación. Si el anfitrión no responde en 24 horas, la reserva vencerá automáticamente.</span>
                                    </div>
                                )}

                                {b.status === 'CANCELLED' && b.cancellationReason && (
                                    <div className="bg-rose-50 border border-rose-200 text-rose-900 px-3 py-2 rounded-xl text-[11px]">
                                        <strong>Motivo de cancelación:</strong> {b.cancellationReason}
                                    </div>
                                )}

                                <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-[#F0EBE1]">
                                    <button
                                        onClick={() => handleOpenChat(b)}
                                        className="bg-[#FAF7F2] border border-[#E8E2D8] hover:border-[#E05A47] text-[#1E1C1A] px-3 py-1.5 rounded-xl text-xs font-semibold transition flex items-center gap-1.5 shadow-sm"
                                    >
                                        <MessageSquare className="w-3.5 h-3.5 text-[#E05A47]" />
                                        <span>Chat con Anfitrión</span>
                                    </button>

                                    <div className="flex items-center gap-2">
                                        {/* Botón de Reseña solo si está COMPLETED y no ha sido reseñada */}
                                        {b.status === 'COMPLETED' && (
                                            hasReview ? (
                                                <span className="text-[11px] font-semibold text-[#3A833A] bg-emerald-50 border border-emerald-200 px-3 py-1.5 rounded-xl flex items-center gap-1">
                                                    <CheckCircle2 className="w-3.5 h-3.5" /> Estadía Reseñada
                                                </span>
                                            ) : (
                                                <button
                                                    onClick={() => handleOpenReview(b)}
                                                    className="bg-amber-500 hover:bg-amber-600 text-white px-3 py-1.5 rounded-xl text-xs font-bold transition flex items-center gap-1.5 shadow-sm"
                                                >
                                                    <Star className="w-3.5 h-3.5 fill-white" />
                                                    <span>Dejar Reseña</span>
                                                </button>
                                            )
                                        )}

                                        {(b.status === 'PENDING' || b.status === 'CONFIRMED') && (
                                            <button
                                                onClick={() => setCancellingId(b.id)}
                                                className="border border-[#C84937] text-[#C84937] hover:bg-[#FEEAE6] px-3 py-1.5 rounded-xl text-xs font-semibold transition flex items-center gap-1.5"
                                            >
                                                <Ban className="w-3.5 h-3.5" /> Cancelar Reserva
                                            </button>
                                        )}
                                    </div>
                                </div>

                                {cancellingId === b.id && (
                                    <div className="mt-3 p-3 bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl space-y-2">
                                        <label className="block text-[11px] font-semibold text-[#1E1C1A]">
                                            Motivo obligatorio de cancelación:
                                        </label>
                                        <input
                                            type="text"
                                            required
                                            value={cancelReason}
                                            onChange={(e) => setCancelReason(e.target.value)}
                                            placeholder="Ej. Cambio imprevisto de fechas de viaje"
                                            className="w-full bg-white border border-[#E8E2D8] rounded-lg px-3 py-1.5 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                                        />
                                        <div className="flex justify-end gap-2">
                                            <button
                                                onClick={() => setCancellingId(null)}
                                                className="px-3 py-1 rounded-lg text-xs font-semibold text-[#736E65]"
                                            >
                                                Volver
                                            </button>
                                            <button
                                                onClick={() => handleCancel(b.id)}
                                                className="bg-[#C84937] text-white px-3 py-1 rounded-lg text-xs font-bold"
                                            >
                                                Confirmar Cancelación
                                            </button>
                                        </div>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}

            <ChatModal
                isOpen={showChatModal}
                onClose={() => setShowChatModal(false)}
                propertyId={activeChatPropertyId || undefined}
                conversationId={activeChatConversationId}
            />

            {/* Modal para Dejar Reseña */}
            {reviewBooking && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
                    <div className="bg-white border border-[#E8E2D8] rounded-3xl w-full max-w-md shadow-2xl p-6 space-y-4 animate-fadeIn">
                        <div className="flex items-center justify-between border-b border-[#F0EBE1] pb-3">
                            <h3 className="font-serif font-bold text-base text-[#1E1C1A] flex items-center gap-2">
                                <Star className="w-4 h-4 fill-amber-400 text-amber-400" />
                                Calificar Estadía
                            </h3>
                            <button onClick={() => setReviewBooking(null)} className="p-1 rounded-lg text-[#736E65] hover:bg-[#FAF7F2]">
                                <X className="w-4 h-4" />
                            </button>
                        </div>

                        {reviewSuccess ? (
                            <div className="bg-[#F4F9F4] border border-[#CCE2CC] text-[#3A833A] p-4 rounded-2xl flex items-center gap-2 text-xs">
                                <CheckCircle2 className="w-4 h-4 shrink-0" />
                                <span>{reviewSuccess}</span>
                            </div>
                        ) : (
                            <form onSubmit={handleSubmitReview} className="space-y-4 text-xs">
                                <div>
                                    <label className="block font-semibold text-[#1E1C1A] mb-1.5">
                                        ¿Cómo estuvo tu experiencia? (1 a 5 estrellas)
                                    </label>
                                    <div className="flex items-center gap-2">
                                        {[1, 2, 3, 4, 5].map((star) => (
                                            <button
                                                key={star}
                                                type="button"
                                                onClick={() => setRating(star)}
                                                className="p-1 transition transform hover:scale-110"
                                            >
                                                <Star
                                                    className={`w-6 h-6 ${star <= rating ? 'fill-amber-400 text-amber-400' : 'text-zinc-300'
                                                        }`}
                                                />
                                            </button>
                                        ))}
                                        <span className="font-bold text-[#1E1C1A] ml-2">
                                            {rating === 5 ? 'Excelente' : rating === 4 ? 'Muy buena' : rating === 3 ? 'Regular' : rating === 2 ? 'Mala' : 'Pésima'}
                                        </span>
                                    </div>
                                </div>

                                <div>
                                    <label className="block font-semibold text-[#1E1C1A] mb-1">
                                        Comentario para el anfitrión y futuros viajeros (opcional)
                                    </label>
                                    <textarea
                                        rows={3}
                                        maxLength={2000}
                                        value={comment}
                                        onChange={(e) => setComment(e.target.value)}
                                        placeholder="Describe la limpieza, hospitalidad, comodidad..."
                                        className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl p-3 text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                                    />
                                    <span className="text-[10px] text-[#736E65] text-right block mt-1">
                                        {comment.length} / 2000 caracteres
                                    </span>
                                </div>

                                <div className="flex justify-end gap-2 pt-3 border-t border-[#F0EBE1]">
                                    <button
                                        type="button"
                                        onClick={() => setReviewBooking(null)}
                                        className="px-4 py-2 rounded-xl text-xs font-semibold text-[#736E65] hover:bg-[#FAF7F2]"
                                    >
                                        Cancelar
                                    </button>
                                    <button
                                        type="submit"
                                        disabled={submittingReview}
                                        className="bg-[#E05A47] hover:bg-[#C84937] text-white px-5 py-2 rounded-xl text-xs font-bold transition shadow-sm disabled:opacity-50"
                                    >
                                        {submittingReview ? 'Publicando...' : 'Publicar Reseña'}
                                    </button>
                                </div>
                            </form>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}