import { useRef, useState, useMemo } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { toast } from 'sonner';
import { Ban, Copy, Eye, LogOut, Mail, Plus, Send, X } from 'lucide-react';
import { Badge } from '../components/ui/badge.tsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { useFetch, useTiposDocumento, useLiveValidation } from '../lib/hooks.js';
import { useTenantApi } from '../lib/useTenantApi.js';
import { formatDate, formatApto, imageSrc } from '../lib/utils.js';
import {
  soloNumeros,
  soloLetras,
  soloAlfanumerico,
  valSelect,
  valDocumento,
  valNombre,
  valApellido,
  valTelefono,
  valEmail,
  valEntero,
  valPlaca,
  getDocPlaceholder,
} from '../lib/validation.js';

const ESTADOS = ['', 'PROGRAMADA', 'EN_CURSO', 'ACTIVA', 'FINALIZADA', 'CANCELADA'];
const ESTADO_BADGE = {
  PROGRAMADA: 'info',
  PENDIENTE: 'warning',
  EN_CURSO: 'success',
  ACTIVA: 'success',
  FINALIZADA: 'secondary',
  CANCELADA: 'destructive',
};

const ESTADO_LABEL = {
  PROGRAMADA: 'Programada',
  PENDIENTE: 'Pendiente',
  EN_CURSO: 'En curso',
  ACTIVA: 'Activa',
  FINALIZADA: 'Finalizada',
  CANCELADA: 'Cancelada',
};

const TIPOS_VEHICULO = ['VEHICULO', 'MOTO', 'BICICLETA', 'OTRO'];

/** Config de filtrado/limite de documento por codigo de tipo (portado del legacy). */
function configDocumento(codigo) {
  if (['CC', 'TI', 'RC'].includes(codigo)) return { max: 10, upper: false, alfa: false };
  if (codigo === 'NIT') return { max: 13, upper: false, alfa: false };
  if (codigo === 'CE') return { max: 12, upper: true, alfa: true };
  if (codigo === 'PP' || codigo === 'PASAPORTE' || codigo === 'PEP') return { max: 15, upper: true, alfa: true };
  return { max: 15, upper: false, alfa: false, guiones: true };
}

const emptyForm = {
  unidadId: '',
  idResidente: '',
  tiempoValidezMin: '60',
  cantidadPersonas: '1',
  tipoDoc: '',
  documento: '',
  nombres: '',
  apellidos: '',
  telefono: '',
  email: '',
  tipoVehiculo: '',
  placa: '',
  descripcion: '',
  notas: '',
};

