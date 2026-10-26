import { useState, useMemo, useEffect } from 'react';
import { toast } from 'sonner';
import { useNavigate } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowRight,
  Bike,
  Car,
  CheckCircle2,
  ChevronRight,
  ClipboardList,
  Clock,
  Gavel,
  LogIn,
  LogOut,
  MapPin,
  Package,
  Phone,
  Plus,
  QrCode,
  RefreshCw,
  Search,
  Siren,
  UserCheck,
  Users,
  Volume2,
} from 'lucide-react';
import { useFetch } from '../lib/hooks.js';
import { useTenantApi } from '../lib/useTenantApi.js';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import { Input as ShadcnInput } from '../components/ui/input.tsx';
import { Button as UiButton } from '../components/ui/button.tsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { formatDate, formatDateTime, formatApto, imageSrc } from '../lib/utils.js';
import { VideoCamara } from '../components/ui/VideoCamara.jsx';
import { KpiSummaryPanel } from '../components/KpiSummaryPanel.jsx';

const ACCIONES_OPERATIVAS = [
  {
    id: 'visitas',
    label: 'Visitas',
    desc: 'Consultar programación y visitas registradas',
    icon: Users,
    path: '/visitas',
  },
  {
    id: 'escanner',
    label: 'Escáner QR',
    desc: 'Escanear credenciales y autorizar ingreso',
    icon: QrCode,
    path: '/escanner-qr',
  },
  {
    id: 'paquetes',
    label: 'Paquetes',
    desc: 'Custodia, registro y entrega con PIN',
    icon: Package,
    path: '/paquetes',
  },
  {
    id: 'parqueaderos',
    label: 'Parqueaderos',
    desc: 'Disponibilidad de cupos y vehículos dentro',
    icon: Car,
    path: '/parqueaderos',
  },
];

function ModalRegistrarDomicilio({ open, onClose, onConfirm, apartamentos, tenantApi }) {
  const [idUnidad, setIdUnidad] = useState('');
  const [empresa, setEmpresa] = useState('');
  const [nombreDomiciliario, setNombreDomiciliario] = useState('');
  const [documentoDomiciliario, setDocumentoDomiciliario] = useState('');
  const [telefonoDomiciliario, setTelefonoDomiciliario] = useState('');
  const [tipoDomicilio, setTipoDomicilio] = useState('COMIDA');
  const [medioTransporte, setMedioTransporte] = useState('MOTO');
  const [placaVehiculo, setPlacaVehiculo] = useState('');
  const [numeroGuia, setNumeroGuia] = useState('');
  const [observaciones, setObservaciones] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setIdUnidad('');
      setEmpresa('');
      setNombreDomiciliario('');
      setDocumentoDomiciliario('');
      setTelefonoDomiciliario('');
      setTipoDomicilio('COMIDA');
      setMedioTransporte('MOTO');
      setPlacaVehiculo('');
      setNumeroGuia('');
      setObservaciones('');
      setError('');
    }
  }, [open]);

  async function handleSubmit(e) {
    if (e) e.preventDefault();
    if (!idUnidad) {
      setError('Seleccione la unidad de destino');
      return;
    }
    if (!empresa.trim()) {
      setError('Indique la empresa o servicio de domicilio');
      return;
    }
    if (!nombreDomiciliario.trim()) {
      setError('Indique el nombre del repartidor o domiciliario');
      return;
    }

    setSaving(true);
    setError('');
    try {
      const payload = {
        idUnidad: Number(idUnidad),
        empresa: empresa.trim(),
        nombreDomiciliario: nombreDomiciliario.trim(),
        documentoDomiciliario: documentoDomiciliario.trim() || null,
        telefonoDomiciliario: telefonoDomiciliario.trim() || null,
        tipoDomicilio,
        numeroGuia: numeroGuia.trim() || null,
        medioTransporte,
        placaVehiculo: placaVehiculo.trim() || null,
        observaciones: observaciones.trim() || null,
      };
      await tenantApi.post('/domicilios', payload);
      toast.success('Ingreso de domicilio registrado en garita');
      onConfirm();
    } catch (err) {
      setError(err.message || 'Error al registrar el domicilio');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Registrar Ingreso de Domicilio / Repartidor"
      size="md"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={saving}>
            Cancelar
          </Button>
          <Button onClick={handleSubmit} disabled={saving || !idUnidad || !empresa || !nombreDomiciliario}>
            {saving ? 'Registrando...' : 'Registrar Ingreso'}
          </Button>
        </div>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div className="flex items-center gap-2 rounded-lg border border-tint-red-fg/25 bg-tint-red-bg p-3 text-xs font-medium text-tint-red-fg">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="form-group">
            <Select
              id="domApto"
              label="Apartamento Destino"
              required
              value={idUnidad}
              onChange={(e) => {
                setIdUnidad(e.target.value);
                setError('');
              }}
            >
              <option value="">- Seleccionar apartamento -</option>
              {(apartamentos?.items || apartamentos || [])
                .filter((a) => a.estado === 'ACTIVA' || a.estado === 'OCUPADO' || !a.estado)
                .map((a) => (
                  <option key={a.idApartamento || a.id} value={a.idApartamento || a.id}>
                    {formatApto(a.numero || a.identificador)}
                  </option>
                ))}
            </Select>
          </div>

          <div className="form-group">
            <Input
              id="domEmpresa"
              label="Empresa / Plataforma"
              placeholder="Rappi, Didi, Servientrega..."
              required
              value={empresa}
              onChange={(e) => setEmpresa(e.target.value)}
            />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="form-group">
            <Input
              id="domNombre"
              label="Nombre Repartidor"
              placeholder="Nombre y apellido"
              required
              value={nombreDomiciliario}
              onChange={(e) => setNombreDomiciliario(e.target.value)}
            />
          </div>

          <div className="form-group">
            <Input
              id="domDoc"
              label="Cédula / Documento"
              placeholder="Opcional"
              value={documentoDomiciliario}
              onChange={(e) => setDocumentoDomiciliario(e.target.value)}
            />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div className="form-group">
            <Select
              id="domTipo"
              label="Tipo de Entrega"
              value={tipoDomicilio}
              onChange={(e) => setTipoDomicilio(e.target.value)}
            >
              <option value="COMIDA">Comida / Restaurante</option>
              <option value="MEDICAMENTOS">Medicamentos / Farmacia</option>
              <option value="MENSAJERIA">Mensajería / Documentos</option>
              <option value="PAQUETE_EXPRESS">Paquete Express</option>
              <option value="SUPERMERCADO">Supermercado</option>
              <option value="OTRO">Otro</option>
            </Select>
          </div>

          <div className="form-group">
            <Select
              id="domMedio"
              label="Medio Transporte"
              value={medioTransporte}
              onChange={(e) => setMedioTransporte(e.target.value)}
            >
              <option value="MOTO">Motocicleta</option>
              <option value="BICICLETA">Bicicleta</option>
              <option value="A_PIE">A pie / Peatonal</option>
              <option value="CARRO">Automóvil / Furgón</option>
              <option value="OTRO">Otro</option>
            </Select>
          </div>

          <div className="form-group">
            <Input
              id="domPlaca"
              label="Placa de Vehículo"
              placeholder="Ej. ABC-12D"
              value={placaVehiculo}
              onChange={(e) => setPlacaVehiculo(e.target.value)}
            />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="form-group">
            <Input
              id="domTel"
              label="Teléfono Repartidor"
              placeholder="Opcional"
              value={telefonoDomiciliario}
              onChange={(e) => setTelefonoDomiciliario(e.target.value)}
            />
          </div>

          <div className="form-group">
            <Input
              id="domGuia"
              label="N° Guía / Pedido"
              placeholder="Ej. RAPPI-1234"
              value={numeroGuia}
              onChange={(e) => setNumeroGuia(e.target.value)}
            />
          </div>
        </div>

        <div className="form-group">
          <Textarea
            id="domObs"
            label="Observaciones"
            rows={2}
            placeholder="Instrucciones o notas sobre la entrega..."
            value={observaciones}
            onChange={(e) => setObservaciones(e.target.value)}
          />
        </div>
      </form>
    </Modal>
  );
}

