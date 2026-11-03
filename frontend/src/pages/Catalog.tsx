import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { apiFetch } from '../api/client';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { MapPin, Users, Star, Search } from 'lucide-react';

// Corrección de iconos de Leaflet
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
    iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
    iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
    shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

// Icono con precio en el mapa
const createCustomIcon = (price: number) => {
    return L.divIcon({
        className: 'custom-leaflet-marker',
        html: `<div style="background-color: #1E1C1A; color: white; padding: 4px 8px; border-radius: 12px; font-weight: bold; font-size: 11px; box-shadow: 0 2px 4px rgba(0,0,0,0.3); border: 2px solid white; white-space: nowrap;">Q${price}</div>`,
        iconSize: [45, 25],
        iconAnchor: [22, 12],
    });
};

interface Property {
    id: string;
    title: string;
    description: string;
    pricePerNight: number;
    capacity: number;
    bedrooms?: number;
    bathrooms?: number;
    location?: {
        municipality?: string;
        department?: string;
        address?: string;
        latitude?: number;
        longitude?: number;
    };
    municipality?: string;
    latitude?: number;
    longitude?: number;
    imageUrl?: string;
    coverImageUrl?: string;
    cover_image_url?: string;
    coverImage?: string;
    images?: Array<{ imageUrl: string; isCover?: boolean }>;
    averageRating?: number;
    rating?: number;
    reviewCount?: number;
}

