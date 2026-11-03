import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { apiFetch } from '../api/client';
import { Home, Edit3, Plus, AlertCircle, CheckCircle2, Users, X } from 'lucide-react';

interface Property {
    id: string;
    title: string;
    description: string;
    pricePerNight: number;
    capacity: number;
    bedrooms: number;
    bathrooms: number;
    status: string;
}

export default function MyProperties() {
    const [properties, setProperties] = useState<Property[]>([]);
    const [loading, setLoading] = useState(true);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);

    // Estado para modal de edición
    const [editingProp, setEditingProp] = useState<Property | null>(null);
    const [editTitle, setEditTitle] = useState('');
    const [editDescription, setEditDescription] = useState('');
    const [editPrice, setEditPrice] = useState('');
    const [editCapacity, setEditCapacity] = useState('');
    const [saving, setSaving] = useState(false);
    const [successMsg, setSuccessMsg] = useState<string | null>(null);

    const fetchProperties = async () => {
        setLoading(true);
        setErrorMsg(null);
        try {
            const data: any = await apiFetch('/api/v1/me/properties').catch(() => apiFetch('/api/v1/properties'));
            const list = Array.isArray(data) ? data : data?.content || [];
            setProperties(list);
        } catch (err: any) {
            setErrorMsg(err.message || 'Error al cargar tus alojamientos.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchProperties();
    }, []);

    const openEditModal = (prop: Property) => {
        setEditingProp(prop);
        setEditTitle(prop.title);
        setEditDescription(prop.description || '');
        setEditPrice(String(prop.pricePerNight || ''));
        setEditCapacity(String(prop.capacity || ''));
        setSuccessMsg(null);
    };

    const handleUpdate = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!editingProp) return;

        setSaving(true);
        setErrorMsg(null);
        try {
            await apiFetch(`/api/v1/properties/${editingProp.id}`, {
                method: 'PUT',
                body: JSON.stringify({
                    title: editTitle.trim(),
                    description: editDescription.trim(),
                    pricePerNight: Number(editPrice),
                    capacity: Number(editCapacity),
                }),
            });

            setSuccessMsg('¡Alojamiento actualizado correctamente!');
            setTimeout(() => {
                setEditingProp(null);
                fetchProperties();
            }, 1000);
        } catch (err: any) {
            alert(err.message || 'Error al actualizar el alojamiento.');
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="max-w-5xl mx-auto space-y-6 pb-16 animate-fadeIn">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between border-b border-[#E8E2D8] pb-4 gap-4">
                <div>
                    <h1 className="text-3xl font-serif font-bold text-[#1E1C1A]">Mis Alojamientos</h1>
                    <p className="text-xs text-[#736E65] mt-1">Administra y edita la información de tus estancias publicadas</p>
                </div>
                <Link
                    to="/host"
                    className="bg-[#E05A47] hover:bg-[#C84937] text-white px-4 py-2.5 rounded-xl text-xs font-bold transition flex items-center gap-1.5 shadow-sm"
                >
                    <Plus className="w-4 h-4" /> Publicar Nueva Estancia
                </Link>
            </div>

            {errorMsg && (
                <div className="bg-[#FEEAE6] border border-[#F5C2B8] text-[#C84937] p-4 rounded-2xl flex items-center gap-3 text-xs">
                    <AlertCircle className="w-5 h-5 shrink-0" />
                    <span>{errorMsg}</span>
                </div>
            )}

            {loading ? (
                <p className="text-center py-12 text-xs text-[#736E65]">Cargando tus estancias...</p>
            ) : properties.length === 0 ? (
                <div className="bg-white border border-[#E8E2D8] rounded-3xl p-12 text-center text-xs text-[#736E65] space-y-3">
                    <Home className="w-8 h-8 text-[#E05A47] mx-auto opacity-60" />
                    <p>Aún no tienes alojamientos registrados.</p>
                    <Link to="/host" className="inline-block text-[#E05A47] font-bold hover:underline">
                        Publicar tu primer alojamiento
                    </Link>
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {properties.map((p) => (
                        <div key={p.id} className="bg-white border border-[#E8E2D8] rounded-2xl p-5 shadow-sm flex flex-col justify-between space-y-3">
                            <div>
                                <div className="flex items-start justify-between gap-2">
                                    <h3 className="font-serif font-bold text-base text-[#1E1C1A]">{p.title}</h3>
                                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full uppercase bg-emerald-100 text-emerald-800">
                                        {p.status || 'PUBLISHED'}
                                    </span>
                                </div>
                                <p className="text-xs text-[#736E65] mt-1 line-clamp-2">{p.description}</p>
                            </div>

                            <div className="flex items-center justify-between text-xs text-[#5C574F] pt-2 border-t border-[#F0EBE1]">
                                <div className="flex items-center gap-3">
                                    <span className="font-bold text-[#1E1C1A]">Q{p.pricePerNight} / noche</span>
                                    <span className="flex items-center gap-1"><Users className="w-3.5 h-3.5 text-[#E05A47]" /> {p.capacity} pers.</span>
                                </div>
                                <button
                                    onClick={() => openEditModal(p)}
                                    className="bg-[#FAF7F2] border border-[#E8E2D8] hover:border-[#E05A47] text-[#1E1C1A] px-3 py-1.5 rounded-xl text-xs font-semibold transition flex items-center gap-1.5 shadow-sm"
                                >
                                    <Edit3 className="w-3.5 h-3.5 text-[#E05A47]" /> Editar
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {/* Modal de Edición */}
            {editingProp && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
                    <div className="bg-white border border-[#E8E2D8] rounded-3xl w-full max-w-lg shadow-2xl p-6 space-y-4 animate-fadeIn">
                        <div className="flex items-center justify-between border-b border-[#F0EBE1] pb-3">
                            <h3 className="font-serif font-bold text-base text-[#1E1C1A]">Editar Alojamiento</h3>
                            <button onClick={() => setEditingProp(null)} className="p-1 rounded-lg text-[#736E65] hover:bg-[#FAF7F2]">
                                <X className="w-4 h-4" />
                            </button>
                        </div>

                        {successMsg && (
                            <div className="bg-[#F4F9F4] border border-[#CCE2CC] text-[#3A833A] p-3 rounded-xl flex items-center gap-2 text-xs">
                                <CheckCircle2 className="w-4 h-4 shrink-0" />
                                <span>{successMsg}</span>
                            </div>
                        )}

                        <form onSubmit={handleUpdate} className="space-y-4 text-xs">
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

                            <div className="flex justify-end gap-2 pt-3 border-t border-[#F0EBE1]">
                                <button
                                    type="button"
                                    onClick={() => setEditingProp(null)}
                                    className="px-4 py-2 rounded-xl text-xs font-semibold text-[#736E65] hover:bg-[#FAF7F2]"
                                >
                                    Cancelar
                                </button>
                                <button
                                    type="submit"
                                    disabled={saving}
                                    className="bg-[#E05A47] hover:bg-[#C84937] text-white px-5 py-2 rounded-xl text-xs font-bold transition shadow-sm disabled:opacity-50"
                                >
                                    {saving ? 'Guardando...' : 'Guardar Cambios'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}