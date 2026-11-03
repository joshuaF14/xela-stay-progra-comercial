import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import type { PageResponse, PropertySummary } from '../types';
import { apiFetch } from '../api/client';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { Search, MapPin, Sparkles, AlertCircle } from 'lucide-react';

const customPin = L.divIcon({
    className: 'custom-leaflet-pin',
    html: `<div style="background-color: #E05A47; width: 26px; height: 26px; border-radius: 50%; border: 2.5px solid white; box-shadow: 0 4px 10px rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; color: white;">
           <span style="font-size: 11px; font-weight: bold;">Q</span>
         </div>`,
    iconSize: [26, 26],
    iconAnchor: [13, 13],
});

export default function Catalog() {
    const [properties, setProperties] = useState<PropertySummary[]>([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [loading, setLoading] = useState(true);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);

    useEffect(() => {
        setLoading(true);
        setErrorMsg(null);
        apiFetch<PageResponse<PropertySummary>>('/api/v1/properties')
            .then((data) => {
                setProperties(data.content || []);
            })
            .catch((err: any) => {
                // CERO datos falsos: muestra el mensaje devuelto por el servidor
                setErrorMsg(err.message || 'Error al conectar con el servidor.');
                setProperties([]);
            })
            .finally(() => setLoading(false));
    }, []);

    const filteredProperties = properties.filter((p) =>
        p.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
        (p.municipality && p.municipality.toLowerCase().includes(searchTerm.toLowerCase()))
    );

    return (
        <div className="space-y-8 animate-fadeIn">
            {/* Buscador Superior */}
            <div className="bg-white border border-[#E8E2D8] rounded-full p-2 pl-6 shadow-sm flex items-center justify-between max-w-2xl mx-auto">
                <div className="flex items-center gap-3 flex-1">
                    <Search className="w-4 h-4 text-[#E05A47]" />
                    <input
                        type="text"
                        placeholder="Buscar por zona, municipio o nombre de estancia..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        className="w-full text-xs bg-transparent focus:outline-none text-[#1E1C1A]"
                    />
                </div>
            </div>

            {/* Mapa Panorámico con Leaflet */}
            <div className="relative w-full h-[360px] rounded-3xl overflow-hidden border border-[#E8E2D8] shadow-sm bg-[#EDE8DE]">
                <MapContainer
                    center={[14.8347, -91.518]}
                    zoom={13}
                    scrollWheelZoom={false}
                    className="w-full h-full z-0"
                >
                    <TileLayer
                        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                    />
                    {filteredProperties.map((p) =>
                        p.latitude && p.longitude ? (
                            <Marker key={p.id} position={[p.latitude, p.longitude]} icon={customPin}>
                                <Popup>
                                    <div className="text-xs p-1">
                                        <span className="font-bold block text-[#1E1C1A]">{p.title}</span>
                                        <span className="text-[#E05A47] font-semibold">{p.currency} {p.pricePerNight} / noche</span>
                                    </div>
                                </Popup>
                            </Marker>
                        ) : null
                    )}
                </MapContainer>
            </div>

            {/* Listado de Alojamientos */}
            {loading ? (
                <div className="p-12 text-center text-[#736E65] text-xs">Cargando alojamientos...</div>
            ) : errorMsg ? (
                <div className="bg-[#FEEAE6] border border-[#F5C2B8] text-[#C84937] p-6 rounded-2xl text-center text-xs space-y-1">
                    <AlertCircle className="w-6 h-6 mx-auto mb-2 text-[#E05A47]" />
                    <span className="font-bold block">No se pudo cargar el catálogo</span>
                    <p>{errorMsg}</p>
                </div>
            ) : filteredProperties.length === 0 ? (
                <div className="bg-white border border-[#E8E2D8] p-12 rounded-3xl text-center space-y-2">
                    <Sparkles className="w-8 h-8 text-[#878175] mx-auto opacity-50" />
                    <h3 className="text-sm font-bold text-[#1E1C1A]">No hay alojamientos todavía</h3>
                    <p className="text-xs text-[#736E65]">Actualmente no hay propiedades publicadas en el catálogo central.</p>
                </div>
            ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                    {filteredProperties.map((p) => (
                        <Link
                            key={p.id}
                            to={`/properties/${p.id}`}
                            className="bg-white border border-[#E8E2D8] rounded-3xl overflow-hidden hover:shadow-lg transition-all group flex flex-col"
                        >
                            <div className="h-48 overflow-hidden bg-slate-100">
                                <img
                                    src={p.coverImageUrl || 'https://via.placeholder.com/600x400?text=Xela+Stay'}
                                    alt={p.title}
                                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                                />
                            </div>
                            <div className="p-5 flex-1 flex flex-col justify-between space-y-3">
                                <div>
                                    <div className="flex items-center gap-1 text-[10px] text-[#E05A47] uppercase font-bold tracking-wider mb-1">
                                        <MapPin className="w-3 h-3" /> {p.municipality || 'Quetzaltenango'}
                                    </div>
                                    <h3 className="font-bold text-sm text-[#1E1C1A] group-hover:text-[#E05A47] transition-colors">{p.title}</h3>
                                </div>
                                <div className="flex items-baseline justify-between border-t border-[#F0EBE1] pt-3">
                                    <span className="text-sm font-black text-[#1E1C1A]">
                                        {p.currency} {p.pricePerNight} <span className="text-[10px] font-normal text-[#736E65]">/ noche</span>
                                    </span>
                                    <span className="text-[11px] text-[#736E65]">{p.capacity} huéspedes</span>
                                </div>
                            </div>
                        </Link>
                    ))}
                </div>
            )}
        </div>
    );
}