export default function Catalog() {
    const [properties, setProperties] = useState<Property[]>([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        setLoading(true);
        apiFetch<any>('/api/v1/properties')
            .then((data) => {
                const list = Array.isArray(data) ? data : data?.content || [];
                setProperties(list);
            })
            .catch(() => setProperties([]))
            .finally(() => setLoading(false));
    }, []);

    // Extracción flexible de imagen real de cada casa
    const getPropertyImage = (p: Property) => {
        if (p.coverImageUrl) return p.coverImageUrl;
        if (p.cover_image_url) return p.cover_image_url;
        if (p.coverImage) return p.coverImage;
        if (p.imageUrl) return p.imageUrl;
        if (p.images && p.images.length > 0) {
            const cover = p.images.find((img) => img.isCover);
            return cover ? cover.imageUrl : p.images[0].imageUrl;
        }
        return 'https://images.unsplash.com/photo-1542314831-068cd1dbfeeb';
    };

    const filteredProperties = properties.filter((p) => {
        const term = searchTerm.toLowerCase();
        const titleMatch = p.title.toLowerCase().includes(term);
        const muni = p.location?.municipality || p.municipality || '';
        const muniMatch = muni.toLowerCase().includes(term);
        return titleMatch || muniMatch;
    });

    // Coordenadas centrales: Parque Central de Quetzaltenango
    const centerLat = 14.8347;
    const centerLng = -91.5181;

    return (
        <div className="space-y-8 pb-16 animate-fadeIn">
            {/* Encabezado y Buscador */}
            <div className="text-center max-w-2xl mx-auto space-y-3 pt-2">
                <h1 className="text-3xl sm:text-4xl font-serif font-bold text-[#1E1C1A] tracking-tight">
                    Encuentra tu refugio en Quetzaltenango
                </h1>
                <p className="text-xs sm:text-sm text-[#736E65]">
                    Casas coloniales, cabañas en el bosque y apartamentos con vistas al Valle de Xelajú
                </p>

                <div className="pt-2 flex items-center justify-center">
                    <div className="relative w-full max-w-md">
                        <Search className="w-4 h-4 text-[#736E65] absolute left-3.5 top-3" />
                        <input
                            type="text"
                            placeholder="Buscar por nombre o municipio (ej. La Esperanza, Salcajá)..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="w-full bg-white border border-[#E8E2D8] rounded-2xl pl-10 pr-4 py-2.5 text-xs text-[#1E1C1A] placeholder-[#736E65] focus:outline-none focus:border-[#E05A47] shadow-sm transition"
                        />
                    </div>
                </div>
            </div>

            {/* MAPA FIJO PERMANENTE */}
            <div className="w-full h-80 sm:h-96 rounded-3xl overflow-hidden border border-[#E8E2D8] shadow-sm z-0 relative">
                <MapContainer
                    center={[centerLat, centerLng]}
                    zoom={13}
                    scrollWheelZoom={false}
                    className="w-full h-full"
                >
                    <TileLayer
                        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                    />
                    {filteredProperties.map((p) => {
                        const lat = p.location?.latitude || p.latitude || centerLat;
                        const lng = p.location?.longitude || p.longitude || centerLng;
                        const img = getPropertyImage(p);

                        return (
                            <Marker
                                key={p.id}
                                position={[lat, lng]}
                                icon={createCustomIcon(p.pricePerNight)}
                            >
                                <Popup className="custom-popup">
                                    <div className="w-48 space-y-2 p-1 text-xs">
                                        <div className="h-24 w-full rounded-xl overflow-hidden">
                                            <img src={img} alt={p.title} className="w-full h-full object-cover" />
                                        </div>
                                        <h4 className="font-serif font-bold text-sm text-[#1E1C1A] line-clamp-1">{p.title}</h4>
                                        <p className="font-bold text-[#E05A47]">
                                            Q{p.pricePerNight} <span className="text-[#736E65] font-normal">/ noche</span>
                                        </p>
                                        <Link
                                            to={`/properties/${p.id}`}
                                            className="block text-center bg-[#E05A47] text-white py-1.5 rounded-lg font-bold text-[11px] hover:bg-[#C84937] transition"
                                        >
                                            Ver Detalle
                                        </Link>
                                    </div>
                                </Popup>
                            </Marker>
                        );
                    })}
                </MapContainer>
            </div>

            {/* LISTADO DE ALOJAMIENTOS */}
            <div>
                <div className="flex items-center justify-between pb-4">
                    <h2 className="font-serif font-bold text-xl text-[#1E1C1A]">Alojamientos Disponibles</h2>
                    <span className="text-xs text-[#736E65]">
                        {filteredProperties.length} {filteredProperties.length === 1 ? 'estancia' : 'estancias'}
                    </span>
                </div>

                {loading ? (
                    <p className="text-center py-16 text-xs text-[#736E65]">Cargando alojamientos disponibles...</p>
                ) : filteredProperties.length === 0 ? (
                    <div className="bg-white border border-[#E8E2D8] rounded-3xl p-12 text-center text-xs text-[#736E65] max-w-md mx-auto">
                        No se encontraron alojamientos con ese criterio de búsqueda.
                    </div>
                ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                        {filteredProperties.map((p) => {
                            const coverImage = getPropertyImage(p);
                            const ratingValue = p.averageRating || p.rating || null;
                            const reviewCount = p.reviewCount ?? 0;
                            const muni = p.location?.municipality || p.municipality || 'Quetzaltenango';

                            return (
                                <Link
                                    key={p.id}
                                    to={`/properties/${p.id}`}
                                    className="group bg-white border border-[#E8E2D8] rounded-3xl overflow-hidden shadow-sm hover:shadow-md transition-all duration-300 flex flex-col justify-between"
                                >
                                    <div>
                                        {/* Foto de Portada con insignia de estrellas */}
                                        <div className="relative aspect-[4/3] w-full overflow-hidden bg-[#FAF7F2]">
                                            <img
                                                src={coverImage}
                                                alt={p.title}
                                                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                                            />

                                            {/* Calificación Flotante */}
                                            {ratingValue ? (
                                                <div className="absolute top-3 right-3 bg-white/95 backdrop-blur-md px-2.5 py-1 rounded-xl flex items-center gap-1 shadow-sm text-xs font-bold text-[#1E1C1A]">
                                                    <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
                                                    <span>{ratingValue.toFixed(1)}</span>
                                                    {reviewCount > 0 && (
                                                        <span className="text-[10px] text-[#736E65] font-normal">({reviewCount})</span>
                                                    )}
                                                </div>
                                            ) : (
                                                <div className="absolute top-3 right-3 bg-white/90 backdrop-blur-md px-2 py-0.5 rounded-lg text-[10px] font-semibold text-[#736E65]">
                                                    Nuevo
                                                </div>
                                            )}
                                        </div>

                                        {/* Datos del Alojamiento */}
                                        <div className="p-5 space-y-2">
                                            <div className="flex items-center justify-between gap-2">
                                                <div className="flex items-center gap-1 text-[11px] text-[#736E65]">
                                                    <MapPin className="w-3.5 h-3.5 text-[#E05A47]" />
                                                    <span>{muni}, Quetzaltenango</span>
                                                </div>

                                                {ratingValue && (
                                                    <div className="flex items-center gap-1 text-xs font-bold text-[#1E1C1A]">
                                                        <Star className="w-3 h-3 fill-amber-400 text-amber-400" />
                                                        <span>{ratingValue.toFixed(1)}</span>
                                                    </div>
                                                )}
                                            </div>

                                            <h3 className="font-serif font-bold text-base text-[#1E1C1A] group-hover:text-[#E05A47] transition line-clamp-1">
                                                {p.title}
                                            </h3>

                                            <p className="text-xs text-[#736E65] line-clamp-2 leading-relaxed">
                                                {p.description}
                                            </p>
                                        </div>
                                    </div>

                                    {/* Footer de Tarjeta */}
                                    <div className="px-5 pb-5 pt-3 border-t border-[#F0EBE1] flex items-center justify-between text-xs text-[#5C574F]">
                                        <div className="flex items-baseline gap-1">
                                            <span className="font-serif font-bold text-base text-[#1E1C1A]">Q{p.pricePerNight}</span>
                                            <span className="text-[11px] text-[#736E65]">/ noche</span>
                                        </div>

                                        <div className="flex items-center gap-1 text-[11px] text-[#736E65]">
                                            <Users className="w-3.5 h-3.5 text-[#E05A47]" />
                                            <span>{p.capacity} huéspedes</span>
                                        </div>
                                    </div>
                                </Link>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
}