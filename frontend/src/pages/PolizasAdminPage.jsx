import React, { useState, useMemo } from 'react';
import {
  Shield,
  ShieldAlert,
  ShieldCheck,
  Plus,
  Search,
  FileText,
  Calendar,
  DollarSign,
  Building2,
  Phone,
  User,
  Edit2,
  Trash2,
  ExternalLink,
  AlertCircle,
  CheckCircle2,
  Clock,
  XCircle,
} from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader';
import { Modal } from '../components/ui/Modal';
import { useFetch } from '../lib/hooks';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { toast } from 'sonner';

const RAMOS_COBERTURA = [
  'TODO RIESGO DAÑOS MATERIALES',
  'RESPONSABILIDAD CIVIL EXTRACONTRACTUAL',
  'TERREMOTO Y ERUPCION VOLCANICA',
  'DIRECTORES Y ADMINISTRADORES (D&O)',
  'MAQUINARIA Y EQUIPO ELECTRICO',
  'TRANSPORTE DE VALORES',
  'OTRO',
];

const ESTADOS_POLIZA = {
  VIGENTE: { label: 'Vigente', color: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border border-emerald-500/30' },
  POR_VENCER: { label: 'Por Vencer', color: 'bg-amber-500/15 text-amber-700 dark:text-amber-400 border border-amber-500/30 animate-pulse' },
  VENCIDA: { label: 'Vencida', color: 'bg-red-500/15 text-red-700 dark:text-red-400 border border-red-500/30' },
  CANCELADA: { label: 'Cancelada', color: 'bg-neutral/20 text-neutral-content' },
};

function formatCurrency(val) {
  if (val == null) return '$ 0';
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(val);
}

export default function PolizasAdminPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  const [filtroEstado, setFiltroEstado] = useState('TODOS');
  const [busqueda, setBusqueda] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [modalDeleteOpen, setModalDeleteOpen] = useState(false);
  const [polizaEditar, setPolizaEditar] = useState(null);
  const [polizaEliminar, setPolizaEliminar] = useState(null);
  const [saving, setSaving] = useState(false);

  // Form State
  const initialForm = {
    companiaAseguradora: '',
    numeroPoliza: '',
    ramoCobertura: RAMOS_COBERTURA[0],
    valorAsegurado: '',
    valorPrimaAnual: '',
    fechaInicio: '',
    fechaFin: '',
    diasAlertaVencimiento: 45,
    nombreCorredorAgente: '',
    telefonoContactoAgente: '',
    documentoCaratulaUrl: '',
    estado: 'VIGENTE',
  };
  const [formData, setFormData] = useState(initialForm);

  // Data Fetching
  const {
    data: polizasRaw,
    loading: loadingPolizas,
    error: errorPolizas,
    refetch: refetchPolizas,
  } = useFetch(() => tenantApi.get('/seguros/polizas'), [tenant.activeAssignmentId]);

  const {
    data: resumenRaw,
    loading: loadingResumen,
    refetch: refetchResumen,
  } = useFetch(() => tenantApi.get('/seguros/polizas/resumen'), [tenant.activeAssignmentId]);

  const polizas = useMemo(() => {
    if (!polizasRaw) return [];
    if (Array.isArray(polizasRaw)) return polizasRaw;
    if (Array.isArray(polizasRaw.items)) return polizasRaw.items;
    return polizasRaw.data || [];
  }, [polizasRaw]);

  const resumen = resumenRaw?.data || resumenRaw || {
    totalPolizas: 0,
    vigentes: 0,
    porVencer: 0,
    vencidas: 0,
    canceladas: 0,
    valorAseguradoTotal: 0,
    primaAnualTotal: 0,
  };

  const polizasFiltradas = useMemo(() => {
    return polizas.filter((p) => {
      const matchEstado = filtroEstado === 'TODOS' || p.estado === filtroEstado;
      const matchBusqueda =
        !busqueda ||
        p.companiaAseguradora?.toLowerCase().includes(busqueda.toLowerCase()) ||
        p.numeroPoliza?.toLowerCase().includes(busqueda.toLowerCase()) ||
        p.ramoCobertura?.toLowerCase().includes(busqueda.toLowerCase());
      return matchEstado && matchBusqueda;
    });
  }, [polizas, filtroEstado, busqueda]);

  const handleOpenCrear = () => {
    setPolizaEditar(null);
    setFormData(initialForm);
    setModalOpen(true);
  };

  const handleOpenEditar = (p) => {
    setPolizaEditar(p);
    setFormData({
      companiaAseguradora: p.companiaAseguradora || '',
      numeroPoliza: p.numeroPoliza || '',
      ramoCobertura: p.ramoCobertura || RAMOS_COBERTURA[0],
      valorAsegurado: p.valorAsegurado || '',
      valorPrimaAnual: p.valorPrimaAnual || '',
      fechaInicio: p.fechaInicio || '',
      fechaFin: p.fechaFin || '',
      diasAlertaVencimiento: p.diasAlertaVencimiento || 45,
      nombreCorredorAgente: p.nombreCorredorAgente || '',
      telefonoContactoAgente: p.telefonoContactoAgente || '',
      documentoCaratulaUrl: p.documentoCaratulaUrl || '',
      estado: p.estado || 'VIGENTE',
    });
    setModalOpen(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!formData.companiaAseguradora.trim() || !formData.numeroPoliza.trim()) {
      toast.error('Compañía y número de póliza son obligatorios.');
      return;
    }
    if (!formData.fechaInicio || !formData.fechaFin) {
      toast.error('Las fechas de vigencia son obligatorias.');
      return;
    }
    if (new Date(formData.fechaFin) < new Date(formData.fechaInicio)) {
      toast.error('La fecha de fin debe ser posterior a la fecha de inicio.');
      return;
    }

    try {
      setSaving(true);
      const payload = {
        ...formData,
        valorAsegurado: Number(formData.valorAsegurado) || 0,
        valorPrimaAnual: Number(formData.valorPrimaAnual) || 0,
        diasAlertaVencimiento: Number(formData.diasAlertaVencimiento) || 45,
      };

      if (polizaEditar) {
        await tenantApi.put(`/seguros/polizas/${polizaEditar.idPoliza}`, payload);
        toast.success('Póliza actualizada correctamente');
      } else {
        await tenantApi.post('/seguros/polizas', payload);
        toast.success('Póliza registrada correctamente');
      }

      setModalOpen(false);
      refetchPolizas();
      refetchResumen();
    } catch (err) {
      toast.error(err.message || 'Error al guardar la póliza');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!polizaEliminar) return;
    try {
      setSaving(true);
      await tenantApi.del(`/seguros/polizas/${polizaEliminar.idPoliza}`);
      toast.success('Póliza eliminada con éxito');
      setModalDeleteOpen(false);
      setPolizaEliminar(null);
      refetchPolizas();
      refetchResumen();
    } catch (err) {
      toast.error(err.message || 'Error al eliminar la póliza');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Gestión de Pólizas de Seguro"
        description="Administración de coberturas, pólizas de áreas comunes y alertas de vencimiento bajo Ley 675"
        action={
          <button
            onClick={handleOpenCrear}
            className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-content rounded-lg font-medium shadow-sm hover:bg-primary/90 transition-colors"
          >
            <Plus className="w-4 h-4" />
            Nueva Póliza
          </button>
        }
      />

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-card border border-border rounded-xl p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Total Pólizas</p>
              <h3 className="text-2xl font-bold mt-1 text-foreground">{resumen.totalPolizas}</h3>
            </div>
            <div className="p-3 bg-blue-500/10 text-blue-600 dark:text-blue-400 rounded-xl">
              <Shield className="w-6 h-6" />
            </div>
          </div>
          <p className="text-xs text-muted-foreground mt-3 flex items-center gap-1">
            <Building2 className="w-3.5 h-3.5" /> Cobertura Integral de Copropiedad
          </p>
        </div>

        <div className="bg-card border border-border rounded-xl p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Pólizas Vigentes</p>
              <h3 className="text-2xl font-bold mt-1 text-emerald-600 dark:text-emerald-400">{resumen.vigentes}</h3>
            </div>
            <div className="p-3 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 rounded-xl">
              <ShieldCheck className="w-6 h-6" />
            </div>
          </div>
          <p className="text-xs text-muted-foreground mt-3 flex items-center gap-1">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" /> Sin riesgo inmediato
          </p>
        </div>

        <div className="bg-card border border-border rounded-xl p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Por Vencer</p>
              <h3 className="text-2xl font-bold mt-1 text-amber-600 dark:text-amber-400">{resumen.porVencer}</h3>
            </div>
            <div className="p-3 bg-amber-500/10 text-amber-600 dark:text-amber-400 rounded-xl">
              <ShieldAlert className="w-6 h-6" />
            </div>
          </div>
          <p className="text-xs text-amber-600 dark:text-amber-400 mt-3 flex items-center gap-1">
            <Clock className="w-3.5 h-3.5" /> Requieren renovación próxima
          </p>
        </div>

        <div className="bg-card border border-border rounded-xl p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Total Asegurado</p>
              <h3 className="text-xl font-bold mt-1 text-foreground">{formatCurrency(resumen.valorAseguradoTotal)}</h3>
            </div>
            <div className="p-3 bg-purple-500/10 text-purple-600 dark:text-purple-400 rounded-xl">
              <DollarSign className="w-6 h-6" />
            </div>
          </div>
          <p className="text-xs text-muted-foreground mt-3 flex items-center gap-1">
            Prima anual: <span className="font-semibold">{formatCurrency(resumen.primaAnualTotal)}</span>
          </p>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-card border border-border rounded-xl p-4 flex flex-col sm:flex-row gap-4 items-center justify-between">
        <div className="flex items-center gap-2 w-full sm:w-auto">
          <span className="text-xs font-medium text-muted-foreground">Estado:</span>
          <div className="flex flex-wrap gap-1">
            {['TODOS', 'VIGENTE', 'POR_VENCER', 'VENCIDA', 'CANCELADA'].map((st) => (
              <button
                key={st}
                onClick={() => setFiltroEstado(st)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                  filtroEstado === st
                    ? 'bg-primary text-primary-content shadow-xs'
                    : 'bg-muted/40 text-muted-foreground hover:bg-muted/70'
                }`}
              >
                {st === 'TODOS' ? 'Todas' : ESTADOS_POLIZA[st]?.label || st}
              </button>
            ))}
          </div>
        </div>

        <div className="relative w-full sm:w-64">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder="Buscar aseguradora o póliza..."
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            className="w-full pl-9 pr-3 py-1.5 text-sm bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
          />
        </div>
      </div>

      {/* Main Content: Policy Cards & Table */}
      {loadingPolizas ? (
        <div className="p-12 text-center text-muted-foreground bg-card border border-border rounded-xl">
          <div className="animate-spin w-8 h-8 border-4 border-primary border-t-transparent rounded-full mx-auto mb-3" />
          <p className="text-sm">Cargando pólizas de seguro...</p>
        </div>
      ) : polizasFiltradas.length === 0 ? (
        <div className="p-12 text-center bg-card border border-border rounded-xl">
          <ShieldAlert className="w-12 h-12 text-muted-foreground/40 mx-auto mb-3" />
          <h3 className="text-lg font-semibold text-foreground">No se encontraron pólizas</h3>
          <p className="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
            {busqueda || filtroEstado !== 'TODOS'
              ? 'No hay registros que coincidan con los filtros aplicados.'
              : 'Aún no se han registrado pólizas de seguro para esta copropiedad.'}
          </p>
          {!busqueda && filtroEstado === 'TODOS' && (
            <button
              onClick={handleOpenCrear}
              className="mt-4 inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-content rounded-lg text-sm font-medium shadow-sm hover:bg-primary/90 transition-colors"
            >
              <Plus className="w-4 h-4" />
              Registrar Primera Póliza
            </button>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {polizasFiltradas.map((p) => {
            const estadoCfg = ESTADOS_POLIZA[p.estado] || { label: p.estado, color: 'bg-muted text-muted-foreground' };
            return (
              <div
                key={p.idPoliza}
                className="bg-card border border-border rounded-xl p-5 shadow-sm hover:shadow-md transition-shadow flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-start justify-between gap-3 mb-3">
                    <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${estadoCfg.color}`}>
                      {estadoCfg.label}
                    </span>
                    <span className="text-xs font-medium text-muted-foreground bg-muted/60 px-2 py-0.5 rounded">
                      ID #{p.idPoliza}
                    </span>
                  </div>

                  <h4 className="font-bold text-base text-foreground mb-1 leading-snug">
                    {p.ramoCobertura}
                  </h4>
                  <p className="text-xs font-semibold text-primary mb-3">
                    {p.companiaAseguradora} &bull; Póliza N° {p.numeroPoliza}
                  </p>

                  <div className="space-y-2 border-t border-border/60 pt-3 text-xs">
                    <div className="flex justify-between items-center text-muted-foreground">
                      <span className="flex items-center gap-1.5">
                        <Calendar className="w-3.5 h-3.5" /> Vigencia:
                      </span>
                      <span className="font-medium text-foreground">
                        {p.fechaInicio} &rarr; {p.fechaFin}
                      </span>
                    </div>

                    <div className="flex justify-between items-center text-muted-foreground">
                      <span className="flex items-center gap-1.5">
                        <Shield className="w-3.5 h-3.5" /> Valor Asegurado:
                      </span>
                      <span className="font-semibold text-foreground">
                        {formatCurrency(p.valorAsegurado)}
                      </span>
                    </div>

                    <div className="flex justify-between items-center text-muted-foreground">
                      <span className="flex items-center gap-1.5">
                        <DollarSign className="w-3.5 h-3.5" /> Prima Anual:
                      </span>
                      <span className="font-medium text-foreground">
                        {formatCurrency(p.valorPrimaAnual)}
                      </span>
                    </div>

                    {p.nombreCorredorAgente && (
                      <div className="flex justify-between items-center text-muted-foreground pt-1">
                        <span className="flex items-center gap-1.5">
                          <User className="w-3.5 h-3.5" /> Corredor/Agente:
                        </span>
                        <span className="font-medium text-foreground">
                          {p.nombreCorredorAgente}
                        </span>
                      </div>
                    )}

                    {p.telefonoContactoAgente && (
                      <div className="flex justify-between items-center text-muted-foreground">
                        <span className="flex items-center gap-1.5">
                          <Phone className="w-3.5 h-3.5" /> Contacto:
                        </span>
                        <a
                          href={`tel:${p.telefonoContactoAgente}`}
                          className="font-medium text-primary hover:underline flex items-center gap-1"
                        >
                          {p.telefonoContactoAgente}
                        </a>
                      </div>
                    )}
                  </div>
                </div>

                <div className="flex items-center justify-between border-t border-border/60 pt-3 mt-4">
                  <div>
                    {p.documentoCaratulaUrl ? (
                      <a
                        href={p.documentoCaratulaUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1 text-xs text-primary hover:underline font-medium"
                      >
                        <FileText className="w-3.5 h-3.5" /> Ver Carátula
                        <ExternalLink className="w-3 h-3" />
                      </a>
                    ) : (
                      <span className="text-xs text-muted-foreground italic">Sin documento adjunto</span>
                    )}
                  </div>

                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => handleOpenEditar(p)}
                      className="p-1.5 text-muted-foreground hover:text-primary hover:bg-primary/10 rounded-md transition-colors"
                      title="Editar Póliza"
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => {
                        setPolizaEliminar(p);
                        setModalDeleteOpen(true);
                      }}
                      className="p-1.5 text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded-md transition-colors"
                      title="Eliminar Póliza"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal Crear / Editar */}
      <Modal
        open={modalOpen}
        onOpenChange={setModalOpen}
        title={polizaEditar ? 'Editar Póliza de Seguro' : 'Registrar Nueva Póliza de Seguro'}
        description="Configure los detalles contractuales, aseguradora y cobertura según la Ley 675"
      >
        <form onSubmit={handleSave} className="space-y-4 pt-2">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Compañía Aseguradora *
              </label>
              <input
                type="text"
                required
                value={formData.companiaAseguradora}
                onChange={(e) => setFormData({ ...formData, companiaAseguradora: e.target.value })}
                placeholder="Ej. Seguros del Estado, Suramericana"
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Número de Póliza *
              </label>
              <input
                type="text"
                required
                value={formData.numeroPoliza}
                onChange={(e) => setFormData({ ...formData, numeroPoliza: e.target.value })}
                placeholder="Ej. POL-99482-A"
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-muted-foreground mb-1">
              Ramo de Cobertura *
            </label>
            <select
              value={formData.ramoCobertura}
              onChange={(e) => setFormData({ ...formData, ramoCobertura: e.target.value })}
              className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
            >
              {RAMOS_COBERTURA.map((ramo) => (
                <option key={ramo} value={ramo}>
                  {ramo}
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Valor Asegurado (COP) *
              </label>
              <input
                type="number"
                required
                min="1"
                step="any"
                value={formData.valorAsegurado}
                onChange={(e) => setFormData({ ...formData, valorAsegurado: e.target.value })}
                placeholder="Ej. 1500000000"
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Prima Anual (COP) *
              </label>
              <input
                type="number"
                required
                min="0"
                step="any"
                value={formData.valorPrimaAnual}
                onChange={(e) => setFormData({ ...formData, valorPrimaAnual: e.target.value })}
                placeholder="Ej. 12500000"
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Fecha Inicio *
              </label>
              <input
                type="date"
                required
                value={formData.fechaInicio}
                onChange={(e) => setFormData({ ...formData, fechaInicio: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Fecha Fin *
              </label>
              <input
                type="date"
                required
                value={formData.fechaFin}
                onChange={(e) => setFormData({ ...formData, fechaFin: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Días Alerta Previa
              </label>
              <input
                type="number"
                min="1"
                max="180"
                value={formData.diasAlertaVencimiento}
                onChange={(e) => setFormData({ ...formData, diasAlertaVencimiento: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Nombre Corredor / Agente
              </label>
              <input
                type="text"
                value={formData.nombreCorredorAgente}
                onChange={(e) => setFormData({ ...formData, nombreCorredorAgente: e.target.value })}
                placeholder="Nombre del asesor de seguros"
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Teléfono de Contacto
              </label>
              <input
                type="tel"
                value={formData.telefonoContactoAgente}
                onChange={(e) => setFormData({ ...formData, telefonoContactoAgente: e.target.value })}
                placeholder="Ej. 3001234567"
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-muted-foreground mb-1">
              URL Carátula / Documento Digital (PDF)
            </label>
            <input
              type="url"
              value={formData.documentoCaratulaUrl}
              onChange={(e) => setFormData({ ...formData, documentoCaratulaUrl: e.target.value })}
              placeholder="https://..."
              className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
            />
          </div>

          <div className="flex justify-end gap-2 pt-4 border-t border-border">
            <button
              type="button"
              onClick={() => setModalOpen(false)}
              className="px-4 py-2 text-sm font-medium text-muted-foreground hover:bg-muted rounded-lg transition-colors"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={saving}
              className="px-4 py-2 text-sm font-medium bg-primary text-primary-content rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50"
            >
              {saving ? 'Guardando...' : polizaEditar ? 'Actualizar Póliza' : 'Guardar Póliza'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Modal Confirmar Eliminación */}
      <Modal
        open={modalDeleteOpen}
        onOpenChange={setModalDeleteOpen}
        title="Confirmar Eliminación"
        description="¿Está seguro de eliminar esta póliza de seguro? Esta acción no se puede deshacer."
      >
        <div className="pt-2 space-y-4">
          {polizaEliminar && (
            <div className="p-3 bg-destructive/10 text-destructive rounded-lg text-sm">
              <p className="font-semibold">{polizaEliminar.ramoCobertura}</p>
              <p className="text-xs text-muted-foreground">
                {polizaEliminar.companiaAseguradora} - Póliza N° {polizaEliminar.numeroPoliza}
              </p>
            </div>
          )}

          <div className="flex justify-end gap-2 pt-2 border-t border-border">
            <button
              type="button"
              onClick={() => setModalDeleteOpen(false)}
              className="px-4 py-2 text-sm font-medium text-muted-foreground hover:bg-muted rounded-lg transition-colors"
            >
              Cancelar
            </button>
            <button
              type="button"
              disabled={saving}
              onClick={handleDelete}
              className="px-4 py-2 text-sm font-medium bg-destructive text-destructive-content rounded-lg hover:bg-destructive/90 transition-colors disabled:opacity-50"
            >
              {saving ? 'Eliminando...' : 'Sí, Eliminar Póliza'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
