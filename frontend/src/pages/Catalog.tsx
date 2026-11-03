import { useEffect, useState } from 'react';
import type { Property } from '../types';
import { apiFetch } from '../api/client';
import { Link } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import {
    Users, Bed, Bath, Search, MapPin, Star, Heart,
    Sparkles, TreePine, Mountain, Landmark, Flame, Compass, ArrowUpRight
} from 'lucide-react';

delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
    iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
    iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

const CATEGORIAS = [
    { id: 'todas', label: 'Todas las estancias', icon: Sparkles },
    { id: 'cabanas', label: 'Cabañas y Bosque', icon: TreePine },
    { id: 'vistas', label: 'Vistas al Volcán', icon: Mountain },
    { id: 'historico', label: 'Centro Histórico', icon: Landmark },
    { id: 'chimenea', label: 'Con Chimenea', icon: Flame },
];

export default function Catalog() {
    const [properties, setProperties] = useState<Property[]>([]);
    const [loading, setLoading] = useState(true);
    const [filterQuery, setFilterQuery] = useState('');
    const [selectedCategory, setSelectedCategory] = useState('todas');
    const [hoveredPropertyId, setHoveredPropertyId] = useState<string | null>(null);

    useEffect(() => {
        loadProperties();
    }, []);

    const loadProperties = async () => {
        try {
            const data = await apiFetch<Property[]>('/api/v1/properties');
            setProperties(data || []);
        } catch {
            // Mock con categorías y detalles para validar el filtro
            setProperties([
                {
                    id: 'p1',
                    host_id: 'u1',
                    category_id: 'cabanas',
                    location_id: 'l1',
                    title: 'Cabaña El Mirador de San Nicolás',
                    description: 'Cabaña rústica privada con acabados de madera fina, chimenea tradicional y vistas panorámicas hacia el Valle de Palajunoj.',
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
                        { id: 'i1', property_id: 'p1', image_url: 'https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?auto=format&fit=crop&w=800&q=80', display_order: 1, is_cover: true }
                    ]
                },
                {
                    id: 'p2',
                    host_id: 'u2',
                    category_id: 'historico',
                    location_id: 'l2',
                    title: 'Loft Colonial a 2 Cuadras del Parque Central',
                    description: 'Espacio renovado con techos altos de madera y luz natural. A pasos del Teatro Municipal y cafeterías emblemáticas de Xela.',
                    price_per_night: 420.0,
                    currency: 'GTQ',
                    capacity: 2,
                    bedrooms: 1,
                    bathrooms: 1,
                    status: 'PUBLISHED',
                    location: {
                        id: 'l2',
                        department: 'Quetzaltenango',
                        municipality: 'Quetzaltenango',
                        address: 'Zona 1, Centro Histórico',
                        latitude: 14.838,
                        longitude: -91.514,
                    },
                    images: [
                        { id: 'i2', property_id: 'p2', image_url: 'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=800&q=80', display_order: 1, is_cover: true }
                    ]
                },
                {
                    id: 'p3',
                    host_id: 'u3',
                    category_id: 'vistas',
                    location_id: 'l3',
                    title: 'Villa Los Volcanes con Jardín Privado',
                    description: 'Hospedaje campestre amplio con estacionamiento cerrado, terraza mirador y vista directa al Volcán Santa María.',
                    price_per_night: 580.0,
                    currency: 'GTQ',
                    capacity: 6,
                    bedrooms: 3,
                    bathrooms: 2,
                    status: 'PUBLISHED',
                    location: {
                        id: 'l3',
                        department: 'Quetzaltenango',
                        municipality: 'Almolonga',
                        address: 'Camino a los Baños Termales',
                        latitude: 14.815,
                        longitude: -91.495,
                    },
                    images: [
                        { id: 'i3', property_id: 'p3', image_url: 'https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=800&q=80', display_order: 1, is_cover: true }
                    ]
                },
                {
                    id: 'p4',
                    host_id: 'u4',
                    category_id: 'chimenea',
                    location_id: 'l4',
                    title: 'Refugio de Montaña Xelajú',
                    description: 'Estancia climatizada con chimenea de piedra volcánica natural, leña incluida y senderos privados en el bosque.',
                    price_per_night: 490.0,
                    currency: 'GTQ',
                    capacity: 5,
                    bedrooms: 2,
                    bathrooms: 2,
                    status: 'PUBLISHED',
                    location: {
                        id: 'l4',
                        department: 'Quetzaltenango',
                        municipality: 'Quetzaltenango',
                        address: 'Camino a Cerro Quemado',
                        latitude: 14.821,
                        longitude: -91.528,
                    },
                    images: [
                        { id: 'i4', property_id: 'p4', image_url: 'https://images.unsplash.com/photo-1518780664697-55e3ad937233?auto=format&fit=crop&w=800&q=80', display_order: 1, is_cover: true }
                    ]
                }
            ]);
        } finally {
            setLoading(false);
        }
    };

    const filtered = properties.filter((p) => {
        const matchesSearch =
            p.title.toLowerCase().includes(filterQuery.toLowerCase()) ||
            p.location?.municipality.toLowerCase().includes(filterQuery.toLowerCase()) ||
            p.location?.address.toLowerCase().includes(filterQuery.toLowerCase());

        const matchesCategory =
            selectedCategory === 'todas' ||
            p.category_id === selectedCategory ||
            (selectedCategory === 'chimenea' && p.description?.toLowerCase().includes('chimenea'));

        return matchesSearch && matchesCategory;
    });

    return (
        <div className="space-y-10 animate-fade-in pb-12">
            {/* 1. Barra Flotante de Categorías y Búsqueda */}
            <div className="bg-white/80 backdrop-blur-xl border border-[#E8E2D8]/80 rounded-3xl p-6 shadow-[0_8px_30px_rgb(0,0,0,0.04)] transition-all">
                <div className="flex flex-col md:flex-row items-center justify-between gap-5">
                    <div>
                        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#FEEAE6] border border-[#F5C2B8] text-[#E05A47] text-[11px] font-bold tracking-wider uppercase mb-1">
                            <span className="w-1.5 h-1.5 rounded-full bg-[#E05A47] animate-ping"></span>
                            Altiplano Occidental · Guatemala
                        </div>
                        <h1 className="text-3xl sm:text-4xl font-serif font-bold text-[#1E1C1A] tracking-tight">
                            Estancias Exclusivas en Xela
                        </h1>
                    </div>

                    {/* Buscador Píldora con Transición */}
                    <div className="w-full md:w-80 relative group">
                        <Search className="w-4 h-4 text-[#878175] absolute left-4 top-1/2 -translate-y-1/2 group-focus-within:text-[#E05A47] transition-colors" />
                        <input
                            type="text"
                            placeholder="Buscar por zona, volcán o lugar..."
                            value={filterQuery}
                            onChange={(e) => setFilterQuery(e.target.value)}
                            className="w-full bg-[#FAF7F2] border border-[#E3DDD2] rounded-full pl-10 pr-4 py-2.5 text-xs text-[#1E1C1A] placeholder-[#999285] focus:outline-none focus:border-[#E05A47] focus:ring-4 focus:ring-[#FEEAE6] transition-all shadow-inner"
                        />
                    </div>
                </div>

                {/* Categorías con micro-animaciones */}
                <div className="flex items-center gap-3 overflow-x-auto pt-6 mt-6 border-t border-[#F0EBE1] no-scrollbar">
                    {CATEGORIAS.map((cat) => {
                        const Icon = cat.icon;
                        const isSelected = selectedCategory === cat.id;
                        return (
                            <button
                                key={cat.id}
                                onClick={() => setSelectedCategory(cat.id)}
                                className={`flex items-center gap-2 px-5 py-2.5 rounded-full text-xs font-semibold whitespace-nowrap transition-all duration-300 transform active:scale-95 ${isSelected
                                        ? 'bg-[#E05A47] text-white shadow-lg shadow-[#E05A47]/30 scale-105'
                                        : 'bg-[#F3EFE8]/70 hover:bg-[#EAE3D9] text-[#736E65] hover:text-[#1E1C1A]'
                                    }`}
                            >
                                <Icon className={`w-3.5 h-3.5 ${isSelected ? 'text-white' : 'text-[#878175]'}`} />
                                {cat.label}
                            </button>
                        );
                    })}
                </div>
            </div>

            {/* 2. MAPA PANORÁMICO CON DEGRADADO INFERIOR (SE FUNDE HACIA ABAJO) */}
            <div className="relative rounded-3xl overflow-hidden border border-[#E8E2D8]/80 shadow-[0_12px_40px_rgb(0,0,0,0.06)] bg-white">
                <div className="h-[360px] sm:h-[420px] w-full relative z-0">
                    <MapContainer
                        center={[14.8347, -91.518]}
                        zoom={13}
                        scrollWheelZoom={false}
                        style={{ height: '100%', width: '100%' }}
                    >
                        <TileLayer
                            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                        />
                        {filtered.map((item) => (
                            item.location?.latitude && item.location?.longitude && (
                                <Marker
                                    key={item.id}
                                    position={[Number(item.location.latitude), Number(item.location.longitude)]}
                                >
                                    <Popup>
                                        <div className="p-1 font-sans text-xs">
                                            <strong className="block text-sm text-[#1E1C1A] font-bold">{item.title}</strong>
                                            <p className="text-[#E05A47] font-bold mt-1">Q{item.price_per_night} GTQ / noche</p>
                                            <Link to={`/properties/${item.id}`} className="text-[#555047] hover:underline block mt-1">
                                                Ver detalles →
                                            </Link>
                                        </div>
                                    </Popup>
                                </Marker>
                            )
                        ))}
                    </MapContainer>
                </div>

                {/* Degradado inferior para transición suave con las tarjetas */}
                <div className="absolute inset-x-0 bottom-0 h-28 bg-gradient-to-t from-[#FAF7F2] via-[#FAF7F2]/60 to-transparent pointer-events-none z-10"></div>

                {/* Badge flotante en el mapa */}
                <div className="absolute top-5 left-5 z-10 bg-white/90 backdrop-blur-md px-4 py-2 rounded-full border border-[#E8E2D8] shadow-sm flex items-center gap-2 pointer-events-none">
                    <Compass className="w-4 h-4 text-[#E05A47] animate-spin" style={{ animationDuration: '8s' }} />
                    <span className="text-xs font-semibold text-[#1E1C1A]">Explorador Geográfico en Tiempo Real</span>
                </div>
            </div>

            {/* 3. SECCIÓN DE PROPIEDADES EN CUADRÍCULA ELEVADA */}
            <div className="space-y-6 relative -mt-12 z-20">
                <div className="flex items-center justify-between text-xs text-[#736E65] px-3 font-medium">
                    <span className="bg-white/80 backdrop-blur-sm px-3 py-1.5 rounded-full border border-[#E8E2D8]">
                        Mostrando <strong>{filtered.length}</strong> estancias verificadas
                    </span>
                    <span className="text-[#878175]">Tarifas en Quetzales (GTQ)</span>
                </div>

                {loading ? (
                    <div className="bg-white/90 backdrop-blur-md border border-[#E8E2D8] p-16 rounded-3xl text-center text-[#736E65]">
                        Cargando alojamientos...
                    </div>
                ) : filtered.length === 0 ? (
                    <div className="bg-white/90 backdrop-blur-md border border-[#E8E2D8] p-16 rounded-3xl text-center text-[#736E65] space-y-2 shadow-sm">
                        <p className="text-base font-semibold text-[#1E1C1A]">No encontramos estancias en esta categoría</p>
                        <p className="text-xs">Selecciona "Todas las estancias" o realiza otra búsqueda por nombre o lugar.</p>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-7">
                        {filtered.map((item) => {
                            const isHovered = hoveredPropertyId === item.id;
                            return (
                                <div
                                    key={item.id}
                                    onMouseEnter={() => setHoveredPropertyId(item.id)}
                                    onMouseLeave={() => setHoveredPropertyId(null)}
                                    className={`bg-white/90 backdrop-blur-md border rounded-3xl overflow-hidden hover:shadow-[0_20px_40px_-10px_rgba(224,90,71,0.12)] hover:-translate-y-2 transition-all duration-300 flex flex-col justify-between group ${isHovered ? 'border-[#E05A47] ring-2 ring-[#E05A47]/20 shadow-lg' : 'border-[#E8E2D8]'
                                        }`}
                                >
                                    {/* Foto de Portada con Zoom Suave */}
                                    <div className="h-60 overflow-hidden relative">
                                        <img
                                            src={item.images?.[0]?.image_url || 'https://via.placeholder.com/400x300?text=Sin+Imagen'}
                                            alt={item.title}
                                            className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-700 ease-out"
                                        />
                                        <div className="absolute top-3.5 left-3.5 flex items-center gap-1.5">
                                            <span className="bg-white/90 backdrop-blur-md text-[10px] font-bold px-3 py-1 rounded-full text-[#1E1C1A] shadow-sm uppercase tracking-wider border border-white/40">
                                                {item.location?.municipality}
                                            </span>
                                        </div>
                                        <button className="absolute top-3.5 right-3.5 w-8 h-8 rounded-full bg-white/80 backdrop-blur-sm flex items-center justify-center text-[#635E55] hover:text-[#E05A47] hover:bg-white transition-all shadow-sm">
                                            <Heart className="w-4 h-4" />
                                        </button>
                                    </div>

                                    {/* Detalle de la Tarjeta */}
                                    <div className="p-6 flex-1 flex flex-col justify-between space-y-4">
                                        <div>
                                            <div className="flex items-center justify-between mb-1.5">
                                                <span className="text-[11px] font-semibold text-[#878175] flex items-center gap-1">
                                                    <MapPin className="w-3.5 h-3.5 text-[#E05A47]" />
                                                    {item.location?.address}
                                                </span>
                                                <div className="flex items-center gap-1 text-xs font-bold text-[#1E1C1A] bg-[#FAF7F2] px-2 py-0.5 rounded-md border border-[#E8E2D8]">
                                                    <Star className="w-3 h-3 fill-[#E89E28] text-[#E89E28]" />
                                                    4.95
                                                </div>
                                            </div>

                                            <h3 className="text-base font-bold text-[#1E1C1A] group-hover:text-[#E05A47] transition-colors line-clamp-1">
                                                {item.title}
                                            </h3>
                                            <p className="text-xs text-[#736E65] mt-1.5 line-clamp-2 leading-relaxed">
                                                {item.description}
                                            </p>
                                        </div>

                                        <div className="flex items-center gap-4 text-xs text-[#736E65] pt-3 border-t border-[#F2EDE4]">
                                            <span className="flex items-center gap-1"><Users className="w-3.5 h-3.5 text-[#E05A47]" /> {item.capacity} pers.</span>
                                            <span className="flex items-center gap-1"><Bed className="w-3.5 h-3.5 text-[#E05A47]" /> {item.bedrooms} hab.</span>
                                            <span className="flex items-center gap-1"><Bath className="w-3.5 h-3.5 text-[#E05A47]" /> {item.bathrooms} baños</span>
                                        </div>

                                        <div className="flex items-center justify-between pt-2">
                                            <div>
                                                <span className="text-xl font-black text-[#1E1C1A]">Q{item.price_per_night}</span>
                                                <span className="text-xs text-[#878175]"> / noche</span>
                                            </div>
                                            <Link
                                                to={`/properties/${item.id}`}
                                                className="bg-[#FEEAE6] hover:bg-[#E05A47] text-[#E05A47] hover:text-white font-semibold text-xs px-4 py-2 rounded-xl transition-all duration-200 flex items-center gap-1 group-hover:shadow-md"
                                            >
                                                Ver Detalle
                                                <ArrowUpRight className="w-3.5 h-3.5" />
                                            </Link>
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
}