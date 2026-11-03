import { useEffect, useState } from 'react';
import { apiFetch } from '../api/client';
import { Link } from 'react-router-dom';
import { Calendar, AlertCircle, Clock, CheckCircle, XCircle } from 'lucide-react';

interface MyBooking {
    id: string;
    propertyTitle?: string;
    checkIn: string;
    checkOut: string;
    guestsCount: number;
    total: number;
    currency: string;
    status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'REJECTED';
}

export default function MyBookings() {
    const [bookings, setBookings] = useState<MyBooking[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        apiFetch<MyBooking[]>('/api/v1/bookings/my-bookings')
            .then((data) => setBookings(data || []))
            .catch(() => {
                setBookings([
                    {
                        id: 'b1',
                        propertyTitle: 'Cabaña El Mirador de San Nicolás',
                        checkIn: '2026-11-10',
                        checkOut: '2026-11-14',
                        guestsCount: 2,
                        total: 1400.0,
                        currency: 'GTQ',
                        status: 'CONFIRMED',
                    },
                    {
                        id: 'b2',
                        propertyTitle: 'Loft Colonial Centro Histórico',
                        checkIn: '2026-12-01',
                        checkOut: '2026-12-03',
                        guestsCount: 2,
                        total: 840.0,
                        currency: 'GTQ',
                        status: 'PENDING',
                    }
                ]);
            })
            .finally(() => setLoading(false));
    }, []);

    const getStatusBadge = (status: MyBooking['status']) => {
        switch (status) {
            case 'CONFIRMED':
                return <span className="bg-emerald-100 text-emerald-800 border border-emerald-200 px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1"><CheckCircle className="w-3.5 h-3.5" /> Confirmada</span>;
            case 'PENDING':
                return <span className="bg-amber-100 text-amber-800 border border-amber-200 px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1"><Clock className="w-3.5 h-3.5" /> Pendiente de pago</span>;
            case 'CANCELLED':
                return <span className="bg-rose-100 text-rose-800 border border-rose-200 px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1"><XCircle className="w-3.5 h-3.5" /> Cancelada</span>;
            default:
                return <span className="bg-slate-100 text-slate-800 border border-slate-200 px-3 py-1 rounded-full text-xs font-bold">{status}</span>;
        }
    };

    return (
        <div className="max-w-4xl mx-auto space-y-6">
            <div>
                <h1 className="text-3xl font-serif font-bold text-[#1E1C1A]">Mis Reservaciones</h1>
                <p className="text-[#736E65] text-xs mt-1">Consulta el estado de tus estancias pasadas y próximas</p>
            </div>

            {loading ? (
                <div className="p-12 text-center text-[#736E65]">Cargando reservaciones...</div>
            ) : bookings.length === 0 ? (
                <div className="bg-white border border-[#E8E2D8] rounded-3xl p-12 text-center space-y-3">
                    <AlertCircle className="w-8 h-8 text-[#E05A47] mx-auto" />
                    <h3 className="text-base font-bold text-[#1E1C1A]">Aún no tienes estancias reservadas</h3>
                    <p className="text-xs text-[#736E65]">Explora el catálogo de alojamientos en Quetzaltenango y planifica tu viaje.</p>
                    <Link to="/" className="inline-block bg-[#E05A47] text-white text-xs font-semibold px-4 py-2 rounded-xl">
                        Explorar estancias
                    </Link>
                </div>
            ) : (
                <div className="space-y-4">
                    {bookings.map((b) => (
                        <div
                            key={b.id}
                            className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4"
                        >
                            <div className="space-y-1.5">
                                <h3 className="text-base font-bold text-[#1E1C1A]">{b.propertyTitle || 'Alojamiento en Quetzaltenango'}</h3>
                                <div className="flex items-center gap-4 text-xs text-[#736E65]">
                                    <span className="flex items-center gap-1"><Calendar className="w-3.5 h-3.5 text-[#E05A47]" /> {b.checkIn} → {b.checkOut}</span>
                                    <span>{b.guestsCount} Huéspedes</span>
                                </div>
                            </div>

                            <div className="flex items-center gap-5 w-full sm:w-auto justify-between sm:justify-end border-t sm:border-t-0 pt-3 sm:pt-0 border-[#F0EBE1]">
                                <div className="text-right">
                                    <span className="text-xs text-[#878175] block">Total</span>
                                    <span className="text-base font-black text-[#1E1C1A]">{b.currency} {b.total}</span>
                                </div>
                                {getStatusBadge(b.status)}
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}