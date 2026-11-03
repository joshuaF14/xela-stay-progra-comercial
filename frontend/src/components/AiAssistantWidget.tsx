import { useEffect, useState } from 'react';
import { apiFetch } from '../api/client';
import { supabase } from '../services/supabase';
import { Bot, X, Send, Sparkles, BookOpen, Lock } from 'lucide-react';

interface SourceRef {
    documentTitle: string;
    source: string;
    chunkIndex: number;
}

interface ChatResponse {
    answer: string;
    sources: SourceRef[];
}

export default function AiAssistantWidget() {
    const [isOpen, setIsOpen] = useState(false);
    const [hasSession, setHasSession] = useState(false);
    const [messages, setMessages] = useState<Array<{ sender: 'user' | 'ai'; text: string; sources?: SourceRef[] }>>([]);
    const [query, setQuery] = useState('');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        supabase.auth.getSession().then(({ data: { session } }) => {
            setHasSession(!!session);
            if (session) {
                setMessages([
                    {
                        sender: 'ai',
                        text: '¡Hola! Soy el asistente oficial de Xela Stay. Pregúntame sobre políticas de cancelación, estancias o reglas de la casa.'
                    }
                ]);
            }
        });

        const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
            setHasSession(!!session);
        });

        return () => subscription.unsubscribe();
    }, []);

    const handleSend = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!query.trim() || loading || !hasSession) return;

        const userText = query.trim();
        setQuery('');
        setMessages((prev) => [...prev, { sender: 'user', text: userText }]);
        setLoading(true);

        try {
            const data = await apiFetch<ChatResponse>('/api/v1/ai/chat', {
                method: 'POST',
                body: JSON.stringify({ question: userText }),
            });

            setMessages((prev) => [
                ...prev,
                { sender: 'ai', text: data.answer, sources: data.sources }
            ]);
        } catch (err: any) {
            // CERO respuestas falsas: muestra el error real de ApiError
            setMessages((prev) => [
                ...prev,
                { sender: 'ai', text: `Error del asistente: ${err.message || 'No se pudo conectar con el servicio de IA.'}` }
            ]);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="fixed bottom-6 right-6 z-50 font-sans">
            {!isOpen && (
                <button
                    onClick={() => setIsOpen(true)}
                    className="bg-gradient-to-r from-[#E05A47] to-[#C84937] hover:scale-105 text-white p-4 rounded-full shadow-2xl flex items-center gap-2.5 transition-all duration-300"
                    title="Abrir Asistente IA"
                >
                    <Bot className="w-6 h-6" />
                    <span className="text-xs font-bold tracking-wide pr-1 hidden sm:inline-block">Preguntar a la IA</span>
                </button>
            )}

            {isOpen && (
                <div className="bg-white border border-[#E8E2D8] w-88 sm:w-96 h-[480px] rounded-3xl shadow-2xl flex flex-col overflow-hidden animate-fadeIn">
                    <div className="bg-gradient-to-r from-[#FAF7F2] to-[#F5ECE5] px-5 py-4 border-b border-[#E8E2D8] flex items-center justify-between">
                        <div className="flex items-center gap-2.5">
                            <div className="w-8 h-8 rounded-full bg-[#E05A47] text-white flex items-center justify-center shadow-sm">
                                <Sparkles className="w-4 h-4" />
                            </div>
                            <h3 className="text-xs font-bold text-[#1E1C1A]">Asistente Xela Stay (RAG)</h3>
                        </div>
                        <button
                            onClick={() => setIsOpen(false)}
                            className="text-[#878175] hover:text-[#1E1C1A] p-1 rounded-full transition-colors"
                        >
                            <X className="w-4 h-4" />
                        </button>
                    </div>

                    <div className="flex-1 p-4 overflow-y-auto space-y-3 bg-[#FAF7F2]/40 text-xs">
                        {!hasSession ? (
                            <div className="h-full flex flex-col items-center justify-center text-center p-6 space-y-2 text-[#736E65]">
                                <Lock className="w-8 h-8 text-[#E05A47]" />
                                <p className="font-semibold text-[#1E1C1A]">Inicia sesión para usar el asistente</p>
                                <p className="text-[11px]">Por seguridad y auditoría, el agente de IA requiere que tengas una cuenta activa.</p>
                            </div>
                        ) : (
                            <>
                                {messages.map((m, idx) => (
                                    <div
                                        key={idx}
                                        className={`flex flex-col ${m.sender === 'user' ? 'items-end' : 'items-start'}`}
                                    >
                                        <div
                                            className={`p-3 rounded-2xl max-w-[85%] leading-relaxed ${m.sender === 'user'
                                                    ? 'bg-[#E05A47] text-white rounded-tr-none'
                                                    : 'bg-white border border-[#E8E2D8] text-[#1E1C1A] rounded-tl-none shadow-sm'
                                                }`}
                                        >
                                            {m.text}
                                        </div>

                                        {m.sources && m.sources.length > 0 && (
                                            <div className="mt-1 flex flex-wrap gap-1 max-w-[85%]">
                                                {m.sources.map((s, sIdx) => (
                                                    <span
                                                        key={sIdx}
                                                        className="bg-[#F3EFE8] text-[#736E65] text-[9px] px-2 py-0.5 rounded-md flex items-center gap-1 border border-[#E3DDD2]"
                                                    >
                                                        <BookOpen className="w-2.5 h-2.5 text-[#E05A47]" />
                                                        {s.documentTitle}
                                                    </span>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                ))}
                                {loading && (
                                    <div className="text-left">
                                        <span className="inline-block bg-white border border-[#E8E2D8] px-3 py-1.5 rounded-full text-[10px] text-[#878175] animate-pulse">
                                            Consultando documentos RAG...
                                        </span>
                                    </div>
                                )}
                            </>
                        )}
                    </div>

                    <form onSubmit={handleSend} className="p-3 bg-white border-t border-[#E8E2D8] flex items-center gap-2">
                        <input
                            type="text"
                            disabled={!hasSession || loading}
                            placeholder={hasSession ? "Escribe tu consulta..." : "Sesión requerida"}
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            className="flex-1 bg-[#FAF7F2] border border-[#E3DDD2] rounded-full px-4 py-2 text-xs text-[#1E1C1A] placeholder-[#999285] focus:outline-none focus:border-[#E05A47] disabled:opacity-50"
                        />
                        <button
                            type="submit"
                            disabled={!hasSession || loading || !query.trim()}
                            className="bg-[#E05A47] text-white p-2 rounded-full transition-colors disabled:opacity-40"
                        >
                            <Send className="w-4 h-4" />
                        </button>
                    </form>
                </div>
            )}
        </div>
    );
}