function ModalAvisoRuido({ open, onClose, onConfirm, apartamentos, tenantApi }) {
  const [idApartamento, setIdApartamento] = useState('');
  const [cuerpo, setCuerpo] = useState('Ruido excesivo en zona común. Por favor moderar el volumen.');
  const [sending, setSending] = useState(false);

  async function send() {
    if (!idApartamento) return;
    setSending(true);
    try {
      await tenantApi.post('/buzon/aviso-ruido', {
        idApartamento: Number(idApartamento),
        cuerpo,
      });
      toast.success('Aviso de ruido notificado al residente');
      onConfirm();
    } catch (err) {
      toast.error(err.message || 'Error al enviar aviso');
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
              .filter((a) => a.estado === 'ACTIVA' || a.estado === 'OCUPADO' || !a.estado)
              .map((a) => (
                <option key={a.idApartamento || a.id} value={a.idApartamento || a.id}>
                  {formatApto(a.numero || a.identificador)}
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

function ModalGenerarMulta({ open, onClose, onConfirm, apartamentos, tipoInicial, tenantApi }) {
  const [tipo, setTipo] = useState(tipoInicial || 'RUIDO');
  const [idApartamento, setIdApartamento] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [foto, setFoto] = useState(null);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [estadoAviso, setEstadoAviso] = useState(null);
  const [checkingAviso, setCheckingAviso] = useState(false);

  useEffect(() => {
    if (open) {
      setTipo(tipoInicial || 'RUIDO');
      setIdApartamento('');
      setDescripcion('');
      setFoto(null);
      setError('');
      setEstadoAviso(null);
    }
  }, [open, tipoInicial]);

  useEffect(() => {
    if (!open || tipo !== 'RUIDO' || !idApartamento) {
      setEstadoAviso(null);
      return;
    }
    let cancel = false;
    setCheckingAviso(true);
    tenantApi
      .get(`/buzon/aviso-ruido/estado?idApartamento=${idApartamento}`)
      .then((res) => {
        if (!cancel) setEstadoAviso(res);
      })
      .catch((err) => {
        if (!cancel) {
          setEstadoAviso({
            tieneAviso: false,
            puedeMultar: false,
            mensaje: err.message || 'No fue posible verificar el aviso de ruido.',
          });
        }
      })
      .finally(() => {
        if (!cancel) setCheckingAviso(false);
      });
    return () => {
      cancel = true;
    };
  }, [open, tipo, idApartamento, tenantApi]);

  async function send() {
    if (!idApartamento) {
      setError('Seleccione un apartamento');
      return;
    }
    if (tipo === 'RUIDO') {
      if (!estadoAviso || !estadoAviso.tieneAviso) {
        setError('Debe hacer el aviso primero antes de poder generar la multa.');
        return;
      }
      if (!estadoAviso.puedeMultar) {
        setError(
          estadoAviso.mensaje ||
            'No puede aplicar la multa aún. Deben transcurrir al menos 30 minutos desde el aviso de ruido.'
        );
        return;
      }
    }
    if (tipo === 'PARQUEADERO' && !foto) {
      setError('La foto de evidencia es obligatoria para reporte de parqueadero');
      return;
    }
    setSending(true);
    setError('');
    try {
      const payload = {
        idUnidad: Number(idApartamento),
        titulo: tipo === 'RUIDO' ? 'Infracción por Ruido Excesivo' : 'Infracción por Uso Indebido de Parqueadero',
        tipoIncidente: tipo === 'RUIDO' ? 'CONVIVENCIA' : 'PARQUEADERO',
        nivelSeveridad: 'MODERADA',
        descripcionHechos: descripcion.trim() || (tipo === 'RUIDO' ? 'Ruido excesivo reiterado reportado desde portería' : 'Infracción de parqueadero reportada desde portería'),
        fechaHoraIncidente: new Date().toISOString(),
        evidenciasUrls: foto || null,
        requirioAutoridades: 'N',
        estado: 'ABIERTO',
      };
      await tenantApi.post('/incidentes', payload);
      toast.success('Infracción reportada a administración');
      onConfirm();
    } catch (err) {
      setError(err.message || 'Error al reportar la infracción');
    } finally {
      setSending(false);
    }
  }

  const bloqueoRuido = tipo === 'RUIDO' && idApartamento && (!estadoAviso || !estadoAviso.puedeMultar);

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Reportar Infracción (${tipo === 'RUIDO' ? 'Ruido Excesivo' : 'Parqueadero'})`}
      size="md"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={sending}>
            Cancelar
          </Button>
          <Button onClick={send} disabled={sending || checkingAviso || Boolean(bloqueoRuido)}>
            {sending ? 'Reportando...' : 'Reportar a Administración'}
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {error && (
          <div className="flex items-center gap-2 rounded-lg border border-tint-red-fg/25 bg-tint-red-bg p-3 text-xs font-medium text-tint-red-fg">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="form-group">
          <Select id="multaTipo" label="Tipo de Infracción" value={tipo} onChange={(e) => setTipo(e.target.value)}>
            <option value="RUIDO">Ruido Excesivo / Convivencia</option>
            <option value="PARQUEADERO">Infracción de Parqueadero</option>
          </Select>
        </div>

        <div className="form-group">
          <Select
            id="multaApto"
            label="Apartamento Involucrado"
            value={idApartamento}
            onChange={(e) => {
              setIdApartamento(e.target.value);
              setError('');
            }}
          >
            <option value="">- Seleccionar apartamento -</option>
            {(apartamentos?.items || apartamentos || [])
              .filter((a) => a.estado === 'ACTIVA' || a.estado === 'OCUPADO' || !a.estado)
              .map((a) => (
                <option key={a.idApartamento || a.id} value={a.idApartamento || a.id}>
                  {formatApto(a.numero || a.identificador)}
                </option>
              ))}
          </Select>
        </div>

        {/* Verificación de precedentes de aviso de ruido */}
        {tipo === 'RUIDO' && idApartamento && (
          <div className="space-y-2">
            {checkingAviso ? (
              <div className="flex items-center gap-2 rounded-lg border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
                <Clock className="h-4 w-4 shrink-0" />
                <span>Verificando precedentes de aviso de ruido...</span>
              </div>
            ) : estadoAviso && !estadoAviso.tieneAviso ? (
              <div className="flex items-start gap-2.5 rounded-lg border border-tint-red-fg/25 bg-tint-red-bg p-3 text-xs font-medium text-tint-red-fg">
                <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5 text-tint-red-fg" />
                <div className="space-y-1">
                  <p className="font-bold">Debe hacer el aviso de ruido primero</p>
                  <p className="text-xs leading-relaxed">
                    No se encontró ningún aviso de ruido previo para este apartamento. El protocolo exige enviar primero el aviso al residente.
                  </p>
                </div>
              </div>
            ) : estadoAviso && estadoAviso.tieneAviso && !estadoAviso.puedeMultar ? (
              <div className="flex items-start gap-2.5 rounded-lg border border-tint-amber-fg/25 bg-tint-amber-bg p-3 text-xs font-medium text-tint-amber-fg">
                <Clock className="h-4 w-4 shrink-0 mt-0.5 text-tint-amber-fg" />
                <div className="space-y-1">
                  <p className="font-bold">Tiempo de espera reglamentario en curso</p>
                  <p className="text-xs leading-relaxed">
                    {estadoAviso.mensaje}
                  </p>
                </div>
              </div>
            ) : estadoAviso && estadoAviso.puedeMultar ? (
              <div className="flex items-center gap-2 rounded-lg border border-tint-green-fg/25 bg-tint-green-bg p-3 text-xs font-medium text-tint-green-fg">
                <CheckCircle2 className="h-4 w-4 shrink-0 text-tint-green-fg" />
                <span>{estadoAviso.mensaje}</span>
              </div>
            ) : null}
          </div>
        )}

        <div className="form-group">
          <Textarea
            id="multaDesc"
            label="Descripción de los Hechos"
            rows={2}
            value={descripcion}
            onChange={(e) => setDescripcion(e.target.value)}
            placeholder={tipo === 'RUIDO' ? 'Música a alto volumen reiterada en zona común o apartamento...' : 'Vehículo ocupando zona de maniobra o parqueadero ajeno...'}
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

function ModalPaquetes({ open, onClose, onConfirm, tenantApi }) {
  const { data: paquetes, loading, refetch } = useFetch(
    () => (open ? tenantApi.get('/buzon/paquetes') : Promise.resolve([])),
    [open]
  );
  const [detalle, setDetalle] = useState(null);
  const [marcaId, setMarcaId] = useState(null);

  async function marcarEntregado(idMensaje) {
    setMarcaId(idMensaje);
    try {
      await tenantApi.put(`/buzon/${idMensaje}/entregado`);
      toast.success('Encomienda entregada exitosamente');
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
            { key: 'numeroApartamento', label: 'Apto', render: (r) => formatApto(r.numeroApartamento) },
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
              <span className="font-bold text-foreground">{formatApto(detalle.numeroApartamento)}</span>
            </div>
            <div className="flex justify-between border-b border-border/60 pb-2 text-xs">
              <span className="text-muted-foreground">Destinatario:</span>
              <span className="font-bold text-foreground">{detalle.nombreResidente}</span>
            </div>
            <div className="flex justify-between border-b border-border/60 pb-2 text-xs">
              <span className="text-muted-foreground">Fecha Recepción:</span>
              <span className="text-foreground tabular-nums">{formatDate(detalle.fechaCreacion)}</span>
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

function ModalVisitasActivas({ open, onClose, visitasActivas, onMarcarSalida, marcandoId }) {
  const [search, setSearch] = useState('');

  const filtered = useMemo(() => {
    if (!search.trim()) return visitasActivas;
    const q = search.toLowerCase();
    return visitasActivas.filter((v) =>
      (v.nombreVisitante || '').toLowerCase().includes(q) ||
      (v.documentoVisitante || '').toLowerCase().includes(q) ||
      String(v.numeroApartamento || '').toLowerCase().includes(q)
    );
  }, [visitasActivas, search]);

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Visitas Activas en Predio"
      size="lg"
      footer={
        <div className="flex items-center justify-between w-full">
          <span className="text-xs text-muted-foreground">
            {visitasActivas.length} visitante(s) en curso
          </span>
          <Button variant="outline" onClick={onClose}>
            Cerrar
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <div className="flex items-center gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <input
              type="text"
              aria-label="Buscar visitas activas"
              placeholder="Buscar por visitante, cédula o apartamento..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="h-10 w-full rounded-md border border-input bg-surface pl-9 pr-4 text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25"
            />
          </div>
          {search && (
            <Button variant="ghost" size="sm" onClick={() => setSearch('')}>
              Limpiar
            </Button>
          )}
        </div>

        <DataTable
          columns={[
            {
              key: 'visitante',
              label: 'Visitante',
              render: (r) => (
                <div>
                  <p className="font-semibold text-foreground text-xs">{r.nombreVisitante || 'Visitante'}</p>
                  {r.documentoVisitante && (
                    <p className="text-xs text-muted-foreground tabular-nums">Doc: {r.documentoVisitante}</p>
                  )}
                </div>
              ),
            },
            {
              key: 'numeroApartamento',
              label: 'Apto',
              render: (r) => (
                <Badge variant="outline" className="tabular-nums text-xs">
                  {formatApto(r.numeroApartamento)}
                </Badge>
              ),
            },
            {
              key: 'fechaIngreso',
              label: 'Ingreso',
              render: (r) => (
                <span className="text-xs text-muted-foreground tabular-nums">
                  {r.fechaIngreso ? formatDate(r.fechaIngreso) : 'En espera'}
                </span>
              ),
            },
            {
              key: 'estado',
              label: 'Estado',
              render: (r) => (
                <Badge variant={r.estado === 'ACTIVA' ? 'success' : 'warning'} className="text-xs">
                  {r.estado}
                </Badge>
              ),
            },
            {
              key: 'actions',
              label: '',
              render: (r) => (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => onMarcarSalida(r)}
                  disabled={marcandoId === r.idVisita}
                  className="text-xs py-1 px-2.5 h-auto font-semibold text-tint-red-fg hover:text-tint-red-fg hover:bg-tint-red-bg border-tint-red-fg/25 flex items-center gap-1.5"
                >
                  <LogOut className="h-3.5 w-3.5" />
                  {marcandoId === r.idVisita ? 'Registrando...' : 'Marcar Salida'}
                </Button>
              ),
            },
          ]}
          rows={filtered}
          pageSize={5}
          empty={{
            icon: 'person_off',
            title: 'No hay visitas activas',
            subtitle: search ? 'Ningún visitante coincide con la búsqueda.' : 'No hay visitantes dentro del predio en este momento.',
          }}
          keyField="idVisita"
        />
      </div>
    </Modal>
  );
}

export default function PorteroDashboardPage() {
  const navigate = useNavigate();
  const tenantApi = useTenantApi();
  const [modalAviso, setModalAviso] = useState(false);
  const [modalMulta, setModalMulta] = useState(null); // 'RUIDO' | 'PARQUEADERO' | null
  const [modalPaquetes, setModalPaquetes] = useState(false);
  const [modalVisitasActivas, setModalVisitasActivas] = useState(false);
  const [searchVisitasActivas, setSearchVisitasActivas] = useState('');
  const [registrandoSalidaId, setRegistrandoSalidaId] = useState(null);
  const [modalDomicilio, setModalDomicilio] = useState(false);
  const [finalizandoDomId, setFinalizandoDomId] = useState(null);

  // Endpoint atomico de KPIs de Porteria (F11-01.4)
  const { data: kpisPorteriaRaw, refetch: refetchKpisPorteria } = useFetch(
    () => tenantApi.get('/dashboard/porteria'),
    []
  );
  const kpisPorteria = useMemo(() => kpisPorteriaRaw?.data || kpisPorteriaRaw || null, [kpisPorteriaRaw]);

  // Consume endpoints de detalle operativos
  const { data: visitasRaw, loading: loadingVisitas, error: errorVisitas, refetch: refetchVisitas } = useFetch(
    () => tenantApi.get('/porteria/visitas-resumen'),
    []
  );
  const { data: parqueaderosRaw, refetch: refetchParq } = useFetch(
    () => tenantApi.get('/parqueaderos'),
    []
  );
  const { data: paquetesRaw } = useFetch(() => tenantApi.get('/buzon/paquetes-pendientes'), []);
  const { data: apartamentos } = useFetch(() => tenantApi.get('/units'), []);
  const { data: domiciliosRaw, loading: loadingDomicilios, error: errorDomicilios, refetch: refetchDomicilios } = useFetch(
    () => tenantApi.get('/domicilios?estado=EN_CURSO'),
    []
  );
  const { data: minutaRaw, loading: loadingMinuta, error: errorMinuta } = useFetch(
    () => tenantApi.get('/emergencias/contactos/minuta'),
    []
  );

  // GAP-F7-03-B: Polling periódico de 60s para refrescar permanencia operativa
  useEffect(() => {
    const timer = setInterval(() => {
      refetchKpisPorteria();
      refetchVisitas();
      refetchParq();
      refetchDomicilios();
    }, 60000);
    return () => clearInterval(timer);
  }, [refetchKpisPorteria, refetchVisitas, refetchParq, refetchDomicilios]);

  const visitas = useMemo(() => {
    const list = Array.isArray(visitasRaw) ? visitasRaw : visitasRaw?.items ?? [];
    return Array.isArray(list) ? list : [];
  }, [visitasRaw]);

  const parqueaderos = useMemo(() => {
    const list = Array.isArray(parqueaderosRaw) ? parqueaderosRaw : parqueaderosRaw?.items ?? [];
    return Array.isArray(list) ? list : [];
  }, [parqueaderosRaw]);

  const domiciliosActivos = useMemo(() => {
    const list = Array.isArray(domiciliosRaw) ? domiciliosRaw : domiciliosRaw?.items ?? [];
    return Array.isArray(list) ? list : [];
  }, [domiciliosRaw]);

  const contactosMinuta = useMemo(() => {
    if (!minutaRaw) return [];
    if (Array.isArray(minutaRaw)) return minutaRaw;
    if (Array.isArray(minutaRaw.items)) return minutaRaw.items;
    return minutaRaw.data || [];
  }, [minutaRaw]);

  const visitasActivas = useMemo(() => {
    return visitas.filter((v) => v.estado === 'ACTIVA' || v.estado === 'EN_CURSO' || v.estado === 'PENDIENTE');
  }, [visitas]);

  const visitasActivasFiltradas = useMemo(() => {
    if (!searchVisitasActivas.trim()) return visitasActivas;
    const q = searchVisitasActivas.toLowerCase();
    return visitasActivas.filter((v) =>
      (v.nombreVisitante || '').toLowerCase().includes(q) ||
      (v.documentoVisitante || '').toLowerCase().includes(q) ||
      String(v.numeroApartamento || '').toLowerCase().includes(q)
    );
  }, [visitasActivas, searchVisitasActivas]);

  async function handleRegistrarSalida(visita) {
    if (!visita?.idVisita) return;
    setRegistrandoSalidaId(visita.idVisita);
    try {
      await tenantApi.put(`/porteria/visitas/${visita.idVisita}/salida`);
      toast.success(`Salida registrada para ${visita.nombreVisitante || 'visitante'}`);
      await Promise.all([refetchVisitas(), refetchParq()]);
    } catch (err) {
      toast.error(err.message || 'Error al registrar salida de la visita');
    } finally {
      setRegistrandoSalidaId(null);
    }
  }

  async function handleFinalizarDomicilio(dom) {
    if (!dom?.idDomicilio) return;
    setFinalizandoDomId(dom.idDomicilio);
    try {
      await tenantApi.patch(`/domicilios/${dom.idDomicilio}/finalizar`);
      toast.success(`Salida registrada para repartidor de ${dom.empresa}`);
      await refetchDomicilios();
    } catch (err) {
      toast.error(err.message || 'Error al finalizar domicilio');
    } finally {
      setFinalizandoDomId(null);
    }
  }

  const parqVisitantes = useMemo(() => {
    return parqueaderos.filter((p) => p.esVisitante);
  }, [parqueaderos]);

  const parqVisitantesTotal = useMemo(() => {
    if (kpisPorteria?.parqueaderosVisitantesTotal != null) return Number(kpisPorteria.parqueaderosVisitantesTotal);
    return parqVisitantes.length || 0;
  }, [kpisPorteria, parqVisitantes]);

  const parqDisponibles = useMemo(() => {
    if (kpisPorteria?.parqueaderosDisponibles != null) return Number(kpisPorteria.parqueaderosDisponibles);
    return parqVisitantes.filter((p) => p.estado === 'DISPONIBLE').length;
  }, [kpisPorteria, parqVisitantes]);

  const paquetesCount = useMemo(() => {
    if (kpisPorteria?.paquetesEnCustodia != null) return Number(kpisPorteria.paquetesEnCustodia);
    if (paquetesRaw?.count != null) return paquetesRaw.count;
    if (Array.isArray(paquetesRaw)) return paquetesRaw.length;
    if (paquetesRaw?.items) return paquetesRaw.items.length;
    return 0;
  }, [kpisPorteria, paquetesRaw]);

  const totalVisitasActivasCount = useMemo(() => {
    if (kpisPorteria?.visitasActivas != null) return Number(kpisPorteria.visitasActivas);
    return visitasActivas.length;
  }, [kpisPorteria, visitasActivas]);

  const totalDomiciliosActivosCount = useMemo(() => {
    if (kpisPorteria?.domiciliosActivos != null) return Number(kpisPorteria.domiciliosActivos);
    return domiciliosActivos.length;
  }, [kpisPorteria, domiciliosActivos]);

  const totalPasesCount = useMemo(() => {
    if (kpisPorteria?.totalPases != null) return Number(kpisPorteria.totalPases);
    return visitas.length;
  }, [kpisPorteria, visitas]);

  const breadcrumbs = useMemo(
    () => [
      { label: 'Inicio', href: '/' },
      { label: 'Panel de portería', active: true },
    ],
    []
  );

  const refreshing = loadingVisitas || loadingDomicilios;

  return (
    <PageContainer breadcrumbs={breadcrumbs} maxWidth="max-w-7xl">
      <PageHeader
        title="Panel de portería"
        subtitle="Accesos, visitas, domicilios y convivencia del turno en un solo lugar."
        action={
          <>
            <UiButton
              variant="outline"
              className="h-10"
              onClick={() => {
                refetchVisitas();
                refetchParq();
                refetchDomicilios();
              }}
              aria-label="Actualizar datos de garita"
            >
              <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} aria-hidden="true" />
              <span className="hidden sm:inline">Actualizar</span>
            </UiButton>
          </>
        }
      />

      {/* Primary gatehouse actions: large, always in the same place */}
      <section aria-label="Acciones de garita" className="rounded-lg border border-border bg-surface p-3 shadow-sm sm:p-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
          <Button
            size="lg"
            onClick={() => navigate('/escanner-qr')}
            className="h-12 w-full text-base lg:w-auto"
          >
            <QrCode className="h-5 w-5" aria-hidden="true" />
            Abrir escáner QR
          </Button>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:flex lg:flex-1 lg:flex-wrap lg:justify-end">
            <Button variant="outline" onClick={() => setModalDomicilio(true)} className="h-11">
              <Bike className="h-4 w-4" aria-hidden="true" />
              Registrar domicilio
            </Button>
            <Button variant="outline" onClick={() => setModalPaquetes(true)} className="h-11">
              <Package className="h-4 w-4" aria-hidden="true" />
              Entregar paquete
            </Button>
            <Button variant="outline" onClick={() => setModalAviso(true)} className="h-11">
              <Volume2 className="h-4 w-4" aria-hidden="true" />
              Aviso de ruido
            </Button>
            <Button variant="outline" onClick={() => setModalMulta('RUIDO')} className="h-11">
              <Gavel className="h-4 w-4" aria-hidden="true" />
              Multa por ruido
            </Button>
            <Button variant="outline" onClick={() => setModalMulta('PARQUEADERO')} className="h-11">
              <Car className="h-4 w-4" aria-hidden="true" />
              Multa de parqueadero
            </Button>
          </div>
        </div>
      </section>

      {/* Operational KPIs */}
      <div className="grid grid-cols-1 gap-3 min-[420px]:grid-cols-2 lg:grid-cols-5">
        <MetricCard
          title="Visitas dentro"
          value={totalVisitasActivasCount}
          subtitle="En el predio o programadas hoy"
          icon={UserCheck}
          variant="primary"
          onClick={() => setModalVisitasActivas(true)}
        />
        <MetricCard
          title="Domicilios en curso"
          value={totalDomiciliosActivosCount}
          subtitle="Repartidores en el predio"
          icon={Bike}
          variant="warning"
          onClick={() => setModalDomicilio(true)}
        />
        <MetricCard
          title="Pases registrados"
          value={totalPasesCount}
          subtitle="Visitas registradas"
          icon={LogIn}
          variant="info"
        />
        <MetricCard
          title="Cupos de visitantes libres"
          value={`${parqDisponibles} / ${parqVisitantesTotal}`}
          subtitle={parqDisponibles === 0 ? 'Sin cupos disponibles' : 'Disponibles para ingreso'}
          icon={Car}
          variant={parqDisponibles > 2 ? 'success' : 'warning'}
        />
        <MetricCard
          title="Paquetes en custodia"
          value={paquetesCount}
          subtitle="Pendientes de entrega"
          icon={Package}
          variant="secondary"
          onClick={() => setModalPaquetes(true)}
        />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
        <div className="min-w-0 space-y-6 xl:col-span-2">
          {/* Active visits and exits */}
          <OpsSection
            id="porteria-visitas"
            icon={UserCheck}
            title="Visitas activas"
            count={visitasActivas.length}
            countLabel="dentro"
            countVariant={visitasActivas.length > 0 ? 'success' : 'secondary'}
            description="Registre la salida de los visitantes sin salir de este panel."
            actions={
              <>
                <div className="relative w-full sm:w-64">
                  <Search
                    className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
                    aria-hidden="true"
                  />
                  <ShadcnInput
                    type="search"
                    aria-label="Filtrar visitas activas"
                    placeholder="Nombre, documento o apto"
                    value={searchVisitasActivas}
                    onChange={(e) => setSearchVisitasActivas(e.target.value)}
                    className="h-10 pl-9"
                  />
                </div>
                <Button variant="ghost" size="sm" onClick={() => navigate('/visitas')} className="shrink-0">
                  Ver todas
                  <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                </Button>
              </>
            }
          >
            {errorVisitas && !visitas.length ? (
              <ErrorState
                compact
                title="No se pudieron cargar las visitas"
                message={errorVisitas.message || 'Revise la conexión e intente de nuevo.'}
                onRetry={refetchVisitas}
              />
            ) : (
              <DataTable
                loading={loadingVisitas && !visitas.length}
                columns={[
                  {
                    key: 'visitante',
                    label: 'Visitante',
                    render: (r) => (
                      <div className="min-w-0">
                        <span className="text-sm font-semibold text-on-surface">{r.nombreVisitante || 'Visitante'}</span>
                        {r.documentoVisitante && (
                          <span className="block text-xs text-text-muted tabular-nums">Doc. {r.documentoVisitante}</span>
                        )}
                      </div>
                    ),
                  },
                  {
                    key: 'numeroApartamento',
                    label: 'Apartamento',
                    render: (r) => (
                      <Badge variant="outline" className="tabular-nums">
                        {formatApto(r.numeroApartamento)}
                      </Badge>
                    ),
                  },
                  {
                    key: 'fechaIngreso',
                    label: 'Ingreso',
                    render: (r) => (
                      <span className="text-xs text-text-secondary tabular-nums">
                        {r.fechaIngreso ? formatDateTime(r.fechaIngreso) : 'En espera'}
                      </span>
                    ),
                  },
                  {
                    key: 'permanencia',
                    label: 'Permanencia',
                    render: (r) => <Permanencia r={r} excedidoLabel="Demora" />,
                  },
                  {
                    key: 'estado',
                    label: 'Estado',
                    render: (r) => (
                      <Badge variant={r.estado === 'ACTIVA' || r.estado === 'EN_CURSO' ? 'success' : 'warning'}>
                        {ESTADO_VISITA_LABEL[r.estado] || r.estado}
                      </Badge>
                    ),
                  },
                  {
                    key: 'actions',
                    label: 'Salida',
                    render: (r) => (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => handleRegistrarSalida(r)}
                        loading={registrandoSalidaId === r.idVisita}
                        className="text-destructive hover:bg-tint-red-bg hover:text-destructive"
                      >
                        {registrandoSalidaId !== r.idVisita && <LogOut className="h-3.5 w-3.5" aria-hidden="true" />}
                        {registrandoSalidaId === r.idVisita ? 'Registrando...' : 'Marcar salida'}
                      </Button>
                    ),
                  },
                ]}
                rows={visitasActivasFiltradas}
                pageSize={5}
                empty={{
                  icon: UserCheck,
                  title: searchVisitasActivas ? 'Ninguna visita coincide con el filtro' : 'No hay visitas activas en el predio',
                  subtitle: searchVisitasActivas
                    ? 'Revise el nombre, documento o apartamento.'
                    : 'Cuando ingrese una visita autorizada aparecerá aquí para registrar su salida.',
                }}
                keyField="idVisita"
              />
            )}
          </OpsSection>

          {/* Deliveries in progress */}
          <OpsSection
            id="porteria-domicilios"
            icon={Bike}
            title="Domicilios en el predio"
            count={domiciliosActivos.length}
            countLabel="activos"
            countVariant={domiciliosActivos.length > 0 ? 'warning' : 'secondary'}
            description="Controle la permanencia de los repartidores y registre su salida."
            actions={
              <Button size="sm" onClick={() => setModalDomicilio(true)} className="shrink-0">
                <Plus className="h-3.5 w-3.5" aria-hidden="true" />
                Registrar domicilio
              </Button>
            }
          >
            {errorDomicilios && !domiciliosActivos.length ? (
              <ErrorState
                compact
                title="No se pudieron cargar los domicilios"
                message={errorDomicilios.message || 'Revise la conexión e intente de nuevo.'}
                onRetry={refetchDomicilios}
              />
            ) : (
              <DataTable
                loading={loadingDomicilios && !domiciliosActivos.length}
                columns={[
                  {
                    key: 'repartidor',
                    label: 'Repartidor',
                    render: (r) => (
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-1.5">
                          <span className="text-sm font-semibold text-on-surface">{r.nombreDomiciliario}</span>
                          <Badge variant="secondary">{r.empresa}</Badge>
                        </div>
                        <div className="mt-0.5 flex flex-wrap items-center gap-x-2 text-xs text-text-muted tabular-nums">
                          {r.documentoDomiciliario && <span>Doc. {r.documentoDomiciliario}</span>}
                          {r.telefonoDomiciliario && <span>Tel. {r.telefonoDomiciliario}</span>}
                        </div>
                      </div>
                    ),
                  },
                  {
                    key: 'numeroApartamento',
                    label: 'Destino',
                    render: (r) => (
                      <Badge variant="outline" className="tabular-nums">
                        {formatApto(r.numeroApartamento || r.idUnidad)}
                      </Badge>
                    ),
                  },
                  {
                    key: 'tipoTransporte',
                    label: 'Tipo',
                    render: (r) => (
                      <div className="flex flex-col gap-0.5">
                        <span className="text-xs font-medium text-on-surface">{humanize(r.tipoDomicilio || 'COMIDA')}</span>
                        <span className="text-xs text-text-muted">
                          {humanize(r.medioTransporte || 'A PIE')} {r.placaVehiculo ? `· ${r.placaVehiculo}` : ''}
                        </span>
                      </div>
                    ),
                  },
                  {
                    key: 'fechaIngreso',
                    label: 'Ingreso',
                    render: (r) => {
                      const fecha = r.fechaEntrada || r.fechaIngreso;
                      return (
                        <span className="text-xs text-text-secondary tabular-nums">
                          {fecha ? formatDateTime(fecha) : '—'}
                        </span>
                      );
                    },
                  },
                  {
                    key: 'permanencia',
                    label: 'Permanencia',
                    render: (r) => <Permanencia r={r} excedidoLabel="Excedido" />,
                  },
                  {
                    key: 'actions',
                    label: 'Salida',
                    render: (r) => (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => handleFinalizarDomicilio(r)}
                        loading={finalizandoDomId === r.idDomicilio}
                      >
                        {finalizandoDomId !== r.idDomicilio && <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />}
                        {finalizandoDomId === r.idDomicilio ? 'Finalizando...' : 'Finalizar entrega'}
                      </Button>
                    ),
                  },
                ]}
                rows={domiciliosActivos}
                pageSize={5}
                empty={{
                  icon: Bike,
                  title: 'No hay domicilios en el predio',
                  subtitle: 'Registre cada repartidor al ingresar para controlar su permanencia y su salida.',
                }}
                keyField="idDomicilio"
              />
            )}
          </OpsSection>
        </div>

        <div className="min-w-0 space-y-6">
          {/* Emergency directory */}
          <OpsSection
            id="porteria-minuta"
            icon={Siren}
            title="Contactos de emergencia"
            description="Directorio prioritario de la garita."
          >
            {loadingMinuta ? (
              <LoadingState size="sm" message="Cargando contactos de emergencia..." />
            ) : errorMinuta ? (
              <ErrorState
                compact
                title="No se pudo cargar la minuta"
                message="Error al consultar los contactos de emergencia de la propiedad."
              />
            ) : contactosMinuta.length === 0 ? (
              <EmptyState
                icon={Phone}
                title="Sin contactos en la minuta"
                subtitle="La administración aún no ha marcado contactos prioritarios para la garita."
                className="py-6"
              />
            ) : (
              <ul className="divide-y divide-border">
                {contactosMinuta.map((c, idx) => (
                  <li key={c.idContactoEmergencia || idx} className="flex flex-wrap items-center gap-3 py-3 first:pt-0 last:pb-0">
                    <div className="min-w-0 flex-1">
                      <Badge variant="destructive" className="mb-1">
                        {c.tipoServicio ? humanize(c.tipoServicio) : 'Emergencia'}
                      </Badge>
                      <p className="truncate text-sm font-semibold text-on-surface">{c.entidad}</p>
                      {c.direccion && (
                        <p className="mt-0.5 flex items-center gap-1 truncate text-xs text-text-muted">
                          <MapPin className="h-3 w-3 shrink-0" aria-hidden="true" /> {c.direccion}
                        </p>
                      )}
                    </div>
                    <div className="flex shrink-0 flex-col items-end gap-1">
                      <a
                        href={`tel:${c.telefonoPrincipal}`}
                        className="inline-flex h-11 items-center gap-1.5 rounded-md bg-btn-danger px-3 text-sm font-bold text-white tabular-nums shadow-sm transition-colors duration-150 hover:bg-btn-danger-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                        aria-label={`Llamar a ${c.entidad}: ${c.telefonoPrincipal}`}
                      >
                        <Phone className="h-4 w-4" aria-hidden="true" />
                        {c.telefonoPrincipal}
                      </a>
                      {c.telefonoAlterno && (
                        <a
                          href={`tel:${c.telefonoAlterno}`}
                          className="text-xs text-text-secondary tabular-nums underline-offset-4 hover:text-on-surface hover:underline"
                        >
                          Alterno: {c.telefonoAlterno}
                        </a>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </OpsSection>

          {/* Quick navigation to the gatehouse modules */}
          <OpsSection id="porteria-modulos" icon={ClipboardList} title="Módulos de garita">
            <ul className="-mx-1 divide-y divide-border">
              {ACCIONES_OPERATIVAS.map((a) => {
                const Icon = a.icon;
                return (
                  <li key={a.id}>
                    <button
                      type="button"
                      onClick={() => navigate(a.path)}
                      className="group flex min-h-12 w-full items-center gap-3 rounded-md px-1 py-2.5 text-left transition-colors duration-150 hover:bg-surface-container focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    >
                      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-tint-primary-bg text-tint-primary-fg">
                        <Icon className="h-4 w-4" aria-hidden="true" />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block text-sm font-semibold text-on-surface">{a.label}</span>
                        <span className="block truncate text-xs text-text-muted">{a.desc}</span>
                      </span>
                      <ChevronRight
                        className="h-4 w-4 shrink-0 text-text-muted transition-transform duration-150 group-hover:translate-x-0.5"
                        aria-hidden="true"
                      />
                    </button>
                  </li>
                );
              })}
            </ul>
          </OpsSection>
        </div>
      </div>

      <KpiSummaryPanel />

      {/* Modales funcionales */}
      <ModalVisitasActivas
        open={modalVisitasActivas}
        onClose={() => setModalVisitasActivas(false)}
        visitasActivas={visitasActivas}
        onMarcarSalida={handleRegistrarSalida}
        marcandoId={registrandoSalidaId}
      />

      <ModalAvisoRuido
        open={modalAviso}
        onClose={() => setModalAviso(false)}
        onConfirm={() => {
          setModalAviso(false);
        }}
        apartamentos={apartamentos?.items || apartamentos || []}
        tenantApi={tenantApi}
      />

      <ModalGenerarMulta
        key={modalMulta || 'closed'}
        open={!!modalMulta}
        onClose={() => setModalMulta(null)}
        onConfirm={() => {
          setModalMulta(null);
        }}
        apartamentos={apartamentos?.items || apartamentos || []}
        tipoInicial={modalMulta}
        tenantApi={tenantApi}
      />

      <ModalPaquetes
        open={modalPaquetes}
        onClose={() => setModalPaquetes(false)}
        onConfirm={() => toast.success('Encomienda entregada exitosamente')}
        tenantApi={tenantApi}
      />

      <ModalRegistrarDomicilio
        open={modalDomicilio}
        onClose={() => setModalDomicilio(false)}
        onConfirm={async () => {
          setModalDomicilio(false);
          await refetchDomicilios();
        }}
        apartamentos={apartamentos?.items || apartamentos || []}
        tenantApi={tenantApi}
      />
    </PageContainer>
  );
}

const ESTADO_VISITA_LABEL = {
  ACTIVA: 'Activa',
  EN_CURSO: 'En curso',
  PENDIENTE: 'Pendiente',
};

function humanize(value) {
  const s = String(value || '').replace(/_/g, ' ').toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function Permanencia({ r, excedidoLabel }) {
  if (r.minutosTranscurridos == null) {
    return <span className="text-xs text-text-muted">—</span>;
  }
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <Clock className="h-3.5 w-3.5 shrink-0 text-text-muted" aria-hidden="true" />
      <span className="text-xs font-semibold text-on-surface tabular-nums">{r.minutosTranscurridos} min</span>
      {r.tiempoMaximoMinutos && (
        <span className="text-xs text-text-muted tabular-nums">/ {r.tiempoMaximoMinutos} min</span>
      )}
      {r.excedido && (
        <Badge variant="destructive">
          <AlertTriangle className="h-3 w-3" aria-hidden="true" />
          {excedidoLabel}
        </Badge>
      )}
    </div>
  );
}

function OpsSection({ id, icon: Icon, title, count, countLabel, countVariant = 'secondary', description, actions, children }) {
  return (
    <section aria-labelledby={`${id}-title`} className="rounded-lg border border-border bg-surface shadow-sm">
      <div className="flex flex-col gap-3 border-b border-border px-4 py-3.5 sm:flex-row sm:items-center sm:justify-between sm:px-5">
        <div className="min-w-0">
          <h2 id={`${id}-title`} className="flex flex-wrap items-center gap-2 text-base font-bold text-on-surface">
            {Icon && <Icon className="h-4 w-4 text-primary-text" aria-hidden="true" />}
            {title}
            {count != null && (
              <Badge variant={countVariant} className="tabular-nums">
                {count} {countLabel}
              </Badge>
            )}
          </h2>
          {description && <p className="mt-0.5 text-xs text-text-secondary">{description}</p>}
        </div>
        {actions && <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">{actions}</div>}
      </div>
      <div className="p-4 sm:p-5">{children}</div>
    </section>
  );
}
