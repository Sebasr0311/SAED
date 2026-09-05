import { useState, useMemo } from 'react';
import { toast } from 'sonner';
import { useNavigate } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowRight,
  Car,
  ChevronRight,
  ClipboardList,
  Gavel,
  LogIn,
  Package,
  QrCode,
  RefreshCw,
  ShieldCheck,
  UserCheck,
  Users,
  Volume2,
} from 'lucide-react';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useTenantApi } from '../lib/useTenantApi.js';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card.tsx';
import { Select, Textarea } from '../components/ui/Form.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { formatDate, imageSrc } from '../lib/utils.js';
import { VideoCamara } from '../components/ui/VideoCamara.jsx';

const ACCIONES_OPERATIVAS = [
  {
    id: 'visitas',
    label: 'Gestión de Visitas',
    desc: 'Consultar programación y visitas registradas',
    icon: Users,
    path: '/visitas',
  },
  {
    id: 'escanner',
    label: 'Control de Acceso QR',
    desc: 'Escanear credenciales y autorizar ingreso',
    icon: QrCode,
    path: '/escanner-qr',
  },
  {
    id: 'paquetes',
    label: 'Paquetería y Encomiendas',
    desc: 'Custodia, registro y entrega con PIN',
    icon: Package,
    path: '/paquetes',
  },
  {
    id: 'parqueaderos',
    label: 'Control de Parqueaderos',
    desc: 'Disponibilidad de cupos y vehículos dentro',
    icon: Car,
    path: '/parqueaderos',
  },
];

