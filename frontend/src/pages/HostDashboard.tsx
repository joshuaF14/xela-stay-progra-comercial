import { useState } from 'react';
import { apiFetch } from '../api/client';
import { useNavigate } from 'react-router-dom';
import { Home, MapPin, DollarSign, Image as ImageIcon, Sparkles } from 'lucide-react';

export default function HostDashboard() {
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [price, setPrice] = useState(350);
    const [capacity, setCapacity] = useState(4);
    const [bedrooms, setBedrooms] = useState(2);
    const [bathrooms, setBathrooms] = useState(1);
    const [municipality, setMunicipality] = useState('Quetzaltenango');
    const [address, setAddress] = useState('');
    const [latitude, setLatitude] = useState(14.8347);
    const [longitude, setLongitude] = useState(-91.518);
    const [imageUrl, setImageUrl] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleCreateProperty = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);

        const payload = {
            title,
            description,
            price_per_night: Number(price),
            currency: 'GTQ',
            capacity: Number(capacity),
            bedrooms: Number(bedrooms),
            bathrooms: Number(bathrooms),
            status: 'PUBLISHED',
            location: {
                department: 'Quetzaltenango',
                municipality,
                address,
                latitude: Number(latitude),
                longitude: Number(longitude),
            },
            images: imageUrl ? [{ image_url: imageUrl, is_cover: true, display_order: 1 }] : [],
        };

        try {
            await apiFetch('/api/v1/properties', {
                method: 'POST',
                body: JSON.stringify(payload),
            });
            alert('¡Alojamiento publicado con éxito!');
            navigate('/');
        } catch (err: any) {
            alert(`Error al publicar: ${err.message || 'Compruebe su sesión'}`);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="max-w-3xl mx-auto bg-white border border-[#E8E2D8] rounded-3xl p-8 sm:p-10 shadow-[0_4px_24px_-4px_rgba(0,0,0,0.05)] text-[#1E1C1A] animate-fadeIn">
            {/* Cabecera */}
            <div className="flex items-center gap-3 pb-6 border-b border-[#F0EBE1]">
                <div className="w-12 h-12 rounded-2xl bg-[#FDF0EC] text-[#E05A47] flex items-center justify-center shadow-inner">
                    <Sparkles className="w-6 h-6" />
                </div>
                <div>
                    <h1 className="text-2xl font-serif font-bold text-[#1E1C1A]">Publicar Alojamiento</h1>
                    <p className="text-xs text-[#736E65] mt-0.5">Pon tu estancia al alcance de viajeros en Quetzaltenango</p>
                </div>
            </div>

            <form onSubmit={handleCreateProperty} className="space-y-6 mt-6">
                {/* Sección 1: Datos Básicos */}
                <div className="space-y-4">
                    <h2 className="text-xs font-bold tracking-wider text-[#878175] uppercase flex items-center gap-2">
                        <Home className="w-4 h-4 text-[#E05A47]" /> 1. Detalles Principales
                    </h2>

                    <div>
                        <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5">Título del Alojamiento</label>
                        <input
                            type="text"
                            required
                            value={title}
                            onChange={(e) => setTitle(e.target.value)}
                            className="w-full bg-[#FAF7F2] border border-[#E3DDD2] rounded-xl px-4 py-2.5 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47] focus:ring-2 focus:ring-[#FEEAE6] transition-all"
                            placeholder="Ej. Cabaña frente al Volcán Santa María"
                        />
                    </div>

                    <div>
                        <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5">Descripción para Huéspedes</label>
                        <textarea
                            required
                            rows={3}
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            className="w-full bg-[#FAF7F2] border border-[#E3DDD2] rounded-xl px-4 py-2.5 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47] focus:ring-2 focus:ring-[#FEEAE6] transition-all"
                            placeholder="Detalla amenidades, chimenea, vistas panorámicas o acceso seguro..."
                        />
                    </div>
                </div>

                {/* Sección 2: Capacidad y Precio */}
                <div className="space-y-4 pt-4 border-t border-[#F0EBE1]">
                    <h2 className="text-xs font-bold tracking-wider text-[#878175] uppercase flex items-center gap-2">
                        <DollarSign className="w-4 h-4 text-[#E05A47]" /> 2. Capacidad y Tarifa
                    </h2>

                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                        <div>
                            <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5">Precio / Noche (GTQ)</label>
                            <input
                                type="number"
                                min={1}
                                required
                                value={price}
                                onChange={(e) => setPrice(Number(e.target.value))}
                                className="w-full bg-[#FAF7F2] border border-[#E3DDD2] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                        <div>
                            <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5">Capacidad</label>
                            <input
                                type="number"
                                min={1}
                                required
                                value={capacity}
                                onChange={(e) => setCapacity(Number(e.target.value))}
                                className="w-full bg-[#FAF7F2] border border-[#E3DDD2] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                        <div>
                            <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5">Habitaciones</label>
                            <input
                                type="number"
                                min={0}
                                required
                                value={bedrooms}
                                onChange={(e) => setBedrooms(Number(e.target.value))}
                                className="w-full bg-[#FAF7F2] border border-[#E3DDD2] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                        <div>
                            <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5">Baños</label>
                            <input
                                type="number"
                                min={0}
                                step="0.5"
                                required
                                value={bathrooms}
                                onChange={(e) => setBathrooms(Number(e.target.value))}
                                className="w-full bg-[#FAF7F2] border border-[#E3DDD2] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                    </div>
                </div>

                {/* Sección 3: Ubicación Geográfica */}
                <div className="space-y-4 pt-4 border-t border-[#F0EBE1]">
                    <h2 className="text-xs font-bold tracking-wider text-[#878175] uppercase flex items-center gap-2">
                        <MapPin className="w-4 h-4 text-[#E05A47]" /> 3. Ubicación y Coordenadas para el Mapa
                    </h2>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5">Municipio</label>
                            <input
                                type="text"
                                required
                                value={municipality}
                                onChange={(e) => setMunicipality(e.target.value)}
                                className="w-full bg-[#FAF7F2] border border-[#E3DDD2] rounded-xl px-4 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                        <div>
                            <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5">Dirección Exacta</label>
                            <input
                                type="text"
                                required
                                value={address}
                                onChange={(e) => setAddress(e.target.value)}
                                className="w-full bg-[#FAF7F2] border border-[#E3DDD2] rounded-xl px-4 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                                placeholder="Ej. Zona 3, Los Altos"
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5">Latitud</label>
                            <input
                                type="number"
                                step="any"
                                required
                                value={latitude}
                                onChange={(e) => setLatitude(Number(e.target.value))}
                                className="w-full bg-[#FAF7F2] border border-[#E3DDD2] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                        <div>
                            <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5">Longitud</label>
                            <input
                                type="number"
                                step="any"
                                required
                                value={longitude}
                                onChange={(e) => setLongitude(Number(e.target.value))}
                                className="w-full bg-[#FAF7F2] border border-[#E3DDD2] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            />
                        </div>
                    </div>
                </div>

                {/* Sección 4: Foto */}
                <div className="space-y-4 pt-4 border-t border-[#F0EBE1]">
                    <h2 className="text-xs font-bold tracking-wider text-[#878175] uppercase flex items-center gap-2">
                        <ImageIcon className="w-4 h-4 text-[#E05A47]" /> 4. Fotografía de Portada
                    </h2>

                    <div>
                        <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5">URL de Imagen (Unsplash u otro)</label>
                        <input
                            type="url"
                            value={imageUrl}
                            onChange={(e) => setImageUrl(e.target.value)}
                            className="w-full bg-[#FAF7F2] border border-[#E3DDD2] rounded-xl px-4 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                            placeholder="https://images.unsplash.com/photo-..."
                        />
                    </div>
                </div>

                <button
                    type="submit"
                    disabled={loading}
                    className="w-full bg-[#E05A47] hover:bg-[#C84937] text-white font-bold py-3.5 rounded-2xl transition-all shadow-md hover:shadow-lg disabled:opacity-50 text-xs uppercase tracking-wider mt-4"
                >
                    {loading ? 'Publicando...' : 'Guardar y Publicar Alojamiento'}
                </button>
            </form>
        </div>
    );
}