export default function VisitasPage() {
  const api = useTenantApi();
  const [filtroEstado, setFiltroEstado] = useState('');
  const [filtroFecha, setFiltroFecha] = useState('');
  const [detalle, setDetalle] = useState(null);
  const [loadingDetalle, setLoadingDetalle] = useState(false);
  const [fotoGrande, setFotoGrande] = useState(null);
  const [modalRegistro, setModalRegistro] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [sending, setSending] = useState(false);
  const [qrGenerado, setQrGenerado] = useState(null);
  const [confirmCancelar, setConfirmCancelar] = useState(null);
  const savingRef = useRef(false);

  const { data: dataRaw, loading, error: loadError, refetch } = useFetch(() => api.get('/porteria/visitas-resumen'), []);
  // Residentes y unidades para el selector del formulario: se cargan al abrir el modal
  const [residentesCargados, setResidentesCargados] = useState(false);
  const { data: residentesRaw } = useFetch(
    () => (residentesCargados ? api.get('/personas') : Promise.resolve([])),
    [residentesCargados]
  );
  const { data: unidadesRaw } = useFetch(
    () => (residentesCargados ? api.get('/units') : Promise.resolve([])),
    [residentesCargados]
  );
  const { tiposDoc } = useTiposDocumento();
  const { touch, touchAll, resetTouched, fieldError } = useLiveValidation();

  const unidades = useMemo(() => {
    const list = unidadesRaw?.items || unidadesRaw || [];
    return Array.isArray(list) ? list : [];
  }, [unidadesRaw]);

  const residentes = useMemo(() => {
    const list = residentesRaw?.items || residentesRaw || [];
    return Array.isArray(list) ? list : [];
  }, [residentesRaw]);

  const filtradas = (dataRaw?.items || dataRaw || []).filter((v) => {
    if (filtroEstado && (!v.estado || v.estado !== filtroEstado)) return false;
    if (!filtroFecha) return true;
    const fecha = (v.fechaVisita || '').slice(0, 10);
    return fecha === filtroFecha;
  });

  async function verDetalle(row) {
    setLoadingDetalle(true);
    try {
      const d = await api.get(`/porteria/visitas/${row.idVisita}/detalle`);
      setDetalle(d);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setLoadingDetalle(false);
    }
  }

  async function registrarSalida(row) {
    try {
      await api.put(`/porteria/visitas/${row.idVisita}/salida`);
      toast.success('Salida registrada');
      refetch();
    } catch (err) {
      toast.error(err.message);
    }
  }

  async function cancelarVisita() {
    if (!confirmCancelar) return;
    try {
      await api.del(`/visitas/${confirmCancelar.idVisita}`);
      toast.success('Visita cancelada');
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setConfirmCancelar(null);
    }
  }

  function update(path, value) {
    setForm((f) => {
      const next = { ...f };
      next[path] = value;
      return next;
    });
    setErrors((prev) => (prev[path] ? { ...prev, [path]: undefined } : prev));
  }

  function codigoTipoDoc(idTipoDoc) {
    return tiposDoc.find((t) => Number(t.idTipoDoc) === Number(idTipoDoc))?.codigo || '';
  }

  function onTipoDocChange(value) {
    setForm((f) => ({ ...f, tipoDoc: value, documento: '' }));
    setErrors((e) => ({ ...e, tipoDoc: undefined, documento: undefined }));
  }

  function onDocumentoChange(value) {
    const cfg = configDocumento(codigoTipoDoc(form.tipoDoc));
    let v = cfg.alfa ? soloAlfanumerico(value, cfg.max) : soloNumeros(value, cfg.max);
    if (cfg.upper) v = v.toUpperCase();
    update('documento', v);
  }

  function validate() {
    const e = {};
    if (!form.unidadId && !form.idResidente) {
      e.unidadId = 'Seleccione el apartamento de destino o el residente autorizante';
    }
    const rTipo = valSelect(form.tipoDoc, 'Seleccione el tipo de documento');
    if (!rTipo.ok) e.tipoDoc = rTipo.mensaje;
    const rDoc = valDocumento(form.documento, codigoTipoDoc(form.tipoDoc), 'El documento del visitante');
    if (!rDoc.ok) e.documento = rDoc.mensaje;
    const rN = valNombre(form.nombres, 'El nombre del visitante');
    if (!rN.ok) e.nombres = rN.mensaje;
    const rA = valApellido(form.apellidos, 'El apellido del visitante');
    if (!rA.ok) e.apellidos = rA.mensaje;
    const rTel = valTelefono(form.telefono, { required: false });
    if (!rTel.ok) e.telefono = rTel.mensaje;
    const rEmail = valEmail(form.email, { required: false });
    if (!rEmail.ok) e.email = rEmail.mensaje;
    const rVal = valEntero(form.tiempoValidezMin, { min: 5, max: 1440, label: 'El tiempo de validez' });
    if (!rVal.ok) e.tiempoValidezMin = rVal.mensaje || 'El tiempo de validez debe ser entre 5 y 1440 minutos';
    const rPer = valEntero(form.cantidadPersonas, { min: 1, max: 99, label: 'La cantidad de personas' });
    if (!rPer.ok) e.cantidadPersonas = rPer.mensaje || 'La cantidad de personas debe ser entre 1 y 99';
    if (form.tipoVehiculo === 'BICICLETA' && !form.descripcion.trim()) {
      e.descripcion = 'La descripción es obligatoria para bicicletas';
    }
    if (form.tipoVehiculo && form.tipoVehiculo !== 'BICICLETA' && form.placa.trim()) {
      const rPlaca = valPlaca(form.placa, form.tipoVehiculo);
      if (!rPlaca.ok) e.placa = rPlaca.mensaje;
    }
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function registrar() {
    if (savingRef.current) return; // doble submit
    const fieldsToTouch = [
      'unidadId',
      'idResidente',
      'tiempoValidezMin',
      'cantidadPersonas',
      'tipoDoc',
      'documento',
      'nombres',
      'apellidos',
      'telefono',
      'email',
      ...(form.tipoVehiculo && form.tipoVehiculo !== 'BICICLETA' ? ['placa'] : []),
      ...(form.tipoVehiculo === 'BICICLETA' ? ['descripcion'] : []),
    ];
    touchAll(fieldsToTouch);
    if (!validate()) return;
    savingRef.current = true;
    setSending(true);
    try {
      const payload = {
        unidadId: form.unidadId ? Number(form.unidadId) : undefined,
        idResidente: form.idResidente ? Number(form.idResidente) : undefined,
        metodoIngreso: 'CODIGO_QR',
        tiempoValidezMin: Number(form.tiempoValidezMin || 60),
        cantidadPersonas: Number(form.cantidadPersonas || 1),
        visitante: {
          idTipoDoc: form.tipoDoc ? Number(form.tipoDoc) : 1,
          numeroDocumento: form.documento.trim(),
          nombres: form.nombres.trim(),
          apellidos: form.apellidos.trim(),
          telefono: form.telefono.trim() || null,
          email: form.email.trim() || null,
        },
        notas: form.notas.trim() || null,
      };
      if (form.tipoVehiculo === 'BICICLETA') {
        payload.vehiculo = { tipo: form.tipoVehiculo, descripcion: form.descripcion.trim() };
      } else if (form.tipoVehiculo && form.placa.trim()) {
        payload.vehiculo = { placa: form.placa.trim().toUpperCase(), tipo: form.tipoVehiculo };
      }
      const res = await api.post('/porteria/visitas', payload);
      setQrGenerado({ ...res, emailVisitante: payload.visitante.email });
      toast.success('Visita registrada y código QR generado');
      setForm(emptyForm);
      setErrors({});
      resetTouched();
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al registrar la visita');
      const apiErrors = err.errors || err.response?.data?.errors;
      if (apiErrors && typeof apiErrors === 'object') {
        setErrors((prev) => ({ ...prev, ...apiErrors }));
        touchAll(Object.keys(apiErrors));
      }
    } finally {
      savingRef.current = false;
      setSending(false);
    }
  }

  function cerrarRegistro() {
    if (savingRef.current) return;
    setModalRegistro(false);
    setQrGenerado(null);
    setErrors({});
    resetTouched();
    setForm(emptyForm);
  }

  function copiarQR() {
    if (!qrGenerado?.codigoQr) return;
    navigator.clipboard
      .writeText(qrGenerado.codigoQr)
      .then(() => toast.success('Código QR copiado al portapapeles'))
      .catch(() => toast.error('No se pudo copiar el código'));
  }

  function compartirCorreo() {
    if (!qrGenerado?.codigoQr || !qrGenerado.emailVisitante) return;
    window.open(
      `mailto:${qrGenerado.emailVisitante}?subject=Codigo QR de Acceso&body=Su codigo QR es: ${qrGenerado.codigoQr}`
    );
  }

  function compartirTelegram() {
    if (!qrGenerado?.codigoQr) return;
    window.open(
      `https://t.me/share/url?text=Codigo%20QR%20de%20acceso%3A%20${encodeURIComponent(qrGenerado.codigoQr)}`,
      '_blank'
    );
  }

  const columns = [
    { key: 'idVisita', label: 'ID', width: 60, render: (r) => <span className="tabular-nums">{r.idVisita}</span> },
    { key: 'nombreVisitante', label: 'Visitante' },
    { key: 'documentoVisitante', label: 'Documento', render: (r) => <span className="tabular-nums">{r.documentoVisitante}</span> },
    { key: 'numeroApartamento', label: 'Apartamento', render: (r) => formatApto(r.numeroApartamento) },
    {
      key: 'fechaIngreso',
      label: 'Ingreso',
      render: (r) => <span className="tabular-nums">{formatDate(r.fechaIngreso || r.fechaVisita)}</span>,
    },
    { key: 'fechaSalida', label: 'Salida', render: (r) => <span className="tabular-nums">{formatDate(r.fechaSalida)}</span> },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <Badge variant={ESTADO_BADGE[r.estado] || 'secondary'}>{ESTADO_LABEL[r.estado] || r.estado}</Badge>,
    },
    {
      key: 'actions',
      label: 'Acciones',
      width: 160,
      render: (row) => (
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              verDetalle(row);
            }}
            className="flex h-10 w-10 items-center justify-center rounded-md text-text-secondary transition-colors hover:bg-surface-container hover:text-on-surface"
            aria-label="Ver detalle"
            title="Ver detalle"
          >
            <Eye className="h-4 w-4" aria-hidden="true" />
          </button>
          {(row.estado === 'EN_CURSO' || row.estado === 'ACTIVA' || row.estado === 'PENDIENTE') && (
            <Button
              onClick={(e) => {
                e.stopPropagation();
                registrarSalida(row);
              }}
              size="sm"
              variant="outline"
              icon={<LogOut className="h-3.5 w-3.5" />}
            >
              Salida
            </Button>
          )}
          {row.estado !== 'FINALIZADA' && row.estado !== 'CANCELADA' && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                setConfirmCancelar(row);
              }}
              className="flex h-10 w-10 items-center justify-center rounded-md text-destructive transition-colors hover:bg-tint-red-bg"
              aria-label="Cancelar visita"
              title="Cancelar visita"
            >
              <Ban className="h-4 w-4" aria-hidden="true" />
            </button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Visitas"
        subtitle="Registro de visitas al edificio: programadas, en curso y finalizadas."
        action={
          <div className="flex w-full flex-wrap items-end gap-2 sm:w-auto">
            <Select
              id="f-estado"
              aria-label="Filtrar por estado"
              value={filtroEstado}
              onChange={(e) => setFiltroEstado(e.target.value)}
              className="h-10 min-w-[10rem]"
            >
              {ESTADOS.map((e) => (
                <option key={e || 'all'} value={e}>
                  {e ? ESTADO_LABEL[e] || e : 'Todos los estados'}
                </option>
              ))}
            </Select>
            <input
              id="f-fecha"
              type="date"
              aria-label="Filtrar por fecha"
              value={filtroFecha}
              onChange={(e) => setFiltroFecha(e.target.value)}
              className="h-10 rounded-md border border-input bg-surface px-3 text-sm text-on-surface tabular-nums focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25"
            />
            <Button icon={<Plus className="h-4 w-4" />} onClick={() => { setResidentesCargados(true); setModalRegistro(true); }}>
              Nueva visita
            </Button>
          </div>
        }
      />
      <DataTable
        columns={columns}
        rows={filtradas}
        loading={loading}
        error={loadError ? `No se pudieron cargar las visitas: ${loadError.message || 'error de conexión'}` : null}
        onRetry={refetch}
        empty={{
          icon: 'how_to_reg',
          title: 'No hay visitas',
          subtitle: filtroEstado || filtroFecha ? 'Ninguna visita coincide con los filtros.' : 'Las visitas registradas aparecerán aquí.',
        }}
        keyField="idVisita"
        onRowClick={verDetalle}
        pageSize={10}
      />

      <Modal open={modalRegistro} onClose={cerrarRegistro} title="Registrar nueva visita" size="lg">
        {qrGenerado ? (
          <div className="flex flex-col items-center gap-3 py-4" role="status">
            <p className="text-sm font-semibold text-on-surface">Código QR generado</p>
            <div className="max-w-full break-all rounded-lg border border-border bg-tint-primary-bg px-4 py-4 text-center text-xl font-extrabold text-tint-primary-fg tabular-nums">
              {qrGenerado.codigoQr}
            </div>
            <div className="flex flex-wrap justify-center gap-2">
              <Button size="sm" onClick={copiarQR} icon={<Copy className="h-3.5 w-3.5" />}>
                Copiar QR
              </Button>
              {qrGenerado.emailVisitante && (
                <Button size="sm" variant="outline" onClick={compartirCorreo} icon={<Mail className="h-3.5 w-3.5" />}>
                  Correo
                </Button>
              )}
              <Button size="sm" variant="outline" onClick={compartirTelegram} icon={<Send className="h-3.5 w-3.5" />}>
                Telegram
              </Button>
            </div>
            <Button variant="outline" onClick={() => setQrGenerado(null)}>
              Registrar otra visita
            </Button>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            <div className="form-row">
              <Select
                id="vis-unidad"
                label="Apartamento Destino"
                value={form.unidadId}
                onChange={(e) => update('unidadId', e.target.value)}
                onBlur={() => touch('unidadId')}
                error={fieldError('unidadId', !form.unidadId && !form.idResidente ? { ok: false, mensaje: 'Seleccione un apartamento de destino' } : { ok: true }) || errors.unidadId}
                required
              >
                <option value="">Seleccione apartamento...</option>
                {unidades.map((u) => (
                  <option key={u.id || u.idApartamento} value={u.id || u.idApartamento}>
                    {formatApto(u.numero || u.identificador)}
                  </option>
                ))}
              </Select>
              <Select
                id="vis-residente"
                label="Residente Autorizante (Opcional)"
                value={form.idResidente}
                onChange={(e) => update('idResidente', e.target.value)}
              >
                <option value="">Cualquier residente / Portería</option>
                {residentes.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.numeroApartamento ? `${formatApto(r.numeroApartamento)} - ` : ''}
                    {r.primerNombre || r.nombres} {r.primerApellido || r.apellidos}
                  </option>
                ))}
              </Select>
            </div>
            <div className="form-row">
              <Input
                id="vis-validez"
                label="Tiempo Validez (min)"
                type="number"
                min="5"
                max="1440"
                placeholder="60"
                value={form.tiempoValidezMin}
                onChange={(e) => update('tiempoValidezMin', e.target.value)}
                onBlur={() => touch('tiempoValidezMin')}
                error={fieldError('tiempoValidezMin', valEntero(form.tiempoValidezMin, { min: 5, max: 1440, label: 'El tiempo de validez' })) || errors.tiempoValidezMin}
                required
              />
              <Input
                id="vis-personas"
                label="Cantidad Personas"
                type="number"
                min="1"
                max="99"
                placeholder="1"
                value={form.cantidadPersonas}
                onChange={(e) => update('cantidadPersonas', e.target.value)}
                onBlur={() => touch('cantidadPersonas')}
                error={fieldError('cantidadPersonas', valEntero(form.cantidadPersonas, { min: 1, max: 99, label: 'La cantidad de personas' })) || errors.cantidadPersonas}
                required
              />
            </div>
            <h3 className="card-title mt-1">
              Visitante
            </h3>
            <div className="form-row">
              <Select
                id="vis-tipo-doc"
                label="Tipo Documento"
                value={form.tipoDoc}
                onChange={(e) => onTipoDocChange(e.target.value)}
                onBlur={() => touch('tipoDoc')}
                error={fieldError('tipoDoc', valSelect(form.tipoDoc, 'Seleccione el tipo de documento')) || errors.tipoDoc}
                required
              >
                <option value="">Seleccione...</option>
                {tiposDoc.map((t) => (
                  <option key={t.idTipoDoc ?? t.value} value={t.idTipoDoc ?? t.value}>
                    {t.codigo} - {t.descripcion || t.nombre}
                  </option>
                ))}
              </Select>
              <Input
                id="vis-documento"
                label="Número Documento"
                placeholder={getDocPlaceholder(codigoTipoDoc(form.tipoDoc))}
                value={form.documento}
                onChange={(e) => onDocumentoChange(e.target.value)}
                onBlur={() => touch('documento')}
                error={fieldError('documento', valDocumento(form.documento, codigoTipoDoc(form.tipoDoc), 'El documento del visitante')) || errors.documento}
                required
              />
            </div>
            <div className="form-row">
              <Input
                id="vis-nombres"
                label="Nombres"
                placeholder="Ej. Carlos Alberto"
                value={form.nombres}
                onChange={(e) => update('nombres', soloLetras(e.target.value, 25))}
                onBlur={() => touch('nombres')}
                error={fieldError('nombres', valNombre(form.nombres, 'El nombre del visitante')) || errors.nombres}
                required
              />
              <Input
                id="vis-apellidos"
                label="Apellidos"
                placeholder="Ej. Gómez Pérez"
                value={form.apellidos}
                onChange={(e) => update('apellidos', soloLetras(e.target.value, 25))}
                onBlur={() => touch('apellidos')}
                error={fieldError('apellidos', valApellido(form.apellidos, 'El apellido del visitante')) || errors.apellidos}
                required
              />
            </div>
            <div className="form-row">
              <Input
                id="vis-telefono"
                label="Teléfono celular (opcional)"
                placeholder="Ej. 300 123 4567"
                inputMode="numeric"
                maxLength={10}
                value={form.telefono}
                onChange={(e) => update('telefono', soloNumeros(e.target.value, 10))}
                onBlur={() => touch('telefono')}
                error={fieldError('telefono', valTelefono(form.telefono, { required: false })) || errors.telefono}
              />
              <Input
                id="vis-email"
                label="Email (opcional)"
                placeholder="Ej. visitante@correo.com"
                type="email"
                value={form.email}
                onChange={(e) => update('email', e.target.value)}
                onBlur={() => touch('email')}
                error={fieldError('email', valEmail(form.email, { required: false })) || errors.email}
              />
            </div>
            <h3 className="card-title mt-1">
              Vehículo (opcional)
            </h3>
            <div className="form-row">
              <Select
                id="vis-tipo-vehiculo"
                label="Tipo"
                value={form.tipoVehiculo}
                onChange={(e) => {
                  update('tipoVehiculo', e.target.value);
                  setErrors((er) => ({ ...er, placa: undefined, descripcion: undefined }));
                }}
              >
                <option value="">Sin vehículo</option>
                {TIPOS_VEHICULO.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </Select>
              {form.tipoVehiculo === 'BICICLETA' ? (
                <Input
                  id="vis-descripcion"
                  label="Descripción"
                  maxLength="100"
                  value={form.descripcion}
                  onChange={(e) => update('descripcion', e.target.value)}
                  onBlur={() => touch('descripcion')}
                  error={fieldError('descripcion', form.descripcion.trim() ? { ok: true } : { ok: false, mensaje: 'La descripción es obligatoria para bicicletas' }) || errors.descripcion}
                  placeholder="Ej. Bicicleta de ruta GW"
                />
              ) : (
                <Input
                  id="vis-placa"
                  label={form.tipoVehiculo === 'MOTO' ? 'Placa (Moto)' : 'Placa (Carro)'}
                  maxLength="10"
                  value={form.placa}
                  onChange={(e) => update('placa', e.target.value.toUpperCase().replace(/[^A-Z0-9\s-]/g, ''))}
                  onBlur={() => touch('placa')}
                  error={fieldError('placa', form.placa.trim() ? valPlaca(form.placa, form.tipoVehiculo) : { ok: true }) || errors.placa}
                  placeholder={form.tipoVehiculo === 'MOTO' ? 'Ej. ABC12D' : 'Ej. DEM-123'}
                />
              )}
            </div>
            <div className="form-group">
              <Textarea
                id="vis-notas"
                label="Notas"
                maxLength="500"
                value={form.notas}
                onChange={(e) => update('notas', e.target.value)}
              />
            </div>
            <div className="mt-2 flex flex-wrap justify-end gap-2">
              <Button variant="outline" onClick={cerrarRegistro} disabled={sending}>
                Cancelar
              </Button>
              <Button onClick={registrar} disabled={sending} loading={sending}>
                {sending ? 'Generando QR...' : 'Generar QR y Registrar'}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      <Modal open={!!detalle} onClose={() => setDetalle(null)} title="Detalle de la visita" size="md">
        {loadingDetalle && <LoadingState size="sm" message="Cargando detalle..." />}
        {detalle && (
          <div className="flex flex-col gap-2">
            <div className="detail-row">
              <span>Visitante</span>
              <span>
                {detalle.nombreVisitante} {detalle.apellidoVisitante}
              </span>
            </div>
            <div className="detail-row">
              <span>Documento</span>
              <span>{detalle.documentoVisitante}</span>
            </div>
            <div className="detail-row">
              <span>Residente</span>
              <span>{detalle.nombreResidente}</span>
            </div>
            <div className="detail-row">
              <span>Apartamento</span>
              <span>{formatApto(detalle.numeroApartamento)}</span>
            </div>
            <div className="detail-row">
              <span>Ingreso</span>
              <span>{formatDate(detalle.fechaVisita)}</span>
            </div>
            <div className="detail-row">
              <span>Salida</span>
              <span>{formatDate(detalle.fechaSalida) || 'Aún dentro'}</span>
            </div>
            {detalle.placaVehiculo && (
              <div className="detail-row">
                <span>Vehículo</span>
                <span>
                  {detalle.tipoVehiculo} • {detalle.placaVehiculo}
                </span>
              </div>
            )}
            {detalle.codigoParqueadero && (
              <div className="detail-row">
                <span>Parqueadero</span>
                <span>{detalle.codigoParqueadero}</span>
              </div>
            )}
            {detalle.esFrecuente && (
              <Badge variant="info" className="self-start">
                Visitante frecuente
              </Badge>
            )}
            {detalle.fotoCaptura && (
              <img
                  src={imageSrc(detalle.fotoCaptura)}
                  alt="Foto"
                  loading="lazy"
                  width="400"
                  height="300"
                className="mt-2 h-auto max-w-full cursor-zoom-in rounded-md border border-border"
                onClick={() => setFotoGrande(imageSrc(detalle.fotoCaptura))}
              />
            )}
          </div>
        )}
      </Modal>

      {fotoGrande && (
        <div
          role="dialog"
          aria-label="Foto de visita"
          tabIndex={0}
          onClick={() => setFotoGrande(null)}
          onKeyDown={(e) => e.key === 'Escape' && setFotoGrande(null)}
          className="app-overlay fixed inset-0 z-[1000] flex cursor-zoom-out items-center justify-center bg-black/85 p-4"
        >
          <button
            type="button"
            onClick={() => setFotoGrande(null)}
            aria-label="Cerrar foto"
            className="absolute right-4 top-4 flex h-11 w-11 items-center justify-center rounded-md border border-white/40 bg-black/40 text-white"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
          <img
            src={fotoGrande}
            alt="Foto de la visita"
            width="800"
            height="600"
            className="h-auto max-h-[90%] max-w-[90%] rounded-md"
          />
        </div>
      )}

      <ConfirmDialog
        open={!!confirmCancelar}
        onClose={() => setConfirmCancelar(null)}
        onConfirm={cancelarVisita}
        title="Cancelar visita"
        message={`¿Cancelar la visita #${confirmCancelar?.idVisita}?`}
        confirmLabel="Cancelar visita"
        danger
      />
    </div>
  );
}








