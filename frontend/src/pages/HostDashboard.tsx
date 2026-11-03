import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiFetch } from '../api/client';
import { supabase } from '../services/supabase';
import { Home, DollarSign, MapPin, Image, CheckCircle2, AlertCircle, Tag, ListChecks } from 'lucide-react';

interface CategoryItem {
    id: string;
    name: string;
}

interface AmenityItem {
    id: string;
    name: string;
}

export default function HostDashboard() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);
    const [success, setSuccess] = useState(false);

    // Catálogos dinámicos
    const [categories, setCategories] = useState<CategoryItem[]>([]);
    const [amenitiesList, setAmenitiesList] = useState<AmenityItem[]>([]);
    const [selectedCategoryId, setSelectedCategoryId] = useState('');
    const [selectedAmenities, setSelectedAmenities] = useState<string[]>([]);

    // Campos del formulario
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

    // 1. Cargar categorías y amenidades desde los endpoints reales
    useEffect(() => {
        // Cargar categorías
        apiFetch<any>('/api/v1/property-categories')
            .then((data) => {
                const list = Array.isArray(data) ? data : data?.content || [];
                setCategories(list);
                if (list.length > 0) setSelectedCategoryId(list[0].id);
            })
            .catch(() => {
                // Fallback a categories si el backend usa el nombre corto
                apiFetch<any>('/api/v1/categories')
                    .then((data) => {
                        const list = Array.isArray(data) ? data : data?.content || [];
                        setCategories(list);
                        if (list.length > 0) setSelectedCategoryId(list[0].id);
                    })
                    .catch(() => setCategories([]));
            });

        // Cargar amenidades
        apiFetch<any>('/api/v1/amenities')
            .then((data) => {
                const list = Array.isArray(data) ? data : data?.content || [];
                setAmenitiesList(list);
            })
            .catch(() => setAmenitiesList([]));
    }, []);

    const toggleAmenity = (id: string) => {
        setSelectedAmenities((prev) =>
            prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
        );
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setErrorMsg(null);
        setSuccess(false);

        const { data: { session } } = await supabase.auth.getSession();
        if (!session) {
            alert('Debes iniciar sesión para publicar un alojamiento.');
            navigate('/login');
            return;
        }

        if (!selectedCategoryId) {
            setErrorMsg('Debes seleccionar una categoría válida.');
            return;
        }

        setLoading(true);

        try {
            // PASO 1: Habilitar rol de anfitrión al usuario actual
            try {
                await apiFetch('/api/v1/me/host', { method: 'POST' });
            } catch (err: any) {
                // Si ya es anfitrión, continúa sin fallar
            }

            // PASO 2: Crear la propiedad con categoryId real
            const propertyPayload = {
                title: title.trim(),
                description: description.trim(),
                categoryId: selectedCategoryId,
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
            };

            const createdProperty: any = await apiFetch('/api/v1/properties', {
                method: 'POST',
                body: JSON.stringify(propertyPayload),
            });

            const propertyId = createdProperty?.id || createdProperty?.propertyId;
            if (!propertyId) throw new Error('No se recibió el identificador de la propiedad creada.');

            // PASO 3: Asociar imagen de portada
            if (imageUrl.trim()) {
                await apiFetch(`/api/v1/properties/${propertyId}/images`, {
                    method: 'POST',
                    body: JSON.stringify({
                        imageUrl: imageUrl.trim(),
                        isCover: true,
                    }),
                });
            }

            // PASO 4: Asociar amenidades seleccionadas
            if (selectedAmenities.length > 0) {
                await apiFetch(`/api/v1/properties/${propertyId}/amenities`, {
                    method: 'PUT',
                    body: JSON.stringify({ amenityIds: selectedAmenities }),
                });
            }

            // PASO 5: Publicar la propiedad (de DRAFT a PUBLISHED)
            await apiFetch(`/api/v1/properties/${propertyId}/status`, {
                method: 'PATCH',
                body: JSON.stringify({ status: 'PUBLISHED' }),
            });

            // Confirmar éxito solo tras completar los 5 pasos
            setSuccess(true);
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
            setSelectedAmenities([]);

            setTimeout(() => {
                navigate('/');
            }, 1500);
        } catch (err: any) {
            setErrorMsg(err.message || 'Error durante la secuencia de publicación del alojamiento.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="max-w-3xl mx-auto space-y-8 pb-16 animate-fadeIn">
            <div className="border-b border-[#E8E2D8] pb-4">
                <h1 className="text-3xl font-serif font-bold text-[#1E1C1A]">Publicar Alojamiento</h1>
                <p className="text-xs text-[#736E65] mt-1">
                    Pon tu estancia al alcance de viajeros en el altiplano de Quetzaltenango
                </p>
            </div>

            {success && (
                <div className="bg-[#F4F9F4] border border-[#CCE2CC] text-[#3A833A] p-4 rounded-2xl flex items-center gap-3 text-xs">
                    <CheckCircle2 className="w-5 h-5 shrink-0" />
                    <span>¡Alojamiento publicado con éxito en estado PUBLISHED! Redirigiendo al catálogo...</span>
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

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
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
                            <label className="block text-xs font-semibold text-[#1E1C1A] mb-1 flex items-center gap-1">
                                <Tag className="w-3.5 h-3.5 text-[#E05A47]" /> Categoría
                            </label>
                            <select
                                required
                                value={selectedCategoryId}
                                onChange={(e) => setSelectedCategoryId(e.target.value)}
                                className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3.5 py-2.5 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            >
                                {categories.length === 0 ? (
                                    <option value="">Cargando categorías...</option>
                                ) : (
                                    categories.map((c) => (
                                        <option key={c.id} value={c.id}>
                                            {c.name}
                                        </option>
                                    ))
                                )}
                            </select>
                        </div>
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
                                min="1"
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

                {/* 3. Amenidades Disponibles */}
                {amenitiesList.length > 0 && (
                    <div className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm space-y-4">
                        <h3 className="font-serif font-bold text-base text-[#1E1C1A] flex items-center gap-2">
                            <ListChecks className="w-4 h-4 text-[#E05A47]" /> 3. Amenidades y Servicios
                        </h3>
                        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
                            {amenitiesList.map((amenity) => (
                                <label
                                    key={amenity.id}
                                    className={`flex items-center gap-2 p-2.5 rounded-xl border text-xs cursor-pointer transition ${selectedAmenities.includes(amenity.id)
                                            ? 'bg-[#FEEAE6] border-[#E05A47] text-[#E05A47] font-semibold'
                                            : 'bg-[#FAF7F2] border-[#E8E2D8] text-[#1E1C1A]'
                                        }`}
                                >
                                    <input
                                        type="checkbox"
                                        checked={selectedAmenities.includes(amenity.id)}
                                        onChange={() => toggleAmenity(amenity.id)}
                                        className="accent-[#E05A47]"
                                    />
                                    <span>{amenity.name}</span>
                                </label>
                            ))}
                        </div>
                    </div>
                )}

                {/* 4. Ubicación y Coordenadas */}
                <div className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm space-y-4">
                    <h3 className="font-serif font-bold text-base text-[#1E1C1A] flex items-center gap-2">
                        <MapPin className="w-4 h-4 text-[#E05A47]" /> 4. Ubicación y Coordenadas
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

                {/* 5. Fotografía de Portada */}
                <div className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm space-y-4">
                    <h3 className="font-serif font-bold text-base text-[#1E1C1A] flex items-center gap-2">
                        <Image className="w-4 h-4 text-[#E05A47]" /> 5. Fotografía de Portada
                    </h3>

                    <div>
                        <label className="block text-[11px] font-semibold text-[#736E65] mb-1">URL directa de la fotografía</label>
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
                    {loading ? 'Procesando los 5 pasos de publicación...' : 'Publicar Alojamiento'}
                </button>
            </form>
        </div>
    );
}