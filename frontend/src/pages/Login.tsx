import { useState } from 'react';
import { supabase } from '../services/supabase';
import { useNavigate } from 'react-router-dom';

export default function Login() {
    const [isRegister, setIsRegister] = useState(false);
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [fullName, setFullName] = useState('');
    const [errorMsg, setErrorMsg] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setErrorMsg(null);
        setLoading(true);

        try {
            if (isRegister) {
                const { error } = await supabase.auth.signUp({
                    email,
                    password,
                    options: {
                        data: { full_name: fullName }
                    }
                });
                if (error) throw error;
                alert('Registro completado. Ya puedes iniciar sesión con tus credenciales.');
                setIsRegister(false);
            } else {
                const { error } = await supabase.auth.signInWithPassword({
                    email,
                    password,
                });
                if (error) throw error;
                navigate('/');
            }
        } catch (err: any) {
            setErrorMsg(err.message || 'Ocurrió un error en la autenticación');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="max-w-md mx-auto mt-12 bg-slate-900 border border-slate-800 rounded-xl p-8 shadow-xl text-white">
            <h2 className="text-2xl font-bold mb-2 text-center text-slate-100">
                {isRegister ? 'Crear cuenta en Xela Stay' : 'Iniciar Sesión'}
            </h2>
            <p className="text-slate-400 text-sm text-center mb-6">
                {isRegister ? 'Regístrate para reservar o publicar alojamientos' : 'Bienvenido de vuelta a tu plataforma de reservas'}
            </p>

            {errorMsg && (
                <div className="bg-red-500/20 border border-red-500/50 text-red-300 text-sm p-3 rounded-lg mb-4">
                    {errorMsg}
                </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
                {isRegister && (
                    <div>
                        <label className="block text-xs uppercase tracking-wider text-slate-400 mb-1">Nombre Completo</label>
                        <input
                            type="text"
                            required
                            value={fullName}
                            onChange={(e) => setFullName(e.target.value)}
                            className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                            placeholder="Ej. Pedro López"
                        />
                    </div>
                )}

                <div>
                    <label className="block text-xs uppercase tracking-wider text-slate-400 mb-1">Correo Electrónico</label>
                    <input
                        type="email"
                        required
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                        placeholder="usuario@xelastay.com"
                    />
                </div>

                <div>
                    <label className="block text-xs uppercase tracking-wider text-slate-400 mb-1">Contraseña</label>
                    <input
                        type="password"
                        required
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                        placeholder="••••••••"
                    />
                </div>

                <button
                    type="submit"
                    disabled={loading}
                    className="w-full bg-indigo-600 hover:bg-indigo-500 text-white font-medium py-2.5 rounded-lg transition-colors mt-2 disabled:opacity-50 shadow-md"
                >
                    {loading ? 'Procesando...' : isRegister ? 'Registrarme' : 'Entrar'}
                </button>
            </form>

            <div className="mt-6 text-center text-sm text-slate-400">
                {isRegister ? '¿Ya tienes una cuenta?' : '¿Aún no tienes cuenta?'}{' '}
                <button
                    type="button"
                    onClick={() => { setIsRegister(!isRegister); setErrorMsg(null); }}
                    className="text-indigo-400 hover:underline font-semibold"
                >
                    {isRegister ? 'Inicia sesión' : 'Regístrate aquí'}
                </button>
            </div>
        </div>
    );
}