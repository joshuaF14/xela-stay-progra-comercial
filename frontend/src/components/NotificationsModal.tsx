import { useState, useEffect } from 'react';
import { apiFetch } from '../api/client';
import { Bell, Check, X } from 'lucide-react';

interface NotificationItem {
    id: string;
    title?: string;
    message: string;
    read: boolean;
    createdAt: string;
}

interface Props {
    isOpen: boolean;
    onClose: () => void;
    onRefreshCount: () => void;
}

export default function NotificationsModal({ isOpen, onClose, onRefreshCount }: Props) {
    const [notifications, setNotifications] = useState<NotificationItem[]>([]);
    const [loading, setLoading] = useState(false);

    const fetchNotifications = async () => {
        setLoading(true);
        try {
            const data: any = await apiFetch('/api/v1/me/notifications');
            const list = Array.isArray(data) ? data : data?.content || [];
            setNotifications(list);
        } catch {
            setNotifications([]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (isOpen) fetchNotifications();
    }, [isOpen]);

    const markAsRead = async (id: string) => {
        try {
            await apiFetch(`/api/v1/me/notifications/${id}/read`, { method: 'PATCH' });
            setNotifications((prev) =>
                prev.map((n) => (n.id === id ? { ...n, read: true } : n))
            );
            onRefreshCount();
        } catch (err: any) {
            console.error(err);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
            <div className="bg-white border border-[#E8E2D8] rounded-3xl w-full max-w-md shadow-2xl p-6 space-y-4">
                <div className="flex items-center justify-between border-b border-[#F0EBE1] pb-3">
                    <div className="flex items-center gap-2">
                        <Bell className="w-4 h-4 text-[#E05A47]" />
                        <h2 className="font-serif font-bold text-base text-[#1E1C1A]">Notificaciones</h2>
                    </div>
                    <button onClick={onClose} className="p-1 rounded-lg text-[#736E65] hover:bg-[#FAF7F2]">
                        <X className="w-4 h-4" />
                    </button>
                </div>

                <div className="max-h-80 overflow-y-auto space-y-2.5">
                    {loading ? (
                        <p className="text-center text-xs text-[#736E65] py-8">Cargando...</p>
                    ) : notifications.length === 0 ? (
                        <p className="text-center text-xs text-[#736E65] py-8">No tienes notificaciones registradas.</p>
                    ) : (
                        notifications.map((n) => (
                            <div
                                key={n.id}
                                className={`p-3 rounded-xl border text-xs flex items-start justify-between gap-2 ${n.read ? 'bg-[#FAF7F2] border-[#E8E2D8] text-[#5C574F]' : 'bg-[#FEEAE6]/40 border-[#F5C2B8] text-[#1E1C1A]'
                                    }`}
                            >
                                <div>
                                    <p className="font-semibold text-[11px] text-[#1E1C1A]">{n.title || 'Actualización de Reserva'}</p>
                                    <p className="text-[11px] mt-0.5">{n.message}</p>
                                    <span className="text-[10px] text-[#736E65]">{new Date(n.createdAt).toLocaleDateString()}</span>
                                </div>
                                {!n.read && (
                                    <button
                                        onClick={() => markAsRead(n.id)}
                                        title="Marcar como leída"
                                        className="p-1 text-[#3A833A] hover:bg-emerald-100 rounded-md shrink-0"
                                    >
                                        <Check className="w-3.5 h-3.5" />
                                    </button>
                                )}
                            </div>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
}