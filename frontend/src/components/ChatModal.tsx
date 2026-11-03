import { useState, useEffect } from 'react';
import { apiFetch } from '../api/client';
import { MessageSquare, Send, X } from 'lucide-react';

interface Message {
    id: string;
    senderName?: string;
    content: string;
    createdAt: string;
    isMine?: boolean;
}

interface Props {
    isOpen: boolean;
    onClose: () => void;
    conversationId?: string | null;
    propertyId?: string;
}

export default function ChatModal({ isOpen, onClose, conversationId, propertyId }: Props) {
    const [messages, setMessages] = useState<Message[]>([]);
    const [newMsg, setNewMsg] = useState('');
    const [activeConvId, setActiveConvId] = useState<string | null>(conversationId || null);
    const [loading, setLoading] = useState(false);

    const fetchMessages = async (cId: string) => {
        try {
            const data: any = await apiFetch(`/api/v1/conversations/${cId}/messages`);
            const list: Message[] = Array.isArray(data) ? data : data?.content || [];
            // Ordenar de más antiguo a más reciente para el chat
            setMessages([...list].reverse());
            await apiFetch(`/api/v1/conversations/${cId}/read`, { method: 'PATCH' });
        } catch {
            setMessages([]);
        }
    };

    useEffect(() => {
        if (isOpen && conversationId) {
            setActiveConvId(conversationId);
            fetchMessages(conversationId);
        }
    }, [isOpen, conversationId]);

    const handleSend = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!newMsg.trim()) return;

        setLoading(true);
        try {
            if (!activeConvId && propertyId) {
                // Iniciar conversación
                const conv: any = await apiFetch('/api/v1/conversations', {
                    method: 'POST',
                    body: JSON.stringify({ propertyId, message: newMsg.trim() }),
                });
                const cId = conv?.id || conv?.conversationId;
                setActiveConvId(cId);
                fetchMessages(cId);
            } else if (activeConvId) {
                // Enviar mensaje a conversación existente
                await apiFetch(`/api/v1/conversations/${activeConvId}/messages`, {
                    method: 'POST',
                    body: JSON.stringify({ content: newMsg.trim() }),
                });
                fetchMessages(activeConvId);
            }
            setNewMsg('');
        } catch (err: any) {
            alert(err.message || 'Error al enviar mensaje');
        } finally {
            setLoading(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
            <div className="bg-white border border-[#E8E2D8] rounded-3xl w-full max-w-lg shadow-2xl flex flex-col h-[500px]">
                <div className="flex items-center justify-between p-4 border-b border-[#F0EBE1]">
                    <div className="flex items-center gap-2">
                        <MessageSquare className="w-4 h-4 text-[#E05A47]" />
                        <h3 className="font-serif font-bold text-sm text-[#1E1C1A]">Mensajes con el Anfitrión</h3>
                    </div>
                    <button onClick={onClose} className="p-1 rounded-lg text-[#736E65] hover:bg-[#FAF7F2]">
                        <X className="w-4 h-4" />
                    </button>
                </div>

                <div className="flex-1 p-4 overflow-y-auto space-y-2">
                    {messages.length === 0 ? (
                        <p className="text-center text-xs text-[#736E65] py-16">Escribe tu primer mensaje para iniciar la conversación.</p>
                    ) : (
                        messages.map((m, idx) => (
                            <div key={idx} className={`p-2.5 rounded-xl text-xs max-w-[80%] ${m.isMine ? 'ml-auto bg-[#E05A47] text-white' : 'mr-auto bg-[#FAF7F2] text-[#1E1C1A] border border-[#E8E2D8]'}`}>
                                <p>{m.content}</p>
                            </div>
                        ))
                    )}
                </div>

                <form onSubmit={handleSend} className="p-3 border-t border-[#F0EBE1] flex gap-2">
                    <input
                        type="text"
                        required
                        value={newMsg}
                        onChange={(e) => setNewMsg(e.target.value)}
                        placeholder="Escribe tu consulta..."
                        className="flex-1 bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                    />
                    <button
                        type="submit"
                        disabled={loading}
                        className="bg-[#E05A47] hover:bg-[#C84937] text-white px-4 py-2 rounded-xl text-xs font-bold transition flex items-center gap-1"
                    >
                        <Send className="w-3.5 h-3.5" />
                    </button>
                </form>
            </div>
        </div>
    );
}