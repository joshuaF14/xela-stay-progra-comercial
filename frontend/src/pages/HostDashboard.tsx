import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiFetch } from '../api/client';
import { supabase } from '../services/supabase';
import { Home, DollarSign, MapPin, Image, CheckCircle2, AlertCircle } from 'lucide-react';

export default function HostDashboard() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);
    const [success, setSuccess] = useState(false);

    // UUID de categoría dinámico para el backend
    const [activeCategoryId, setActiveCategoryId] = useState<string>('');

    // Estados del formulario inicializados completamente vacíos
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [pricePerNight, setPricePerNight] = useState('');
    const [capacity, setCapacity] = useState('');
    const [bedrooms, setBedrooms] = useState('');
    const [bathrooms, setBathrooms] = useState('');
    const [municipality, setMunicipality] = useState('');
    const [address, setAddress] = useState('');
    const [latitude, setLatitude] = useState('');
    const [longitude, setLongitude] = useState('');
    const [imageUrl, setImageUrl] = useState('');

    // Obtener un UUID de categoría válido desde las propiedades existentes
    useEffect(() => {
        apiFetch<any>('/api/v1/properties')
            .then((data) => {
                const list = Array.isArray(data) ? data : data?.content || [];
                const firstProp = list[0];
                const existingCatId = firstProp?.categoryId || firstProp?.category?.id;
                if (existingCatId && typeof existingCatId === 'string' && existingCatId.length === 36) {
                    setActiveCategoryId(existingCatId);
                } else {
                    setActiveCategoryId('a0000000-0000-0000-0000-000000000001');
                }
            })
            .catch(() => {
                setActiveCategoryId('a0000000-0000-0000-0000-000000000001');
            });
    }, []);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setErrorMsg(null);
        setSuccess(false);

        // 1. Verificar autenticación con Supabase
        const { data: { session } } = await supabase.auth.getSession();
        if (!session) {
            alert('Debes iniciar sesión para publicar un alojamiento.');
            navigate('/login');
            return;
        }

        setLoading(true);

        // 2. Construir payload convirtiendo campos numéricos de forma estricta
        const payload = {
            title: title.trim(),
            description: description.trim(),
            categoryId: activeCategoryId || 'a0000000-0000-0000-0000-000000000001',
            pricePerNight: Number(pricePerNight),
            currency: 'GTQ',
            capacity: Number(capacity),
            bedrooms: Number(bedrooms),
            bathrooms: Number(bathrooms),
            location: {
                department: 'Quetzaltenango',
                municipality: municipality.trim() || 'Quetzaltenango',
                address: address.trim(),
                latitude: Number(latitude),
                longitude: Number(longitude),
            },
            imageUrls: [imageUrl.trim()],
        };

        try {
            await apiFetch('/api/v1/properties', {
                method: 'POST',
                body: JSON.stringify(payload),
            });

            setSuccess(true);
            // Limpiar formulario tras publicar
            setTitle('');
            setDescription('');
            setPricePerNight('');
            setCapacity('');
            setBedrooms('');
            setBathrooms('');
            setMunicipality('');
            setAddress('');
            setLatitude('');
            setLongitude('');
            setImageUrl('');

            setTimeout(() => {
                navigate('/');
            }, 1500);
        } catch (err: any) {
            setErrorMsg(err.message || 'Error al publicar la propiedad. Revisa los datos ingresados.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="max-w-3xl mx-auto space-y-8 pb-16 animate-fadeIn">
            {/* Encabezado */}
            <div className="border-b border-[#E8E2D8] pb-4">
                <h1 className="text-3xl font-serif font-bold text-[#1E1C1A]">Publicar Alojamiento</h1>
                <p className="text-xs text-[#736E65] mt-1">
                    Pon tu estancia al alcance de viajeros en el altiplano de Quetzaltenango
                </p>
            </div>

            {success && (
                <div className="bg-[#F4F9F4] border border-[#CCE2CC] text-[#3A833A] p-4 rounded-2xl flex items-center gap-3 text-xs">
                    <CheckCircle2 className="w-5 h-5 shrink-0" />
                    <span>¡Alojamiento publicado con éxito! Redirigiendo al catálogo...</span>
                </div>
            )}

            {errorMsg && (
                <div className="bg-[#FEEAE6] border border-[#F5C2B8] text-[#C84937] p-4 rounded-2xl flex items-center gap-3 text-xs">
                    <AlertCircle className="w-5 h-5 shrink-0" />
                    <span>{errorMsg}</span>
                </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-6">
                {/* 1. Detalles Principales */}
                <div className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm space-y-4">
                    <h3 className="font-serif font-bold text-base text-[#1E1C1A] flex items-center gap-2">
                        <Home className="w-4 h-4 text-[#E05A47]" /> 1. Detalles Principales
                    </h3>

                    <div>
                        <label className="block text-xs font-semibold text-[#1E1C1A] mb-1">Título del Alojamiento</label>
                        <input
                            type="text"
                            required
                            value={title}
                            onChange={(e) => setTitle(e.target.value)}
                            className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3.5 py-2.5 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                        />
                    </div>

                    <div>
                        <label className="block text-xs font-semibold text-[#1E1C1A] mb-1">Descripción para Huéspedes</label>
                        <textarea
                            rows={3}
                            required
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl p-3 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                        />
                    </div>
                </div>

                {/* 2. Capacidad y Tarifa */}
                <div className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm space-y-4">
                    <h3 className="font-serif font-bold text-base text-[#1E1C1A] flex items-center gap-2">
                        <DollarSign className="w-4 h-4 text-[#E05A47]" /> 2. Capacidad y Tarifa
                    </h3>

                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                        <div>
                            <label className="block text-[11px] font-semibold text-[#736E65] mb-1">Precio / Noche (GTQ)</label>
                            <input
                                type="number"
                                min="50"
                                required
                                value={pricePerNight}
                                onChange={(e) => setPricePerNight(e.target.value)}
                                className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                        <div>
                            <label className="block text-[11px] font-semibold text-[#736E65] mb-1">Capacidad</label>
                            <input
                                type="number"
                                min="1"
                                required
                                value={capacity}
                                onChange={(e) => setCapacity(e.target.value)}
                                className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                        <div>
                            <label className="block text-[11px] font-semibold text-[#736E65] mb-1">Habitaciones</label>
                            <input
                                type="number"
                                min="1"
                                required
                                value={bedrooms}
                                onChange={(e) => setBedrooms(e.target.value)}
                                className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                        <div>
                            <label className="block text-[11px] font-semibold text-[#736E65] mb-1">Baños</label>
                            <input
                                type="number"
                                min="1"
                                required
                                value={bathrooms}
                                onChange={(e) => setBathrooms(e.target.value)}
                                className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                    </div>
                </div>

                {/* 3. Ubicación y Coordenadas */}
                <div className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm space-y-4">
                    <h3 className="font-serif font-bold text-base text-[#1E1C1A] flex items-center gap-2">
                        <MapPin className="w-4 h-4 text-[#E05A47]" /> 3. Ubicación y Coordenadas para el Mapa
                    </h3>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-[11px] font-semibold text-[#736E65] mb-1">Municipio</label>
                            <input
                                type="text"
                                required
                                value={municipality}
                                onChange={(e) => setMunicipality(e.target.value)}
                                className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                        <div>
                            <label className="block text-[11px] font-semibold text-[#736E65] mb-1">Dirección Exacta</label>
                            <input
                                type="text"
                                required
                                value={address}
                                onChange={(e) => setAddress(e.target.value)}
                                className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                        <div>
                            <label className="block text-[11px] font-semibold text-[#736E65] mb-1">Latitud</label>
                            <input
                                type="text"
                                required
                                value={latitude}
                                onChange={(e) => setLatitude(e.target.value)}
                                className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                        <div>
                            <label className="block text-[11px] font-semibold text-[#736E65] mb-1">Longitud</label>
                            <input
                                type="text"
                                required
                                value={longitude}
                                onChange={(e) => setLongitude(e.target.value)}
                                className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                    </div>
                </div>

                {/* 4. Fotografía de Portada */}
                <div className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm space-y-4">
                    <h3 className="font-serif font-bold text-base text-[#1E1C1A] flex items-center gap-2">
                        <Image className="w-4 h-4 text-[#E05A47]" /> 4. Fotografía de Portada
                    </h3>

                    <div>
                        <label className="block text-[11px] font-semibold text-[#736E65] mb-1">URL de Imagen</label>
                        <input
                            type="url"
                            required
                            value={imageUrl}
                            onChange={(e) => setImageUrl(e.target.value)}
                            className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3.5 py-2.5 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                        />
                    </div>
                </div>

                <button
                    type="submit"
                    disabled={loading}
                    className="w-full bg-[#E05A47] hover:bg-[#C84937] text-white font-semibold py-3.5 rounded-2xl transition-all shadow-md shadow-[#E05A47]/20 disabled:opacity-50 text-xs font-bold"
                >
                    {loading ? 'Publicando en Supabase...' : 'Publicar Alojamiento'}
                </button>
            </form>
        </div>
    );
}