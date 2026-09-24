import { useState, useMemo, useEffect } from 'react';
import { toast } from 'sonner';
import { useNavigate } from 'react-router-dom';
import {
  AlertCircle,
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
  ShieldCheck,
  Siren,
  Truck,
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
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card.tsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { formatDate, formatDateTime, formatApto, imageSrc } from '../lib/utils.js';
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
          <div className="flex items-center gap-2 rounded-lg border border-rose-500/20 bg-rose-500/10 p-3 text-xs font-medium text-rose-700 dark:text-rose-300">
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
          <div className="flex items-center gap-2 rounded-lg border border-rose-500/20 bg-rose-500/10 p-3 text-xs font-medium text-rose-700 dark:text-rose-300">
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
              <div className="flex items-center gap-2 rounded-lg border border-border bg-muted/40 p-3 text-xs text-muted-foreground animate-pulse">
                <Clock className="h-4 w-4 shrink-0" />
                <span>Verificando precedentes de aviso de ruido...</span>
              </div>
            ) : estadoAviso && !estadoAviso.tieneAviso ? (
              <div className="flex items-start gap-2.5 rounded-lg border border-rose-500/30 bg-rose-500/10 p-3 text-xs font-medium text-rose-700 dark:text-rose-300">
                <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5 text-rose-600 dark:text-rose-400" />
                <div className="space-y-1">
                  <p className="font-bold">Debe hacer el aviso de ruido primero</p>
                  <p className="text-[11px] leading-relaxed">
                    No se encontró ningún aviso de ruido previo para este apartamento. El protocolo exige enviar primero el aviso al residente.
                  </p>
                </div>
              </div>
            ) : estadoAviso && estadoAviso.tieneAviso && !estadoAviso.puedeMultar ? (
              <div className="flex items-start gap-2.5 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-xs font-medium text-amber-700 dark:text-amber-300">
                <Clock className="h-4 w-4 shrink-0 mt-0.5 text-amber-600 dark:text-amber-400" />
                <div className="space-y-1">
                  <p className="font-bold">Tiempo de espera reglamentario en curso</p>
                  <p className="text-[11px] leading-relaxed">
                    {estadoAviso.mensaje}
                  </p>
                </div>
              </div>
            ) : estadoAviso && estadoAviso.puedeMultar ? (
              <div className="flex items-center gap-2 rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-xs font-medium text-emerald-700 dark:text-emerald-300">
                <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
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
              placeholder="Buscar por visitante, cédula o apartamento..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-9 pr-4 py-2 rounded-lg border border-border bg-background text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary"
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
                    <p className="text-[11px] text-muted-foreground font-mono">Doc: {r.documentoVisitante}</p>
                  )}
                </div>
              ),
            },
            {
              key: 'numeroApartamento',
              label: 'Apto',
              render: (r) => (
                <Badge variant="outline" className="font-mono text-xs">
                  {formatApto(r.numeroApartamento)}
                </Badge>
              ),
            },
            {
              key: 'fechaIngreso',
              label: 'Ingreso',
              render: (r) => (
                <span className="text-xs text-muted-foreground font-mono">
                  {r.fechaIngreso ? formatDate(r.fechaIngreso) : 'En espera'}
                </span>
              ),
            },
            {
              key: 'estado',
              label: 'Estado',
              render: (r) => (
                <Badge variant={r.estado === 'ACTIVA' ? 'success' : 'warning'} className="text-[10px]">
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
                  className="text-xs py-1 px-2.5 h-auto font-semibold text-rose-600 hover:text-rose-700 hover:bg-rose-50 dark:hover:bg-rose-950/30 border-rose-200 dark:border-rose-900/50 flex items-center gap-1.5"
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
  const { data: visitasRaw, loading: loadingVisitas, refetch: refetchVisitas } = useFetch(
    () => tenantApi.get('/porteria/visitas-resumen'),
    []
  );
  const { data: parqueaderosRaw, refetch: refetchParq } = useFetch(
    () => tenantApi.get('/parqueaderos'),
    []
  );
  const { data: paquetesRaw } = useFetch(() => tenantApi.get('/buzon/paquetes-pendientes'), []);
  const { data: apartamentos } = useFetch(() => tenantApi.get('/units'), []);
  const { data: domiciliosRaw, loading: loadingDomicilios, refetch: refetchDomicilios } = useFetch(
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
              refetchDomicilios();
            }}
            className="flex items-center gap-2 border-border/80 hover:bg-muted/50"
            aria-label="Actualizar datos de garita"
          >
            <RefreshCw className={`h-4 w-4 ${loadingVisitas || loadingDomicilios ? 'animate-spin' : ''}`} />
            <span className="hidden sm:inline">Actualizar</span>
          </Button>
        </div>
      </div>

      {/* Tira de KPIs Operativos */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <MetricCard
          title="Visitas Activas / Dentro"
          value={totalVisitasActivasCount}
          subtitle="En predio o programadas hoy · Clic para ver"
          icon={UserCheck}
          variant="primary"
          onClick={() => setModalVisitasActivas(true)}
          className="cursor-pointer transition-all hover:border-primary/50"
        />
        <MetricCard
          title="Domicilios en Curso"
          value={totalDomiciliosActivosCount}
          subtitle="Repartidores en predio · Clic registrar"
          icon={Bike}
          variant="warning"
          onClick={() => setModalDomicilio(true)}
          className="cursor-pointer transition-all hover:border-amber-500/50"
        />
        <MetricCard
          title="Total Pases Registrados"
          value={totalPasesCount}
          subtitle="Censo de visitas del condominio"
          icon={LogIn}
          variant="info"
        />
        <MetricCard
          title="Cupos Visitantes Libres"
          value={`${parqDisponibles} / ${parqVisitantesTotal}`}
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

      {/* Gestión Inmediata de Visitas Activas y Salidas */}
      <Card className="border-border/80 shadow-xs">
        <CardHeader className="pb-3 border-b border-border/60">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle className="text-base font-bold flex items-center gap-2">
                <UserCheck className="h-5 w-5 text-primary" />
                Control de Visitas Activas en Garita
                <Badge variant={visitasActivas.length > 0 ? 'success' : 'secondary'} className="ml-1 text-xs">
                  {visitasActivas.length} dentro
                </Badge>
              </CardTitle>
              <p className="text-xs text-muted-foreground mt-0.5">
                Registre la salida de visitantes directamente desde este panel sin navegar a otras secciones.
              </p>
            </div>
            <div className="flex items-center gap-2">
              <div className="relative w-full sm:w-64">
                <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
                <input
                  type="text"
                  placeholder="Filtrar por nombre, doc o apto..."
                  value={searchVisitasActivas}
                  onChange={(e) => setSearchVisitasActivas(e.target.value)}
                  className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-border bg-background text-xs text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary"
                />
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={() => navigate('/visitas')}
                className="text-xs shrink-0 flex items-center gap-1"
              >
                <span>Ver Todas</span>
                <ArrowRight className="h-3 w-3" />
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent className="pt-4">
          <DataTable
            columns={[
              {
                key: 'visitante',
                label: 'Visitante',
                render: (r) => (
                  <div>
                    <span className="font-semibold text-foreground text-xs">{r.nombreVisitante || 'Visitante'}</span>
                    {r.documentoVisitante && (
                      <span className="block text-[11px] text-muted-foreground font-mono">Doc: {r.documentoVisitante}</span>
                    )}
                  </div>
                ),
              },
              {
                key: 'numeroApartamento',
                label: 'Apartamento',
                render: (r) => (
                  <Badge variant="outline" className="font-mono text-xs">
                    {formatApto(r.numeroApartamento)}
                  </Badge>
                ),
              },
              {
                key: 'fechaIngreso',
                label: 'Hora Ingreso',
                render: (r) => (
                  <span className="text-xs text-muted-foreground font-mono">
                    {r.fechaIngreso ? formatDateTime(r.fechaIngreso) : 'En espera'}
                  </span>
                ),
              },
              {
                key: 'permanencia',
                label: 'Permanencia',
                render: (r) => {
                  if (r.minutosTranscurridos == null) {
                    return <span className="text-xs text-muted-foreground">—</span>;
                  }
                  return (
                    <div className="flex items-center gap-1.5">
                      <Clock className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                      <span className="text-xs font-mono font-medium">
                        {r.minutosTranscurridos} min
                      </span>
                      {r.tiempoMaximoMinutos && (
                        <span className="text-[10px] text-muted-foreground">
                          / {r.tiempoMaximoMinutos}m
                        </span>
                      )}
                      {r.excedido && (
                        <Badge variant="destructive" className="ml-1 text-[10px] py-0 px-1.5 flex items-center gap-1">
                          <AlertTriangle className="h-3 w-3" />
                          <span>Demora</span>
                        </Badge>
                      )}
                    </div>
                  );
                },
              },
              {
                key: 'estado',
                label: 'Estado',
                render: (r) => (
                  <Badge variant={(r.estado === 'ACTIVA' || r.estado === 'EN_CURSO') ? 'success' : 'warning'} className="text-[10px]">
                    {r.estado}
                  </Badge>
                ),
              },
              {
                key: 'actions',
                label: 'Acción Salida',
                render: (r) => (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => handleRegistrarSalida(r)}
                    disabled={registrandoSalidaId === r.idVisita}
                    className="text-xs py-1 px-2.5 h-auto font-semibold text-rose-600 hover:text-rose-700 hover:bg-rose-50 dark:hover:bg-rose-950/30 border-rose-200 dark:border-rose-900/50 flex items-center gap-1.5"
                  >
                    <LogOut className="h-3.5 w-3.5" />
                    {registrandoSalidaId === r.idVisita ? 'Registrando...' : 'Marcar Salida'}
                  </Button>
                ),
              },
            ]}
            rows={visitasActivasFiltradas}
            pageSize={5}
            empty={{
              icon: 'person_off',
              title: 'No hay visitas activas en el predio',
              subtitle: searchVisitasActivas
                ? 'No se encontraron resultados con ese filtro.'
                : 'Cuando ingrese una visita autorizada, aparecerá aquí para registrar su salida.',
            }}
            keyField="idVisita"
          />
        </CardContent>
      </Card>

      {/* Control Operativo de Domicilios en Curso (GAP-F7-03-A) */}
      <Card className="border-border/80 shadow-xs">
        <CardHeader className="pb-3 border-b border-border/60">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle className="text-base font-bold flex items-center gap-2">
                <Bike className="h-5 w-5 text-amber-500" />
                Domicilios y Repartidores en Predio
                <Badge variant={domiciliosActivos.length > 0 ? 'warning' : 'secondary'} className="ml-1 text-xs">
                  {domiciliosActivos.length} activos
                </Badge>
              </CardTitle>
              <p className="text-xs text-muted-foreground mt-0.5">
                Seguimiento en tiempo real de entregas y domiciliarios autorizados en garita.
              </p>
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="primary"
                size="sm"
                onClick={() => setModalDomicilio(true)}
                className="text-xs shrink-0 flex items-center gap-1.5 bg-amber-600 hover:bg-amber-700 text-white font-medium"
              >
                <Plus className="h-3.5 w-3.5" />
                <span>Registrar Domicilio</span>
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent className="pt-4">
          <DataTable
            columns={[
              {
                key: 'repartidor',
                label: 'Repartidor / Empresa',
                render: (r) => (
                  <div>
                    <div className="flex items-center gap-1.5">
                      <span className="font-semibold text-foreground text-xs">{r.nombreDomiciliario}</span>
                      <Badge variant="outline" className="text-[10px] uppercase font-mono px-1.5 py-0">
                        {r.empresa}
                      </Badge>
                    </div>
                    <div className="flex items-center gap-2 mt-0.5 text-[11px] text-muted-foreground">
                      {r.documentoDomiciliario && <span>Doc: {r.documentoDomiciliario}</span>}
                      {r.telefonoDomiciliario && <span>· Tel: {r.telefonoDomiciliario}</span>}
                    </div>
                  </div>
                ),
              },
              {
                key: 'numeroApartamento',
                label: 'Unidad Destino',
                render: (r) => (
                  <Badge variant="outline" className="font-mono text-xs">
                    {formatApto(r.numeroApartamento || r.idUnidad)}
                  </Badge>
                ),
              },
              {
                key: 'tipoTransporte',
                label: 'Tipo / Transporte',
                render: (r) => (
                  <div className="flex flex-col gap-0.5">
                    <span className="text-xs text-foreground font-medium">{r.tipoDomicilio || 'COMIDA'}</span>
                    <span className="text-[10px] text-muted-foreground font-mono">
                      {r.medioTransporte || 'A PIE'} {r.placaVehiculo ? `· ${r.placaVehiculo}` : ''}
                    </span>
                  </div>
                ),
              },
              {
                key: 'fechaIngreso',
                label: 'Hora Ingreso',
                render: (r) => {
                  const fecha = r.fechaEntrada || r.fechaIngreso;
                  return (
                    <span className="text-xs text-muted-foreground font-mono">
                      {fecha ? formatDateTime(fecha) : '—'}
                    </span>
                  );
                },
              },
              {
                key: 'permanencia',
                label: 'Permanencia',
                render: (r) => {
                  if (r.minutosTranscurridos == null) {
                    return <span className="text-xs text-muted-foreground">—</span>;
                  }
                  return (
                    <div className="flex items-center gap-1.5">
                      <Clock className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                      <span className="text-xs font-mono font-medium">
                        {r.minutosTranscurridos} min
                      </span>
                      {r.tiempoMaximoMinutos && (
                        <span className="text-[10px] text-muted-foreground">
                          / {r.tiempoMaximoMinutos}m
                        </span>
                      )}
                      {r.excedido && (
                        <Badge variant="destructive" className="ml-1 text-[10px] py-0 px-1.5 flex items-center gap-1">
                          <AlertTriangle className="h-3 w-3" />
                          <span>Excedido</span>
                        </Badge>
                      )}
                    </div>
                  );
                },
              },
              {
                key: 'actions',
                label: 'Acción Salida',
                render: (r) => (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => handleFinalizarDomicilio(r)}
                    disabled={finalizandoDomId === r.idDomicilio}
                    className="text-xs py-1 px-2.5 h-auto font-semibold text-emerald-600 hover:text-emerald-700 hover:bg-emerald-50 dark:hover:bg-emerald-950/30 border-emerald-200 dark:border-emerald-900/50 flex items-center gap-1.5"
                  >
                    <CheckCircle2 className="h-3.5 w-3.5" />
                    {finalizandoDomId === r.idDomicilio ? 'Finalizando...' : 'Finalizar Entrega'}
                  </Button>
                ),
              },
            ]}
            rows={domiciliosActivos}
            pageSize={5}
            empty={{
              icon: 'delivery_dining',
              title: 'No hay domicilios activos en el predio',
              subtitle: 'Cuando ingrese un repartidor a garita, regístrelo para controlar su permanencia y autorizar salida.',
            }}
            keyField="idDomicilio"
          />
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

      {/* Minuta Táctica de Emergencia (Directorio Rápido de Garita) */}
      <Card className="border-red-500/30 bg-gradient-to-br from-red-500/5 via-transparent to-transparent shadow-xs">
        <CardHeader className="pb-3 border-b border-border/60">
          <div className="flex items-center justify-between">
            <CardTitle className="text-base font-bold flex items-center gap-2 text-foreground">
              <Siren className="h-5 w-5 text-red-600 dark:text-red-400" />
              Minuta Táctica de Emergencia — Directorio Rápido de Garita
            </CardTitle>
            <span className="text-xs font-semibold text-red-700 dark:text-red-400 bg-red-500/10 border border-red-500/20 px-2.5 py-0.5 rounded-full">
              Prioritarios 24/7
            </span>
          </div>
        </CardHeader>
        <CardContent className="pt-4">
          {loadingMinuta ? (
            <div className="py-8 text-center text-muted-foreground">
              <div className="animate-spin w-6 h-6 border-2 border-red-500 border-t-transparent rounded-full mx-auto mb-2" />
              <p className="text-xs">Cargando minuta de emergencia...</p>
            </div>
          ) : errorMinuta ? (
            <div className="p-3 bg-destructive/10 border border-destructive/20 rounded-lg text-destructive text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>Error al consultar los contactos de emergencia de la propiedad.</span>
            </div>
          ) : contactosMinuta.length === 0 ? (
            <div className="p-6 text-center text-muted-foreground bg-muted/20 border border-border/40 rounded-xl">
              <Phone className="w-8 h-8 mx-auto mb-2 opacity-40 text-muted-foreground" />
              <p className="font-semibold text-sm text-foreground">Sin contactos en minuta táctica</p>
              <p className="text-xs mt-0.5">La administración no ha marcado contactos prioritarios para garita.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {contactosMinuta.map((c, idx) => (
                <div
                  key={c.idContactoEmergencia || idx}
                  className="rounded-xl border border-border/80 bg-card p-3.5 flex flex-col justify-between hover:border-red-500/40 hover:shadow-xs transition-all"
                >
                  <div>
                    <div className="flex items-center justify-between gap-1 mb-1.5">
                      <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded bg-red-500/10 text-red-700 dark:text-red-400">
                        {c.tipoServicio ? c.tipoServicio.replace(/_/g, ' ') : 'EMERGENCIA'}
                      </span>
                      <span className="text-[10px] text-muted-foreground font-mono">#{c.ordenVisualizacion || (idx + 1)}</span>
                    </div>
                    <h4 className="text-xs font-bold text-foreground line-clamp-1">{c.entidad}</h4>
                    {c.direccion && (
                      <p className="text-[11px] text-muted-foreground line-clamp-1 flex items-center gap-1 mt-0.5">
                        <MapPin className="w-3 h-3 shrink-0" /> {c.direccion}
                      </p>
                    )}
                  </div>
                  <div className="mt-3 pt-2.5 border-t border-border/50 flex flex-col gap-1">
                    <a
                      href={`tel:${c.telefonoPrincipal}`}
                      className="inline-flex items-center justify-center gap-1.5 w-full py-1.5 px-3 rounded-lg bg-red-600 text-white hover:bg-red-700 font-mono font-bold text-xs shadow-xs transition-colors"
                    >
                      <Phone className="w-3.5 h-3.5" />
                      {c.telefonoPrincipal}
                    </a>
                    {c.telefonoAlterno && (
                      <a
                        href={`tel:${c.telefonoAlterno}`}
                        className="inline-flex items-center justify-center gap-1 text-[11px] text-muted-foreground hover:text-foreground font-mono py-0.5"
                      >
                        Alt: {c.telefonoAlterno}
                      </a>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
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
