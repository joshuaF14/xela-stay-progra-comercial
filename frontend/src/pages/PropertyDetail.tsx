import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import type { PropertyDetail as PropertyDetailType, CreateBookingRequest, ReviewResponse } from '../types';
import { apiFetch } from '../api/client';
import { supabase } from '../services/supabase';
import { Calendar, Users, Bed, Bath, MapPin, ShieldCheck, CheckCircle2, Star, AlertCircle } from 'lucide-react';

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

        // Cargar detalle real
        apiFetch<PropertyDetailType>(`/api/v1/properties/${id}`)
            .then((data) => setProperty(data))
            .catch((err: any) => setErrorMsg(err.message || 'No se pudo cargar la propiedad.'));

        // Cargar reseñas públicas existentes
        apiFetch<ReviewResponse[]>(`/api/v1/properties/${id}/reviews`)
            .then((data) => setReviews(data || []))
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
            setErrorMsg('La fecha de salida debe ser posterior a la de entrada.');
            return;
        }

        setLoading(true);
        // Payload estricto en camelCase esperado por POST /api/v1/bookings
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
            // CERO datos falsos: muestra el error real devuelto por ApiError
            setErrorMsg(err.message || 'Error al procesar la reserva con el servidor.');
        } finally {
            setLoading(false);
        }
    };

    if (errorMsg && !property) {
        return (
            <div className="p-12 text-center text-xs text-[#C84937] bg-[#FEEAE6] rounded-2xl border border-[#F5C2B8] max-w-lg mx-auto">
                <AlertCircle className="w-6 h-6 mx-auto mb-2 text-[#E05A47]" />
                {errorMsg}
            </div>
        );
    }

    if (!property) return <div className="p-12 text-center text-[#7A756D] text-xs">Cargando detalles de la estancia...</div>;

    return (
        <div className="max-w-5xl mx-auto space-y-10 pb-12 animate-fadeIn">
            {/* Portada */}
            <div className="rounded-3xl overflow-hidden border border-[#E8E2D8] shadow-md max-h-[460px] bg-white">
                <img
                    src={property.images?.[0]?.imageUrl || 'https://via.placeholder.com/800x400?text=Xela+Stay'}
                    alt={property.title}
                    className="w-full h-full object-cover"
                />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-12 gap-8">
                <div className="md:col-span-8 space-y-8">
                    <div>
                        <div className="flex items-center gap-1.5 text-[#E05A47] text-xs font-semibold uppercase tracking-wider mb-1">
                            <MapPin className="w-4 h-4" />
                            {property.location?.address}, {property.location?.municipality}
                        </div>
                        <h1 className="text-3xl font-serif font-bold text-[#1E1C1A]">{property.title}</h1>
                    </div>

                    <div className="grid grid-cols-3 gap-4 py-4 border-y border-[#EAE3D9] text-[#1E1C1A] text-sm">
                        <div className="flex items-center gap-2"><Users className="w-5 h-5 text-[#E05A47]" /> {property.capacity} Huéspedes</div>
                        <div className="flex items-center gap-2"><Bed className="w-5 h-5 text-[#E05A47]" /> {property.bedrooms} Habitaciones</div>
                        <div className="flex items-center gap-2"><Bath className="w-5 h-5 text-[#E05A47]" /> {property.bathrooms} Baños</div>
                    </div>

                    <div>
                        <h3 className="text-lg font-serif font-bold text-[#1E1C1A] mb-2">Sobre este alojamiento</h3>
                        <p className="text-[#5C574F] text-sm leading-relaxed whitespace-pre-line">{property.description}</p>
                    </div>

                    <div className="bg-[#FAF7F0] border border-[#EAE3D9] p-4 rounded-2xl flex items-center gap-3 text-xs text-[#736E65]">
                        <ShieldCheck className="w-5 h-5 text-[#E05A47]" />
                        <span>Reserva procesada mediante cola FIFO en RabbitMQ para evitar sobreventa.</span>
                    </div>

                    {/* Reseñas públicas */}
                    <div className="space-y-4 pt-6 border-t border-[#EAE3D9]">
                        <h3 className="text-xl font-serif font-bold text-[#1E1C1A] flex items-center gap-2">
                            <Star className="w-5 h-5 fill-[#E89E28] text-[#E89E28]" />
                            Reseñas de huéspedes ({reviews.length})
                        </h3>
                        {reviews.length === 0 ? (
                            <p className="text-xs text-[#736E65]">Esta propiedad aún no tiene reseñas publicadas.</p>
                        ) : (
                            <div className="space-y-3">
                                {reviews.map((rev) => (
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

                {/* Formulario de Reserva */}
                <div className="md:col-span-4">
                    <div className="bg-white border border-[#EAE3D9] rounded-3xl p-6 shadow-sm sticky top-24 space-y-5">
                        <div className="flex items-baseline justify-between border-b border-[#F2ECE4] pb-4">
                            <span className="text-2xl font-serif font-bold text-[#1E1C1A]">
                                {property.currency} {property.pricePerNight}
                            </span>
                            <span className="text-xs text-[#7A756D]">por noche</span>
                        </div>

                        {success ? (
                            <div className="bg-[#F4F9F4] border border-[#CCE2CC] p-4 rounded-2xl text-center space-y-2">
                                <CheckCircle2 className="w-8 h-8 text-[#3A833A] mx-auto" />
                                <h4 className="text-sm font-bold text-[#1E1C1A]">¡Reserva enviada!</h4>
                                <p className="text-xs text-[#5C574F]">Se ha registrado tu solicitud en estado PENDING. Consúltala en "Mis Reservas".</p>
                            </div>
                        ) : (
                            <form onSubmit={handleBooking} className="space-y-4">
                                {errorMsg && (
                                    <div className="bg-[#FEEAE6] border border-[#F5C2B8] text-[#C84937] text-xs p-3 rounded-xl">
                                        {errorMsg}
                                    </div>
                                )}

                                <div className="grid grid-cols-2 gap-2 bg-[#FAF7F2] p-2 rounded-xl border border-[#EAE3D9]">
                                    <div>
                                        <label className="block text-[10px] font-semibold uppercase text-[#7A756D] mb-1 flex items-center gap-1">
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
                                    <div className="border-l border-[#EAE3D9] pl-2">
                                        <label className="block text-[10px] font-semibold uppercase text-[#7A756D] mb-1 flex items-center gap-1">
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
                                        className="w-full bg-white border border-[#EAE3D9] rounded-xl px-3.5 py-2 text-sm text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                                    />
                                </div>

                                <button
                                    type="submit"
                                    disabled={loading}
                                    className="w-full bg-[#E05A47] hover:bg-[#C84937] text-white font-semibold py-3 rounded-xl transition-all shadow-sm disabled:opacity-50 text-sm"
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