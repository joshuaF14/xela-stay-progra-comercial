import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { apiFetch } from '../api/client';
import type { PropertySummary, PageResponse } from '../types';
import { MapPin, Users, AlertCircle, Sparkles } from 'lucide-react';

// Solución al problema de los iconos invisibles en Leaflet + Vite
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
    iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
    iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

// Icono personalizado terracota para Xela Stay
const customIcon = new L.Icon({
    iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
    iconSize: [25, 41],
    iconAnchor: [12, 41],
    popupAnchor: [1, -34],
    shadowSize: [41, 41],
});

export default function Catalog() {
    const [properties, setProperties] = useState<PropertySummary[]>([]);
    const [loading, setLoading] = useState(true);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);
    const navigate = useNavigate();

    useEffect(() => {
        setLoading(true);
        setErrorMsg(null);

        // Endpoint tolerante a PageResponse o arreglo directo
        apiFetch<PageResponse<PropertySummary> | PropertySummary[]>('/api/v1/properties')
            .then((data: any) => {
                if (Array.isArray(data)) {
                    setProperties(data);
                } else if (data && Array.isArray(data.content)) {
                    setProperties(data.content);
                } else {
                    setProperties([]);
                }
            })
            .catch((err: any) => {
                setErrorMsg(err.message || 'Error al conectar con el catálogo de alojamientos.');
                setProperties([]);
            })
            .finally(() => setLoading(false));
    }, []);

    const safeProperties = Array.isArray(properties) ? properties : [];

    return (
        <div className="space-y-8 animate-fadeIn pb-12">
            {/* Encabezado Editorial */}
            <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 border-b border-[#E8E2D8] pb-6">
                <div>
                    <span className="text-[11px] uppercase font-bold tracking-widest text-[#E05A47] flex items-center gap-1.5">
                        <Sparkles className="w-3.5 h-3.5" /> Estancias Seleccionadas en Los Altos
                    </span>
                    <h1 className="text-3xl sm:text-4xl font-serif font-bold text-[#1E1C1A] mt-1">
                        Explorar Alojamientos
                    </h1>
                </div>
                <div className="text-xs text-[#736E65]">
                    Mostrando <span className="font-bold text-[#1E1C1A]">{safeProperties.length}</span> estancias activas
                </div>
            </div>

            {/* Mapa de Quetzaltenango con pines */}
            <div className="h-[380px] sm:h-[440px] w-full rounded-3xl overflow-hidden border border-[#E8E2D8] shadow-sm relative z-0">
                <MapContainer
                    center={[14.8347, -91.5180]} // Centro en Quetzaltenango
                    zoom={13}
                    scrollWheelZoom={false}
                    className="h-full w-full"
                >
                    <TileLayer
                        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                    />

                    {safeProperties.map((p: any) => {
                        const lat = Number(p.latitude ?? p.location?.latitude);
                        const lng = Number(p.longitude ?? p.location?.longitude);

                        if (isNaN(lat) || isNaN(lng) || lat === 0 || lng === 0) return null;

                        return (
                            <Marker key={p.id} position={[lat, lng]} icon={customIcon}>
                                <Popup>
                                    <div className="p-1 space-y-1.5 text-xs">
                                        <img
                                            src={p.coverImageUrl || 'https://via.placeholder.com/200x120?text=Xela+Stay'}
                                            alt={p.title}
                                            className="w-full h-24 object-cover rounded-xl mb-1"
                                        />
                                        <h4 className="font-serif font-bold text-[#1E1C1A]">{p.title}</h4>
                                        <p className="text-[#E05A47] font-black">{p.currency || 'GTQ'} {p.pricePerNight} / noche</p>
                                        <button
                                            onClick={() => navigate(`/properties/${p.id}`)}
                                            className="w-full bg-[#1E1C1A] text-white py-1 rounded-lg text-[10px] font-bold"
                                        >
                                            Ver detalles
                                        </button>
                                    </div>
                                </Popup>
                            </Marker>
                        );
                    })}
                </MapContainer>
            </div>

            {/* Cuadrícula de Alojamientos */}
            {loading ? (
                <div className="p-16 text-center text-xs text-[#736E65]">Cargando catálogo oficial...</div>
            ) : errorMsg ? (
                <div className="bg-[#FEEAE6] border border-[#F5C2B8] text-[#C84937] p-8 rounded-3xl text-center text-xs space-y-2 max-w-md mx-auto">
                    <AlertCircle className="w-8 h-8 mx-auto text-[#E05A47]" />
                    <h3 className="font-bold text-sm">No se pudo cargar el catálogo</h3>
                    <p>{errorMsg}</p>
                </div>
            ) : safeProperties.length === 0 ? (
                <div className="bg-white border border-[#E8E2D8] rounded-3xl p-16 text-center text-xs text-[#736E65]">
                    No hay alojamientos publicados todavía en Quetzaltenango.
                </div>
            ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
                    {safeProperties.map((p: any) => (
                        <div
                            key={p.id}
                            onClick={() => navigate(`/properties/${p.id}`)}
                            className="bg-white border border-[#E8E2D8] rounded-3xl overflow-hidden shadow-sm hover:shadow-md transition-all cursor-pointer group flex flex-col justify-between"
                        >
                            <div>
                                <div className="aspect-[4/3] w-full overflow-hidden bg-slate-100">
                                    <img
                                        src={p.coverImageUrl || 'https://via.placeholder.com/400x300?text=Xela+Stay'}
                                        alt={p.title}
                                        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                                    />
                                </div>
                                <div className="p-4 space-y-2">
                                    <div className="flex items-center gap-1 text-[11px] font-bold text-[#E05A47] uppercase tracking-wider">
                                        <MapPin className="w-3.5 h-3.5" />
                                        {p.municipality || p.location?.municipality || 'Quetzaltenango'}
                                    </div>
                                    <h3 className="font-serif font-bold text-[#1E1C1A] text-base group-hover:text-[#E05A47] transition-colors line-clamp-1">
                                        {p.title}
                                    </h3>
                                    <div className="flex items-center gap-1 text-xs text-[#736E65]">
                                        <Users className="w-3.5 h-3.5 text-[#878175]" />
                                        <span>Hasta {p.capacity || 2} huéspedes</span>
                                    </div>
                                </div>
                            </div>

                            <div className="px-4 pb-4 pt-2 border-t border-[#F0EBE1] flex items-baseline justify-between">
                                <div>
                                    <span className="text-base font-bold text-[#1E1C1A]">
                                        {p.currency || 'GTQ'} {p.pricePerNight}
                                    </span>
                                    <span className="text-[11px] text-[#736E65]"> / noche</span>
                                </div>
                                <span className="text-[11px] font-bold text-[#E05A47] underline">Ver estancia</span>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}