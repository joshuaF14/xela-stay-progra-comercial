import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { supabase } from '../services/supabase';
import { Compass, Mail, Lock, AlertCircle, ArrowRight, Sparkles } from 'lucide-react';

export default function Login() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [isSignUp, setIsSignUp] = useState(false);
    const [loading, setLoading] = useState(false);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);
    const navigate = useNavigate();

    const handleAuth = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setErrorMsg(null);

        try {
            if (isSignUp) {
                const { error } = await supabase.auth.signUp({ email, password });
                if (error) throw error;
                alert('Registro exitoso. Ya puedes iniciar sesión con tu cuenta.');
                setIsSignUp(false);
            } else {
                const { error } = await supabase.auth.signInWithPassword({ email, password });
                if (error) throw error;
                navigate('/');
            }
        } catch (err: any) {
            setErrorMsg(err.message || 'Error en la autenticación. Verifica tus credenciales.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-[calc(100vh-140px)] flex items-center justify-center p-4">
            {/* Tarjeta de Autenticación Editorial */}
            <div className="bg-white/95 backdrop-blur-md border border-[#E8E2D8] w-full max-w-md p-8 sm:p-10 rounded-3xl shadow-[0_12px_40px_rgba(0,0,0,0.06)] space-y-8 animate-fadeIn relative overflow-hidden">

                {/* Decoración superior sutil */}
                <div className="absolute top-0 left-0 right-0 h-1.5 bg-gradient-to-r from-[#E05A47] via-[#F5D5C8] to-[#E05A47]"></div>

                {/* Encabezado con Identidad Xela Stay */}
                <div className="text-center space-y-3">
                    <div className="inline-flex w-12 h-12 rounded-2xl bg-gradient-to-br from-[#E05A47] to-[#C84937] items-center justify-center text-white shadow-md shadow-[#E05A47]/20 mb-1">
                        <Compass className="w-6 h-6" />
                    </div>
                    <div>
                        <span className="text-[10px] uppercase font-bold tracking-widest text-[#E05A47] block">
                            Altiplano Occidental · Guatemala
                        </span>
                        <h1 className="font-serif text-2xl sm:text-3xl font-bold text-[#1E1C1A] mt-1">
                            {isSignUp ? 'Crear tu Cuenta' : 'Bienvenido de Vuelta'}
                        </h1>
                    </div>
                    <p className="text-xs text-[#736E65] leading-relaxed max-w-xs mx-auto">
                        {isSignUp
                            ? 'Únete a Xela Stay para reservar y vivir estancias exclusivas en Los Altos.'
                            : 'Ingresa a tu cuenta para gestionar tus reservaciones y consultar al asistente.'}
                    </p>
                </div>

                {/* Mensaje de Error en caso de credenciales incorrectas */}
                {errorMsg && (
                    <div className="bg-[#FEEAE6] border border-[#F5C2B8] text-[#C84937] p-3.5 rounded-2xl text-xs flex items-center gap-2.5">
                        <AlertCircle className="w-4 h-4 shrink-0 text-[#E05A47]" />
                        <span>{errorMsg}</span>
                    </div>
                )}

                {/* Formulario */}
                <form onSubmit={handleAuth} className="space-y-4">
                    <div>
                        <label className="block text-[11px] font-bold uppercase tracking-wider text-[#736E65] mb-1.5">
                            Correo Electrónico
                        </label>
                        <div className="relative">
                            <Mail className="w-4 h-4 text-[#878175] absolute left-3.5 top-3.5" />
                            <input
                                type="email"
                                required
                                placeholder="usuario@xelastay.com"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                className="w-full bg-[#FAF7F2] border border-[#E3DDD2] rounded-2xl pl-10 pr-4 py-2.5 text-xs text-[#1E1C1A] placeholder-[#999285] focus:outline-none focus:border-[#E05A47] focus:ring-2 focus:ring-[#FEEAE6] transition-all"
                            />
                        </div>
                    </div>

                    <div>
                        <label className="block text-[11px] font-bold uppercase tracking-wider text-[#736E65] mb-1.5">
                            Contraseña
                        </label>
                        <div className="relative">
                            <Lock className="w-4 h-4 text-[#878175] absolute left-3.5 top-3.5" />
                            <input
                                type="password"
                                required
                                placeholder="••••••••"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                className="w-full bg-[#FAF7F2] border border-[#E3DDD2] rounded-2xl pl-10 pr-4 py-2.5 text-xs text-[#1E1C1A] placeholder-[#999285] focus:outline-none focus:border-[#E05A47] focus:ring-2 focus:ring-[#FEEAE6] transition-all"
                            />
                        </div>
                    </div>

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full bg-[#E05A47] hover:bg-[#C84937] text-white font-semibold py-3 rounded-2xl transition-all duration-200 shadow-md shadow-[#E05A47]/20 hover:shadow-lg disabled:opacity-50 text-xs flex items-center justify-center gap-2 mt-2"
                    >
                        {loading ? (
                            <span className="animate-pulse">Procesando...</span>
                        ) : isSignUp ? (
                            <>
                                <span>Registrarse en Xela Stay</span>
                                <Sparkles className="w-4 h-4" />
                            </>
                        ) : (
                            <>
                                <span>Iniciar Sesión</span>
                                <ArrowRight className="w-4 h-4" />
                            </>
                        )}
                    </button>
                </form>

                {/* Cambio entre Iniciar Sesión / Registrarse */}
                <div className="pt-2 border-t border-[#F0EBE1] text-center">
                    <button
                        type="button"
                        onClick={() => {
                            setIsSignUp(!isSignUp);
                            setErrorMsg(null);
                        }}
                        className="text-xs text-[#736E65] hover:text-[#1E1C1A] transition-colors"
                    >
                        {isSignUp ? (
                            <>
                                ¿Ya tienes una cuenta?{' '}
                                <span className="font-bold text-[#E05A47] hover:underline">Inicia sesión</span>
                            </>
                        ) : (
                            <>
                                ¿Aún no tienes cuenta?{' '}
                                <span className="font-bold text-[#E05A47] hover:underline">Regístrate aquí</span>
                            </>
                        )}
                    </button>
                </div>
            </div>
        </div>
    );
}