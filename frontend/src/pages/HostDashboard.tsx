import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { apiFetch } from '../api/client';
import { supabase } from '../services/supabase';
import {
    Home,
    DollarSign,
    MapPin,
    Image,
    CheckCircle2,
    AlertCircle,
    Tag,
    ListChecks,
    ShieldCheck,
    Plus,
    Edit3,
    Users,
    X,
    Building2,
    Trash2,
    Eye,
    EyeOff,
    ExternalLink
} from 'lucide-react';

interface CategoryItem {
    id: string;
    name: string;
}

interface AmenityItem {
    id: string;
    name: string;
}

interface PropertyItem {
    id: string;
    title: string;
    description: string;
    pricePerNight: number;
    capacity: number;
    bedrooms?: number;
    bathrooms?: number;
    status: string;
    imageUrl?: string;
    images?: Array<{ imageUrl: string; isCover?: boolean }>;
}

export default function HostDashboard() {
    const [isHost, setIsHost] = useState<boolean | null>(null);
    const [enablingHost, setEnablingHost] = useState(false);
    const [loadingProps, setLoadingProps] = useState(true);
    const [myProperties, setMyProperties] = useState<PropertyItem[]>([]);

    const [viewMode, setViewMode] = useState<'list' | 'create'>('list');

    // Modal de edición
    const [editingProp, setEditingProp] = useState<PropertyItem | null>(null);
    const [editTitle, setEditTitle] = useState('');
    const [editDescription, setEditDescription] = useState('');
    const [editPrice, setEditPrice] = useState('');
    const [editCapacity, setEditCapacity] = useState('');
    const [editImageUrl, setEditImageUrl] = useState('');
    const [savingEdit, setSavingEdit] = useState(false);

    // Formulario de creación
    const [loading, setLoading] = useState(false);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);
    const [success, setSuccess] = useState(false);

    const [categories, setCategories] = useState<CategoryItem[]>([]);
    const [amenitiesList, setAmenitiesList] = useState<AmenityItem[]>([]);
    const [selectedCategoryId, setSelectedCategoryId] = useState('');
    const [selectedAmenities, setSelectedAmenities] = useState<string[]>([]);

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

    const fetchHostData = async () => {
        setLoadingProps(true);
        const { data: { session } } = await supabase.auth.getSession();
        if (!session) {
            setIsHost(false);
            setLoadingProps(false);
            return;
        }

        try {
            const me: any = await apiFetch('/api/v1/me');
            const roles = Array.isArray(me?.roles) ? me.roles : [];
            const hostRole = roles.includes('HOST') || me?.role === 'HOST';
            setIsHost(hostRole);

            const data: any = await apiFetch('/api/v1/me/properties').catch(() => apiFetch('/api/v1/properties'));
            const list = Array.isArray(data) ? data : data?.content || [];
            setMyProperties(list);
        } catch {
            setIsHost(false);
            setMyProperties([]);
        } finally {
            setLoadingProps(false);
        }
    };

    useEffect(() => {
        fetchHostData();

        apiFetch<any>('/api/v1/property-categories')
            .then((data) => {
                const list = Array.isArray(data) ? data : data?.content || [];
                setCategories(list);
                if (list.length > 0) setSelectedCategoryId(list[0].id);
            })
            .catch(() => setCategories([]));

        apiFetch<any>('/api/v1/amenities')
            .then((data) => {
                const list = Array.isArray(data) ? data : data?.content || [];
                setAmenitiesList(list);
            })
            .catch(() => setAmenitiesList([]));
    }, []);

    const handleBecomeHost = async () => {
        setEnablingHost(true);
        try {
            await apiFetch('/api/v1/me/host', { method: 'POST' });
            setIsHost(true);
        } catch (err: any) {
            alert(err.message || 'Error al habilitar modo anfitrión');
        } finally {
            setEnablingHost(false);
        }
    };

    const openEditModal = (prop: PropertyItem) => {
        setEditingProp(prop);
        setEditTitle(prop.title);
        setEditDescription(prop.description || '');
        setEditPrice(String(prop.pricePerNight || ''));
        setEditCapacity(String(prop.capacity || ''));

        // Buscar la imagen de portada existente
        const cover = prop.images?.find((img) => img.isCover)?.imageUrl || prop.imageUrl || '';
        setEditImageUrl(cover);
    };

    const handleUpdateProperty = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!editingProp) return;

        setSavingEdit(true);
        try {
            // 1. Actualizar campos base
            await apiFetch(`/api/v1/properties/${editingProp.id}`, {
                method: 'PUT',
                body: JSON.stringify({
                    title: editTitle.trim(),
                    description: editDescription.trim(),
                    pricePerNight: Number(editPrice),
                    capacity: Number(editCapacity),
                }),
            });

            // 2. Si se cambió o agregó la URL de imagen, registrarla como nueva portada
            if (editImageUrl.trim()) {
                try {
                    await apiFetch(`/api/v1/properties/${editingProp.id}/images`, {
                        method: 'POST',
                        body: JSON.stringify({
                            imageUrl: editImageUrl.trim(),
                            isCover: true,
                        }),
                    });
                } catch (imgErr: any) {
                    console.warn('No se pudo actualizar la imagen:', imgErr);
                }
            }

            setEditingProp(null);
            await fetchHostData();
        } catch (err: any) {
            alert(err.message || 'Error al actualizar alojamiento');
        } finally {
            setSavingEdit(false);
        }
    };

    const handleToggleStatus = async (id: string, currentStatus: string) => {
        const nextStatus = currentStatus === 'PAUSED' ? 'PUBLISHED' : 'PAUSED';
        const actionLabel = nextStatus === 'PAUSED' ? 'ocultar del catálogo' : 'volver a publicar';

        if (!window.confirm(`¿Deseas ${actionLabel} este alojamiento?`)) return;

        try {
            await apiFetch(`/api/v1/properties/${id}/status`, {
                method: 'PATCH',
                body: JSON.stringify({ status: nextStatus }),
            });
            await fetchHostData();
        } catch (err: any) {
            alert(err.message || 'Error al cambiar estado de la propiedad.');
        }
    };

    const handleDeleteProperty = async (id: string, propertyTitle: string) => {
        const confirmed = window.confirm(
            `¿Estás seguro de eliminar "${propertyTitle}"?\n\nAdvertencia: Esta acción es definitiva. No se podrá restaurar ni reservar.`
        );
        if (!confirmed) return;

        try {
            await apiFetch(`/api/v1/properties/${id}`, { method: 'DELETE' });
            await fetchHostData();
        } catch (err: any) {
            alert(
                err.message ||
                'No se puede eliminar la estancia porque cuenta con reservas pendientes o confirmadas que aún no concluyen.'
            );
        }
    };

    const toggleAmenity = (id: string) => {
        setSelectedAmenities((prev) =>
            prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
        );
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setErrorMsg(null);
        setSuccess(false);

        if (!selectedCategoryId) {
            setErrorMsg('Debes seleccionar una categoría válida.');
            return;
        }

        setLoading(true);

        try {
            try {
                await apiFetch('/api/v1/me/host', { method: 'POST' });
            } catch {
                // Continuar
            }

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
            if (!propertyId) throw new Error('No se recibió el identificador de la propiedad.');

            if (imageUrl.trim()) {
                await apiFetch(`/api/v1/properties/${propertyId}/images`, {
                    method: 'POST',
                    body: JSON.stringify({ imageUrl: imageUrl.trim(), isCover: true }),
                });
            }

            if (selectedAmenities.length > 0) {
                await apiFetch(`/api/v1/properties/${propertyId}/amenities`, {
                    method: 'PUT',
                    body: JSON.stringify({ amenityIds: selectedAmenities }),
                });
            }

            await apiFetch(`/api/v1/properties/${propertyId}/status`, {
                method: 'PATCH',
                body: JSON.stringify({ status: 'PUBLISHED' }),
            });

            setSuccess(true);
            await fetchHostData();
            setViewMode('list');

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
        } catch (err: any) {
            setErrorMsg(err.message || 'Error durante la publicación.');
        } finally {
            setLoading(false);
        }
    };

    if (isHost === false) {
        return (
            <div className="max-w-md mx-auto p-10 text-center bg-white border border-[#E8E2D8] rounded-3xl space-y-4 shadow-sm animate-fadeIn">
                <ShieldCheck className="w-12 h-12 text-[#E05A47] mx-auto" />
                <h2 className="text-xl font-serif font-bold text-[#1E1C1A]">Conviértete en Anfitrión</h2>
                <p className="text-xs text-[#736E65] leading-relaxed">
                    Activa tu rol de anfitrión para comenzar a registrar y publicar tus alojamientos en Quetzaltenango.
                </p>
                <button
                    onClick={handleBecomeHost}
                    disabled={enablingHost}
                    className="w-full bg-[#E05A47] hover:bg-[#C84937] text-white py-3 rounded-xl text-xs font-bold transition shadow-sm disabled:opacity-50"
                >
                    {enablingHost ? 'Activando perfil...' : 'Activar Modo Anfitrión'}
                </button>
            </div>
        );
    }

    return (
        <div className="max-w-4xl mx-auto space-y-6 pb-16 animate-fadeIn">
            {/* Selector de Pestañas */}
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between border-b border-[#E8E2D8] pb-4 gap-4">
                <div>
                    <h1 className="text-3xl font-serif font-bold text-[#1E1C1A]">Panel de Anfitrión</h1>
                    <p className="text-xs text-[#736E65] mt-1">Gestiona, edita tus estancias o publica un nuevo alojamiento</p>
                </div>

                <div className="flex bg-[#E8E2D8]/40 p-1 rounded-2xl text-xs font-semibold">
                    <button
                        onClick={() => setViewMode('list')}
                        className={`flex items-center gap-1.5 px-4 py-2 rounded-xl transition ${viewMode === 'list' ? 'bg-[#E05A47] text-white shadow-sm' : 'text-[#5C574F] hover:text-[#1E1C1A]'
                            }`}
                    >
                        <Building2 className="w-4 h-4" />
                        <span>Mis Alojamientos ({myProperties.length})</span>
                    </button>
                    <button
                        onClick={() => setViewMode('create')}
                        className={`flex items-center gap-1.5 px-4 py-2 rounded-xl transition ${viewMode === 'create' ? 'bg-[#E05A47] text-white shadow-sm' : 'text-[#5C574F] hover:text-[#1E1C1A]'
                            }`}
                    >
                        <Plus className="w-4 h-4" />
                        <span>Publicar Nuevo</span>
                    </button>
                </div>
            </div>

            {/* VISTA 1: LISTADO Y EDICIÓN/PAUSA/BORRADO */}
            {viewMode === 'list' && (
                <div className="space-y-6">
                    {loadingProps ? (
                        <p className="text-center py-12 text-xs text-[#736E65]">Cargando tus alojamientos...</p>
                    ) : myProperties.length === 0 ? (
                        <div className="bg-white border border-[#E8E2D8] rounded-3xl p-12 text-center text-xs text-[#736E65] space-y-3 shadow-sm">
                            <Home className="w-10 h-10 text-[#E05A47] mx-auto opacity-70" />
                            <p className="font-semibold text-sm text-[#1E1C1A]">Aún no tienes alojamientos registrados.</p>
                            <p>Comienza publicando tu primera propiedad en el altiplano guatemalteco.</p>
                            <button
                                onClick={() => setViewMode('create')}
                                className="mt-2 bg-[#E05A47] hover:bg-[#C84937] text-white px-5 py-2.5 rounded-xl text-xs font-bold transition inline-flex items-center gap-2 shadow-sm"
                            >
                                <Plus className="w-4 h-4" /> Publicar mi primer alojamiento
                            </button>
                        </div>
                    ) : (
                        <div className="space-y-6">
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                {myProperties.map((p) => {
                                    const isPaused = p.status === 'PAUSED';

                                    return (
                                        <div key={p.id} className="bg-white border border-[#E8E2D8] rounded-2xl p-5 shadow-sm flex flex-col justify-between space-y-3">
                                            <div>
                                                <div className="flex items-start justify-between gap-2">
                                                    <Link
                                                        to={`/properties/${p.id}`}
                                                        className="font-serif font-bold text-base text-[#1E1C1A] hover:text-[#E05A47] transition flex items-center gap-1.5 group"
                                                        title="Ver ficha pública del alojamiento"
                                                    >
                                                        <span>{p.title}</span>
                                                        <ExternalLink className="w-3.5 h-3.5 opacity-40 group-hover:opacity-100 transition" />
                                                    </Link>
                                                    <span
                                                        className={`text-[10px] font-bold px-2.5 py-0.5 rounded-full uppercase ${isPaused
                                                                ? 'bg-amber-100 text-amber-800'
                                                                : 'bg-emerald-100 text-emerald-800'
                                                            }`}
                                                    >
                                                        {isPaused ? 'OCULTO (PAUSADO)' : p.status || 'PUBLISHED'}
                                                    </span>
                                                </div>
                                                <p className="text-xs text-[#736E65] mt-1 line-clamp-2">{p.description}</p>
                                            </div>

                                            <div className="flex items-center justify-between text-xs text-[#5C574F] pt-3 border-t border-[#F0EBE1]">
                                                <div className="flex items-center gap-3">
                                                    <span className="font-bold text-[#1E1C1A]">Q{p.pricePerNight} / noche</span>
                                                    <span className="flex items-center gap-1">
                                                        <Users className="w-3.5 h-3.5 text-[#E05A47]" /> {p.capacity} pers.
                                                    </span>
                                                </div>

                                                <div className="flex items-center gap-1.5">
                                                    <button
                                                        onClick={() => handleToggleStatus(p.id, p.status)}
                                                        className={`p-2 rounded-xl text-xs font-semibold transition shadow-sm border ${isPaused
                                                                ? 'bg-emerald-50 border-emerald-200 text-emerald-700 hover:bg-emerald-100'
                                                                : 'bg-amber-50 border-amber-200 text-amber-700 hover:bg-amber-100'
                                                            }`}
                                                        title={isPaused ? 'Reanudar publicación' : 'Ocultar temporalmente'}
                                                    >
                                                        {isPaused ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}
                                                    </button>

                                                    <button
                                                        onClick={() => openEditModal(p)}
                                                        className="bg-[#FAF7F2] border border-[#E8E2D8] hover:border-[#E05A47] text-[#1E1C1A] px-3 py-1.5 rounded-xl text-xs font-semibold transition flex items-center gap-1 shadow-sm"
                                                        title="Editar información"
                                                    >
                                                        <Edit3 className="w-3.5 h-3.5 text-[#E05A47]" /> Editar
                                                    </button>

                                                    <button
                                                        onClick={() => handleDeleteProperty(p.id, p.title)}
                                                        className="bg-[#FAF7F2] border border-[#E8E2D8] hover:border-[#C84937] hover:bg-[#FEEAE6] text-[#C84937] p-2 rounded-xl text-xs font-semibold transition shadow-sm"
                                                        title="Eliminar alojamiento"
                                                    >
                                                        <Trash2 className="w-3.5 h-3.5" />
                                                    </button>
                                                </div>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>

                            <div className="pt-4 border-t border-[#E8E2D8] flex justify-center">
                                <button
                                    onClick={() => setViewMode('create')}
                                    className="bg-[#1E1C1A] hover:bg-black text-white px-6 py-3 rounded-2xl text-xs font-bold transition flex items-center gap-2 shadow-sm"
                                >
                                    <Plus className="w-4 h-4 text-[#E05A47]" /> Agregar otra propiedad
                                </button>
                            </div>
                        </div>
                    )}

                    {/* Modal de Edición Rápida con Fotografía */}
                    {editingProp && (
                        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
                            <div className="bg-white border border-[#E8E2D8] rounded-3xl w-full max-w-lg shadow-2xl p-6 space-y-4 animate-fadeIn max-h-[90vh] overflow-y-auto">
                                <div className="flex items-center justify-between border-b border-[#F0EBE1] pb-3">
                                    <h3 className="font-serif font-bold text-base text-[#1E1C1A]">Editar Alojamiento</h3>
                                    <button onClick={() => setEditingProp(null)} className="p-1 rounded-lg text-[#736E65] hover:bg-[#FAF7F2]">
                                        <X className="w-4 h-4" />
                                    </button>
                                </div>

                                <form onSubmit={handleUpdateProperty} className="space-y-4 text-xs">
                                    <div>
                                        <label className="block font-semibold text-[#1E1C1A] mb-1">Título</label>
                                        <input
                                            type="text"
                                            required
                                            value={editTitle}
                                            onChange={(e) => setEditTitle(e.target.value)}
                                            className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                                        />
                                    </div>

                                    <div>
                                        <label className="block font-semibold text-[#1E1C1A] mb-1">Descripción</label>
                                        <textarea
                                            rows={3}
                                            required
                                            value={editDescription}
                                            onChange={(e) => setEditDescription(e.target.value)}
                                            className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl p-3 text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                                        />
                                    </div>

                                    <div className="grid grid-cols-2 gap-3">
                                        <div>
                                            <label className="block font-semibold text-[#1E1C1A] mb-1">Precio / Noche (GTQ)</label>
                                            <input
                                                type="number"
                                                min="1"
                                                required
                                                value={editPrice}
                                                onChange={(e) => setEditPrice(e.target.value)}
                                                className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                                            />
                                        </div>
                                        <div>
                                            <label className="block font-semibold text-[#1E1C1A] mb-1">Capacidad (Personas)</label>
                                            <input
                                                type="number"
                                                min="1"
                                                required
                                                value={editCapacity}
                                                onChange={(e) => setEditCapacity(e.target.value)}
                                                className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                                            />
                                        </div>
                                    </div>

                                    {/* Edición de Fotografía de Portada */}
                                    <div>
                                        <label className="block font-semibold text-[#1E1C1A] mb-1 flex items-center gap-1">
                                            <Image className="w-3.5 h-3.5 text-[#E05A47]" /> URL de Fotografía de Portada
                                        </label>
                                        <input
                                            type="url"
                                            value={editImageUrl}
                                            onChange={(e) => setEditImageUrl(e.target.value)}
                                            placeholder="https://images.unsplash.com/..."
                                            className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                                        />
                                        {editImageUrl && (
                                            <div className="mt-2 h-24 w-full rounded-xl overflow-hidden border border-[#E8E2D8]">
                                                <img src={editImageUrl} alt="Vista previa" className="w-full h-full object-cover" />
                                            </div>
                                        )}
                                    </div>

                                    <div className="flex justify-end gap-2 pt-3 border-t border-[#F0EBE1]">
                                        <button
                                            type="button"
                                            onClick={() => setEditingProp(null)}
                                            className="px-4 py-2 rounded-xl text-xs font-semibold text-[#736E65]"
                                        >
                                            Cancelar
                                        </button>
                                        <button
                                            type="submit"
                                            disabled={savingEdit}
                                            className="bg-[#E05A47] hover:bg-[#C84937] text-white px-5 py-2 rounded-xl text-xs font-bold transition shadow-sm disabled:opacity-50"
                                        >
                                            {savingEdit ? 'Guardando...' : 'Guardar Cambios'}
                                        </button>
                                    </div>
                                </form>
                            </div>
                        </div>
                    )}
                </div>
            )}

            {/* VISTA 2: PUBLICAR NUEVA PROPIEDAD */}
            {viewMode === 'create' && (
                <div className="space-y-6">
                    {success && (
                        <div className="bg-[#F4F9F4] border border-[#CCE2CC] text-[#3A833A] p-4 rounded-2xl flex items-center gap-3 text-xs">
                            <CheckCircle2 className="w-5 h-5 shrink-0" />
                            <span>¡Alojamiento publicado con éxito! Se ha agregado a tu catálogo.</span>
                        </div>
                    )}

                    {errorMsg && (
                        <div className="bg-[#FEEAE6] border border-[#F5C2B8] text-[#C84937] p-4 rounded-2xl flex items-center gap-3 text-xs">
                            <AlertCircle className="w-5 h-5 shrink-0" />
                            <span>{errorMsg}</span>
                        </div>
                    )}

                    <form onSubmit={handleSubmit} className="space-y-6">
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
                            {loading ? 'Procesando los 5 pasos...' : 'Publicar Alojamiento'}
                        </button>
                    </form>
                </div>
            )}
        </div>
    );
}