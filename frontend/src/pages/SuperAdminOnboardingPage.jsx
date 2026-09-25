import { useEffect, useState, useMemo } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
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
} from 'lucide-react';

export default function SuperAdminOnboardingPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [filterPago, setFilterPago] = useState('TODOS');
  const [filterCorreo, setFilterCorreo] = useState('TODOS');
  const [filterTipo, setFilterTipo] = useState('TODOS');

  // Modal Reenviar Correo
  const [resendModal, setResendModal] = useState(false);
  const [selectedItemForResend, setSelectedItemForResend] = useState(null);
  const [resendEmail, setResendEmail] = useState('');
  const [resendMode, setResendMode] = useState('AUTO'); // 'AUTO' o 'MANUAL'
  const [resendManualPass, setResendManualPass] = useState('');
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

  // Copied tracking
  const [copiedId, setCopiedId] = useState(null);

  async function loadData() {
    try {
      setLoading(true);
      const res = await api.get('/platform/onboarding/solicitudes');
      const list = res?.data || res || [];
      setItems(Array.isArray(list) ? list : []);
    } catch {
      toast.error('Error al cargar solicitudes de onboarding');
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
    setResendMode('AUTO');
    setResendManualPass('');
    setResendResult(null);
    setResendModal(true);
  }

  async function handleExecuteResend(e) {
    e.preventDefault();
    if (!resendEmail.trim()) {
      toast.error('El correo de destino es obligatorio');
      return;
    }
    if (resendMode === 'MANUAL' && resendManualPass.trim().length < 6) {
      toast.error('La contraseña manual debe tener al menos 6 caracteres');
      return;
    }

    try {
      setResending(true);
      const payload = {
        referencia: selectedItemForResend.referencia,
        idOrganizacion: selectedItemForResend.idOrganizacion,
        idUsuario: selectedItemForResend.idUsuario,
        emailDestino: resendEmail.trim(),
        generarNuevaPassword: resendMode === 'AUTO',
        passwordManual: resendMode === 'MANUAL' ? resendManualPass.trim() : null,
      };

      const res = await api.post('/platform/onboarding/reenviar-correo', payload);
      const data = res?.data || res;
      setResendResult(data);

      if (data.estado === 'ENVIADO') {
        toast.success('¡Correo de credenciales despachado exitosamente vía Brevo!');
      } else if (data.estado === 'SIMULADO') {
        toast.info('Correo procesado en modo simulación (local). Credenciales listas para copiar.');
      } else {
        toast.warning('El envío falló en el proveedor: ' + (data.detalle || ''));
      }
      loadData();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Error al reenviar credenciales');
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

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-foreground">Suscripciones & Onboarding</h1>
            <Badge variant="outline" className="border-primary/40 text-primary text-xs">
              SuperAdmin Global
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Gestión de solicitudes de suscripción (organizaciones y personas naturales), trazabilidad de correos y credenciales.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={loadData} disabled={loading} className="gap-2">
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Actualizar
          </Button>
        </div>
      </div>

      {/* KPI Cards Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="bg-card border-border shadow-sm">
          <CardHeader className="pb-2 flex flex-row items-center justify-between">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Total Solicitudes
            </CardTitle>
            <Building2 className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            {loading ? (
              <Skeleton className="h-8 w-16" />
            ) : (
              <div className="text-2xl font-bold text-foreground">{metrics.total}</div>
            )}
            <p className="text-xs text-muted-foreground mt-1">Registros en el sistema</p>
          </CardContent>
        </Card>

        <Card className="bg-card border-border shadow-sm">
          <CardHeader className="pb-2 flex flex-row items-center justify-between">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Activas / Pagadas
            </CardTitle>
            <ShieldCheck className="h-4 w-4 text-emerald-500" />
          </CardHeader>
          <CardContent>
            {loading ? (
              <Skeleton className="h-8 w-16" />
            ) : (
              <div className="text-2xl font-bold text-emerald-500">{metrics.pagados}</div>
            )}
            <p className="text-xs text-muted-foreground mt-1">Materializadas y con acceso</p>
          </CardContent>
        </Card>

        <Card className="bg-card border-border shadow-sm">
          <CardHeader className="pb-2 flex flex-row items-center justify-between">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Pendientes de Pago
            </CardTitle>
            <CreditCard className="h-4 w-4 text-amber-500" />
          </CardHeader>
          <CardContent>
            {loading ? (
              <Skeleton className="h-8 w-16" />
            ) : (
              <div className="text-2xl font-bold text-amber-500">{metrics.pendientes}</div>
            )}
            <p className="text-xs text-muted-foreground mt-1">Esperando pasarela Wompi</p>
          </CardContent>
        </Card>

        <Card className="bg-card border-border shadow-sm">
          <CardHeader className="pb-2 flex flex-row items-center justify-between">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Despacho de Correos
            </CardTitle>
            <Mail className="h-4 w-4 text-primary" />
          </CardHeader>
          <CardContent>
            {loading ? (
              <Skeleton className="h-8 w-24" />
            ) : (
              <div className="flex items-center gap-2">
                <span className="text-xl font-bold text-emerald-500">{metrics.enviadosReal}</span>
                <span className="text-xs text-muted-foreground">reales /</span>
                <span className="text-xl font-bold text-amber-500">{metrics.simulados}</span>
                <span className="text-xs text-muted-foreground">simulados</span>
              </div>
            )}
            <p className="text-xs text-muted-foreground mt-1">
              {metrics.fallidos > 0 ? (
                <span className="text-destructive font-medium">{metrics.fallidos} fallidos</span>
              ) : (
                'Sin fallos en envío'
              )}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Filter and Search Bar */}
      <Card className="bg-card border-border shadow-sm">
        <CardContent className="p-4">
          <div className="flex flex-col md:flex-row gap-3 items-center justify-between">
            <div className="relative w-full md:w-96">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Buscar por organización, persona, usuario, email, NIT o ref..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9 bg-background"
              />
            </div>
            <div className="flex flex-wrap items-center gap-2 w-full md:w-auto">
              {/* Filtro Pago */}
              <select
                className="h-9 px-3 py-1 text-xs rounded-md border border-input bg-background text-foreground shadow-sm focus:outline-none focus:ring-1 focus:ring-primary"
                value={filterPago}
                onChange={(e) => setFilterPago(e.target.value)}
              >
                <option value="TODOS">Todos los Pagos</option>
                <option value="APROBADO">Pagado / Aprobado</option>
                <option value="PENDIENTE">Pendiente Wompi</option>
                <option value="RECHAZADO">Rechazado</option>
              </select>

              {/* Filtro Correo */}
              <select
                className="h-9 px-3 py-1 text-xs rounded-md border border-input bg-background text-foreground shadow-sm focus:outline-none focus:ring-1 focus:ring-primary"
                value={filterCorreo}
                onChange={(e) => setFilterCorreo(e.target.value)}
              >
                <option value="TODOS">Todos los Correos</option>
                <option value="ENVIADO">Enviado (Real)</option>
                <option value="SIMULADO">Simulado (Local)</option>
                <option value="FALLIDO">Fallido</option>
                <option value="PENDIENTE">Pendiente</option>
              </select>

              {/* Filtro Tipo */}
              <select
                className="h-9 px-3 py-1 text-xs rounded-md border border-input bg-background text-foreground shadow-sm focus:outline-none focus:ring-1 focus:ring-primary"
                value={filterTipo}
                onChange={(e) => setFilterTipo(e.target.value)}
              >
                <option value="TODOS">Todos los Tipos</option>
                <option value="NATURAL">Persona Natural</option>
                <option value="JURIDICA">Persona Jurídica</option>
              </select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Main Table */}
      <Card className="bg-card border-border shadow-sm overflow-hidden">
        <CardContent className="p-0">
          {loading ? (
            <div className="p-6 space-y-4">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : filteredItems.length === 0 ? (
            <div className="p-12 text-center">
              <Building2 className="h-12 w-12 text-muted-foreground mx-auto mb-3 opacity-40" />
              <h3 className="text-base font-semibold text-foreground">No se encontraron solicitudes</h3>
              <p className="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
                No hay registros que coincidan con los filtros aplicados o aún no se han registrado organizaciones en la plataforma.
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm border-collapse">
                <thead>
                  <tr className="border-b border-border bg-muted/40 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    <th className="py-3 px-4">Organización / Titular</th>
                    <th className="py-3 px-4">Administrador / Acceso</th>
                    <th className="py-3 px-4">Plan & Membresía</th>
                    <th className="py-3 px-4">Pago Wompi</th>
                    <th className="py-3 px-4">Estado de Correo</th>
                    <th className="py-3 px-4 text-right">Acciones</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {filteredItems.map((item, idx) => {
                    const isNatural = item.tipoPersona === 'NATURAL';
                    const isPaid = item.estadoPago === 'APROBADO' || item.estadoIntencion === 'COMPLETADA';
                    const isPendingPayment = !isPaid && item.estadoPago === 'PENDIENTE';

                    return (
                      <tr key={item.referencia || idx} className="hover:bg-muted/20 transition-colors">
                        {/* Organización / Titular */}
                        <td className="py-3 px-4 align-top">
                          <div className="font-semibold text-foreground flex items-center gap-1.5">
                            {isNatural ? (
                              <User className="h-4 w-4 text-sky-400 shrink-0" />
                            ) : (
                              <Building2 className="h-4 w-4 text-indigo-400 shrink-0" />
                            )}
                            <span>{item.nombreOrganizacion || 'Sin nombre'}</span>
                          </div>
                          <div className="flex items-center gap-2 mt-1">
                            <Badge
                              variant="outline"
                              className={`text-[10px] px-1.5 py-0 font-normal ${
                                isNatural
                                  ? 'border-sky-500/30 text-sky-400 bg-sky-500/10'
                                  : 'border-indigo-500/30 text-indigo-400 bg-indigo-500/10'
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
                            <div className="text-[11px] text-muted-foreground mt-1">
                              Registrado: {item.fechaRegistro}
                            </div>
                          )}
                        </td>

                        {/* Administrador / Acceso */}
                        <td className="py-3 px-4 align-top">
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
                                <Check className="h-3 w-3 text-emerald-500" />
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
                                  <Check className="h-3 w-3 text-emerald-500" />
                                ) : (
                                  <Copy className="h-3 w-3" />
                                )}
                              </button>
                            )}
                          </div>
                        </td>

                        {/* Plan & Membresía */}
                        <td className="py-3 px-4 align-top">
                          <div className="font-medium text-foreground">{item.planNombre || 'Plan Comercial'}</div>
                          <div className="flex items-center gap-1.5 mt-1">
                            <Badge variant="outline" className="text-[10px] px-1.5 py-0">
                              {item.cicloFacturacion || 'MENSUAL'}
                            </Badge>
                            {item.esPrueba && (
                              <Badge variant="secondary" className="text-[10px] px-1.5 py-0 bg-amber-500/10 text-amber-400">
                                Prueba
                              </Badge>
                            )}
                          </div>
                          <div className="text-xs font-mono text-muted-foreground mt-1">
                            {formatCurrency(item.montoPesos)}
                          </div>
                        </td>

                        {/* Pago Wompi */}
                        <td className="py-3 px-4 align-top">
                          {isPaid ? (
                            <Badge className="bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 gap-1 text-xs">
                              <CheckCircle2 className="h-3 w-3 text-emerald-500" />
                              Aprobado
                            </Badge>
                          ) : item.estadoPago === 'RECHAZADO' ? (
                            <Badge className="bg-destructive/10 text-destructive border border-destructive/20 gap-1 text-xs">
                              <XCircle className="h-3 w-3 text-destructive" />
                              Rechazado
                            </Badge>
                          ) : (
                            <Badge className="bg-amber-500/10 text-amber-400 border border-amber-500/20 gap-1 text-xs">
                              <Clock className="h-3 w-3 text-amber-500" />
                              Pendiente Wompi
                            </Badge>
                          )}
                          <div className="text-[11px] font-mono text-muted-foreground mt-1 truncate max-w-[140px]" title={item.referencia}>
                            Ref: {item.referencia}
                          </div>
                        </td>

                        {/* Estado de Correo */}
                        <td className="py-3 px-4 align-top">
                          {item.estadoCorreo === 'ENVIADO' ? (
                            <Badge className="bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 gap-1 text-xs">
                              <CheckCircle2 className="h-3 w-3 text-emerald-500" />
                              Enviado (Brevo)
                            </Badge>
                          ) : item.estadoCorreo === 'SIMULADO' ? (
                            <Badge className="bg-amber-500/10 text-amber-400 border border-amber-500/20 gap-1 text-xs" title="No hay BREVO_API_KEY en entorno">
                              <AlertCircle className="h-3 w-3 text-amber-500" />
                              Simulado (Local)
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
                            <div className="text-[11px] text-muted-foreground mt-1 max-w-[190px] truncate" title={item.detalleCorreo}>
                              {item.detalleCorreo}
                            </div>
                          )}
                          {item.fechaCorreo && (
                            <div className="text-[10px] text-muted-foreground/70">
                              Último: {item.fechaCorreo}
                            </div>
                          )}
                        </td>

                        {/* Acciones */}
                        <td className="py-3 px-4 align-top text-right">
                          <div className="flex items-center justify-end gap-1.5">
                            {/* Reenviar Correo */}
                            <Button
                              variant="outline"
                              size="sm"
                              className="h-8 px-2.5 gap-1.5 text-xs text-foreground hover:bg-muted"
                              onClick={() => openResendModal(item)}
                              title="Reenviar correo de bienvenida y credenciales"
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
                                <KeyRound className="h-3.5 w-3.5 text-amber-400" />
                                <span className="hidden xl:inline">Editar</span>
                              </Button>
                            )}

                            {/* Si está pendiente de pago, opción de aprobar manual */}
                            {isPendingPayment && (
                              <Button
                                variant="secondary"
                                size="sm"
                                className="h-8 px-2.5 gap-1.5 text-xs bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20 border border-emerald-500/30"
                                onClick={() => openApproveModal(item)}
                                title="Aprobar pago manualmente y activar"
                              >
                                <Check className="h-3.5 w-3.5" />
                                <span className="hidden xl:inline">Aprobar</span>
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
          )}
        </CardContent>
      </Card>

      {/* MODAL 1: REENVIAR CORREO DE BIENVENIDA */}
      <Dialog open={resendModal} onOpenChange={setResendModal}>
        <DialogContent className="sm:max-w-md bg-card border-border">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-foreground">
              <Mail className="h-5 w-5 text-primary" />
              Reenviar Correo de Bienvenida
            </DialogTitle>
            <DialogDescription className="text-muted-foreground text-xs">
              Envía nuevamente las instrucciones y credenciales de acceso para esta suscripción.
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
                <p className="text-[11px] text-muted-foreground">
                  Si el usuario se equivocó al escribir su correo, puedes corregirlo aquí.
                </p>
              </div>

              {/* Modo de Contraseña */}
              <div className="space-y-2">
                <Label className="text-xs">Estrategia de Credenciales</Label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setResendMode('AUTO')}
                    className={`p-2.5 rounded-lg border text-left text-xs transition-colors ${
                      resendMode === 'AUTO'
                        ? 'border-primary bg-primary/10 text-primary font-medium'
                        : 'border-border bg-background text-muted-foreground hover:bg-muted/50'
                    }`}
                  >
                    <div className="font-semibold flex items-center gap-1">
                      <Sparkles className="h-3.5 w-3.5" />
                      Generar Nueva
                    </div>
                    <span className="text-[11px] opacity-80 mt-0.5 block">
                      Crea clave temporal segura
                    </span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setResendMode('MANUAL')}
                    className={`p-2.5 rounded-lg border text-left text-xs transition-colors ${
                      resendMode === 'MANUAL'
                        ? 'border-primary bg-primary/10 text-primary font-medium'
                        : 'border-border bg-background text-muted-foreground hover:bg-muted/50'
                    }`}
                  >
                    <div className="font-semibold flex items-center gap-1">
                      <KeyRound className="h-3.5 w-3.5" />
                      Clave Manual
                    </div>
                    <span className="text-[11px] opacity-80 mt-0.5 block">
                      Especificar una fija
                    </span>
                  </button>
                </div>
              </div>

              {resendMode === 'MANUAL' && (
                <div className="space-y-1.5 animate-fadeIn">
                  <Label htmlFor="manual-pass" className="text-xs">
                    Nueva Contraseña Fija
                  </Label>
                  <Input
                    id="manual-pass"
                    type="text"
                    value={resendManualPass}
                    onChange={(e) => setResendManualPass(e.target.value)}
                    placeholder="Mínimo 6 caracteres"
                    className="bg-background text-sm font-mono"
                    required
                  />
                </div>
              )}

              {/* Resultado del reenvío (si ya se ejecutó) */}
              {resendResult && (
                <div className={`p-3 rounded-lg border text-xs space-y-2 animate-fadeIn ${
                  resendResult.estado === 'ENVIADO'
                    ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
                    : resendResult.estado === 'SIMULADO'
                    ? 'bg-amber-500/10 border-amber-500/30 text-amber-300'
                    : 'bg-destructive/10 border-destructive/30 text-destructive'
                }`}>
                  <div className="flex items-center gap-1.5 font-semibold">
                    {resendResult.estado === 'ENVIADO' && <CheckCircle2 className="h-4 w-4" />}
                    {resendResult.estado === 'SIMULADO' && <AlertCircle className="h-4 w-4" />}
                    {resendResult.estado === 'FALLIDO' && <XCircle className="h-4 w-4" />}
                    <span>{resendResult.detalle}</span>
                  </div>

                  {resendResult.passwordGenerada && (
                    <div className="bg-background/80 p-2 rounded border border-border/60 text-foreground">
                      <span className="text-[11px] text-muted-foreground block">
                        Contraseña generada (cópiala y entrégala al usuario):
                      </span>
                      <div className="flex items-center justify-between mt-1">
                        <span className="font-mono font-bold text-sm text-primary">
                          {resendResult.passwordGenerada}
                        </span>
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="h-7 px-2 text-xs"
                          onClick={() => handleCopy(resendResult.passwordGenerada, 'result-pass')}
                        >
                          {copiedId === 'result-pass' ? (
                            <Check className="h-3.5 w-3.5 text-emerald-500" />
                          ) : (
                            <Copy className="h-3.5 w-3.5" />
                          )}
                          <span className="ml-1">Copiar</span>
                        </Button>
                      </div>
                    </div>
                  )}
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
                  {resending ? 'Despachando...' : 'Reenviar Credenciales'}
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
              <KeyRound className="h-5 w-5 text-amber-400" />
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
                    className="text-[11px] text-primary hover:underline flex items-center gap-1 font-medium"
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
                <p className="text-[11px] text-muted-foreground">
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
              <CheckCircle2 className="h-5 w-5 text-emerald-500" />
              Aprobar y Materializar Suscripción
            </DialogTitle>
            <DialogDescription className="text-muted-foreground text-xs">
              Esta acción confirmará manualmente el pago de la membresía y creará de inmediato la organización, usuario y membresía en la base de datos.
            </DialogDescription>
          </DialogHeader>

          {selectedItemForApprove && (
            <div className="space-y-4 pt-2">
              <div className="p-3 rounded-lg bg-amber-500/10 border border-amber-500/30 text-amber-300 text-xs flex items-start gap-2">
                <AlertTriangle className="h-4 w-4 shrink-0 text-amber-400 mt-0.5" />
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
                  className="gap-2 bg-emerald-600 hover:bg-emerald-700 text-white"
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
    </div>
  );
}
