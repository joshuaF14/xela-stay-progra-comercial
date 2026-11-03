import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import type { PropertyDetail as PropertyDetailType, CreateBookingRequest, ReviewResponse } from '../types';
import { apiFetch } from '../api/client';
import { supabase } from '../services/supabase';
import { Calendar, Users, Bed, Bath, MapPin, ShieldCheck, CheckCircle2, Star, AlertCircle, Info } from 'lucide-react';

export default function PropertyDetail() {
    const { id } = useParams<{ id: string }>();
    const [property, setProperty] = useState<PropertyDetailType | null>(null);
    const [checkIn, setCheckIn] = useState('');
    const [checkOut, setCheckOut] = useState('');
    const [guestsCount, setGuestsCount] = useState(1);
    const [loading, setLoading] = useState(false);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);
    const [success, setSuccess] = useState(false);
    const [reviews, setReviews] = useState<ReviewResponse[]>([]);
    const navigate = useNavigate();

    useEffect(() => {
        if (!id) return;

        // 1. Cargar el alojamiento completo
        apiFetch<PropertyDetailType>(`/api/v1/properties/${id}`)
            .then((data) => setProperty(data))
            .catch((err: any) => setErrorMsg(err.message || 'No se pudo cargar la estancia solicitada.'));

        // 2. Cargar reseñas tolerante a PageResponse o arreglo
        apiFetch<any>(`/api/v1/properties/${id}/reviews`)
            .then((data) => {
                if (Array.isArray(data)) {
                    setReviews(data);
                } else if (data && Array.isArray(data.content)) {
                    setReviews(data.content);
                } else {
                    setReviews([]);
                }
            })
            .catch(() => setReviews([]));
    }, [id]);

    const handleBooking = async (e: React.FormEvent) => {
        e.preventDefault();
        setErrorMsg(null);

        const { data: { session } } = await supabase.auth.getSession();
        if (!session) {
            alert('Debes iniciar sesión con tu cuenta para reservar.');
            navigate('/login');
            return;
        }

        if (new Date(checkOut) <= new Date(checkIn)) {
            setErrorMsg('La fecha de salida debe ser posterior a la fecha de entrada.');
            return;
        }

        setLoading(true);

        const payload: CreateBookingRequest = {
            propertyId: property!.id,
            checkIn,
            checkOut,
            guestsCount: Number(guestsCount),
        };

        try {
            await apiFetch('/api/v1/bookings', {
                method: 'POST',
                body: JSON.stringify(payload),
            });
            setSuccess(true);
        } catch (err: any) {
            setErrorMsg(err.message || 'Error al procesar la reservación en el servidor.');
        } finally {
            setLoading(false);
        }
    };

    if (errorMsg && !property) {
        return (
            <div className="p-12 text-center text-xs text-[#C84937] bg-[#FEEAE6] rounded-3xl border border-[#F5C2B8] max-w-lg mx-auto space-y-2">
                <AlertCircle className="w-8 h-8 mx-auto text-[#E05A47]" />
                <h3 className="font-bold text-sm">No se pudo cargar el alojamiento</h3>
                <p>{errorMsg}</p>
            </div>
        );
    }

    if (!property) {
        return <div className="p-12 text-center text-[#736E65] text-xs">Cargando detalles de la estancia...</div>;
    }

    const coverPhoto = property.images?.find((img) => img.isCover)?.imageUrl ||
        property.images?.[0]?.imageUrl ||
        'https://via.placeholder.com/900x500?text=Xela+Stay';

    const safeReviews = Array.isArray(reviews) ? reviews : [];

    return (
        <div className="max-w-5xl mx-auto space-y-8 pb-12 animate-fadeIn">
            {/* Portada */}
            <div className="rounded-3xl overflow-hidden border border-[#E8E2D8] shadow-sm max-h-[460px] bg-slate-100">
                <img src={coverPhoto} alt={property.title} className="w-full h-full object-cover" />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-12 gap-8">
                <div className="md:col-span-8 space-y-6">
                    <div>
                        <div className="flex items-center gap-1.5 text-[#E05A47] text-xs font-bold uppercase tracking-wider mb-1">
                            <MapPin className="w-4 h-4" />
                            {property.location?.address || property.location?.municipality || 'Quetzaltenango'}
                        </div>
                        <h1 className="text-3xl font-serif font-bold text-[#1E1C1A]">{property.title}</h1>
                    </div>

                    <div className="grid grid-cols-3 gap-4 py-4 border-y border-[#E8E2D8] text-[#1E1C1A] text-xs">
                        <div className="flex items-center gap-2"><Users className="w-4 h-4 text-[#E05A47]" /> {property.capacity} Huéspedes</div>
                        <div className="flex items-center gap-2"><Bed className="w-4 h-4 text-[#E05A47]" /> {property.bedrooms} Habitaciones</div>
                        <div className="flex items-center gap-2"><Bath className="w-4 h-4 text-[#E05A47]" /> {property.bathrooms} Baños</div>
                    </div>

                    <div>
                        <h3 className="text-base font-serif font-bold text-[#1E1C1A] mb-2">Descripción del alojamiento</h3>
                        <p className="text-[#5C574F] text-xs leading-relaxed whitespace-pre-line">{property.description}</p>
                    </div>

                    <div className="bg-[#FAF7F2] border border-[#E8E2D8] p-4 rounded-2xl flex items-center gap-3 text-xs text-[#736E65]">
                        <ShieldCheck className="w-5 h-5 text-[#E05A47] shrink-0" />
                        <span>Reserva procesada mediante cola FIFO en RabbitMQ para garantizar disponibilidad sin sobreventa.</span>
                    </div>

                    {/* Sección de Reseñas */}
                    <div className="space-y-4 pt-6 border-t border-[#E8E2D8]">
                        <div className="flex items-center justify-between">
                            <h3 className="text-lg font-serif font-bold text-[#1E1C1A] flex items-center gap-2">
                                <Star className="w-5 h-5 fill-[#E89E28] text-[#E89E28]" />
                                Reseñas de huéspedes ({safeReviews.length})
                            </h3>
                        </div>

                        {/* Aviso informativo de regla de negocio */}
                        <div className="bg-[#FAF7F2] border border-[#E8E2D8] p-3.5 rounded-2xl flex items-center gap-2.5 text-xs text-[#736E65]">
                            <Info className="w-4 h-4 text-[#E05A47] shrink-0" />
                            <span>
                                Para calificar una estancia debes haber completado tu viaje. Puedes dejar tu reseña desde la pestaña{' '}
                                <button
                                    type="button"
                                    onClick={() => navigate('/my-bookings')}
                                    className="font-bold text-[#E05A47] underline hover:text-[#C84937]"
                                >
                                    Mis Reservaciones
                                </button>
                                .
                            </span>
                        </div>

                        {safeReviews.length === 0 ? (
                            <p className="text-xs text-[#736E65]">Esta estancia aún no cuenta con reseñas publicadas.</p>
                        ) : (
                            <div className="space-y-3">
                                {safeReviews.map((rev) => (
                                    <div key={rev.id} className="bg-white border border-[#E8E2D8] rounded-2xl p-4 shadow-sm space-y-1">
                                        <div className="flex items-center justify-between">
                                            <span className="text-xs font-bold text-[#1E1C1A]">{rev.reviewerName || 'Huésped verificado'}</span>
                                            <span className="text-xs text-[#E89E28]">{'★'.repeat(rev.rating)}</span>
                                        </div>
                                        <p className="text-xs text-[#5C574F]">{rev.comment}</p>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>

                {/* Tarjeta lateral para reservar */}
                <div className="md:col-span-4">
                    <div className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm sticky top-24 space-y-5">
                        <div className="flex items-baseline justify-between border-b border-[#F0EBE1] pb-4">
                            <span className="text-2xl font-serif font-bold text-[#1E1C1A]">
                                {property.currency} {property.pricePerNight}
                            </span>
                            <span className="text-xs text-[#736E65]">por noche</span>
                        </div>

                        {success ? (
                            <div className="bg-[#F4F9F4] border border-[#CCE2CC] p-4 rounded-2xl text-center space-y-2">
                                <CheckCircle2 className="w-8 h-8 text-[#3A833A] mx-auto" />
                                <h4 className="text-sm font-bold text-[#1E1C1A]">¡Solicitud enviada!</h4>
                                <p className="text-xs text-[#5C574F]">Tu reservación ha sido registrada en estado PENDING. Consúltala en "Mis Reservas".</p>
                            </div>
                        ) : (
                            <form onSubmit={handleBooking} className="space-y-4">
                                {errorMsg && (
                                    <div className="bg-[#FEEAE6] border border-[#F5C2B8] text-[#C84937] text-xs p-3 rounded-xl">
                                        {errorMsg}
                                    </div>
                                )}

                                <div className="grid grid-cols-2 gap-2 bg-[#FAF7F2] p-2.5 rounded-2xl border border-[#E8E2D8]">
                                    <div>
                                        <label className="block text-[10px] font-bold uppercase text-[#736E65] mb-1 flex items-center gap-1">
                                            <Calendar className="w-3 h-3 text-[#E05A47]" /> Entrada
                                        </label>
                                        <input
                                            type="date"
                                            required
                                            value={checkIn}
                                            onChange={(e) => setCheckIn(e.target.value)}
                                            className="w-full bg-transparent text-xs text-[#1E1C1A] focus:outline-none"
                                        />
                                    </div>
                                    <div className="border-l border-[#E8E2D8] pl-2">
                                        <label className="block text-[10px] font-bold uppercase text-[#736E65] mb-1 flex items-center gap-1">
                                            <Calendar className="w-3 h-3 text-[#E05A47]" /> Salida
                                        </label>
                                        <input
                                            type="date"
                                            required
                                            value={checkOut}
                                            onChange={(e) => setCheckOut(e.target.value)}
                                            className="w-full bg-transparent text-xs text-[#1E1C1A] focus:outline-none"
                                        />
                                    </div>
                                </div>

                                <div>
                                    <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5 flex items-center gap-1">
                                        <Users className="w-3.5 h-3.5 text-[#E05A47]" /> Huéspedes
                                    </label>
                                    <input
                                        type="number"
                                        min={1}
                                        max={property.capacity}
                                        required
                                        value={guestsCount}
                                        onChange={(e) => setGuestsCount(Number(e.target.value))}
                                        className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3.5 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                                    />
                                </div>

                                <button
                                    type="submit"
                                    disabled={loading}
                                    className="w-full bg-[#E05A47] hover:bg-[#C84937] text-white font-semibold py-3 rounded-xl transition-all shadow-sm disabled:opacity-50 text-xs"
                                >
                                    {loading ? 'Procesando...' : 'Reservar Alojamiento'}
                                </button>
                            </form>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}