import { useEffect, useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { supabase } from '../services/supabase';
import type { Session } from '@supabase/supabase-js';
import { Compass, PlusCircle, BarChart3, LogIn, LogOut, Sparkles } from 'lucide-react';

export default function Navbar() {
    const [session, setSession] = useState<Session | null>(null);
    const navigate = useNavigate();
    const location = useLocation();

    useEffect(() => {
        supabase.auth.getSession().then(({ data: { session } }) => setSession(session));
        const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
            setSession(session);
        });
        return () => subscription.unsubscribe();
    }, []);

    const handleLogout = async () => {
        await supabase.auth.signOut();
        navigate('/login');
    };

    // Helper para detectar la ruta actual
    const isActive = (path: string) => location.pathname === path;

    // Iniciales o primera letra para el avatar del usuario
    const userInitial = session?.user.email ? session.user.email.charAt(0).toUpperCase() : 'U';

    return (
        <nav className="sticky top-0 z-50 bg-[#FDFBF7]/90 backdrop-blur-md border-b border-[#E8E2D8] px-4 sm:px-6 py-3.5 transition-all">
            <div className="max-w-7xl mx-auto flex items-center justify-between">

                {/* Brand / Logotipo */}
                <Link to="/" className="flex items-center gap-3 group">
                    <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-[#E05A47] to-[#C84937] flex items-center justify-center text-white shadow-md shadow-[#E05A47]/25 group-hover:scale-105 transition-all duration-300">
                        <Compass className="w-5 h-5" />
                    </div>
                    <div>
                        <div className="flex items-center gap-1.5">
                            <span className="font-serif text-xl font-bold tracking-tight text-[#1E1C1A]">
                                Xela Stay
                            </span>
                            <span className="w-1.5 h-1.5 rounded-full bg-[#E05A47]"></span>
                        </div>
                        <span className="text-[10px] uppercase tracking-widest text-[#878175] font-semibold block -mt-0.5">
                            Occidente · GT
                        </span>
                    </div>
                </Link>

                {/* Menú de Navegación Central (Píldora Flotante) */}
                <div className="hidden md:flex items-center gap-1 bg-[#F3EFE6] p-1 rounded-full border border-[#E3DDD2] shadow-inner">
                    <Link
                        to="/"
                        className={`px-4 py-2 rounded-full text-xs font-semibold transition-all duration-200 flex items-center gap-1.5 ${isActive('/')
                                ? 'bg-white text-[#1E1C1A] shadow-[0_2px_8px_rgba(0,0,0,0.06)]'
                                : 'text-[#736E65] hover:text-[#1E1C1A] hover:bg-white/50'
                            }`}
                    >
                        <Sparkles className={`w-3.5 h-3.5 ${isActive('/') ? 'text-[#E05A47]' : 'text-[#878175]'}`} />
                        Explorar
                    </Link>

                    <Link
                        to="/host"
                        className={`px-4 py-2 rounded-full text-xs font-semibold transition-all duration-200 flex items-center gap-1.5 ${isActive('/host')
                                ? 'bg-white text-[#1E1C1A] shadow-[0_2px_8px_rgba(0,0,0,0.06)]'
                                : 'text-[#736E65] hover:text-[#1E1C1A] hover:bg-white/50'
                            }`}
                    >
                        <PlusCircle className={`w-3.5 h-3.5 ${isActive('/host') ? 'text-[#E05A47]' : 'text-[#878175]'}`} />
                        Modo Anfitrión
                    </Link>

                    <Link
                        to="/reports"
                        className={`px-4 py-2 rounded-full text-xs font-semibold transition-all duration-200 flex items-center gap-1.5 ${isActive('/reports')
                                ? 'bg-white text-[#1E1C1A] shadow-[0_2px_8px_rgba(0,0,0,0.06)]'
                                : 'text-[#736E65] hover:text-[#1E1C1A] hover:bg-white/50'
                            }`}
                    >
                        <BarChart3 className={`w-3.5 h-3.5 ${isActive('/reports') ? 'text-[#E05A47]' : 'text-[#878175]'}`} />
                        Reportes
                    </Link>
                </div>

                {/* Acciones de Sesión y Perfil */}
                <div className="flex items-center gap-3">
                    {session ? (
                        <div className="flex items-center gap-2 bg-white border border-[#E8E2D8] p-1.5 pl-2 rounded-full shadow-[0_2px_8px_rgba(0,0,0,0.04)]">
                            {/* Avatar con Inicial */}
                            <div className="w-7 h-7 rounded-full bg-[#FEEAE6] text-[#E05A47] font-bold text-xs flex items-center justify-center border border-[#F5C2B8]">
                                {userInitial}
                            </div>

                            {/* Email / Identificador */}
                            <span className="text-xs font-medium text-[#1E1C1A] max-w-[120px] sm:max-w-[160px] truncate pr-1">
                                {session.user.email?.split('@')[0]}
                            </span>

                            {/* Botón Salir */}
                            <button
                                onClick={handleLogout}
                                className="w-7 h-7 rounded-full bg-[#FAF7F2] hover:bg-[#FEEAE6] text-[#736E65] hover:text-[#E05A47] flex items-center justify-center transition-colors"
                                title="Cerrar sesión"
                            >
                                <LogOut className="w-3.5 h-3.5" />
                            </button>
                        </div>
                    ) : (
                        <Link
                            to="/login"
                            className="bg-[#E05A47] hover:bg-[#C84937] text-white px-5 py-2.5 rounded-full text-xs font-semibold transition-all shadow-sm hover:shadow-md flex items-center gap-1.5"
                        >
                            <LogIn className="w-3.5 h-3.5" /> Iniciar Sesión
                        </Link>
                    )}
                </div>

            </div>
        </nav>
    );
}