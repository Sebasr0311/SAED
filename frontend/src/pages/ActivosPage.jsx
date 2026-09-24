import React, { useState, useEffect, useMemo } from 'react';
import {
  Boxes,
  Plus,
  CheckCircle2,
  AlertTriangle,
  RotateCcw,
  Search,
  Eye,
  Edit2,
  Trash2,
  DollarSign,
  Calendar,
  Layers,
  Wrench,
  Ban,
  ArrowLeftRight,
  Clock,
  ShieldCheck,
  FileText,
} from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { api } from '../lib/api.js';
import { formatCurrency, formatDate } from '../lib/utils.js';
import { toast } from 'sonner';

const CATEGORIAS_COMUNES = [
  'Ascensores y Elevadores',
  'Bombas y Equipos Hidráulicos',
  'Plantas Eléctricas y Subestaciones',
  'CCTV y Seguridad Electrónica',
  'Equipos de Gimnasio',
  'Equipos de Piscina y Zonas Húmedas',
  'Control de Acceso y Talanqueras',
  'Iluminación y Luminarias',
  'Mobiliario Zonas Comunes',
  'Herramientas y Mantenimiento',
  'Otros',
];

export default function ActivosPage() {
  const [activos, setActivos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterEstado, setFilterEstado] = useState('TODOS');
  const [filterCategoria, setFilterCategoria] = useState('TODAS');

  // Modales
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [showEstadoModal, setShowEstadoModal] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [selectedActivo, setSelectedActivo] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Form states
  const initialForm = {
    codigoActivo: '',
    nombre: '',
    categoria: '',
    fechaAdquisicion: '',
    valorAdquisicion: '',
    estado: 'OPERATIVO',
  };
  const [form, setForm] = useState(initialForm);
  const [estadoForm, setEstadoForm] = useState({ estado: 'OPERATIVO', motivo: '' });

  const cargarActivos = async () => {
    try {
      setLoading(true);
      const res = await api.get('/activos');
      const items = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : [];
      setActivos(items);
    } catch (error) {
      console.error('Error al cargar activos:', error);
      toast.error('No se pudieron cargar los activos de la copropiedad');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    cargarActivos();
  }, []);

  // Métricas
  const stats = useMemo(() => {
    const total = activos.length;
    const operativos = activos.filter((a) => a.estado === 'OPERATIVO').length;
    const enMantenimiento = activos.filter((a) => a.estado === 'MANTENIMIENTO').length;
    const dadosDeBaja = activos.filter((a) => a.estado === 'DADO_DE_BAJA').length;
    const valorTotal = activos
      .filter((a) => a.estado !== 'DADO_DE_BAJA')
      .reduce((sum, a) => sum + (Number(a.valorAdquisicion) || 0), 0);

    return { total, operativos, enMantenimiento, dadosDeBaja, valorTotal };
  }, [activos]);

  // Filtrado
  const filteredActivos = useMemo(() => {
    return activos.filter((a) => {
      const matchEstado = filterEstado === 'TODOS' || a.estado === filterEstado;
      if (!matchEstado) return false;

      const matchCat = filterCategoria === 'TODAS' || a.categoria === filterCategoria;
      if (!matchCat) return false;

      if (!searchTerm.trim()) return true;
      const term = searchTerm.toLowerCase();
      const cod = (a.codigoActivo || '').toLowerCase();
      const nom = (a.nombre || '').toLowerCase();
      const cat = (a.categoria || '').toLowerCase();
      return cod.includes(term) || nom.includes(term) || cat.includes(term);
    });
  }, [activos, filterEstado, filterCategoria, searchTerm]);

  // Handlers CRUD
  const handleOpenCreate = () => {
    setForm(initialForm);
    setShowCreateModal(true);
  };

  const handleOpenEdit = (activo) => {
    setSelectedActivo(activo);
    setForm({
      codigoActivo: activo.codigoActivo || '',
      nombre: activo.nombre || '',
      categoria: activo.categoria || '',
      fechaAdquisicion: activo.fechaAdquisicion || '',
      valorAdquisicion: activo.valorAdquisicion != null ? String(activo.valorAdquisicion) : '',
    });
    setShowEditModal(true);
  };

  const handleOpenDetail = (activo) => {
    setSelectedActivo(activo);
    setShowDetailModal(true);
  };

  const handleOpenCambiarEstado = (activo) => {
    setSelectedActivo(activo);
    setEstadoForm({
      estado: activo.estado === 'OPERATIVO' ? 'MANTENIMIENTO' : 'OPERATIVO',
      motivo: '',
    });
    setShowEstadoModal(true);
  };

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    if (!form.codigoActivo.trim()) {
      toast.error('El código del activo es obligatorio');
      return;
    }
    if (!form.nombre.trim()) {
      toast.error('El nombre del activo es obligatorio');
      return;
    }
    if (form.valorAdquisicion && Number(form.valorAdquisicion) < 0) {
      toast.error('El valor de adquisición no puede ser negativo');
      return;
    }

    try {
      setIsSubmitting(true);
      const payload = {
        codigoActivo: form.codigoActivo.trim(),
        nombre: form.nombre.trim(),
        categoria: form.categoria.trim() || null,
        fechaAdquisicion: form.fechaAdquisicion || null,
        valorAdquisicion: form.valorAdquisicion ? Number(form.valorAdquisicion) : null,
        estado: form.estado || 'OPERATIVO',
      };

      await api.post('/activos', payload);
      toast.success('Activo registrado con éxito');
      setShowCreateModal(false);
      cargarActivos();
    } catch (error) {
      const msg = error?.response?.data?.message || 'Error al registrar el activo';
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    if (!form.codigoActivo.trim()) {
      toast.error('El código del activo es obligatorio');
      return;
    }
    if (!form.nombre.trim()) {
      toast.error('El nombre del activo es obligatorio');
      return;
    }
    if (form.valorAdquisicion && Number(form.valorAdquisicion) < 0) {
      toast.error('El valor de adquisición no puede ser negativo');
      return;
    }

    try {
      setIsSubmitting(true);
      const payload = {
        codigoActivo: form.codigoActivo.trim(),
        nombre: form.nombre.trim(),
        categoria: form.categoria.trim() || null,
        fechaAdquisicion: form.fechaAdquisicion || null,
        valorAdquisicion: form.valorAdquisicion ? Number(form.valorAdquisicion) : null,
      };

      await api.put(`/activos/${selectedActivo.idActivo}`, payload);
      toast.success('Activo actualizado con éxito');
      setShowEditModal(false);
      cargarActivos();
    } catch (error) {
      const msg = error?.response?.data?.message || 'Error al actualizar el activo';
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleEstadoSubmit = async (e) => {
    e.preventDefault();
    try {
      setIsSubmitting(true);
      await api.patch(`/activos/${selectedActivo.idActivo}/estado`, {
        estado: estadoForm.estado,
        motivo: estadoForm.motivo || null,
      });
      toast.success(`Estado actualizado a ${estadoForm.estado}`);
      setShowEstadoModal(false);
      cargarActivos();
    } catch (error) {
      const msg = error?.response?.data?.message || 'Error al cambiar estado del activo';
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleConfirmBaja = async () => {
    if (!deleteTarget) return;
    try {
      await api.delete(`/activos/${deleteTarget.idActivo}`);
      toast.success(`Activo '${deleteTarget.nombre}' dado de baja satisfactoriamente`);
      setDeleteTarget(null);
      cargarActivos();
    } catch (error) {
      const msg = error?.response?.data?.message || 'Error al dar de baja el activo';
      toast.error(msg);
    }
  };

  const renderBadgeEstado = (estado) => {
    switch (estado) {
      case 'OPERATIVO':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-800">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
            Operativo
          </span>
        );
      case 'MANTENIMIENTO':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800">
            <span className="h-1.5 w-1.5 rounded-full bg-amber-500 animate-pulse" />
            En Mantenimiento
          </span>
        );
      case 'DADO_DE_BAJA':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-slate-100 text-slate-600 border border-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:border-slate-700">
            <span className="h-1.5 w-1.5 rounded-full bg-slate-400" />
            Dado de Baja
          </span>
        );
      default:
        return <Badge variant="outline">{estado}</Badge>;
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <PageHeader
        title="Activos e Inventario Físico"
        subtitle="Gestión de maquinaria, equipos e infraestructura de la copropiedad"
        action={
          <Button onClick={handleOpenCreate} className="gap-2">
            <Plus className="h-4 w-4" />
            Registrar Activo
          </Button>
        }
      />

      {/* Tarjetas de Métricas */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          title="Total Activos"
          value={stats.total}
          subtitle={`Inventario valorado en ${formatCurrency(stats.valorTotal)}`}
          icon={<Boxes className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />}
          variant="primary"
        />
        <MetricCard
          title="Operativos"
          value={stats.operativos}
          subtitle="Disponibles y en funcionamiento"
          icon={<CheckCircle2 className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />}
          variant="success"
        />
        <MetricCard
          title="En Mantenimiento"
          value={stats.enMantenimiento}
          subtitle="En revisión técnica o reparación"
          icon={<AlertTriangle className="h-5 w-5 text-amber-600 dark:text-amber-400" />}
          variant="warning"
        />
        <MetricCard
          title="Dados de Baja"
          value={stats.dadosDeBaja}
          subtitle="Retirados del inventario activo"
          icon={<Ban className="h-5 w-5 text-slate-500 dark:text-slate-400" />}
          variant="neutral"
        />
      </div>

      {/* Controles de Filtros y Búsqueda */}
      <div className="bg-card rounded-xl border border-border p-4 space-y-4">
        <div className="flex flex-col md:flex-row gap-3 items-stretch md:items-center justify-between">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <input
              type="text"
              placeholder="Buscar por código, nombre o categoría..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-9 pr-4 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <select
              value={filterCategoria}
              onChange={(e) => setFilterCategoria(e.target.value)}
              className="px-3 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
            >
              <option value="TODAS">Todas las Categorías</option>
              {CATEGORIAS_COMUNES.map((cat) => (
                <option key={cat} value={cat}>
                  {cat}
                </option>
              ))}
            </select>

            <Button variant="outline" size="sm" onClick={cargarActivos} title="Refrescar">
              <RotateCcw className="h-4 w-4" />
            </Button>
          </div>
        </div>

        {/* Pestañas de Estado */}
        <div className="flex flex-wrap gap-1 border-b border-border pb-1">
          {[
            { id: 'TODOS', label: 'Todos' },
            { id: 'OPERATIVO', label: 'Operativos' },
            { id: 'MANTENIMIENTO', label: 'En Mantenimiento' },
            { id: 'DADO_DE_BAJA', label: 'Dados de Baja' },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setFilterEstado(tab.id)}
              className={`px-3 py-1.5 text-xs sm:text-sm font-medium rounded-lg transition-colors ${
                filterEstado === tab.id
                  ? 'bg-primary/10 text-primary font-semibold'
                  : 'text-muted-foreground hover:bg-muted hover:text-foreground'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Tabla de Activos */}
      <div className="bg-card rounded-xl border border-border overflow-hidden">
        {loading ? (
          <LoadingState message="Cargando inventario de activos..." />
        ) : filteredActivos.length === 0 ? (
          <EmptyState
            icon={<Boxes className="h-12 w-12 text-muted-foreground/60" />}
            title="No se encontraron activos"
            description={
              searchTerm || filterEstado !== 'TODOS' || filterCategoria !== 'TODAS'
                ? 'Intente ajustar los filtros de búsqueda.'
                : 'No hay activos físicos registrados en esta copropiedad.'
            }
            action={
              <Button onClick={handleOpenCreate} size="sm" className="gap-2">
                <Plus className="h-4 w-4" />
                Registrar Primer Activo
              </Button>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="bg-muted/50 text-muted-foreground text-xs uppercase border-b border-border">
                <tr>
                  <th className="px-4 py-3 font-semibold">Código</th>
                  <th className="px-4 py-3 font-semibold">Nombre del Activo</th>
                  <th className="px-4 py-3 font-semibold">Categoría</th>
                  <th className="px-4 py-3 font-semibold">Adquisición</th>
                  <th className="px-4 py-3 font-semibold">Valor Registrado</th>
                  <th className="px-4 py-3 font-semibold">Estado</th>
                  <th className="px-4 py-3 font-semibold text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filteredActivos.map((activo) => {
                  const isDadoDeBaja = activo.estado === 'DADO_DE_BAJA';
                  return (
                    <tr
                      key={activo.idActivo}
                      className="hover:bg-muted/30 transition-colors"
                    >
                      <td className="px-4 py-3 font-mono font-medium text-foreground">
                        {activo.codigoActivo}
                      </td>
                      <td className="px-4 py-3 font-medium text-foreground">
                        {activo.nombre}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {activo.categoria ? (
                          <span className="px-2 py-0.5 rounded text-xs bg-muted text-foreground border border-border">
                            {activo.categoria}
                          </span>
                        ) : (
                          <span className="text-muted-foreground/60 italic">Sin categoría</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {activo.fechaAdquisicion ? formatDate(activo.fechaAdquisicion) : '-'}
                      </td>
                      <td className="px-4 py-3 font-medium text-foreground">
                        {activo.valorAdquisicion != null
                          ? formatCurrency(activo.valorAdquisicion)
                          : '-'}
                      </td>
                      <td className="px-4 py-3">
                        {renderBadgeEstado(activo.estado)}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleOpenDetail(activo)}
                            title="Ver Hoja de Vida"
                            className="h-8 w-8 p-0"
                          >
                            <Eye className="h-4 w-4 text-muted-foreground hover:text-foreground" />
                          </Button>

                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleOpenEdit(activo)}
                            disabled={isDadoDeBaja}
                            title={isDadoDeBaja ? 'Activo dado de baja no modificable' : 'Editar activo'}
                            className="h-8 w-8 p-0"
                          >
                            <Edit2 className="h-4 w-4 text-muted-foreground hover:text-foreground" />
                          </Button>

                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleOpenCambiarEstado(activo)}
                            disabled={isDadoDeBaja}
                            title={isDadoDeBaja ? 'Estado terminal no modificable' : 'Cambiar estado'}
                            className="h-8 w-8 p-0"
                          >
                            <ArrowLeftRight className="h-4 w-4 text-amber-600 hover:text-amber-700" />
                          </Button>

                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setDeleteTarget(activo)}
                            disabled={isDadoDeBaja}
                            title={isDadoDeBaja ? 'Ya se encuentra dado de baja' : 'Dar de baja'}
                            className="h-8 w-8 p-0 text-red-500 hover:text-red-700 hover:bg-red-50 dark:hover:bg-red-950/30"
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
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

      {/* Modal Registrar Activo */}
      <Modal
        open={showCreateModal}
        onClose={() => !isSubmitting && setShowCreateModal(false)}
        title="Registrar Nuevo Activo"
        size="lg"
      >
        <form onSubmit={handleCreateSubmit} className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1 text-foreground">
                Código del Activo <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                required
                maxLength={50}
                placeholder="Ej. ASC-001, BOM-02, CCTV-EXT"
                value={form.codigoActivo}
                onChange={(e) => setForm({ ...form, codigoActivo: e.target.value.toUpperCase() })}
                className="w-full px-3 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30 uppercase font-mono"
              />
              <p className="text-xs text-muted-foreground mt-1">
                Identificador único del activo dentro de la copropiedad.
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium mb-1 text-foreground">
                Categoría
              </label>
              <input
                type="text"
                list="categorias-list"
                maxLength={50}
                placeholder="Seleccione o escriba categoría"
                value={form.categoria}
                onChange={(e) => setForm({ ...form, categoria: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <datalist id="categorias-list">
                {CATEGORIAS_COMUNES.map((c) => (
                  <option key={c} value={c} />
                ))}
              </datalist>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium mb-1 text-foreground">
              Nombre o Descripción del Activo <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              required
              maxLength={100}
              placeholder="Ej. Ascensor Principal Torre 1 Mitsubishi 8 Pasajeros"
              value={form.nombre}
              onChange={(e) => setForm({ ...form, nombre: e.target.value })}
              className="w-full px-3 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1 text-foreground">
                Fecha de Adquisición
              </label>
              <input
                type="date"
                value={form.fechaAdquisicion}
                onChange={(e) => setForm({ ...form, fechaAdquisicion: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-1 text-foreground">
                Valor de Adquisición (COP)
              </label>
              <input
                type="number"
                min="0"
                step="0.01"
                placeholder="0.00"
                value={form.valorAdquisicion}
                onChange={(e) => setForm({ ...form, valorAdquisicion: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-1 text-foreground">
                Estado Inicial
              </label>
              <select
                value={form.estado}
                onChange={(e) => setForm({ ...form, estado: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
              >
                <option value="OPERATIVO">Operativo</option>
                <option value="MANTENIMIENTO">En Mantenimiento</option>
              </select>
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-4 border-t border-border">
            <Button
              type="button"
              variant="outline"
              onClick={() => setShowCreateModal(false)}
              disabled={isSubmitting}
            >
              Cancelar
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Guardando...' : 'Registrar Activo'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal Editar Activo */}
      <Modal
        open={showEditModal}
        onClose={() => !isSubmitting && setShowEditModal(false)}
        title={`Editar Activo: ${selectedActivo?.codigoActivo || ''}`}
        size="lg"
      >
        <form onSubmit={handleEditSubmit} className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1 text-foreground">
                Código del Activo <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                required
                maxLength={50}
                value={form.codigoActivo}
                onChange={(e) => setForm({ ...form, codigoActivo: e.target.value.toUpperCase() })}
                className="w-full px-3 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30 uppercase font-mono"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-1 text-foreground">
                Categoría
              </label>
              <input
                type="text"
                list="categorias-list-edit"
                maxLength={50}
                value={form.categoria}
                onChange={(e) => setForm({ ...form, categoria: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <datalist id="categorias-list-edit">
                {CATEGORIAS_COMUNES.map((c) => (
                  <option key={c} value={c} />
                ))}
              </datalist>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium mb-1 text-foreground">
              Nombre del Activo <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              required
              maxLength={100}
              value={form.nombre}
              onChange={(e) => setForm({ ...form, nombre: e.target.value })}
              className="w-full px-3 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1 text-foreground">
                Fecha de Adquisición
              </label>
              <input
                type="date"
                value={form.fechaAdquisicion}
                onChange={(e) => setForm({ ...form, fechaAdquisicion: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-1 text-foreground">
                Valor de Adquisición (COP)
              </label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.valorAdquisicion}
                onChange={(e) => setForm({ ...form, valorAdquisicion: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-4 border-t border-border">
            <Button
              type="button"
              variant="outline"
              onClick={() => setShowEditModal(false)}
              disabled={isSubmitting}
            >
              Cancelar
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Guardando...' : 'Guardar Cambios'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal Cambiar Estado */}
      <Modal
        open={showEstadoModal}
        onClose={() => !isSubmitting && setShowEstadoModal(false)}
        title="Cambiar Estado Operativo del Activo"
        size="md"
      >
        <form onSubmit={handleEstadoSubmit} className="space-y-4">
          <div className="p-3 rounded-lg bg-muted/60 border border-border text-sm">
            <div className="flex justify-between items-center mb-1">
              <span className="font-semibold text-foreground">{selectedActivo?.nombre}</span>
              <span className="font-mono text-xs text-muted-foreground">{selectedActivo?.codigoActivo}</span>
            </div>
            <div className="text-xs text-muted-foreground">
              Estado actual: <span className="font-medium text-foreground">{selectedActivo?.estado}</span>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium mb-1 text-foreground">
              Nuevo Estado <span className="text-red-500">*</span>
            </label>
            <select
              value={estadoForm.estado}
              onChange={(e) => setEstadoForm({ ...estadoForm, estado: e.target.value })}
              className="w-full px-3 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
            >
              <option value="OPERATIVO">OPERATIVO (En servicio)</option>
              <option value="MANTENIMIENTO">MANTENIMIENTO (Pausado por servicio o falla)</option>
              <option value="DADO_DE_BAJA">DADO_DE_BAJA (Retiro definitivo del equipo)</option>
            </select>
          </div>

          {estadoForm.estado === 'DADO_DE_BAJA' && (
            <div className="p-3 rounded-lg bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-800 text-xs text-amber-800 dark:text-amber-300 flex items-start gap-2">
              <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" />
              <span>
                <strong>Atención:</strong> La baja de un activo es un estado terminal e irreversible. No podrá volver a activarse.
              </span>
            </div>
          )}

          <div>
            <label className="block text-sm font-medium mb-1 text-foreground">
              Motivo o Justificación del Cambio
            </label>
            <textarea
              rows={3}
              placeholder="Describa el motivo del cambio de estado o diagnóstico técnico..."
              value={estadoForm.motivo}
              onChange={(e) => setEstadoForm({ ...estadoForm, motivo: e.target.value })}
              className="w-full px-3 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none"
            />
          </div>

          <div className="flex justify-end gap-2 pt-4 border-t border-border">
            <Button
              type="button"
              variant="outline"
              onClick={() => setShowEstadoModal(false)}
              disabled={isSubmitting}
            >
              Cancelar
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Actualizando...' : 'Confirmar Cambio'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal Detalle / Hoja de Vida */}
      <Modal
        open={showDetailModal}
        onClose={() => setShowDetailModal(false)}
        title={`Hoja de Vida: ${selectedActivo?.nombre || ''}`}
        size="lg"
      >
        {selectedActivo && (
          <div className="space-y-6">
            {/* Header del Activo */}
            <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-2 p-4 bg-muted/40 rounded-xl border border-border">
              <div>
                <span className="font-mono text-xs text-primary font-semibold tracking-wider">
                  {selectedActivo.codigoActivo}
                </span>
                <h3 className="text-lg font-bold text-foreground mt-0.5">
                  {selectedActivo.nombre}
                </h3>
              </div>
              <div>{renderBadgeEstado(selectedActivo.estado)}</div>
            </div>

            {/* Grid de Atributos */}
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 text-sm">
              <div className="p-3 bg-card rounded-lg border border-border">
                <div className="text-xs text-muted-foreground">Categoría</div>
                <div className="font-semibold text-foreground mt-0.5">
                  {selectedActivo.categoria || 'Sin clasificar'}
                </div>
              </div>

              <div className="p-3 bg-card rounded-lg border border-border">
                <div className="text-xs text-muted-foreground">Fecha Adquisición</div>
                <div className="font-semibold text-foreground mt-0.5">
                  {selectedActivo.fechaAdquisicion ? formatDate(selectedActivo.fechaAdquisicion) : 'No registrada'}
                </div>
              </div>

              <div className="p-3 bg-card rounded-lg border border-border">
                <div className="text-xs text-muted-foreground">Valor de Compra</div>
                <div className="font-semibold text-foreground mt-0.5">
                  {selectedActivo.valorAdquisicion != null
                    ? formatCurrency(selectedActivo.valorAdquisicion)
                    : 'No registrado'}
                </div>
              </div>
            </div>

            {/* Historial de Mantenimientos (Placeholder Próximamente GAP-F9-03) */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h4 className="font-semibold text-sm text-foreground flex items-center gap-2">
                  <Wrench className="h-4 w-4 text-primary" />
                  Historial de Mantenimientos y Órdenes de Servicio
                </h4>
                <span className="text-xs px-2 py-0.5 bg-primary/10 text-primary font-medium rounded-full">
                  Próximamente GAP-F9-03
                </span>
              </div>

              <div className="border border-dashed border-border rounded-xl p-6 text-center space-y-2 bg-muted/20">
                <Clock className="h-8 w-8 text-muted-foreground/60 mx-auto" />
                <p className="text-sm font-medium text-foreground">
                  Bitácora de Mantenimientos Preventivos y Correctivos
                </p>
                <p className="text-xs text-muted-foreground max-w-md mx-auto">
                  La trazabilidad de intervenciones técnicas, contratos de soporte asociados y registro de bitácora estará vinculada directamente en el siguiente módulo de la Fase 9.
                </p>
              </div>
            </div>

            <div className="flex justify-end pt-4 border-t border-border">
              <Button variant="outline" onClick={() => setShowDetailModal(false)}>
                Cerrar Hoja de Vida
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* Diálogo Confirmar Baja */}
      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleConfirmBaja}
        title="Dar de Baja Activo"
        message={`¿Está seguro de retirar y dar de baja el activo '${deleteTarget?.nombre}' (${deleteTarget?.codigoActivo})? Esta acción marcará el activo como DADO_DE_BAJA de forma permanente en el inventario.`}
        confirmLabel="Dar de Baja"
        danger={true}
      />
    </div>
  );
}
