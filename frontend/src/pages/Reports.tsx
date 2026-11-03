import { useState, useEffect } from 'react';
import { apiFetch } from '../api/client';
import { supabase } from '../services/supabase';
import { FileText, Download, Filter, AlertCircle, ShieldAlert, Table } from 'lucide-react';

interface ReportDefinition {
    id: string;
    name: string;
    description?: string;
}

export default function Reports() {
    const [isAdmin, setIsAdmin] = useState<boolean | null>(null);
    const [reports, setReports] = useState<ReportDefinition[]>([]);
    const [selectedReportId, setSelectedReportId] = useState('');
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [reportData, setReportData] = useState<any[]>([]);
    const [loadingData, setLoadingData] = useState(false);
    const [downloading, setDownloading] = useState(false);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);

    // Validar rol de ADMIN desde /api/v1/me
    useEffect(() => {
        apiFetch<any>('/api/v1/me')
            .then((me) => {
                const roles = Array.isArray(me?.roles) ? me.roles : [];
                setIsAdmin(roles.includes('ADMIN') || me?.role === 'ADMIN');
            })
            .catch(() => setIsAdmin(false));
    }, []);

    // Cargar lista de reportes
    useEffect(() => {
        if (!isAdmin) return;

        apiFetch<ReportDefinition[]>('/api/v1/reports')
            .then((data) => {
                const list = Array.isArray(data) ? data : (data as any)?.content || [];
                setReports(list);
                if (list.length > 0) setSelectedReportId(list[0].id);
            })
            .catch((err: any) => setErrorMsg(err.message || 'Error al cargar lista de reportes.'));
    }, [isAdmin]);

    const handleQueryData = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedReportId) return;

        setLoadingData(true);
        setErrorMsg(null);

        const queryParams = new URLSearchParams();
        if (startDate) queryParams.append('start', startDate);
        if (endDate) queryParams.append('end', endDate);

        try {
            const data: any = await apiFetch(`/api/v1/reports/${selectedReportId}?${queryParams.toString()}`);
            const rows = Array.isArray(data) ? data : data?.content || [];
            setReportData(rows);
        } catch (err: any) {
            setErrorMsg(err.message || 'Error al obtener los datos del reporte.');
            setReportData([]);
        } finally {
            setLoadingData(false);
        }
    };

    const handleDownload = async (fileExtension: 'xlsx' | 'pdf' = 'xlsx') => {
        if (!selectedReportId) return;

        setDownloading(true);
        setErrorMsg(null);

        try {
            const { data: { session } } = await supabase.auth.getSession();
            const token = session?.access_token;
            if (!token) throw new Error('No hay sesión activa para descargar el reporte.');

            const queryParams = new URLSearchParams();
            if (startDate) queryParams.append('start', startDate);
            if (endDate) queryParams.append('end', endDate);
            queryParams.append('format', fileExtension);

            const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
            const endpoint = `${API_URL}/api/v1/reports/${selectedReportId}/export?${queryParams.toString()}`;

            const response = await fetch(endpoint, {
                method: 'GET',
                headers: { Authorization: `Bearer ${token}` },
            });

            if (!response.ok) throw new Error(`Error ${response.status}: no se pudo generar el archivo.`);

            const blob = await response.blob();
            const blobUrl = window.URL.createObjectURL(blob);
            const tempLink = document.createElement('a');
            tempLink.href = blobUrl;
            tempLink.setAttribute('download', `reporte_${selectedReportId}_${Date.now()}.${fileExtension}`);
            document.body.appendChild(tempLink);
            tempLink.click();
            document.body.removeChild(tempLink);
            window.URL.revokeObjectURL(blobUrl);
        } catch (err: any) {
            setErrorMsg(err.message || 'Error al descargar el archivo.');
        } finally {
            setDownloading(false);
        }
    };

    if (isAdmin === false) {
        return (
            <div className="max-w-md mx-auto p-12 text-center bg-white border border-[#E8E2D8] rounded-3xl space-y-4 shadow-sm animate-fadeIn">
                <ShieldAlert className="w-12 h-12 text-[#E05A47] mx-auto" />
                <h2 className="text-xl font-serif font-bold text-[#1E1C1A]">Acceso Restringido</h2>
                <p className="text-xs text-[#736E65]">
                    Este módulo es de uso exclusivo para cuentas con rol de Administrador.
                </p>
            </div>
        );
    }

    if (isAdmin === null) {
        return <div className="p-12 text-center text-xs text-[#736E65]">Verificando permisos...</div>;
    }

    return (
        <div className="max-w-5xl mx-auto space-y-8 pb-16 animate-fadeIn">
            <div className="border-b border-[#E8E2D8] pb-4">
                <h1 className="text-3xl font-serif font-bold text-[#1E1C1A]">Reportes del Sistema</h1>
                <p className="text-xs text-[#736E65] mt-1">Exportación y visualización auditada para administración y Power BI</p>
            </div>

            {errorMsg && (
                <div className="bg-[#FEEAE6] border border-[#F5C2B8] text-[#C84937] p-4 rounded-2xl flex items-center gap-3 text-xs">
                    <AlertCircle className="w-5 h-5 shrink-0" />
                    <span>{errorMsg}</span>
                </div>
            )}

            <form onSubmit={handleQueryData} className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div>
                        <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5 flex items-center gap-1">
                            <FileText className="w-3.5 h-3.5 text-[#E05A47]" /> Tipo de Reporte
                        </label>
                        <select
                            value={selectedReportId}
                            onChange={(e) => setSelectedReportId(e.target.value)}
                            className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                        >
                            {reports.map((r) => (
                                <option key={r.id} value={r.id}>
                                    {r.name}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div>
                        <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5 flex items-center gap-1">
                            <Filter className="w-3.5 h-3.5 text-[#E05A47]" /> Fecha Inicial
                        </label>
                        <input
                            type="date"
                            value={startDate}
                            onChange={(e) => setStartDate(e.target.value)}
                            className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                        />
                    </div>

                    <div>
                        <label className="block text-xs font-semibold text-[#1E1C1A] mb-1.5 flex items-center gap-1">
                            <Filter className="w-3.5 h-3.5 text-[#E05A47]" /> Fecha Final
                        </label>
                        <input
                            type="date"
                            value={endDate}
                            onChange={(e) => setEndDate(e.target.value)}
                            className="w-full bg-[#FAF7F2] border border-[#E8E2D8] rounded-xl px-3 py-2 text-xs text-[#1E1C1A] focus:outline-none focus:border-[#E05A47]"
                        />
                    </div>
                </div>

                <div className="flex flex-wrap items-center justify-between gap-3 pt-4 border-t border-[#F0EBE1]">
                    <button
                        type="submit"
                        disabled={loadingData}
                        className="bg-[#1E1C1A] hover:bg-black text-white px-5 py-2.5 rounded-xl text-xs font-bold transition flex items-center gap-2"
                    >
                        <Table className="w-3.5 h-3.5" />
                        {loadingData ? 'Cargando datos...' : 'Visualizar Tabla'}
                    </button>

                    <div className="flex items-center gap-2">
                        <button
                            type="button"
                            disabled={downloading}
                            onClick={() => handleDownload('xlsx')}
                            className="bg-[#3A833A] hover:bg-[#2F6D2F] text-white px-4 py-2.5 rounded-xl text-xs font-bold transition flex items-center gap-2 shadow-sm disabled:opacity-50"
                        >
                            <Download className="w-3.5 h-3.5" /> Descargar .XLSX
                        </button>
                        <button
                            type="button"
                            disabled={downloading}
                            onClick={() => handleDownload('pdf')}
                            className="bg-[#E05A47] hover:bg-[#C84937] text-white px-4 py-2.5 rounded-xl text-xs font-bold transition flex items-center gap-2 shadow-sm disabled:opacity-50"
                        >
                            <Download className="w-3.5 h-3.5" /> Descargar .PDF
                        </button>
                    </div>
                </div>
            </form>

            {/* Resultados tabulares */}
            <div className="bg-white border border-[#E8E2D8] rounded-3xl p-6 shadow-sm overflow-hidden">
                <h3 className="font-serif font-bold text-base text-[#1E1C1A] mb-4 flex items-center gap-2">
                    <Table className="w-4 h-4 text-[#E05A47]" /> Resultados del Reporte
                </h3>

                {reportData.length === 0 ? (
                    <p className="text-xs text-[#736E65] text-center py-8">
                        Selecciona los filtros y haz clic en "Visualizar Tabla" para ver la información.
                    </p>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-left text-xs border-collapse">
                            <thead>
                                <tr className="border-b border-[#E8E2D8] bg-[#FAF7F2]">
                                    {Object.keys(reportData[0]).map((h) => (
                                        <th key={h} className="p-3 text-[11px] font-bold text-[#1E1C1A] uppercase tracking-wider">
                                            {h}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {reportData.map((row, idx) => (
                                    <tr key={idx} className="border-b border-[#F0EBE1] hover:bg-[#FAF7F2]/50 transition">
                                        {Object.values(row).map((val: any, cIdx) => (
                                            <td key={cIdx} className="p-3 text-[#5C574F]">
                                                {typeof val === 'object' ? JSON.stringify(val) : String(val)}
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
}