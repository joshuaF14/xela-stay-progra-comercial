import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import type { Property, BookingPayload } from '../types';
import { apiFetch } from '../api/client';
import { supabase } from '../services/supabase';
import { Calendar, Users, Bed, Bath, MapPin, ShieldCheck, CheckCircle2 } from 'lucide-react';

export default function PropertyDetail() {
    const { id } = useParams<{ id: string }>();
    const [property, setProperty] = useState<Property | null>(null);
    const [checkIn, setCheckIn] = useState('');
    const [checkOut, setCheckOut] = useState('');
    const [guestsCount, setGuestsCount] = useState(1);
    const [loading, setLoading] = useState(false);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);
    const [success, setSuccess] = useState(false);
    const navigate = useNavigate();

    useEffect(() => {
        apiFetch<Property>(`/api/v1/properties/${id}`)
            .then((data) => setProperty(data))
            .catch(() => {
                setProperty({
                    id: id || 'p1',
                    host_id: 'u1',
                    category_id: 'c1',
                    location_id: 'l1',
                    title: 'Cabaña Acogedora en San Nicolás',
                    description: 'Disfruta de una experiencia única en Quetzaltenango. Esta estancia cuenta con acabados rústicos, chimenea tradicional, cocina equipada y una terraza con vistas al Valle de Palajunoj. Ideal para descanso o viajes de trabajo.',
                    price_per_night: 350.0,
                    currency: 'GTQ',
                    capacity: 4,
                    bedrooms: 2,
                    bathrooms: 1,
                    status: 'PUBLISHED',
                    location: {
                        id: 'l1',
                        department: 'Quetzaltenango',
                        municipality: 'Quetzaltenango',
                        address: 'Zona 3, Los Altos',
                        latitude: 14.8347,
                        longitude: -91.518,
                    },
                    images: [
                        { id: 'i1', property_id: 'p1', image_url: 'https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?auto=format&fit=crop&w=1200&q=80', display_order: 1, is_cover: true }
                    ]
                });
            });
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
        const payload: BookingPayload = {
            property_id: property!.id,
            check_in: checkIn,
            check_out: checkOut,
            guests_count: Number(guestsCount),
        };

        try {
            await apiFetch('/api/v1/bookings', {
                method: 'POST',
                body: JSON.stringify(payload),
            });
            setSuccess(true);
        } catch (err: any) {
            setErrorMsg(err.message || 'No se pudo crear la reserva en este momento.');
        } finally {
            setLoading(false);
        }
    };

    if (!property) return <div className="p-12 text-center text-[#7A756D]">Cargando detalles de la estancia...</div>;

    return (
        <div className="max-w-5xl mx-auto space-y-8">
            {/* Galería Principal */}
            <div className="rounded-2xl overflow-hidden border border-[#EAE3D9] shadow-sm max-h-[460px] bg-white">
                <img
                    src={property.images?.[0]?.image_url || 'https://via.placeholder.com/800x400?text=Xela+Stay'}
                    alt={property.title}
                    className="w-full h-full object-cover"
                />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-12 gap-8">
                {/* Información Principal */}
                <div className="md:col-span-8 space-y-6">
                    <div>
                        <div className="flex items-center gap-1.5 text-[#E05A47] text-xs font-semibold uppercase tracking-wider mb-1">
                            <MapPin className="w-4 h-4" />
                            {property.location?.address}, {property.location?.municipality}
                        </div>
                        <h1 className="text-3xl font-serif font-bold text-[#1F1E1D]">{property.title}</h1>
                    </div>

                    <div className="grid grid-cols-3 gap-4 py-4 border-y border-[#EAE3D9] text-[#1F1E1D] text-sm">
                        <div className="flex items-center gap-2"><Users className="w-5 h-5 text-[#E05A47]" /> {property.capacity} Huéspedes</div>
                        <div className="flex items-center gap-2"><Bed className="w-5 h-5 text-[#E05A47]" /> {property.bedrooms} Habitaciones</div>
                        <div className="flex items-center gap-2"><Bath className="w-5 h-5 text-[#E05A47]" /> {property.bathrooms} Baños</div>
                    </div>

                    <div>
                        <h3 className="text-lg font-serif font-bold text-[#1F1E1D] mb-2">Sobre este alojamiento</h3>
                        <p className="text-[#5C574F] text-sm leading-relaxed whitespace-pre-line">{property.description}</p>
                    </div>

                    <div className="bg-[#FAF7F0] border border-[#EAE3D9] p-4 rounded-xl flex items-center gap-3 text-xs text-[#7A756D]">
                        <ShieldCheck className="w-5 h-5 text-[#E05A47]" />
                        <span>Reserva protegida por la política de seguridad y disponibilidad oficial de Xela Stay.</span>
                    </div>
                </div>

                {/* Tarjeta Flotante de Reserva */}
                <div className="md:col-span-4">
                    <div className="bg-white border border-[#EAE3D9] rounded-2xl p-6 shadow-sm sticky top-24 space-y-5">
                        <div className="flex items-baseline justify-between border-b border-[#F2ECE4] pb-4">
                            <span className="text-2xl font-serif font-bold text-[#1F1E1D]">
                                Q{property.price_per_night}
                            </span>
                            <span className="text-xs text-[#7A756D]">por noche</span>
                        </div>

                        {success ? (
                            <div className="bg-[#F4F9F4] border border-[#CCE2CC] p-4 rounded-xl text-center space-y-2">
                                <CheckCircle2 className="w-8 h-8 text-[#3A833A] mx-auto" />
                                <h4 className="text-sm font-bold text-[#1F1E1D]">¡Reserva solicitada!</h4>
                                <p className="text-xs text-[#5C574F]">Se ha registrado tu solicitud en estado PENDING. Puedes consultar su estado en tu perfil.</p>
                            </div>
                        ) : (
                            <form onSubmit={handleBooking} className="space-y-4">
                                {errorMsg && (
                                    <div className="bg-[#FEEAE6] border border-[#F5C2B8] text-[#C84937] text-xs p-3 rounded-xl">
                                        {errorMsg}
                                    </div>
                                )}

                                <div className="grid grid-cols-2 gap-2 bg-[#FAF7F0] p-2 rounded-xl border border-[#EAE3D9]">
                                    <div>
                                        <label className="block text-[10px] font-semibold uppercase text-[#7A756D] mb-1 flex items-center gap-1">
                                            <Calendar className="w-3 h-3 text-[#E05A47]" /> Entrada
                                        </label>
                                        <input
                                            type="date"
                                            required
                                            value={checkIn}
                                            onChange={(e) => setCheckIn(e.target.value)}
                                            className="w-full bg-transparent text-xs text-[#1F1E1D] focus:outline-none"
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
                                            className="w-full bg-transparent text-xs text-[#1F1E1D] focus:outline-none"
                                        />
                                    </div>
                                </div>

                                <div>
                                    <label className="block text-xs font-semibold text-[#1F1E1D] mb-1.5 flex items-center gap-1">
                                        <Users className="w-3.5 h-3.5 text-[#E05A47]" /> Huéspedes
                                    </label>
                                    <input
                                        type="number"
                                        min={1}
                                        max={property.capacity}
                                        required
                                        value={guestsCount}
                                        onChange={(e) => setGuestsCount(Number(e.target.value))}
                                        className="w-full bg-white border border-[#EAE3D9] rounded-xl px-3.5 py-2 text-sm text-[#1F1E1D] focus:outline-none focus:border-[#E05A47]"
                                    />
                                </div>

                                <button
                                    type="submit"
                                    disabled={loading}
                                    className="w-full bg-[#E05A47] hover:bg-[#C84937] text-white font-semibold py-3 rounded-xl transition-all shadow-sm hover:shadow disabled:opacity-50 text-sm"
                                >
                                    {loading ? 'Verificando fechas...' : 'Reservar Alojamiento'}
                                </button>
                            </form>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}