import { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { supabase } from '../services/supabase';
import { apiFetch } from '../api/client';
import NotificationsModal from './NotificationsModal';
import { Compass, CalendarDays, Home, FileBarChart, LogOut, Bell, ClipboardList, User } from 'lucide-react';

export default function Navbar() {
    const navigate = useNavigate();
    const location = useLocation();

    const [sessionUser, setSessionUser] = useState<any>(null);
    const [isAdmin, setIsAdmin] = useState(false);
    const [hasProperties, setHasProperties] = useState(false);
    const [unreadCount, setUnreadCount] = useState(0);
    const [showNotifModal, setShowNotifModal] = useState(false);

    const fetchUnreadCount = async () => {
        try {
            const data: any = await apiFetch('/api/v1/me/notifications?unreadOnly=true');
            const count = data?.totalElements ?? (Array.isArray(data) ? data.length : 0);
            setUnreadCount(count);
        } catch {
            setUnreadCount(0);
        }
    };

    const fetchUserData = async () => {
        try {
            const me: any = await apiFetch('/api/v1/me');
            const rolesArray: string[] = Array.isArray(me?.roles) ? me.roles : [];
            setIsAdmin(rolesArray.includes('ADMIN') || me?.role === 'ADMIN');

            // Consultar si el anfitrión tiene casas registradas
            const propsData: any = await apiFetch('/api/v1/me/properties').catch(() => null);
            if (propsData) {
                const list = Array.isArray(propsData) ? propsData : propsData?.content || [];
                setHasProperties(list.length > 0);
            } else {
                setHasProperties(rolesArray.includes('HOST') || me?.role === 'HOST');
            }
        } catch {
            setIsAdmin(false);
            setHasProperties(false);
        }
    };

    useEffect(() => {
        supabase.auth.getSession().then(({ data: { session } }) => {
            setSessionUser(session?.user ?? null);
            if (session) {
                fetchUserData();
                fetchUnreadCount();
            }
        });

        const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
            setSessionUser(session?.user ?? null);
            if (session) {
                fetchUserData();
                fetchUnreadCount();
            } else {
                setIsAdmin(false);
                setHasProperties(false);
                setUnreadCount(0);
            }
        });

        return () => subscription.unsubscribe();
    }, []);

    const handleLogout = async () => {
        await supabase.auth.signOut();
        setIsAdmin(false);
        setHasProperties(false);
        navigate('/login');
    };

    const isActive = (path: string) => location.pathname === path;

    // Extraer nombre amigable o correo
    const userIdentifier = sessionUser?.email ? sessionUser.email.split('@')[0] : 'Usuario';

    return (
        <>
            <header className="sticky top-0 z-40 bg-[#FAF7F2]/90 backdrop-blur-md border-b border-[#E8E2D8]">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
                    <Link to="/" className="flex items-center gap-2 group">
                        <div className="w-9 h-9 rounded-xl bg-[#E05A47] flex items-center justify-center text-white font-serif font-black text-lg shadow-sm group-hover:bg-[#C84937] transition">
                            X
                        </div>
                        <div className="flex flex-col">
                            <span className="font-serif font-bold text-lg text-[#1E1C1A] tracking-tight leading-tight">Xela Stay</span>
                            <span className="text-[10px] uppercase font-bold tracking-widest text-[#E05A47]">Altos de Guatemala</span>
                        </div>
                    </Link>

                    <nav className="flex items-center gap-1 sm:gap-2">
                        <Link
                            to="/"
                            className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold transition ${isActive('/') ? 'bg-[#E8E2D8]/60 text-[#E05A47]' : 'text-[#5C574F] hover:text-[#1E1C1A]'
                                }`}
                        >
                            <Compass className="w-4 h-4" />
                            <span>Explorar</span>
                        </Link>

                        {sessionUser && (
                            <Link
                                to="/my-bookings"
                                className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold transition ${isActive('/my-bookings') ? 'bg-[#E8E2D8]/60 text-[#E05A47]' : 'text-[#5C574F] hover:text-[#1E1C1A]'
                                    }`}
                            >
                                <CalendarDays className="w-4 h-4" />
                                <span>Mis Reservas</span>
                            </Link>
                        )}

                        {/* Modo Anfitrión */}
                        <Link
                            to="/host"
                            className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold transition ${isActive('/host') ? 'bg-[#E8E2D8]/60 text-[#E05A47]' : 'text-[#5C574F] hover:text-[#1E1C1A]'
                                }`}
                        >
                            <Home className="w-4 h-4" />
                            <span>Modo Anfitrión</span>
                        </Link>

                        {/* Solicitudes: Solo si tiene alojamientos */}
                        {sessionUser && hasProperties && (
                            <Link
                                to="/host-bookings"
                                className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold transition ${isActive('/host-bookings') ? 'bg-[#E8E2D8]/60 text-[#E05A47]' : 'text-[#5C574F] hover:text-[#1E1C1A]'
                                    }`}
                            >
                                <ClipboardList className="w-4 h-4" />
                                <span>Solicitudes</span>
                            </Link>
                        )}

                        {isAdmin && (
                            <Link
                                to="/reports"
                                className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold transition ${isActive('/reports') ? 'bg-[#E05A47] text-white' : 'text-[#E05A47] bg-[#FEEAE6]'
                                    }`}
                            >
                                <FileBarChart className="w-4 h-4" />
                                <span>Reportes</span>
                            </Link>
                        )}
                    </nav>

                    <div className="flex items-center gap-3">
                        {sessionUser ? (
                            <div className="flex items-center gap-2">
                                {/* Campanita de Notificaciones */}
                                <button
                                    onClick={() => setShowNotifModal(true)}
                                    className="relative p-2 rounded-xl border border-[#E8E2D8] bg-white text-[#5C574F] hover:text-[#E05A47] transition shadow-sm"
                                    title="Notificaciones"
                                >
                                    <Bell className="w-4 h-4" />
                                    {unreadCount > 0 && (
                                        <span className="absolute -top-1 -right-1 bg-[#E05A47] text-white text-[9px] font-bold w-4 h-4 rounded-full flex items-center justify-center">
                                            {unreadCount}
                                        </span>
                                    )}
                                </button>

                                {/* Identificador de Usuario a la par del botón de Salir */}
                                <div className="hidden sm:flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white border border-[#E8E2D8] text-xs font-semibold text-[#1E1C1A] shadow-sm">
                                    <User className="w-3.5 h-3.5 text-[#E05A47]" />
                                    <span className="max-w-[120px] truncate">{userIdentifier}</span>
                                </div>

                                {/* Botón Salir */}
                                <button
                                    onClick={handleLogout}
                                    className="flex items-center gap-1 px-3 py-1.5 rounded-xl border border-[#E8E2D8] bg-white text-xs font-semibold text-[#5C574F] hover:text-[#C84937] hover:border-[#F5C2B8] transition shadow-sm"
                                >
                                    <LogOut className="w-3.5 h-3.5" />
                                    <span className="hidden sm:inline">Salir</span>
                                </button>
                            </div>
                        ) : (
                            <Link
                                to="/login"
                                className="bg-[#E05A47] hover:bg-[#C84937] text-white px-4 py-2 rounded-xl text-xs font-bold transition shadow-sm"
                            >
                                Iniciar Sesión
                            </Link>
                        )}
                    </div>
                </div>
            </header>

            <NotificationsModal
                isOpen={showNotifModal}
                onClose={() => setShowNotifModal(false)}
                onRefreshCount={fetchUnreadCount}
            />
        </>
    );
}