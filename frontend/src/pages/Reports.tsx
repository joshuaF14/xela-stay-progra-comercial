import { useState } from 'react';
import { FileSpreadsheet, FileText, Filter, Calendar } from 'lucide-react';

export default function Reports() {
    const [reportType, setReportType] = useState('bookings');
    const [startDate, setStartDate] = useState('2026-01-01');
    const [endDate, setEndDate] = useState('2026-12-31');

    const handleExport = (format: 'excel' | 'pdf') => {
        const url = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/v1/reports/${reportType}/export?format=${format}&start=${startDate}&end=${endDate}`;
        window.open(url, '_blank');
    };

    return (
        <div className="max-w-4xl mx-auto bg-white border border-[#EAE3D9] rounded-2xl p-8 shadow-sm text-[#1F1E1D] space-y-6">
            <div>
                <h1 className="text-3xl font-serif font-bold text-[#1F1E1D]">Reportes Empresariales</h1>
                <p className="text-[#7A756D] text-sm mt-1">Genera y exporta estadísticas operativas de reservaciones y ocupación</p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 bg-[#FAF7F0] p-5 rounded-xl border border-[#EAE3D9]">
                <div>
                    <label className="block text-xs font-semibold text-[#7A756D] uppercase mb-1.5 flex items-center gap-1">
                        <Filter className="w-3.5 h-3.5 text-[#E05A47]" /> Tipo de Reporte
                    </label>
                    <select
                        value={reportType}
                        onChange={(e) => setReportType(e.target.value)}
                        className="w-full bg-white border border-[#EAE3D9] rounded-xl px-3 py-2 text-sm text-[#1F1E1D] focus:outline-none focus:border-[#E05A47]"
                    >
                        <option value="bookings">1. Reservaciones por período</option>
                        <option value="revenue">2. Ingresos por período</option>
                        <option value="top-properties">3. Alojamientos más reservados</option>
                        <option value="host-earnings">4. Anfitriones con más ingresos</option>
                        <option value="occupancy-municipality">8. Ocupación por municipio</option>
                        <option value="ratings">9. Calificaciones de alojamientos</option>
                    </select>
                </div>

                <div>
                    <label className="block text-xs font-semibold text-[#7A756D] uppercase mb-1.5 flex items-center gap-1">
                        <Calendar className="w-3.5 h-3.5 text-[#E05A47]" /> Fecha Inicial
                    </label>
                    <input
                        type="date"
                        value={startDate}
                        onChange={(e) => setStartDate(e.target.value)}
                        className="w-full bg-white border border-[#EAE3D9] rounded-xl px-3 py-2 text-sm text-[#1F1E1D] focus:outline-none focus:border-[#E05A47]"
                    />
                </div>

                <div>
                    <label className="block text-xs font-semibold text-[#7A756D] uppercase mb-1.5 flex items-center gap-1">
                        <Calendar className="w-3.5 h-3.5 text-[#E05A47]" /> Fecha Final
                    </label>
                    <input
                        type="date"
                        value={endDate}
                        onChange={(e) => setEndDate(e.target.value)}
                        className="w-full bg-white border border-[#EAE3D9] rounded-xl px-3 py-2 text-sm text-[#1F1E1D] focus:outline-none focus:border-[#E05A47]"
                    />
                </div>
            </div>

            <div className="flex flex-col sm:flex-row gap-4 pt-4 border-t border-[#F2ECE4]">
                <button
                    onClick={() => handleExport('pdf')}
                    className="flex-1 bg-[#FEEAE6] hover:bg-[#FDD5CD] text-[#E05A47] font-semibold py-3 px-4 rounded-xl flex items-center justify-center gap-2 transition-colors border border-[#F5C2B8]"
                >
                    <FileText className="w-4 h-4" /> Exportar a PDF
                </button>
                <button
                    onClick={() => handleExport('excel')}
                    className="flex-1 bg-[#1F1E1D] hover:bg-[#33312E] text-white font-semibold py-3 px-4 rounded-xl flex items-center justify-center gap-2 transition-colors shadow-sm"
                >
                    <FileSpreadsheet className="w-4 h-4 text-emerald-400" /> Exportar a Excel
                </button>
            </div>
        </div>
    );
}