import { useEffect, useState, useMemo } from 'react';
import api from '../lib/api.js';
import { Card, CardContent } from '../components/ui/card.tsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Input } from '../components/ui/input.tsx';
import { Label } from '../components/ui/label.tsx';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog.tsx';
import { toast } from 'sonner';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';

const FILTER_SELECT =
  'h-10 w-full rounded-md border border-input bg-surface px-3 text-sm text-on-surface transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 sm:w-auto';
import {
  Search,
  RefreshCw,
  Mail,
  KeyRound,
  CheckCircle2,
  AlertCircle,
  XCircle,
  Clock,
  Copy,
  Check,
  Eye,
  EyeOff,
  Sparkles,
  Building2,
  User,
  CreditCard,
  Send,
  ShieldCheck,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Trash2,
} from 'lucide-react';

export default function SuperAdminOnboardingPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [filterPago, setFilterPago] = useState('TODOS');
  const [filterCorreo, setFilterCorreo] = useState('TODOS');
  const [filterTipo, setFilterTipo] = useState('TODOS');
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Modal Reenviar Correo
  const [resendModal, setResendModal] = useState(false);
  const [selectedItemForResend, setSelectedItemForResend] = useState(null);
  const [resendEmail, setResendEmail] = useState('');
  const [resending, setResending] = useState(false);
  const [resendResult, setResendResult] = useState(null);

  // Modal Editar Credenciales
  const [editModal, setEditModal] = useState(false);
  const [selectedItemForEdit, setSelectedItemForEdit] = useState(null);
  const [editUsername, setEditUsername] = useState('');
  const [editEmail, setEditEmail] = useState('');
  const [editPassword, setEditPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [editNotify, setEditNotify] = useState(true);
  const [savingCredentials, setSavingCredentials] = useState(false);

  // Modal Aprobar Manual
  const [approveModal, setApproveModal] = useState(false);
  const [selectedItemForApprove, setSelectedItemForApprove] = useState(null);
  const [approving, setApproving] = useState(false);

  // Modal Rechazar
  const [rechazarModal, setRechazarModal] = useState(false);
  const [selectedItemForRechazar, setSelectedItemForRechazar] = useState(null);
  const [rechazarMotivo, setRechazarMotivo] = useState('');
  const [rechazando, setRechazando] = useState(false);

  // Modal Eliminar
  const [eliminarModal, setEliminarModal] = useState(false);
  const [selectedItemForEliminar, setSelectedItemForEliminar] = useState(null);
  const [eliminando, setEliminando] = useState(false);

  // Copied tracking
  const [copiedId, setCopiedId] = useState(null);

  const [loadError, setLoadError] = useState(null);

  async function loadData() {
    try {
      setLoading(true);
      setLoadError(null);
      const res = await api.get('/platform/onboarding/solicitudes');
      const list = res?.data || res || [];
      setItems(Array.isArray(list) ? list : []);
    } catch (err) {
      toast.error('Error al cargar solicitudes de onboarding');
      setLoadError(err?.message || 'No fue posible consultar las solicitudes.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  function handleCopy(text, id) {
    if (!text) return;
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    toast.success('Copiado al portapapeles');
    setTimeout(() => setCopiedId(null), 2500);
  }

  function generateSecurePassword() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*';
    let pwd = '';
    for (let i = 0; i < 12; i++) {
      pwd += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return pwd;
  }

  // Métricas
  const metrics = useMemo(() => {
    const total = items.length;
    const pagados = items.filter((i) => i.estadoPago === 'APROBADO' || i.estadoIntencion === 'COMPLETADA').length;
    const pendientes = items.filter((i) => i.estadoPago === 'PENDIENTE' && i.estadoIntencion !== 'COMPLETADA').length;
    const enviadosReal = items.filter((i) => i.estadoCorreo === 'ENVIADO').length;
    const simulados = items.filter((i) => i.estadoCorreo === 'SIMULADO').length;
    const fallidos = items.filter((i) => i.estadoCorreo === 'FALLIDO').length;
    return { total, pagados, pendientes, enviadosReal, simulados, fallidos };
  }, [items]);

  // Filtrado
  const filteredItems = useMemo(() => {
    return items.filter((item) => {
      const q = search.toLowerCase().trim();
      const matchSearch =
        !q ||
        (item.nombreOrganizacion && item.nombreOrganizacion.toLowerCase().includes(q)) ||
        (item.identificacionFiscal && item.identificacionFiscal.toLowerCase().includes(q)) ||
        (item.adminNombreCompleto && item.adminNombreCompleto.toLowerCase().includes(q)) ||
        (item.adminUsername && item.adminUsername.toLowerCase().includes(q)) ||
        (item.adminEmail && item.adminEmail.toLowerCase().includes(q)) ||
        (item.referencia && item.referencia.toLowerCase().includes(q));

      const matchPago =
        filterPago === 'TODOS' ||
        (filterPago === 'APROBADO' && (item.estadoPago === 'APROBADO' || item.estadoIntencion === 'COMPLETADA')) ||
        (filterPago === 'PENDIENTE' && item.estadoPago === 'PENDIENTE' && item.estadoIntencion !== 'COMPLETADA') ||
        (filterPago === 'RECHAZADO' && item.estadoPago === 'RECHAZADO');

      const matchCorreo =
        filterCorreo === 'TODOS' ||
        item.estadoCorreo === filterCorreo;

      const matchTipo =
        filterTipo === 'TODOS' ||
        (filterTipo === 'NATURAL' && item.tipoPersona === 'NATURAL') ||
        (filterTipo === 'JURIDICA' && (item.tipoPersona === 'JURIDICA' || !item.tipoPersona));

      return matchSearch && matchPago && matchCorreo && matchTipo;
    });
  }, [items, search, filterPago, filterCorreo, filterTipo]);

  // Reset de página al cambiar filtros
  useEffect(() => {
    setCurrentPage(1);
  }, [search, filterPago, filterCorreo, filterTipo, pageSize]);

  // Paginación
  const totalItems = filteredItems.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const startIndex = (currentPage - 1) * pageSize;
  const endIndex = Math.min(startIndex + pageSize, totalItems);
  const paginatedItems = useMemo(() => {
    return filteredItems.slice(startIndex, endIndex);
  }, [filteredItems, startIndex, endIndex]);

  function formatCurrency(amount) {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: 'COP',
      maximumFractionDigits: 0,
    }).format(amount || 0);
  }

  // Reenviar correo handlers
  function openResendModal(item) {
    setSelectedItemForResend(item);
    setResendEmail(item.adminEmail || '');
    setResendResult(null);
    setResendModal(true);
  }

  async function handleExecuteResend(e) {
    e.preventDefault();
    if (!resendEmail.trim()) {
      toast.error('El correo de destino es obligatorio');
      return;
    }

    try {
      setResending(true);
      const payload = {
        referencia: selectedItemForResend.referencia,
        idOrganizacion: selectedItemForResend.idOrganizacion,
        idUsuario: selectedItemForResend.idUsuario,
        emailDestino: resendEmail.trim(),
      };

      const res = await api.post('/platform/onboarding/reenviar-correo', payload);
      const data = res?.data || res;
      setResendResult(data);

      if (data.estado === 'ENVIADO') {
        toast.success('Enlace de activación enviado. La persona definirá su propia contraseña.');
      } else if (data.estado === 'SIMULADO') {
        toast.info('Correo procesado en modo simulación (local). No se envió ningún mensaje real.');
      } else {
        toast.warning('El envío falló en el proveedor: ' + (data.detalle || ''));
      }
      loadData();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Error al reenviar el enlace de activación');
    } finally {
      setResending(false);
    }
  }

  // Editar credenciales handlers
  function openEditModal(item) {
    setSelectedItemForEdit(item);
    setEditUsername(item.adminUsername || '');
    setEditEmail(item.adminEmail || '');
    setEditPassword('');
    setShowPassword(false);
    setEditNotify(true);
    setEditModal(true);
  }

  async function handleSaveCredentials(e) {
    e.preventDefault();
    if (!editUsername.trim() || editUsername.trim().length < 3) {
      toast.error('El nombre de usuario debe tener al menos 3 caracteres');
      return;
    }
    if (!editEmail.trim() || !editEmail.includes('@')) {
      toast.error('Por favor ingresa un correo válido');
      return;
    }
    if (editPassword && editPassword.length < 6) {
      toast.error('Si modificas la contraseña, debe tener al menos 6 caracteres');
      return;
    }

    try {
      setSavingCredentials(true);
      const payload = {
        idUsuario: selectedItemForEdit.idUsuario,
        nuevoUsername: editUsername.trim(),
        nuevoEmail: editEmail.trim(),
        nuevaPassword: editPassword.trim() || null,
        reenviarCorreo: editNotify && Boolean(editPassword.trim()),
      };

      const res = await api.put('/platform/onboarding/credenciales', payload);
      const data = res?.data || res;
      toast.success(data?.mensaje || 'Credenciales actualizadas exitosamente');
      setEditModal(false);
      loadData();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Error al actualizar credenciales');
    } finally {
      setSavingCredentials(false);
    }
  }

  // Aprobar manual handlers
  function openApproveModal(item) {
    setSelectedItemForApprove(item);
    setApproveModal(true);
  }

  async function handleExecuteApprove() {
    if (!selectedItemForApprove) return;
    try {
      setApproving(true);
      await api.post('/platform/onboarding/aprobar-manual', {
        referencia: selectedItemForApprove.referencia,
      });
      toast.success('¡Organización y administrador materializados exitosamente!');
      setApproveModal(false);
      loadData();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Error al aprobar solicitud');
    } finally {
      setApproving(false);
    }
  }

  // Rechazar handlers
  function openRechazarModal(item) {
    setSelectedItemForRechazar(item);
    setRechazarMotivo('');
    setRechazarModal(true);
  }

  async function handleExecuteRechazar() {
    if (!selectedItemForRechazar) return;
    try {
      setRechazando(true);
      await api.post('/platform/onboarding/rechazar', {
        referencia: selectedItemForRechazar.referencia,
        motivo: rechazarMotivo.trim() || 'Rechazado por administrador de plataforma',
      });
      toast.success('Solicitud rechazada correctamente');
      setRechazarModal(false);
      loadData();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Error al rechazar solicitud');
    } finally {
      setRechazando(false);
    }
  }

  // Eliminar handlers
  function openEliminarModal(item) {
    setSelectedItemForEliminar(item);
    setEliminarModal(true);
  }

  async function handleExecuteEliminar() {
    if (!selectedItemForEliminar) return;
    try {
      setEliminando(true);
      await api.delete(`/platform/onboarding/${selectedItemForEliminar.referencia}`);
      toast.success('Solicitud eliminada correctamente');
      setEliminarModal(false);
      loadData();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Error al eliminar solicitud');
    } finally {
      setEliminando(false);
    }
  }

  return (
    <PageContainer>
      <PageHeader
        title="Suscripciones y onboarding"
        subtitle="Solicitudes de suscripción de organizaciones y personas naturales, con trazabilidad de pagos y correos de activación."
        action={
          <Button variant="outline" size="sm" onClick={loadData} disabled={loading} className="min-h-10 gap-2 sm:min-h-9">
            <RefreshCw className={loading ? 'animate-spin' : ''} aria-hidden="true" />
            Actualizar
          </Button>
        }
      />

      {loading ? (
        <div role="status" aria-busy="true" aria-label="Cargando indicadores" className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-28 rounded-lg" />
          ))}
        </div>
      ) : loadError ? null : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <MetricCard label="Total de solicitudes" value={metrics.total} context="Registros en el sistema" icon={Building2} />
          <MetricCard
            label="Activas o pagadas"
            value={metrics.pagados}
            context="Materializadas y con acceso"
            icon={ShieldCheck}
            variant="success"
          />
          <MetricCard
            label="Pendientes de pago"
            value={metrics.pendientes}
            context="Esperando confirmación de Wompi"
            icon={CreditCard}
            variant={metrics.pendientes > 0 ? 'warning' : 'secondary'}
          />
          <MetricCard
            label="Correos de activación"
            value={
              <span className="flex items-baseline gap-1.5">
                <span>{metrics.enviadosReal}</span>
                <span className="font-sans text-xs font-semibold text-text-muted">enviados ·</span>
                <span>{metrics.simulados}</span>
                <span className="font-sans text-xs font-semibold text-text-muted">simulados</span>
              </span>
            }
            context={metrics.fallidos > 0 ? `${metrics.fallidos} fallidos` : 'Sin fallos de envío'}
            icon={Mail}
            variant={metrics.fallidos > 0 ? 'danger' : 'info'}
          />
        </div>
      )}

      {/* Filter and Search Bar */}
      <Card className="bg-card border-border shadow-sm">
        <CardContent className="p-4">
          <div className="flex flex-col md:flex-row gap-3 items-center justify-between">
            <div className="relative w-full md:w-96">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" aria-hidden="true" />
              <Input
                type="search"
                aria-label="Buscar solicitudes"
                placeholder="Buscar por organización, persona, usuario, email, NIT o ref..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="h-10 pl-9"
              />
            </div>
            <div className="flex flex-wrap items-center gap-2 w-full md:w-auto">
              {/* Filtro Pago */}
              <select
                aria-label="Filtrar por pago"
                className={FILTER_SELECT}
                value={filterPago}
                onChange={(e) => setFilterPago(e.target.value)}
              >
                <option value="TODOS">Todos los pagos</option>
                <option value="APROBADO">Pagado o aprobado</option>
                <option value="PENDIENTE">Pendiente en Wompi</option>
                <option value="RECHAZADO">Rechazado</option>
              </select>

              {/* Filtro Correo */}
              <select
                aria-label="Filtrar por correo"
                className={FILTER_SELECT}
                value={filterCorreo}
                onChange={(e) => setFilterCorreo(e.target.value)}
              >
                <option value="TODOS">Todos los correos</option>
                <option value="ENVIADO">Enviado</option>
                <option value="SIMULADO">Simulado (local)</option>
                <option value="FALLIDO">Fallido</option>
                <option value="PENDIENTE">Pendiente</option>
              </select>

              {/* Filtro Tipo */}
              <select
                aria-label="Filtrar por tipo"
                className={FILTER_SELECT}
                value={filterTipo}
                onChange={(e) => setFilterTipo(e.target.value)}
              >
                <option value="TODOS">Todos los tipos</option>
                <option value="NATURAL">Persona natural</option>
                <option value="JURIDICA">Persona jurídica</option>
              </select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Main Table */}
      <Card className="bg-card border-border shadow-sm overflow-hidden">
        <CardContent className="p-0">
          {loading ? (
            <div className="p-6 space-y-4" role="status" aria-busy="true" aria-label="Cargando solicitudes">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : loadError ? (
            <ErrorState className="m-4" title="No se pudieron cargar las solicitudes" message={loadError} onRetry={loadData} />
          ) : filteredItems.length === 0 ? (
            <EmptyState
              icon={Building2}
              title="No se encontraron solicitudes"
              subtitle={
                items.length === 0
                  ? 'Aún no se han registrado organizaciones en la plataforma.'
                  : 'No hay registros que coincidan con los filtros aplicados.'
              }
            >
              {items.length > 0 && (
                <Button
                  variant="outline"
                  size="sm"
                  className="mt-3"
                  onClick={() => {
                    setSearch('');
                    setFilterPago('TODOS');
                    setFilterCorreo('TODOS');
                    setFilterTipo('TODOS');
                  }}
                >
                  Limpiar filtros
                </Button>
              )}
            </EmptyState>
          ) : (
            <>
              <div className="max-h-[70vh] overflow-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Organización o titular</th>
                    <th scope="col">Administrador y acceso</th>
                    <th scope="col">Plan y membresía</th>
                    <th scope="col">Pago Wompi</th>
                    <th scope="col">Correo de activación</th>
                    <th scope="col" className="text-right">Acciones</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {paginatedItems.map((item, idx) => {
                    const isNatural = item.tipoPersona === 'NATURAL';
                    const isPaid = item.estadoPago === 'APROBADO' || item.estadoIntencion === 'COMPLETADA';
                    const isPendingPayment = !isPaid && item.estadoPago === 'PENDIENTE';

                    return (
                      <tr key={item.referencia || idx} className="hover:bg-muted/20 transition-colors">
                        {/* Organización o titular */}
                        <td className="align-top">
                          <div className="font-semibold text-foreground flex items-center gap-1.5">
                            {isNatural ? (
                              <User className="h-4 w-4 text-tint-blue-fg shrink-0" />
                            ) : (
                              <Building2 className="h-4 w-4 text-primary-text shrink-0" />
                            )}
                            <span>{item.nombreOrganizacion || 'Sin nombre'}</span>
                          </div>
                          <div className="flex items-center gap-2 mt-1">
                            <Badge
                              variant="outline"
                              className={`text-xs px-1.5 py-0 font-normal ${
                                isNatural
                                  ? 'border-tint-blue-fg/25 text-tint-blue-fg bg-tint-blue-bg'
                                  : 'border-primary/40 text-primary-text bg-tint-primary-bg'
                              }`}
                            >
                              {isNatural ? 'Persona Natural' : 'Persona Jurídica'}
                            </Badge>
                            <span className="text-xs text-muted-foreground font-mono">
                              {item.tipoDocumento ? `${item.tipoDocumento}: ` : 'ID: '}
                              {item.identificacionFiscal || item.numeroDocumento || '-'}
                            </span>
                          </div>
                          {item.fechaRegistro && (
                            <div className="text-xs text-muted-foreground mt-1">
                              Registrado: {item.fechaRegistro}
                            </div>
                          )}
                        </td>

                        {/* Administrador y acceso */}
                        <td className="align-top">
                          <div className="font-medium text-foreground">
                            {item.adminNombreCompleto || 'No asignado'}
                          </div>
                          <div className="flex items-center gap-1.5 mt-0.5">
                            <span className="text-xs font-mono text-muted-foreground bg-muted/50 px-1.5 py-0.5 rounded border border-border">
                              @{item.adminUsername || 'sin_usuario'}
                            </span>
                            <button
                              onClick={() => handleCopy(item.adminUsername, `usr-${idx}`)}
                              className="text-muted-foreground hover:text-foreground transition-colors p-0.5"
                              title="Copiar usuario"
                            >
                              {copiedId === `usr-${idx}` ? (
                                <Check className="h-3 w-3 text-tint-green-fg" />
                              ) : (
                                <Copy className="h-3 w-3" />
                              )}
                            </button>
                          </div>
                          <div className="flex items-center gap-1.5 mt-1 text-xs text-muted-foreground">
                            <span className="truncate max-w-[180px]">{item.adminEmail || '-'}</span>
                            {item.adminEmail && (
                              <button
                                onClick={() => handleCopy(item.adminEmail, `email-${idx}`)}
                                className="text-muted-foreground hover:text-foreground transition-colors p-0.5"
                                title="Copiar correo"
                              >
                                {copiedId === `email-${idx}` ? (
                                  <Check className="h-3 w-3 text-tint-green-fg" />
                                ) : (
                                  <Copy className="h-3 w-3" />
                                )}
                              </button>
                            )}
                          </div>
                        </td>

                        {/* Plan y membresía */}
                        <td className="align-top">
                          <div className="font-medium text-foreground">{item.planNombre || 'Plan Comercial'}</div>
                          <div className="flex items-center gap-1.5 mt-1">
                            <Badge variant="outline" className="text-xs px-1.5 py-0">
                              {item.cicloFacturacion || 'MENSUAL'}
                            </Badge>
                            {item.esPrueba && (
                              <Badge variant="secondary" className="text-xs px-1.5 py-0 bg-tint-amber-bg text-tint-amber-fg">
                                Prueba
                              </Badge>
                            )}
                          </div>
                          <div className="text-xs font-mono text-muted-foreground mt-1">
                            {formatCurrency(item.montoPesos)}
                          </div>
                        </td>

                        {/* Pago Wompi */}
                        <td className="align-top">
                          {isPaid ? (
                            <Badge className="bg-tint-green-bg text-tint-green-fg border border-tint-green-fg/25 gap-1 text-xs">
                              <CheckCircle2 className="h-3 w-3 text-tint-green-fg" />
                              Aprobado
                            </Badge>
                          ) : item.estadoPago === 'RECHAZADO' ? (
                            <Badge className="bg-destructive/10 text-destructive border border-destructive/20 gap-1 text-xs">
                              <XCircle className="h-3 w-3 text-destructive" />
                              Rechazado
                            </Badge>
                          ) : (
                            <Badge className="bg-tint-amber-bg text-tint-amber-fg border border-tint-amber-fg/25 gap-1 text-xs">
                              <Clock className="h-3 w-3 text-tint-amber-fg" />
                              Pendiente en Wompi
                            </Badge>
                          )}
                          <div className="text-xs font-mono text-muted-foreground mt-1 truncate max-w-[140px]" title={item.referencia}>
                            Ref: {item.referencia}
                          </div>
                        </td>

                        {/* Correo de activación */}
                        <td className="align-top">
                          {item.estadoCorreo === 'ENVIADO' ? (
                            <Badge className="bg-tint-green-bg text-tint-green-fg border border-tint-green-fg/25 gap-1 text-xs">
                              <CheckCircle2 className="h-3 w-3 text-tint-green-fg" />
                              Enviado (Brevo)
                            </Badge>
                          ) : item.estadoCorreo === 'SIMULADO' ? (
                            <Badge className="bg-tint-amber-bg text-tint-amber-fg border border-tint-amber-fg/25 gap-1 text-xs" title="No hay BREVO_API_KEY en entorno">
                              <AlertCircle className="h-3 w-3 text-tint-amber-fg" />
                              Simulado (local)
                            </Badge>
                          ) : item.estadoCorreo === 'FALLIDO' ? (
                            <Badge className="bg-destructive/10 text-destructive border border-destructive/20 gap-1 text-xs" title={item.detalleCorreo}>
                              <XCircle className="h-3 w-3 text-destructive" />
                              Error al Enviar
                            </Badge>
                          ) : (
                            <Badge variant="outline" className="text-muted-foreground gap-1 text-xs">
                              <Clock className="h-3 w-3" />
                              Pendiente
                            </Badge>
                          )}
                          {item.detalleCorreo && (
                            <div className="text-xs text-muted-foreground mt-1 max-w-[190px] truncate" title={item.detalleCorreo}>
                              {item.detalleCorreo}
                            </div>
                          )}
                          {item.fechaCorreo && (
                            <div className="text-xs text-muted-foreground/70">
                              Último: {item.fechaCorreo}
                            </div>
                          )}
                        </td>

                        {/* Acciones */}
                        <td className="align-top text-right">
                          <div className="flex items-center justify-end gap-1.5">
                            {/* Reenviar Correo */}
                            <Button
                              variant="outline"
                              size="sm"
                              className="h-8 px-2.5 gap-1.5 text-xs text-foreground hover:bg-muted"
                              onClick={() => openResendModal(item)}
                              title="Reenviar enlace de activación"
                            >
                              <Mail className="h-3.5 w-3.5 text-primary" />
                              <span className="hidden xl:inline">Reenviar</span>
                            </Button>

                            {/* Editar Credenciales */}
                            {item.idUsuario && (
                              <Button
                                variant="outline"
                                size="sm"
                                className="h-8 px-2.5 gap-1.5 text-xs text-foreground hover:bg-muted"
                                onClick={() => openEditModal(item)}
                                title="Editar usuario y contraseña"
                              >
                                <KeyRound className="h-3.5 w-3.5 text-tint-amber-fg" />
                                <span className="hidden xl:inline">Editar</span>
                              </Button>
                            )}

                            {/* Si está pendiente de pago, opción de aprobar manual */}
                            {isPendingPayment && (
                              <Button
                                variant="secondary"
                                size="sm"
                                className="h-8 px-2.5 gap-1.5 text-xs bg-tint-green-bg text-tint-green-fg hover:bg-tint-green-bg border border-tint-green-fg/25"
                                onClick={() => openApproveModal(item)}
                                title="Aprobar pago manualmente y activar"
                              >
                                <Check className="h-3.5 w-3.5" />
                                <span className="hidden xl:inline">Aprobar</span>
                              </Button>
                            )}

                            {/* Rechazar: solo si no está aprobada/completada */}
                            {item.estadoIntencion !== 'APROBADA' && item.estadoIntencion !== 'COMPLETADA' && (
                              <Button
                                variant="outline"
                                size="sm"
                                className="h-8 px-2.5 gap-1.5 text-xs text-tint-amber-fg hover:text-tint-amber-fg hover:bg-tint-amber-bg border-tint-amber-fg/25"
                                onClick={() => openRechazarModal(item)}
                                title="Rechazar solicitud de onboarding"
                              >
                                <XCircle className="h-3.5 w-3.5" />
                                <span className="hidden xl:inline">Rechazar</span>
                              </Button>
                            )}

                            {/* Eliminar: solo si PENDIENTE o RECHAZADA (no APROBADA) */}
                            {item.estadoIntencion !== 'APROBADA' && item.estadoIntencion !== 'COMPLETADA' && (
                              <Button
                                variant="outline"
                                size="sm"
                                className="h-8 px-2.5 gap-1.5 text-xs text-destructive hover:text-destructive hover:bg-destructive/10 border-destructive/30"
                                onClick={() => openEliminarModal(item)}
                                title="Eliminar solicitud de onboarding permanentemente"
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                                <span className="hidden xl:inline">Eliminar</span>
                              </Button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Barra de Paginación */}
            {filteredItems.length > 0 && (
              <div className="flex flex-col sm:flex-row items-center justify-between gap-4 p-4 border-t border-border bg-card/50 text-xs text-muted-foreground">
                <div className="flex items-center gap-2">
                  <span>
                    Mostrando <strong className="text-foreground">{startIndex + 1}</strong> a{' '}
                    <strong className="text-foreground">{endIndex}</strong> de{' '}
                    <strong className="text-foreground">{totalItems}</strong> solicitudes
                  </span>
                  <span className="hidden sm:inline text-muted-foreground/50">•</span>
                  <div className="flex items-center gap-1.5">
                    <span>Filas:</span>
                    <select
                      value={pageSize}
                      onChange={(e) => setPageSize(Number(e.target.value))}
                      aria-label="Filas por página"
                      className="h-7 px-2 rounded-md bg-muted/50 border border-border text-foreground text-xs focus:outline-none focus:ring-1 focus:ring-primary"
                    >
                      <option value={5}>5</option>
                      <option value={10}>10</option>
                      <option value={25}>25</option>
                      <option value={50}>50</option>
                    </select>
                  </div>
                </div>

                <div className="flex items-center gap-1.5">
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-8 px-2.5 text-xs gap-1 border-border disabled:opacity-40"
                    onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                    disabled={currentPage <= 1}
                    title="Página anterior"
                  >
                    <ChevronLeft className="h-3.5 w-3.5" />
                    <span className="hidden sm:inline">Anterior</span>
                  </Button>

                  <div className="flex items-center gap-1 px-2">
                    <span className="font-medium text-foreground">{currentPage}</span>
                    <span className="text-muted-foreground">/</span>
                    <span>{totalPages}</span>
                  </div>

                  <Button
                    variant="outline"
                    size="sm"
                    className="h-8 px-2.5 text-xs gap-1 border-border disabled:opacity-40"
                    onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                    disabled={currentPage >= totalPages}
                    title="Página siguiente"
                  >
                    <span className="hidden sm:inline">Siguiente</span>
                    <ChevronRight className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
        </CardContent>
      </Card>

      {/* MODAL 1: REENVIAR CORREO DE BIENVENIDA */}
      <Dialog open={resendModal} onOpenChange={setResendModal}>
        <DialogContent className="sm:max-w-md bg-card border-border">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-foreground">
              <Mail className="h-5 w-5 text-primary" />
              Reenviar Enlace de Activación
            </DialogTitle>
            <DialogDescription className="text-muted-foreground text-xs">
              Envía nuevamente el enlace de activación de la cuenta de esta suscripción.
            </DialogDescription>
          </DialogHeader>

          {selectedItemForResend && (
            <form onSubmit={handleExecuteResend} className="space-y-4 pt-2">
              <div className="bg-muted/40 p-3 rounded-lg border border-border space-y-1.5 text-xs">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Organización:</span>
                  <span className="font-semibold text-foreground">{selectedItemForResend.nombreOrganizacion}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Usuario:</span>
                  <span className="font-mono text-foreground">@{selectedItemForResend.adminUsername || 'sin_usuario'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Plan:</span>
                  <span className="text-foreground">{selectedItemForResend.planNombre || 'Comercial'}</span>
                </div>
              </div>

              {/* Correo de Destino */}
              <div className="space-y-1.5">
                <Label htmlFor="resend-email" className="text-xs">
                  Correo de Envío (Destinatario)
                </Label>
                <Input
                  id="resend-email"
                  type="email"
                  value={resendEmail}
                  onChange={(e) => setResendEmail(e.target.value)}
                  placeholder="admin@correo.com"
                  className="bg-background text-sm"
                  required
                />
                <p className="text-xs text-muted-foreground">
                  Si el usuario se equivocó al escribir su correo, puedes corregirlo aquí.
                </p>
              </div>

              <p className="text-xs text-muted-foreground flex items-center gap-1.5">
                <Mail className="h-3.5 w-3.5 text-primary shrink-0" />
                Se enviará un enlace de activación de un solo uso (vigente 48 horas). Nunca se envía ni se muestra una contraseña.
              </p>

              {/* Resultado del reenvío (si ya se ejecutó) */}
              {resendResult && (
                <div className={`p-3 rounded-lg border text-xs space-y-2 animate-fadeIn ${
                  resendResult.estado === 'ENVIADO'
                    ? 'bg-tint-green-bg border-tint-green-fg/25 text-tint-green-fg'
                    : resendResult.estado === 'SIMULADO'
                    ? 'bg-tint-amber-bg border-tint-amber-fg/25 text-tint-amber-fg'
                    : 'bg-destructive/10 border-destructive/30 text-destructive'
                }`}>
                  <div className="flex items-center gap-1.5 font-semibold">
                    {resendResult.estado === 'ENVIADO' && <CheckCircle2 className="h-4 w-4" />}
                    {resendResult.estado === 'SIMULADO' && <AlertCircle className="h-4 w-4" />}
                    {resendResult.estado === 'FALLIDO' && <XCircle className="h-4 w-4" />}
                    <span>{resendResult.detalle}</span>
                  </div>
                </div>
              )}

              <DialogFooter className="pt-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setResendModal(false)}
                  disabled={resending}
                >
                  Cerrar
                </Button>
                <Button
                  type="submit"
                  size="sm"
                  disabled={resending}
                  className="gap-2 bg-primary text-primary-foreground"
                >
                  {resending ? (
                    <RefreshCw className="h-4 w-4 animate-spin" />
                  ) : (
                    <Send className="h-4 w-4" />
                  )}
                  {resending ? 'Enviando...' : 'Reenviar Enlace'}
                </Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>

      {/* MODAL 2: EDITAR CREDENCIALES */}
      <Dialog open={editModal} onOpenChange={setEditModal}>
        <DialogContent className="sm:max-w-md bg-card border-border">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-foreground">
              <KeyRound className="h-5 w-5 text-tint-amber-fg" />
              Editar Credenciales del Administrador
            </DialogTitle>
            <DialogDescription className="text-muted-foreground text-xs">
              Modifica directamente el usuario y contraseña de la cuenta para soporte administrativo.
            </DialogDescription>
          </DialogHeader>

          {selectedItemForEdit && (
            <form onSubmit={handleSaveCredentials} className="space-y-4 pt-2">
              <div className="bg-muted/40 p-3 rounded-lg border border-border space-y-1 text-xs">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Organización:</span>
                  <span className="font-semibold text-foreground">{selectedItemForEdit.nombreOrganizacion}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Titular:</span>
                  <span className="text-foreground">{selectedItemForEdit.adminNombreCompleto}</span>
                </div>
              </div>

              {/* Nombre de Usuario */}
              <div className="space-y-1.5">
                <Label htmlFor="edit-username" className="text-xs">
                  Nombre de Usuario (Login)
                </Label>
                <Input
                  id="edit-username"
                  type="text"
                  value={editUsername}
                  onChange={(e) => setEditUsername(e.target.value.toLowerCase().replace(/\s+/g, ''))}
                  className="bg-background text-sm font-mono"
                  placeholder="admin_usuario"
                  required
                />
              </div>

              {/* Correo Electrónico */}
              <div className="space-y-1.5">
                <Label htmlFor="edit-email" className="text-xs">
                  Correo Electrónico
                </Label>
                <Input
                  id="edit-email"
                  type="email"
                  value={editEmail}
                  onChange={(e) => setEditEmail(e.target.value)}
                  className="bg-background text-sm"
                  placeholder="admin@dominio.com"
                  required
                />
              </div>

              {/* Nueva Contraseña */}
              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <Label htmlFor="edit-pass" className="text-xs">
                    Nueva Contraseña
                  </Label>
                  <button
                    type="button"
                    onClick={() => setEditPassword(generateSecurePassword())}
                    className="text-xs text-primary hover:underline flex items-center gap-1 font-medium"
                  >
                    <Sparkles className="h-3 w-3" />
                    Generar segura
                  </button>
                </div>
                <div className="relative">
                  <Input
                    id="edit-pass"
                    type={showPassword ? 'text' : 'password'}
                    value={editPassword}
                    onChange={(e) => setEditPassword(e.target.value)}
                    placeholder="Dejar en blanco para no modificar"
                    className="bg-background text-sm font-mono pr-10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-2.5 text-muted-foreground hover:text-foreground"
                    title={showPassword ? 'Ocultar' : 'Mostrar'}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                <p className="text-xs text-muted-foreground">
                  Solo llena este campo si deseas cambiar la clave actual del usuario.
                </p>
              </div>

              {/* Notificar por correo */}
              {editPassword && (
                <div className="flex items-center gap-2 p-2.5 rounded-lg border border-border bg-muted/20 animate-fadeIn">
                  <input
                    type="checkbox"
                    id="notify-user"
                    checked={editNotify}
                    onChange={(e) => setEditNotify(e.target.checked)}
                    className="h-4 w-4 rounded border-border text-primary focus:ring-primary"
                  />
                  <Label htmlFor="notify-user" className="text-xs text-foreground font-normal cursor-pointer">
                    Despachar correo al usuario notificando sus nuevas credenciales
                  </Label>
                </div>
              )}

              <DialogFooter className="pt-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setEditModal(false)}
                  disabled={savingCredentials}
                >
                  Cancelar
                </Button>
                <Button
                  type="submit"
                  size="sm"
                  disabled={savingCredentials}
                  className="gap-2 bg-primary text-primary-foreground"
                >
                  {savingCredentials ? (
                    <RefreshCw className="h-4 w-4 animate-spin" />
                  ) : (
                    <Check className="h-4 w-4" />
                  )}
                  {savingCredentials ? 'Guardando...' : 'Guardar Cambios'}
                </Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>

      {/* MODAL 3: APROBAR MANUALMENTE */}
      <Dialog open={approveModal} onOpenChange={setApproveModal}>
        <DialogContent className="sm:max-w-md bg-card border-border">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-foreground">
              <CheckCircle2 className="h-5 w-5 text-tint-green-fg" />
              Aprobar y Materializar Suscripción
            </DialogTitle>
            <DialogDescription className="text-muted-foreground text-xs">
              Esta acción confirmará manualmente el pago de la membresía y creará de inmediato la organización, usuario y membresía en la base de datos.
            </DialogDescription>
          </DialogHeader>

          {selectedItemForApprove && (
            <div className="space-y-4 pt-2">
              <div className="p-3 rounded-lg bg-tint-amber-bg border border-tint-amber-fg/25 text-tint-amber-fg text-xs flex items-start gap-2">
                <AlertTriangle className="h-4 w-4 shrink-0 text-tint-amber-fg mt-0.5" />
                <div>
                  <strong>Atención:</strong> Utiliza esta opción únicamente si validaste que el pago en Wompi (o transferencia manual) fue efectuado correctamente, o si estás en entorno sandbox/pruebas.
                </div>
              </div>

              <div className="bg-muted/40 p-3 rounded-lg border border-border space-y-1.5 text-xs">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Referencia:</span>
                  <span className="font-mono text-foreground">{selectedItemForApprove.referencia}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Organización:</span>
                  <span className="font-semibold text-foreground">{selectedItemForApprove.nombreOrganizacion}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Administrador:</span>
                  <span className="text-foreground">{selectedItemForApprove.adminNombreCompleto}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Monto:</span>
                  <span className="font-mono font-bold text-foreground">{formatCurrency(selectedItemForApprove.montoPesos)}</span>
                </div>
              </div>

              <DialogFooter className="pt-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setApproveModal(false)}
                  disabled={approving}
                >
                  Cancelar
                </Button>
                <Button
                  type="button"
                  size="sm"
                  onClick={handleExecuteApprove}
                  disabled={approving}
                  className="gap-2 bg-btn-success hover:opacity-90 text-white"
                >
                  {approving ? (
                    <RefreshCw className="h-4 w-4 animate-spin" />
                  ) : (
                    <Check className="h-4 w-4" />
                  )}
                  {approving ? 'Activando...' : 'Confirmar y Activar'}
                </Button>
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* MODAL 4: RECHAZAR SOLICITUD */}
      <Dialog open={rechazarModal} onOpenChange={setRechazarModal}>
        <DialogContent className="sm:max-w-md bg-card border-border">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-foreground">
              <XCircle className="h-5 w-5 text-tint-amber-fg" />
              Rechazar Solicitud de Onboarding
            </DialogTitle>
            <DialogDescription className="text-muted-foreground text-xs">
              La solicitud quedará marcada como <strong>RECHAZADA</strong>. El prospecto no podrá activar su cuenta hasta que se gestione una nueva solicitud.
            </DialogDescription>
          </DialogHeader>

          {selectedItemForRechazar && (
            <div className="space-y-4 pt-2">
              <div className="bg-muted/40 p-3 rounded-lg border border-border space-y-1.5 text-xs">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Referencia:</span>
                  <span className="font-mono text-foreground">{selectedItemForRechazar.referencia}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Organización:</span>
                  <span className="font-semibold text-foreground">{selectedItemForRechazar.nombreOrganizacion}</span>
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="rechazar-motivo" className="text-xs">
                  Motivo del rechazo <span className="text-muted-foreground">(opcional)</span>
                </Label>
                <textarea
                  id="rechazar-motivo"
                  value={rechazarMotivo}
                  onChange={(e) => setRechazarMotivo(e.target.value)}
                  placeholder="Ej: Documentación incompleta, NIT duplicado, datos inválidos..."
                  rows={3}
                  className="w-full px-3 py-2 text-xs rounded-md border border-input bg-background text-foreground resize-none focus:outline-none focus:ring-1 focus:ring-primary"
                />
              </div>

              <DialogFooter className="pt-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setRechazarModal(false)}
                  disabled={rechazando}
                >
                  Cancelar
                </Button>
                <Button
                  type="button"
                  size="sm"
                  onClick={handleExecuteRechazar}
                  disabled={rechazando}
                  className="gap-2 bg-btn-warn hover:bg-btn-warn-hover text-white"
                >
                  {rechazando ? (
                    <RefreshCw className="h-4 w-4 animate-spin" />
                  ) : (
                    <XCircle className="h-4 w-4" />
                  )}
                  {rechazando ? 'Rechazando...' : 'Confirmar Rechazo'}
                </Button>
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* MODAL 5: ELIMINAR SOLICITUD */}
      <Dialog open={eliminarModal} onOpenChange={setEliminarModal}>
        <DialogContent className="sm:max-w-md bg-card border-border">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-foreground">
              <Trash2 className="h-5 w-5 text-destructive" />
              Eliminar Solicitud
            </DialogTitle>
            <DialogDescription className="text-muted-foreground text-xs">
              Esta acción es <strong>permanente e irreversible</strong>. La solicitud será eliminada de la base de datos junto con cualquier transacción de pago pendiente asociada.
            </DialogDescription>
          </DialogHeader>

          {selectedItemForEliminar && (
            <div className="space-y-4 pt-2">
              <div className="p-3 rounded-lg bg-destructive/10 border border-destructive/30 text-destructive text-xs flex items-start gap-2">
                <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" />
                <div>
                  <strong>¿Estás seguro?</strong> Se eliminará permanentemente la solicitud de{' '}
                  <span className="font-semibold">{selectedItemForEliminar.nombreOrganizacion}</span> (ref: <code className="font-mono">{selectedItemForEliminar.referencia}</code>).
                </div>
              </div>

              <DialogFooter className="pt-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setEliminarModal(false)}
                  disabled={eliminando}
                >
                  Cancelar
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="destructive"
                  onClick={handleExecuteEliminar}
                  disabled={eliminando}
                  className="gap-2"
                >
                  {eliminando ? (
                    <RefreshCw className="h-4 w-4 animate-spin" />
                  ) : (
                    <Trash2 className="h-4 w-4" />
                  )}
                  {eliminando ? 'Eliminando...' : 'Eliminar Definitivamente'}
                </Button>
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
