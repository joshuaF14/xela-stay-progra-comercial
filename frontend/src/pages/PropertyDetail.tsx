import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { apiFetch } from '../api/client';
import { supabase } from '../services/supabase';
import ChatModal from '../components/ChatModal';
import {
    MapPin,
    Users,
    Bed,
    Bath,
    MessageSquare,
    AlertCircle,
    CheckCircle2,
    Star,
    User
} from 'lucide-react';

interface ReviewItem {
    id: string;
    reviewerName?: string;
    authorName?: string;
    rating: number;
    comment?: string;
    createdAt: string;
}

export default function PropertyDetail() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const [property, setProperty] = useState<any>(null);
    const [reviews, setReviews] = useState<ReviewItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);

    const [checkIn, setCheckIn] = useState('');
    const [checkOut, setCheckOut] = useState('');
    const [bookingLoading, setBookingLoading] = useState(false);
    const [bookingSuccess, setBookingSuccess] = useState(false);

    const [showChat, setShowChat] = useState(false);

    useEffect(() => {
        if (!id) return;
        setLoading(true);

        // 1. Cargar datos del alojamiento
        apiFetch<any>(`/api/v1/properties/${id}`)
            .then((data) => {
                setProperty(data);
                if (data.reviews && Array.isArray(data.reviews)) {
                    setReviews(data.reviews);
                }
            })
            .catch((err) => setErrorMsg(err.message || 'Error al cargar la propiedad'))
            .finally(() => setLoading(false));

        // 2. Cargar reseñas paginadas (según Gio: /properties/{id}/reviews)
        apiFetch<any>(`/api/v1/properties/${id}/reviews`)
            .then((data) => {
                const list = Array.isArray(data) ? data : data?.content || [];
                setReviews(list);
            })
            .catch(() => { });
    }, [id]);

    const handleBooking = async (e: React.FormEvent) => {
        e.preventDefault();
        const { data: { session } } = await supabase.auth.getSession();
        if (!session) {
            alert('Debes iniciar sesión para reservar.');
            navigate('/login');
            return;
        }

        setBookingLoading(true);
        try {
            await apiFetch('/api/v1/bookings', {
                method: 'POST',
                body: JSON.stringify({
                    propertyId: id,
                    checkInDate: checkIn,
                    checkOutDate: checkOut,
                }),
            });
            setBookingSuccess(true);
            setTimeout(() => navigate('/my-bookings'), 1500);
        } catch (err: any) {
            alert(err.message || 'Error al procesar la reserva');
        } finally {
            setBookingLoading(false);
        }
    };

    if (loading) return <div className="p-12 text-center text-xs text-[#736E65]">Cargando estancia...</div>;
    if (errorMsg || !property) {
        return (
            <div className="max-w-xl mx-auto p-8 bg-white border border-[#E8E2D8] rounded-3xl text-center space-y-3">
                <AlertCircle className="w-8 h-8 text-[#E05A47] mx-auto" />
                <p className="text-xs text-[#736E65]">{errorMsg || 'Alojamiento no encontrado.'}</p>
            </div>
        );
    }

    const coverImage =
        property.images?.find((img: any) => img.isCover)?.imageUrl ||
        property.images?.[0]?.imageUrl ||
        property.imageUrl ||
        'https://images.unsplash.com/photo-1542314831-068cd1dbfeeb';

    // Gio indicó que el promedio y total vienen en averageRating y reviewCount
    const avg = property.averageRating || property.rating || null;
    const count = property.reviewCount ?? reviews.length;

    return (
        <div className="max-w-5xl mx-auto space-y-8 pb-16 animate-fadeIn">
            {/* Portada y Título */}
            <div className="space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <h1 className="text-3xl font-serif font-bold text-[#1E1C1A]">{property.title}</h1>
                    {avg && (
                        <div className="flex items-center gap-1.5 bg-[#FAF7F2] border border-[#E8E2D8] px-3 py-1.5 rounded-xl text-xs font-bold text-[#1E1C1A]">
                            <Star className="w-4 h-4 fill-amber-400 text-amber-400" />
                            <span>{avg}</span>
                            <span className="text-[#736E65] font-normal">({count} {count === 1 ? 'reseña' : 'reseñas'})</span>
                        </div>
                    )}
                </div>

                <div className="flex items-center gap-2 text-xs text-[#736E65]">
                    <MapPin className="w-4 h-4 text-[#E05A47]" />
                    <span>{property.location?.address || property.address || 'Quetzaltenango'}, {property.location?.municipality || 'Quetzaltenango'}</span>
                </div>
            </div>

            <div className="w-full h-80 sm:h-96 rounded-3xl overflow-hidden shadow-sm border border-[#E8E2D8]">
                <img src={coverImage} alt={property.title} className="w-full h-full object-cover" />
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                <div className="lg:col-span-2 space-y-6">
                    <div className="bg-white border border-[#E8E2D8] rounded-3xl p-6 space-y-4 shadow-sm">
                        <h2 className="text-lg font-serif font-bold text-[#1E1C1A]">Acerca de este lugar</h2>
                        <p className="text-xs text-[#5C574F] leading-relaxed whitespace-pre-line">{property.description}</p>

                        <div className="flex flex-wrap gap-4 pt-4 border-t border-[#F0EBE1] text-xs text-[#5C574F]">
                            <div className="flex items-center gap-1.5"><Users className="w-4 h-4 text-[#E05A47]" /> {property.capacity || 2} personas</div>
                            <div className="flex items-center gap-1.5"><Bed className="w-4 h-4 text-[#E05A47]" /> {property.bedrooms || 1} habitaciones</div>
                            <div className="flex items-center gap-1.5"><Bath className="w-4 h-4 text-[#E05A47]" /> {property.bathrooms || 1} baños</div>
                        </div>
                    </div>

                    <div className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm flex items-center justify-between">
                        <div>
                            <h3 className="font-serif font-bold text-sm text-[#1E1C1A]">¿Tienes dudas sobre la estancia?</h3>
                            <p className="text-[11px] text-[#736E65] mt-0.5">Contacta directamente al anfitrión para coordinar cualquier detalle.</p>
                        </div>
                        <button
                            onClick={() => setShowChat(true)}
                            className="bg-[#FAF7F2] border border-[#E05A47] text-[#E05A47] hover:bg-[#FEEAE6] px-4 py-2 rounded-xl text-xs font-semibold transition flex items-center gap-1.5 shadow-sm"
                        >
                            <MessageSquare className="w-3.5 h-3.5" /> Contactar al Anfitrión
                        </button>
                    </div>

                    {/* SECCIÓN DE RESEÑAS */}
                    <div className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm space-y-4">
                        <div className="flex items-center justify-between border-b border-[#F0EBE1] pb-3">
                            <h3 className="font-serif font-bold text-base text-[#1E1C1A] flex items-center gap-2">
                                <Star className="w-4 h-4 fill-amber-400 text-amber-400" />
                                Reseñas de Huéspedes
                            </h3>
                            <span className="text-xs text-[#736E65]">
                                {reviews.length} {reviews.length === 1 ? 'opinión' : 'opiniones'}
                            </span>
                        </div>

                        {reviews.length === 0 ? (
                            <p className="text-xs text-[#736E65] py-4 text-center">
                                Aún no hay reseñas registradas para este alojamiento.
                            </p>
                        ) : (
                            <div className="space-y-3.5 pt-1">
                                {reviews.map((r, idx) => {
                                    const author = r.reviewerName || r.authorName || 'Huésped verificado';
                                    const starsCount = r.rating || 5;
                                    const commentText = r.comment || 'Excelente estancia.';

                                    return (
                                        <div key={r.id || idx} className="p-3.5 bg-[#FAF7F2] border border-[#E8E2D8] rounded-2xl space-y-2">
                                            <div className="flex items-center justify-between">
                                                <div className="flex items-center gap-2">
                                                    <div className="w-7 h-7 rounded-full bg-[#E8E2D8] flex items-center justify-center text-[#5C574F]">
                                                        <User className="w-3.5 h-3.5" />
                                                    </div>
                                                    <span className="font-semibold text-xs text-[#1E1C1A]">{author}</span>
                                                </div>
                                                <div className="flex items-center gap-0.5">
                                                    {[...Array(5)].map((_, i) => (
                                                        <Star
                                                            key={i}
                                                            className={`w-3.5 h-3.5 ${i < starsCount ? 'fill-amber-400 text-amber-400' : 'text-zinc-300'
                                                                }`}
                                                        />
                                                    ))}
                                                </div>
                                            </div>
                                            <p className="text-xs text-[#5C574F] leading-relaxed pl-9">{commentText}</p>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                </div>

                {/* Panel lateral de Reserva */}
                <div className="lg:col-span-1">
                    <div className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm space-y-5 sticky top-24">
                        <div className="flex items-baseline justify-between border-b border-[#F0EBE1] pb-3">
                            <span className="text-2xl font-bold font-serif text-[#1E1C1A]">Q{property.pricePerNight || property.price}</span>
                            <span className="text-xs text-[#736E65]">/ noche</span>
                        </div>

                        {bookingSuccess ? (
                            <div className="bg-[#F4F9F4] border border-[#CCE2CC] text-[#3A833A] p-4 rounded-2xl flex items-center gap-2 text-xs">
                                <CheckCircle2 className="w-4 h-4 shrink-0" />
                                <span>¡Reserva enviada! Redirigiendo a tus reservaciones...</span>
                            </div>
                        ) : (
                            <form onSubmit={handleBooking} className="space-y-4">
                                <div>
                                    <label className="block text-[11px] font-semibold text-[#1E1C1A] mb-1">Entrada</label>
                                    <input
                                        type="date"
                                        required
                                        value={checkIn}
                                        onChange={(e) => setCheckIn(e.target.value)}
                                        className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                                    />
                                </div>
                                <div>
                                    <label className="block text-[11px] font-semibold text-[#1E1C1A] mb-1">Salida</label>
                                    <input
                                        type="date"
                                        required
                                        value={checkOut}
                                        onChange={(e) => setCheckOut(e.target.value)}
                                        className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                                    />
                                </div>
                                <button
                                    type="submit"
                                    disabled={bookingLoading}
                                    className="w-full bg-[#E05A47] hover:bg-[#C84937] text-white py-3 rounded-xl font-bold text-xs transition shadow-sm disabled:opacity-50"
                                >
                                    {bookingLoading ? 'Enviando solicitud...' : 'Solicitar Reserva'}
                                </button>
                            </form>
                        )}
                    </div>
                </div>
            </div>

            <ChatModal
                isOpen={showChat}
                onClose={() => setShowChat(false)}
                propertyId={id}
            />
        </div>
    );
}