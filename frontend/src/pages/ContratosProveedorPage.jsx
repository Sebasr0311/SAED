import React, { useState, useEffect, useMemo } from 'react';
import {
  FileText,
  Plus,
  CheckCircle2,
  Clock,
  Trash2,
  RefreshCw,
  Search,
  ShieldCheck,
  DollarSign,
  Calendar,
  AlertTriangle,
  RotateCcw,
} from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import { Input } from '../components/ui/Form.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { api } from '../lib/api.js';
import { formatCurrency, formatDate } from '../lib/utils.js';
import { toast } from 'sonner';

export default function ContratosProveedorPage() {
  const [contratos, setContratos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [dialog, setDialog] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterTab, setFilterTab] = useState('TODOS');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [form, setForm] = useState({
    idProveedor: '',
    numeroContrato: '',
    objetoContrato: '',
    valorTotal: '',
    periodicidadPago: 'MENSUAL',
    fechaInicio: '',
    fechaFin: '',
    diasAlertaVenc: '30',
  });
  const [deleteTarget, setDeleteTarget] = useState(null);

  const cargar = async () => {
    try {
      setLoading(true);
      const res = await api.get('/contratos-admin/proveedores');
      setContratos(Array.isArray(res.data) ? res.data : Array.isArray(res) ? res : []);
    } catch (e) {
      toast.error('No se pudieron cargar los contratos de proveedor');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    cargar();
  }, []);

  const stats = useMemo(() => {
    const total = contratos.length;
    const vigentes = contratos.filter((c) => c.estado === 'VIGENTE').length;
    const finalizados = contratos.filter((c) => c.estado === 'FINALIZADO').length;
    const valorComprometido = contratos
      .filter((c) => c.estado === 'VIGENTE')
      .reduce((sum, c) => sum + (Number(c.valorTotal) || 0), 0);
    return { total, vigentes, finalizados, valorComprometido };
  }, [contratos]);

  const filteredContratos = useMemo(() => {
    return contratos.filter((c) => {
      const matchEstado = filterTab === 'TODOS' || c.estado === filterTab;
      if (!matchEstado) return false;
      if (!searchTerm.trim()) return true;

      const q = searchTerm.toLowerCase();
      const num = (c.numeroContrato || '').toLowerCase();
      const obj = (c.objetoContrato || '').toLowerCase();
      const prov = String(c.idProveedor || '');
      return num.includes(q) || obj.includes(q) || prov.includes(q);
    });
  }, [contratos, filterTab, searchTerm]);

  const crear = async (e) => {
    e?.preventDefault?.();
    if (
      !form.idProveedor ||
      !form.numeroContrato ||
      !form.objetoContrato ||
      !form.valorTotal ||
      !form.fechaInicio ||
      !form.fechaFin
    ) {
      toast.error('Complete todos los campos obligatorios');
      return;
    }

    setIsSubmitting(true);
    try {
      await api.post('/contratos-admin/proveedores', {
        idProveedor: Number(form.idProveedor),
        numeroContrato: form.numeroContrato.trim(),
        objetoContrato: form.objetoContrato.trim(),
        valorTotal: Number(form.valorTotal),
        periodicidadPago: form.periodicidadPago,
        fechaInicio: form.fechaInicio,
        fechaFin: form.fechaFin,
        diasAlertaVenc: Number(form.diasAlertaVenc) || 30,
      });
      toast.success('Contrato de proveedor registrado correctamente');
      setDialog(false);
      setForm({
        idProveedor: '',
        numeroContrato: '',
        objetoContrato: '',
        valorTotal: '',
        periodicidadPago: 'MENSUAL',
        fechaInicio: '',
        fechaFin: '',
        diasAlertaVenc: '30',
      });
      cargar();
    } catch (e) {
      toast.error('Error: ' + (e.message || 'Error desconocido'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const eliminar = async () => {
    if (!deleteTarget) return;
    try {
      await api.delete(`/contratos-admin/proveedores/${deleteTarget.idContratoProveedor}`);
      toast.success('Contrato eliminado del registro');
      setDeleteTarget(null);
      cargar();
    } catch (e) {
      toast.error('Error al eliminar el contrato');
    }
  };

  const toggleEstado = async (c) => {
    const nuevo = c.estado === 'VIGENTE' ? 'FINALIZADO' : 'VIGENTE';
    try {
      await api.patch(`/contratos-admin/proveedores/${c.idContratoProveedor}/estado`, {
        estado: nuevo,
      });
      toast.success(`Estado actualizado a ${nuevo}`);
      cargar();
    } catch (e) {
      toast.error('Error al cambiar estado del contrato');
    }
  };

  const fmt = (v) => formatCurrency(Number(v || 0));

  if (loading && contratos.length === 0) {
    return <LoadingState message="Cargando contratos y acuerdos de proveedores..." />;
  }

  return (
    <div className="space-y-6 animate-fadeIn pb-12">
      {/* Header Principal */}
      <PageHeader
        title="Contratos de Proveedor"
        subtitle="Gestión de pólizas, acuerdos de nivel de servicio (SLA) y control de vencimientos"
        action={
          <Button
            variant="primary"
            icon={<Plus className="w-4 h-4" />}
            onClick={() => setDialog(true)}
          >
            Nuevo Contrato
          </Button>
        }
      />

      {/* Tarjetas Ejecutivas de Estado */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          label="Total Contratos"
          value={stats.total}
          subtitle="Histórico de convenios"
          icon={<FileText className="w-5 h-5 text-primary" />}
          variant="primary"
        />
        <MetricCard
          label="Contratos Vigentes"
          value={stats.vigentes}
          subtitle="En ejecución activa"
          icon={<CheckCircle2 className="w-5 h-5 text-emerald-500" />}
          variant="success"
        />
        <MetricCard
          label="Contratos Finalizados"
          value={stats.finalizados}
          subtitle="Concluidos o archivados"
          icon={<Clock className="w-5 h-5 text-muted-foreground" />}
          variant="secondary"
        />
        <MetricCard
          label="Compromiso Vigente"
          value={fmt(stats.valorComprometido)}
          subtitle="Valor total en ejecución"
          icon={<DollarSign className="w-5 h-5 text-sky-500" />}
          variant="info"
        />
      </div>

      {/* Barra de Filtros y Búsqueda */}
      <div className="bg-card border border-border rounded-xl p-4 shadow-sm flex flex-col md:flex-row gap-4 items-center justify-between">
        <div className="relative w-full md:w-96">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder="Buscar por número, objeto o ID proveedor..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-4 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
          />
        </div>

        <div className="flex flex-wrap items-center gap-1.5 w-full md:w-auto">
          {['TODOS', 'VIGENTE', 'FINALIZADO'].map((st) => (
            <button
              key={st}
              onClick={() => setFilterTab(st)}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-all ${
                filterTab === st
                  ? 'bg-primary text-primary-foreground shadow-sm'
                  : 'bg-muted/50 text-muted-foreground hover:bg-muted hover:text-foreground'
              }`}
            >
              {st === 'TODOS' ? 'Todos' : st === 'VIGENTE' ? 'Vigentes' : 'Finalizados'}
            </button>
          ))}

          <Button
            variant="outline"
            size="sm"
            onClick={cargar}
            icon={<RefreshCw className="w-3.5 h-3.5" />}
            title="Recargar datos"
          >
            Actualizar
          </Button>
        </div>
      </div>

      {/* Tabla Principal */}
      <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden">
        {filteredContratos.length === 0 ? (
          <div className="py-12">
            <EmptyState
              icon={<FileText className="w-12 h-12 text-muted-foreground" />}
              title="No hay contratos encontrados"
              description={
                searchTerm || filterTab !== 'TODOS'
                  ? 'No se encontraron resultados para los filtros seleccionados.'
                  : 'Aún no se han registrado contratos de proveedores.'
              }
              action={
                <Button variant="outline" size="sm" onClick={() => setDialog(true)}>
                  Registrar Primer Contrato
                </Button>
              }
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/30 text-muted-foreground font-semibold text-xs uppercase tracking-wider">
                  <th className="py-3 px-4">N° Contrato</th>
                  <th className="py-3 px-4">Objeto</th>
                  <th className="py-3 px-4 text-right">Valor Total</th>
                  <th className="py-3 px-4">Periodicidad</th>
                  <th className="py-3 px-4">Vigencia</th>
                  <th className="py-3 px-4 text-center">Estado</th>
                  <th className="py-3 px-4 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filteredContratos.map((c) => {
                  const isVigente = c.estado === 'VIGENTE';
                  return (
                    <tr
                      key={c.idContratoProveedor}
                      className="hover:bg-muted/20 transition-colors"
                    >
                      <td className="py-3 px-4">
                        <div className="font-semibold text-foreground">{c.numeroContrato}</div>
                        <span className="text-xs text-muted-foreground">
                          Prov #{c.idProveedor}
                        </span>
                      </td>
                      <td className="py-3 px-4 max-w-xs truncate font-medium text-foreground" title={c.objetoContrato}>
                        {c.objetoContrato}
                      </td>
                      <td className="py-3 px-4 text-right font-bold text-foreground whitespace-nowrap">
                        {fmt(c.valorTotal)}
                      </td>
                      <td className="py-3 px-4 text-xs text-muted-foreground">
                        <span className="px-2 py-0.5 rounded-md bg-muted font-medium text-foreground">
                          {c.periodicidadPago}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-xs whitespace-nowrap">
                        <div className="text-foreground font-medium">{formatDate(c.fechaInicio)}</div>
                        <div className="text-muted-foreground text-[11px]">
                          hasta {formatDate(c.fechaFin)}
                        </div>
                      </td>
                      <td className="py-3 px-4 text-center whitespace-nowrap">
                        <Badge variant={isVigente ? 'success' : 'secondary'}>
                          {c.estado}
                        </Badge>
                      </td>
                      <td className="py-3 px-4 text-right whitespace-nowrap">
                        <div className="flex items-center justify-end gap-1.5">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => toggleEstado(c)}
                            title={isVigente ? 'Finalizar contrato' : 'Reactivar contrato'}
                            className="text-xs"
                          >
                            {isVigente ? 'Finalizar' : 'Reactivar'}
                          </Button>
                          <Button
                            variant="danger"
                            size="sm"
                            onClick={() => setDeleteTarget(c)}
                            icon={<Trash2 className="w-3.5 h-3.5" />}
                            title="Eliminar contrato"
                          />
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal Crear Contrato */}
      <Modal
        open={dialog}
        onClose={() => !isSubmitting && setDialog(false)}
        title="Registrar Nuevo Contrato de Proveedor"
        size="lg"
        footer={
          <div className="flex items-center justify-end gap-2 w-full">
            <Button
              variant="outline"
              disabled={isSubmitting}
              onClick={() => setDialog(false)}
            >
              Cancelar
            </Button>
            <Button
              variant="primary"
              loading={isSubmitting}
              onClick={crear}
              icon={<Plus className="w-4 h-4" />}
            >
              Crear Contrato
            </Button>
          </div>
        }
      >
        <form onSubmit={crear} className="space-y-4 py-2 text-sm">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              type="number"
              label="ID Proveedor *"
              placeholder="Ej. 10"
              value={form.idProveedor}
              onChange={(e) => setForm({ ...form, idProveedor: e.target.value })}
              required
            />
            <Input
              type="text"
              label="N° Contrato / Radicado *"
              placeholder="Ej. CTR-2026-004"
              value={form.numeroContrato}
              onChange={(e) => setForm({ ...form, numeroContrato: e.target.value })}
              required
            />
          </div>

          <div>
            <Input
              type="text"
              label="Objeto del Contrato *"
              placeholder="Ej. Servicio de vigilancia y seguridad privada 24/7..."
              value={form.objetoContrato}
              onChange={(e) => setForm({ ...form, objetoContrato: e.target.value })}
              required
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              type="number"
              min="0"
              label="Valor Total (COP) *"
              placeholder="0"
              value={form.valorTotal}
              onChange={(e) => setForm({ ...form, valorTotal: e.target.value })}
              required
            />
            <div>
              <label className="text-sm font-medium text-foreground block mb-1.5">
                Periodicidad de Pago
              </label>
              <select
                value={form.periodicidadPago}
                onChange={(e) => setForm({ ...form, periodicidadPago: e.target.value })}
                className="w-full py-2 px-3 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              >
                <option value="MENSUAL">Mensual</option>
                <option value="TRIMESTRAL">Trimestral</option>
                <option value="SEMESTRAL">Semestral</option>
                <option value="ANUAL">Anual</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              type="date"
              label="Fecha de Inicio *"
              value={form.fechaInicio}
              onChange={(e) => setForm({ ...form, fechaInicio: e.target.value })}
              required
            />
            <Input
              type="date"
              label="Fecha de Fin / Vencimiento *"
              value={form.fechaFin}
              onChange={(e) => setForm({ ...form, fechaFin: e.target.value })}
              required
            />
          </div>

          <div>
            <Input
              type="number"
              min="1"
              max="90"
              label="Días de Anticipación para Alerta de Vencimiento"
              placeholder="30"
              value={form.diasAlertaVenc}
              onChange={(e) => setForm({ ...form, diasAlertaVenc: e.target.value })}
            />
          </div>
        </form>
      </Modal>

      {/* Diálogo de Confirmación */}
      <ConfirmDialog
        open={!!deleteTarget}
        title="Eliminar Contrato"
        message={`¿Estás seguro de eliminar el contrato ${deleteTarget?.numeroContrato}? Esta acción no se puede deshacer.`}
        onConfirm={eliminar}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}

