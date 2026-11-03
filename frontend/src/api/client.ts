import { supabase } from '../services/supabase';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export interface ApiErrorResponse {
    timestamp: string;
    status: number;
    error: string;
    message: string;
    path: string;
    details: Array<{ field: string; message: string }>;
}

export async function apiFetch<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const { data: { session } } = await supabase.auth.getSession();

    const headers = new Headers(options.headers || {});
    headers.set('Content-Type', 'application/json');

    if (session?.access_token) {
        headers.set('Authorization', `Bearer ${session.access_token}`);
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        ...options,
        headers,
    });

    if (!response.ok) {
        let errorData: ApiErrorResponse;
        try {
            errorData = await response.json();
        } catch {
            errorData = {
                timestamp: new Date().toISOString(),
                status: response.status,
                error: response.statusText,
                message: 'Error al procesar la solicitud en el servidor',
                path: endpoint,
                details: [],
            };
        }
        throw errorData;
    }

    // Si no hay contenido (204 No Content)
    if (response.status === 204) {
        return {} as T;
    }

    return response.json() as Promise<T>;
}