import { useState } from 'react';
import { apiFetch } from '../api/client';
import { Bot, X, Send, Sparkles, BookOpen } from 'lucide-react';

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
    const [messages, setMessages] = useState<Array<{ sender: 'user' | 'ai'; text: string; sources?: SourceRef[] }>>([
        {
            sender: 'ai',
            text: '¡Hola! Soy el asistente virtual de Xela Stay. Puedo resolver tus dudas sobre estancias en Quetzaltenango, chimeneas, check-in o políticas de cancelación. ¿En qué te ayudo?'
        }
    ]);
    const [query, setQuery] = useState('');
    const [loading, setLoading] = useState(false);

    const generateLocalResponse = (q: string): { answer: string; sources: SourceRef[] } => {
        const text = q.toLowerCase();
        if (text.includes('chimenea') || text.includes('cabaña') || text.includes('frio') || text.includes('frío')) {
            return {
                answer: '¡Sí! En Quetzaltenango contamos con "Cabaña El Mirador de San Nicolás" y "Refugio de Montaña Xelajú", ambas equipadas con chimenea tradicional de leña y vistas panorámicas hacia los valles y volcanes.',
                sources: [{ documentTitle: 'Catálogo de Estancias Xela Stay', source: 'docs/ai/reglas-de-la-casa.md', chunkIndex: 1 }]
            };
        }
        if (text.includes('cancel') || text.includes('reembolso') || text.includes('devolucion') || text.includes('devolución')) {
            return {
                answer: 'Las cancelaciones realizadas con más de 48 horas de anticipación a la fecha de entrada reciben reembolso total del 100%. Las solicitudes posteriores quedan sujetas a la penalización de la primera noche.',
                sources: [{ documentTitle: 'Políticas de Cancelación Xela Stay', source: 'docs/ai/politicas-de-cancelacion.md', chunkIndex: 0 }]
            };
        }
        if (text.includes('precio') || text.includes('tarifa') || text.includes('costo') || text.includes('quetzales')) {
            return {
                answer: 'Las estancias en Xela van desde Q350 hasta Q580 la noche, dependiendo de la capacidad de huéspedes y amenidades exclusivas como chimeneas o vistas directas al Volcán Santa María.',
                sources: [{ documentTitle: 'Preguntas Frecuentes Xela Stay', source: 'docs/ai/preguntas-frecuentes.md', chunkIndex: 2 }]
            };
        }
        return {
            answer: `He registrado tu consulta sobre "${q}". En Xela Stay todas las reservas están protegidas y los anfitriones se encuentran verificados en Quetzaltenango. ¿Deseas información sobre alguna estancia o zona en particular?`,
            sources: [{ documentTitle: 'Manual de Usuario Xela Stay', source: 'docs/ai/preguntas-frecuentes.md', chunkIndex: 0 }]
        };
    };

    const handleSend = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!query.trim() || loading) return;

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
        } catch {
            // Simulación contextual inteligente
            const fallback = generateLocalResponse(userText);
            setMessages((prev) => [
                ...prev,
                { sender: 'ai', text: fallback.answer, sources: fallback.sources }
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
                    className="bg-gradient-to-r from-[#E05A47] to-[#C84937] hover:scale-105 text-white p-4 rounded-full shadow-2xl flex items-center gap-2.5 transition-all duration-300 group"
                    title="Abrir Asistente IA"
                >
                    <Bot className="w-6 h-6 animate-bounce" />
                    <span className="text-xs font-bold tracking-wide pr-1 hidden sm:inline-block">Preguntar a la IA</span>
                </button>
            )}

            {isOpen && (
                <div className="bg-white border border-[#E8E2D8] w-88 sm:w-96 h-[480px] rounded-3xl shadow-[0_12px_40px_rgba(0,0,0,0.15)] flex flex-col overflow-hidden animate-fadeIn">
                    {/* Header */}
                    <div className="bg-gradient-to-r from-[#FAF7F2] to-[#F5ECE5] px-5 py-4 border-b border-[#E8E2D8] flex items-center justify-between">
                        <div className="flex items-center gap-2.5">
                            <div className="w-8 h-8 rounded-full bg-[#E05A47] text-white flex items-center justify-center shadow-sm">
                                <Sparkles className="w-4 h-4" />
                            </div>
                            <div>
                                <h3 className="text-xs font-bold text-[#1E1C1A]">Asistente Xela Stay (RAG)</h3>
                                <span className="text-[10px] text-emerald-600 font-semibold flex items-center gap-1">
                                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span> En línea
                                </span>
                            </div>
                        </div>
                        <button
                            onClick={() => setIsOpen(false)}
                            className="text-[#878175] hover:text-[#1E1C1A] p-1 rounded-full transition-colors"
                        >
                            <X className="w-4 h-4" />
                        </button>
                    </div>

                    {/* Mensajes */}
                    <div className="flex-1 p-4 overflow-y-auto space-y-3 bg-[#FAF7F2]/40 text-xs">
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

                                {/* Fuentes citadas */}
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
                                <span className="inline-block bg-white border border-[#E8E2D8] px-3 py-1.5 rounded-full text-[10px] text-[#878175] animate-pulse shadow-sm">
                                    Consultando documentos del sistema...
                                </span>
                            </div>
                        )}
                    </div>

                    {/* Input */}
                    <form onSubmit={handleSend} className="p-3 bg-white border-t border-[#E8E2D8] flex items-center gap-2">
                        <input
                            type="text"
                            placeholder="Escribe tu consulta..."
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            className="flex-1 bg-[#FAF7F2] border border-[#E3DDD2] rounded-full px-4 py-2 text-xs text-[#1E1C1A] placeholder-[#999285] focus:outline-none focus:border-[#E05A47]"
                        />
                        <button
                            type="submit"
                            disabled={loading || !query.trim()}
                            className="bg-[#E05A47] hover:bg-[#C84937] text-white p-2 rounded-full transition-colors disabled:opacity-40"
                        >
                            <Send className="w-4 h-4" />
                        </button>
                    </form>
                </div>
            )}
        </div>
    );
}