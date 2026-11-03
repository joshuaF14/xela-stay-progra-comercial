import { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { supabase } from '../services/supabase';
import { Compass, CalendarDays, Home, FileBarChart, LogIn, LogOut, User } from 'lucide-react';

export default function Navbar() {
    const navigate = useNavigate();
    const location = useLocation();

    const [sessionUser, setSessionUser] = useState<any>(null);
    const [isAdmin, setIsAdmin] = useState(false);

    // 1. Escuchar sesión activa y determinar si es ADMIN
    useEffect(() => {
        // Verificar sesión inicial
        supabase.auth.getSession().then(({ data: { session } }) => {
            setSessionUser(session?.user ?? null);
            checkAdminRole(session?.user);
        });

        // Escuchar cambios de inicio/cierre de sesión en tiempo real
        const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
            setSessionUser(session?.user ?? null);
            checkAdminRole(session?.user);
        });

        return () => subscription.unsubscribe();
    }, []);

    const checkAdminRole = (user: any) => {
        if (!user) {
            setIsAdmin(false);
            return;
        }
        // Revisa en user_metadata o app_metadata según cómo guarde Supabase el rol
        const role = user.user_metadata?.role || user.app_metadata?.role;
        setIsAdmin(role === 'ADMIN');
    };

    const handleLogout = async () => {
        await supabase.auth.signOut();
        navigate('/login');
    };

    // Helper para saber si la ruta está activa
    const isActive = (path: string) => location.pathname === path;

    return (
        <header className="sticky top-0 z-50 bg-[#FAF7F2]/90 backdrop-blur-md border-b border-[#E8E2D8]">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">

                {/* LOGO DE LA MARCA */}
                <Link to="/" className="flex items-center gap-2 group">
                    <div className="w-9 h-9 rounded-xl bg-[#E05A47] flex items-center justify-center text-white font-serif font-black text-lg shadow-sm group-hover:bg-[#C84937] transition">
                        X
                    </div>
                    <div className="flex flex-col">
                        <span className="font-serif font-bold text-lg text-[#1E1C1A] tracking-tight leading-tight">
                            Xela Stay
                        </span>
                        <span className="text-[10px] uppercase font-bold tracking-widest text-[#E05A47]">
                            Altos de Guatemala
                        </span>
                    </div>
                </Link>

                {/* ENLACES PRINCIPALES */}
                <nav className="flex items-center gap-1 sm:gap-2">
                    {/* Explorar / Catálogo */}
                    <Link
                        to="/"
                        className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold transition ${isActive('/')
                                ? 'bg-[#E8E2D8]/60 text-[#E05A47]'
                                : 'text-[#5C574F] hover:text-[#1E1C1A] hover:bg-[#E8E2D8]/30'
                            }`}
                    >
                        <Compass className="w-4 h-4" />
                        <span>Explorar</span>
                    </Link>

                    {/* Mis Reservaciones (Visible para usuarios con sesión) */}
                    {sessionUser && (
                        <Link
                            to="/my-bookings"
                            className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold transition ${isActive('/my-bookings')
                                    ? 'bg-[#E8E2D8]/60 text-[#E05A47]'
                                    : 'text-[#5C574F] hover:text-[#1E1C1A] hover:bg-[#E8E2D8]/30'
                                }`}
                        >
                            <CalendarDays className="w-4 h-4" />
                            <span>Mis Reservas</span>
                        </Link>
                    )}

                    {/* Modo Anfitrión */}
                    <Link
                        to="/host"
                        className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold transition ${isActive('/host')
                                ? 'bg-[#E8E2D8]/60 text-[#E05A47]'
                                : 'text-[#5C574F] hover:text-[#1E1C1A] hover:bg-[#E8E2D8]/30'
                            }`}
                    >
                        <Home className="w-4 h-4" />
                        <span>Modo Anfitrión</span>
                    </Link>

                    {/* PESTAÑA DE REPORTES: Solo visible si el rol es ADMIN */}
                    {isAdmin && (
                        <Link
                            to="/reports"
                            className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold transition ${isActive('/reports')
                                    ? 'bg-[#E05A47] text-white'
                                    : 'text-[#E05A47] bg-[#FEEAE6] hover:bg-[#F5C2B8]'
                                }`}
                        >
                            <FileBarChart className="w-4 h-4" />
                            <span>Reportes (Admin)</span>
                        </Link>
                    )}
                </nav>

                {/* ACCIONES DE SESIÓN / USUARIO */}
                <div className="flex items-center gap-3">
                    {sessionUser ? (
                        <div className="flex items-center gap-3">
                            <div className="hidden sm:flex items-center gap-1.5 text-xs text-[#736E65]">
                                <User className="w-3.5 h-3.5 text-[#E05A47]" />
                                <span className="font-semibold text-[#1E1C1A] max-w-[120px] truncate">
                                    {sessionUser.email?.split('@')[0]}
                                </span>
                            </div>
                            <button
                                onClick={handleLogout}
                                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-[#E8E2D8] bg-white text-xs font-semibold text-[#5C574F] hover:text-[#C84937] hover:border-[#F5C2B8] transition shadow-sm"
                            >
                                <LogOut className="w-3.5 h-3.5" />
                                <span className="hidden sm:inline">Cerrar Sesión</span>
                            </button>
                        </div>
                    ) : (
                        <Link
                            to="/login"
                            className="flex items-center gap-1.5 bg-[#E05A47] hover:bg-[#C84937] text-white px-4 py-2 rounded-xl text-xs font-bold transition shadow-sm"
                        >
                            <LogIn className="w-3.5 h-3.5" />
                            <span>Iniciar Sesión</span>
                        </Link>
                    )}
                </div>

            </div>
        </header>
    );
}