function ModalAvisoRuido({ open, onClose, onConfirm, apartamentos }) {
  const [idApartamento, setIdApartamento] = useState('');
  const [cuerpo, setCuerpo] = useState('Ruido excesivo en zona común. Por favor moderar el volumen.');
  const [sending, setSending] = useState(false);

  async function send() {
    if (!idApartamento) return;
    setSending(true);
    try {
      await api.post('/buzon/aviso-ruido', {
        idApartamento: Number(idApartamento),
        cuerpo,
      });
      onConfirm();
    } finally {
      setSending(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Enviar Aviso de Ruido"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={sending}>
            Cancelar
          </Button>
          <Button onClick={send} disabled={sending || !idApartamento}>
            {sending ? 'Enviando...' : 'Enviar Aviso'}
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <div className="form-group">
          <Select
            id="avisoApto"
            label="Apartamento Involucrado"
            value={idApartamento}
            onChange={(e) => setIdApartamento(e.target.value)}
          >
            <option value="">- Seleccionar apartamento -</option>
            {(apartamentos?.items || apartamentos || [])
              .filter((a) => a.estado === 'OCUPADO')
              .map((a) => (
                <option key={a.idApartamento} value={a.idApartamento}>
                  Apto {a.numero}
                </option>
              ))}
          </Select>
        </div>
        <div className="form-group">
          <Textarea
            id="avisoCuerpo"
            label="Mensaje para el residente"
            rows={3}
            value={cuerpo}
            onChange={(e) => setCuerpo(e.target.value)}
          />
        </div>
      </div>
    </Modal>
  );
}

function ModalGenerarMulta({ open, onClose, onConfirm, apartamentos, quejasRuido, tipoInicial }) {
  const [tipo, setTipo] = useState(tipoInicial || 'RUIDO');
  const [idApartamento, setIdApartamento] = useState('');
  const [idMensaje, setIdMensaje] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [foto, setFoto] = useState(null);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');

  async function send() {
    if (!idApartamento) {
      setError('Seleccione un apartamento');
      return;
    }
    if (tipo === 'PARQUEADERO' && !foto) {
      setError('La foto de evidencia es obligatoria para multa de parqueadero');
      return;
    }
    setSending(true);
    setError('');
    try {
      const payload = { tipo, idApartamento: Number(idApartamento), descripcion };
      if (idMensaje) payload.idMensaje = Number(idMensaje);
      if (foto) payload.fotoEvidencia = foto;
      await api.post('/multas/generar', payload);
      onConfirm();
    } catch (err) {
      setError(err.message || 'Error al generar multa');
    } finally {
      setSending(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Generar Multa (${tipo === 'RUIDO' ? 'Ruido Excesivo' : 'Parqueadero'})`}
      size="md"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={sending}>
            Cancelar
          </Button>
          <Button onClick={send} disabled={sending}>
            {sending ? 'Generando...' : 'Generar Multa'}
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {error && (
          <div className="flex items-center gap-2 rounded-lg border border-rose-500/20 bg-rose-500/10 p-3 text-xs font-medium text-rose-700 dark:text-rose-300">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="form-group">
          <Select id="multaTipo" label="Tipo de Multa" value={tipo} onChange={(e) => setTipo(e.target.value)}>
            <option value="RUIDO">Ruido Excesivo</option>
            <option value="PARQUEADERO">Infracción de Parqueadero</option>
          </Select>
        </div>

        {tipo === 'RUIDO' && quejasRuido && quejasRuido.length > 0 && (
          <div className="form-group">
            <Select id="idMensaje" label="Vincular a aviso previo (opcional)" value={idMensaje} onChange={(e) => setIdMensaje(e.target.value)}>
              <option value="">- Multa directa sin aviso previo -</option>
              {quejasRuido.map((q) => (
                <option key={q.idMensaje} value={q.idMensaje}>
                  {formatDate(q.fechaCreacion)} - Apto {q.numeroApartamento} - {q.titulo}
                </option>
              ))}
            </Select>
            <p className="mt-1 text-[11px] text-muted-foreground">
              Deben haber transcurrido al menos 20 minutos desde el aviso (validado en backend).
            </p>
          </div>
        )}

        <div className="form-group">
          <Select
            id="multaApto"
            label="Apartamento Sancionado"
            value={idApartamento}
            onChange={(e) => setIdApartamento(e.target.value)}
          >
            <option value="">- Seleccionar apartamento -</option>
            {(apartamentos?.items || apartamentos || [])
              .filter((a) => a.estado === 'OCUPADO')
              .map((a) => (
                <option key={a.idApartamento} value={a.idApartamento}>
                  Apto {a.numero}
                </option>
              ))}
          </Select>
        </div>

        <div className="form-group">
          <Textarea
            id="multaDesc"
            label="Descripción del Suceso"
            rows={2}
            value={descripcion}
            onChange={(e) => setDescripcion(e.target.value)}
            placeholder={tipo === 'RUIDO' ? 'Música a alto volumen reiterada...' : 'Vehículo invadiendo zona de maniobra o cupo ajeno...'}
          />
        </div>

        {tipo === 'PARQUEADERO' && (
          <div className="form-group space-y-2">
            <label className="text-xs font-semibold text-foreground">
              Foto de evidencia obligatoria
            </label>
            <VideoCamara onCapture={setFoto} buttonLabel="Capturar Foto de Evidencia" dualCamera maxHeight="260px" />
            {foto && (
              <img
                src={imageSrc(foto)}
                alt="Evidencia"
                loading="lazy"
                className="max-w-[200px] rounded-lg border border-border mt-2"
              />
            )}
          </div>
        )}
      </div>
    </Modal>
  );
}

function ModalPaquetes({ open, onClose, onConfirm }) {
  const { data: paquetes, loading, refetch } = useFetch(
    () => (open ? api.get('/buzon/paquetes') : Promise.resolve([])),
    [open]
  );
  const [detalle, setDetalle] = useState(null);
  const [marcaId, setMarcaId] = useState(null);

  async function marcarEntregado(idMensaje) {
    setMarcaId(idMensaje);
    try {
      await api.put(`/buzon/${idMensaje}/entregado`);
      onConfirm();
      refetch();
    } catch (err) {
      toast.error(err.message || 'No se pudo marcar como entregado');
    } finally {
      setMarcaId(null);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Paquetes y Encomiendas en Custodia"
      size="lg"
      footer={
        <div className="flex justify-end">
          <Button variant="outline" onClick={onClose}>
            Cerrar
          </Button>
        </div>
      }
    >
      {loading ? (
        <p className="text-xs text-muted-foreground p-4 text-center">Cargando paquetes...</p>
      ) : (
        <DataTable
          columns={[
            { key: 'idMensaje', label: 'ID', width: 60 },
            { key: 'numeroApartamento', label: 'Apto' },
            { key: 'nombreResidente', label: 'Destinatario' },
            { key: 'fechaCreacion', label: 'Recibido', render: (r) => formatDate(r.fechaCreacion) },
            {
              key: 'foto',
              label: '',
              render: (row) => (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setDetalle(row)}
                  className="text-xs py-1 px-2.5 h-auto"
                >
                  Ver
                </Button>
              ),
            },
            {
              key: 'actions',
              label: '',
              render: (row) => (
                <Button
                  size="sm"
                  onClick={() => marcarEntregado(row.idMensaje)}
                  disabled={marcaId === row.idMensaje}
                  className="text-xs py-1 px-3 h-auto font-semibold"
                >
                  {marcaId === row.idMensaje ? 'Entregando...' : 'Marcar Entregado'}
                </Button>
              ),
            },
          ]}
          rows={paquetes?.items || paquetes || []}
          empty={{
            icon: 'inventory_2',
            title: 'No hay paquetes pendientes',
            subtitle: 'Las encomiendas recibidas en portería aparecerán aquí.',
          }}
          keyField="idMensaje"
        />
      )}

      {detalle && (
        <Modal open={!!detalle} onClose={() => setDetalle(null)} title="Detalle de Encomienda" size="md">
          <div className="space-y-3">
            <div className="flex justify-between border-b border-border/60 pb-2 text-xs">
              <span className="text-muted-foreground">Apartamento:</span>
              <span className="font-bold text-foreground">Apto {detalle.numeroApartamento}</span>
            </div>
            <div className="flex justify-between border-b border-border/60 pb-2 text-xs">
              <span className="text-muted-foreground">Destinatario:</span>
              <span className="font-bold text-foreground">{detalle.nombreResidente}</span>
            </div>
            <div className="flex justify-between border-b border-border/60 pb-2 text-xs">
              <span className="text-muted-foreground">Fecha Recepción:</span>
              <span className="font-mono text-foreground">{formatDate(detalle.fechaCreacion)}</span>
            </div>
            {detalle.fotoCaptura && (
              <div className="pt-2">
                <img
                  src={imageSrc(detalle.fotoCaptura)}
                  alt="Foto Paquete"
                  loading="lazy"
                  className="w-full max-h-64 rounded-lg object-cover border border-border"
                />
              </div>
            )}
          </div>
        </Modal>
      )}
    </Modal>
  );
}

export default function PorteroDashboardPage() {
  const navigate = useNavigate();
  const tenantApi = useTenantApi();
  const [modalAviso, setModalAviso] = useState(false);
  const [modalMulta, setModalMulta] = useState(null); // 'RUIDO' | 'PARQUEADERO' | null
  const [modalPaquetes, setModalPaquetes] = useState(false);

  // Consume endpoints reales existentes
  const { data: visitasRaw, loading: loadingVisitas, refetch: refetchVisitas } = useFetch(
    () => tenantApi.get('/porteria/visitas-resumen'),
    []
  );
  const { data: parqueaderosRaw, refetch: refetchParq } = useFetch(
    () => tenantApi.get('/parqueaderos'),
    []
  );
  const { data: paquetesRaw } = useFetch(() => api.get('/buzon/paquetes-pendientes'), []);
  const { data: apartamentos } = useFetch(() => api.get('/units'), []);
  const { data: quejasRuido } = useFetch(
    () => (modalMulta === 'RUIDO' ? api.get('/buzon/quejas-ruido-pendientes') : Promise.resolve([])),
    [modalMulta]
  );

  const visitas = useMemo(() => {
    const list = Array.isArray(visitasRaw) ? visitasRaw : visitasRaw?.items ?? [];
    return Array.isArray(list) ? list : [];
  }, [visitasRaw]);

  const parqueaderos = useMemo(() => {
    const list = Array.isArray(parqueaderosRaw) ? parqueaderosRaw : parqueaderosRaw?.items ?? [];
    return Array.isArray(list) ? list : [];
  }, [parqueaderosRaw]);

  const visitasActivas = useMemo(() => {
    return visitas.filter((v) => v.estado === 'ACTIVA' || v.estado === 'EN_CURSO' || v.estado === 'PENDIENTE');
  }, [visitas]);

  const parqVisitantes = useMemo(() => {
    return parqueaderos.filter((p) => p.esVisitante);
  }, [parqueaderos]);

  const parqDisponibles = useMemo(() => {
    return parqVisitantes.filter((p) => p.estado === 'DISPONIBLE').length;
  }, [parqVisitantes]);

  const paquetesCount = useMemo(() => {
    if (paquetesRaw?.count != null) return paquetesRaw.count;
    if (Array.isArray(paquetesRaw)) return paquetesRaw.length;
    if (paquetesRaw?.items) return paquetesRaw.items.length;
    return 0;
  }, [paquetesRaw]);

  const breadcrumbs = useMemo(
    () => [
      { label: 'Inicio', href: '/' },
      { label: 'Garita de Portería', active: true },
    ],
    []
  );

  return (
    <PageContainer breadcrumbs={breadcrumbs} maxWidth="max-w-7xl">
      {/* Header Contextual */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              Panel Operativo de Portería
            </h1>
            <Badge variant="outline" className="text-xs font-semibold uppercase tracking-wider">
              PORTERO
            </Badge>
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <span className="flex items-center gap-1.5 font-medium text-emerald-600 dark:text-emerald-400">
              <span className="inline-block h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
              Turno Activo
            </span>
            <span>·</span>
            <span>Centro de control de garita, accesos vehiculares y convivencia</span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              refetchVisitas();
              refetchParq();
            }}
            className="flex items-center gap-2 border-border/80 hover:bg-muted/50"
            aria-label="Actualizar datos de garita"
          >
            <RefreshCw className={`h-4 w-4 ${loadingVisitas ? 'animate-spin' : ''}`} />
            <span className="hidden sm:inline">Actualizar</span>
          </Button>
        </div>
      </div>

      {/* Tira de KPIs Operativos */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          title="Visitas Activas / Dentro"
          value={visitasActivas.length}
          subtitle="En predio o programadas hoy"
          icon={UserCheck}
          variant="primary"
        />
        <MetricCard
          title="Total Pases Registrados"
          value={visitas.length}
          subtitle="Censo de visitas del condominio"
          icon={LogIn}
          variant="info"
        />
        <MetricCard
          title="Cupos Visitantes Libres"
          value={`${parqDisponibles} / ${parqVisitantes.length || 0}`}
          subtitle={parqDisponibles === 0 ? 'Sin cupos disponibles' : 'Disponibles para ingreso'}
          icon={Car}
          variant={parqDisponibles > 2 ? 'success' : 'warning'}
        />
        <MetricCard
          title="Paquetes en Custodia"
          value={paquetesCount}
          subtitle="Pendientes por entrega en garita"
          icon={Package}
          variant="secondary"
        />
      </div>

      {/* Hero Showcase Card: Centro de Control QR */}
      <Card className="border-primary/30 bg-gradient-to-br from-primary/10 via-primary/5 to-transparent shadow-xs">
        <CardContent className="p-6">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
            <div className="space-y-2 max-w-2xl">
              <div className="inline-flex items-center gap-2 rounded-full bg-primary/15 px-3 py-1 text-xs font-bold text-primary">
                <QrCode className="h-3.5 w-3.5" />
                Showcase Operativo SAED 2.0
              </div>
              <h2 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
                Centro de Validación y Control de Acceso QR
              </h2>
              <p className="text-xs sm:text-sm text-muted-foreground">
                Acredite credenciales de visitantes en tiempo real mediante cámara o token manual, registre
                ingresos vehiculares con asignación instantánea de parqueadero y certifique salidas seguras.
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <Button
                onClick={() => navigate('/escanner-qr')}
                className="bg-primary hover:bg-primary/90 text-primary-foreground font-bold shadow-xs flex items-center gap-2 px-5 py-2.5 text-sm"
              >
                <QrCode className="h-4 w-4" />
                Abrir Escáner y Control de Acceso
                <ArrowRight className="h-4 w-4 ml-1" />
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Acciones Rápidas de Seguridad y Convivencia */}
      <Card className="border-border/80 shadow-xs">
        <CardHeader className="pb-3 border-b border-border/60">
          <CardTitle className="text-base font-bold flex items-center gap-2">
            <ShieldCheck className="h-5 w-5 text-primary" />
            Protocolos de Seguridad y Convivencia
          </CardTitle>
        </CardHeader>
        <CardContent className="pt-4">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <button
              type="button"
              onClick={() => setModalAviso(true)}
              className="flex flex-col items-center justify-center gap-2 rounded-xl border border-border/80 bg-card p-4 text-center transition-all hover:border-primary/50 hover:bg-muted/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-amber-500/10 text-amber-600 dark:text-amber-400">
                <Volume2 className="h-6 w-6" />
              </div>
              <span className="text-xs font-bold text-foreground">Aviso de Ruido</span>
              <span className="text-[10px] text-muted-foreground">Notificar a residente</span>
            </button>

            <button
              type="button"
              onClick={() => setModalMulta('RUIDO')}
              className="flex flex-col items-center justify-center gap-2 rounded-xl border border-border/80 bg-card p-4 text-center transition-all hover:border-primary/50 hover:bg-muted/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-rose-500/10 text-rose-600 dark:text-rose-400">
                <Gavel className="h-6 w-6" />
              </div>
              <span className="text-xs font-bold text-foreground">Multa por Ruido</span>
              <span className="text-[10px] text-muted-foreground">Sanción de convivencia</span>
            </button>

            <button
              type="button"
              onClick={() => setModalMulta('PARQUEADERO')}
              className="flex flex-col items-center justify-center gap-2 rounded-xl border border-border/80 bg-card p-4 text-center transition-all hover:border-primary/50 hover:bg-muted/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-indigo-500/10 text-indigo-600 dark:text-indigo-400">
                <Car className="h-6 w-6" />
              </div>
              <span className="text-xs font-bold text-foreground">Multa Parqueadero</span>
              <span className="text-[10px] text-muted-foreground">Infracción vehicular</span>
            </button>

            <button
              type="button"
              onClick={() => setModalPaquetes(true)}
              className="flex flex-col items-center justify-center gap-2 rounded-xl border border-border/80 bg-card p-4 text-center transition-all hover:border-primary/50 hover:bg-muted/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-orange-500/10 text-orange-600 dark:text-orange-400">
                <Package className="h-6 w-6" />
              </div>
              <span className="text-xs font-bold text-foreground">Paquetes Pendientes</span>
              <span className="text-[10px] text-muted-foreground">Entrega con PIN ({paquetesCount})</span>
            </button>
          </div>
        </CardContent>
      </Card>

      {/* Módulos de Operación de Garita */}
      <Card className="border-border/80 shadow-xs">
        <CardHeader className="pb-3 border-b border-border/60">
          <CardTitle className="text-base font-bold flex items-center gap-2">
            <ClipboardList className="h-5 w-5 text-primary" />
            Módulos Operativos de Garita
          </CardTitle>
        </CardHeader>
        <CardContent className="pt-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {ACCIONES_OPERATIVAS.map((a) => {
              const Icon = a.icon;
              return (
                <button
                  key={a.id}
                  type="button"
                  onClick={() => navigate(a.path)}
                  className="flex items-center justify-between rounded-xl border border-border/80 bg-card p-4 text-left transition-all hover:border-primary/60 hover:bg-muted/30 hover:shadow-xs group focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary group-hover:bg-primary group-hover:text-primary-foreground transition-colors">
                      <Icon className="h-5 w-5" />
                    </div>
                    <div>
                      <h4 className="text-xs font-bold text-foreground group-hover:text-primary transition-colors">
                        {a.label}
                      </h4>
                      <p className="text-[11px] text-muted-foreground mt-0.5 line-clamp-1">{a.desc}</p>
                    </div>
                  </div>
                  <ChevronRight className="h-4 w-4 text-muted-foreground/60 group-hover:text-primary group-hover:translate-x-0.5 transition-all shrink-0" />
                </button>
              );
            })}
          </div>
        </CardContent>
      </Card>

      {/* Modales funcionales */}
      <ModalAvisoRuido
        open={modalAviso}
        onClose={() => setModalAviso(false)}
        onConfirm={() => {
          setModalAviso(false);
          toast.success('Aviso de ruido notificado al residente');
        }}
        apartamentos={apartamentos?.items || apartamentos || []}
      />

      <ModalGenerarMulta
        open={!!modalMulta}
        onClose={() => setModalMulta(null)}
        onConfirm={() => {
          setModalMulta(null);
          toast.success('Multa generada con éxito');
        }}
        apartamentos={apartamentos?.items || apartamentos || []}
        quejasRuido={quejasRuido?.items || quejasRuido || []}
        tipoInicial={modalMulta}
      />

      <ModalPaquetes
        open={modalPaquetes}
        onClose={() => setModalPaquetes(false)}
        onConfirm={() => toast.success('Encomienda entregada exitosamente')}
      />
    </PageContainer>
  );
}
