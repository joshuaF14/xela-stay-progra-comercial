import { useEffect, useState } from 'react';
import { apiFetch } from '../api/client';
import type { BookingResponse, PageResponse } from '../types';
import { Calendar, AlertCircle, Clock, CheckCircle, XCircle, Star } from 'lucide-react';

export default function MyBookings() {
    const [bookings, setBookings] = useState<BookingResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);

    // Estado para modal de reseña en reservas COMPLETED
    const [activeReviewBookingId, setActiveReviewBookingId] = useState<string | null>(null);
    const [rating, setRating] = useState(5);
    const [hoverRating, setHoverRating] = useState<number | null>(null);
    const [comment, setComment] = useState('');
    const [submittingReview, setSubmittingReview] = useState(false);

    useEffect(() => {
        setLoading(true);
        setErrorMsg(null);

        // Consulta tolerante a PageResponse o Array directo
        apiFetch<PageResponse<BookingResponse> | BookingResponse[]>('/api/v1/me/bookings')
            .then((data: any) => {
                if (Array.isArray(data)) {
                    setBookings(data);
                } else if (data && Array.isArray(data.content)) {
                    setBookings(data.content);
                } else {
                    setBookings([]);
                }
            })
            .catch((err: any) => {
                setErrorMsg(err.message || 'Error al obtener tus reservaciones.');
                setBookings([]);
            })
            .finally(() => setLoading(false));
    }, []);

    const handleReviewSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!activeReviewBookingId || !comment.trim()) return;

        setSubmittingReview(true);
        try {
            await apiFetch(`/api/v1/bookings/${activeReviewBookingId}/review`, {
                method: 'POST',
                body: JSON.stringify({ rating, comment }),
            });
            alert('¡Reseña registrada con éxito!');
            setActiveReviewBookingId(null);
            setComment('');
        } catch (err: any) {
            alert(err.message || 'No se pudo enviar la reseña.');
        } finally {
            setSubmittingReview(false);
        }
    };

    const getStatusBadge = (status: BookingResponse['status']) => {
        switch (status) {
            case 'CONFIRMED':
                return <span className="bg-emerald-100 text-emerald-800 px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1"><CheckCircle className="w-3.5 h-3.5" /> Confirmada</span>;
            case 'PENDING':
                return <span className="bg-amber-100 text-amber-800 px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1"><Clock className="w-3.5 h-3.5" /> Pendiente</span>;
            case 'CANCELLED':
                return <span className="bg-rose-100 text-rose-800 px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1"><XCircle className="w-3.5 h-3.5" /> Cancelada</span>;
            case 'COMPLETED':
                return <span className="bg-blue-100 text-blue-800 px-3 py-1 rounded-full text-xs font-bold">Completada</span>;
            default:
                return <span className="bg-slate-100 text-slate-800 px-3 py-1 rounded-full text-xs font-bold">{status}</span>;
        }
    };

    const safeBookings = Array.isArray(bookings) ? bookings : [];

    return (
        <div className="max-w-4xl mx-auto space-y-6 animate-fadeIn">
            <div>
                <h1 className="text-3xl font-serif font-bold text-[#1E1C1A]">Mis Reservaciones</h1>
                <p className="text-[#736E65] text-xs mt-1">Historial transaccional de tus estancias</p>
            </div>

            {loading ? (
                <div className="p-12 text-center text-[#736E65] text-xs">Cargando reservaciones...</div>
            ) : errorMsg ? (
                <div className="bg-[#FEEAE6] border border-[#F5C2B8] text-[#C84937] p-6 rounded-2xl text-center text-xs space-y-1">
                    <AlertCircle className="w-6 h-6 mx-auto mb-2 text-[#E05A47]" />
                    <span className="font-bold block">No se pudieron cargar tus reservas</span>
                    <p>{errorMsg}</p>
                </div>
            ) : safeBookings.length === 0 ? (
                <div className="bg-white border border-[#E8E2D8] rounded-3xl p-12 text-center space-y-2">
                    <AlertCircle className="w-8 h-8 text-[#878175] mx-auto opacity-50" />
                    <h3 className="text-sm font-bold text-[#1E1C1A]">No tienes reservaciones registradas</h3>
                    <p className="text-xs text-[#736E65]">Tus reservas creadas aparecerán aquí con su estado en tiempo real.</p>
                </div>
            ) : (
                <div className="space-y-4">
                    {safeBookings.map((b) => (
                        <div
                            key={b.id}
                            className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4"
                        >
                            <div className="space-y-1.5">
                                <h3 className="text-base font-bold text-[#1E1C1A]">{b.propertyTitle || `Reserva #${b.id.substring(0, 8)}`}</h3>
                                <div className="flex items-center gap-4 text-xs text-[#736E65]">
                                    <span className="flex items-center gap-1"><Calendar className="w-3.5 h-3.5 text-[#E05A47]" /> {b.checkIn} → {b.checkOut}</span>
                                    <span>{b.guestsCount} Huéspedes</span>
                                </div>
                            </div>

                            <div className="flex items-center gap-4 w-full sm:w-auto justify-between sm:justify-end border-t sm:border-t-0 pt-3 sm:pt-0 border-[#F0EBE1]">
                                <div className="text-right">
                                    <span className="text-[10px] text-[#878175] block uppercase font-semibold">Total</span>
                                    <span className="text-sm font-black text-[#1E1C1A]">{b.currency} {b.totalPrice}</span>
                                </div>
                                {getStatusBadge(b.status)}

                                {b.status === 'COMPLETED' && (
                                    <button
                                        onClick={() => setActiveReviewBookingId(b.id)}
                                        className="bg-[#1E1C1A] text-white text-xs px-3 py-1.5 rounded-xl hover:bg-[#33312E] transition-all flex items-center gap-1"
                                    >
                                        <Star className="w-3.5 h-3.5 text-[#E89E28]" /> Calificar
                                    </button>
                                )}
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {/* Modal para calificar reserva completada */}
            {activeReviewBookingId && (
                <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center p-4 z-50">
                    <form onSubmit={handleReviewSubmit} className="bg-white rounded-3xl p-6 max-w-md w-full space-y-4 shadow-2xl border border-[#E8E2D8]">
                        <h3 className="font-serif font-bold text-lg text-[#1E1C1A]">Califica tu estancia</h3>

                        <div className="flex items-center gap-1.5" onMouseLeave={() => setHoverRating(null)}>
                            {[1, 2, 3, 4, 5].map((star) => {
                                const isFilled = (hoverRating !== null ? hoverRating : rating) >= star;
                                return (
                                    <button
                                        type="button"
                                        key={star}
                                        onClick={() => setRating(star)}
                                        onMouseEnter={() => setHoverRating(star)}
                                        className="focus:outline-none transition-transform hover:scale-125"
                                    >
                                        <Star
                                            className={`w-6 h-6 ${isFilled ? 'fill-[#E89E28] text-[#E89E28]' : 'text-slate-300'
                                                }`}
                                        />
                                    </button>
                                );
                            })}
                        </div>

                        <textarea
                            required
                            rows={3}
                            value={comment}
                            onChange={(e) => setComment(e.target.value)}
                            placeholder="Escribe tu opinión sobre el alojamiento..."
                            className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl p-3 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                        />

                        <div className="flex justify-end gap-2">
                            <button
                                type="button"
                                onClick={() => setActiveReviewBookingId(null)}
                                className="px-4 py-2 text-xs font-semibold text-[#736E65] hover:text-[#1E1C1A]"
                            >
                                Cancelar
                            </button>
                            <button
                                type="submit"
                                disabled={submittingReview}
                                className="bg-[#E05A47] text-white px-4 py-2 text-xs font-semibold rounded-xl hover:bg-[#C84937] transition-all disabled:opacity-50"
                            >
                                {submittingReview ? 'Enviando...' : 'Publicar'}
                            </button>
                        </div>
                    </form>
                </div>
            )}
        </div>
    );
}