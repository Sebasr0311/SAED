import { useRef, useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch, useTiposDocumento, useLiveValidation } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate, formatMiles, imageSrc } from '../lib/utils.js';
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
} from '../lib/validation.js';

const ESTADOS = ['', 'ACTIVA', 'FINALIZADA', 'CANCELADA'];
const ESTADO_BADGE = {
  PENDIENTE: 'badge-pendiente-firma',
  ACTIVA: 'badge-activo',
  FINALIZADA: 'badge-finalizada',
  CANCELADA: 'badge-cancelado',
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
  idResidente: '',
  tiempoValidezMin: '30',
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
  const [filtroEstado, setFiltroEstado] = useState('');
  const [filtroFecha, setFiltroFecha] = useState('');
  const [toast, setToast] = useState(null);
  const [detalle, setDetalle] = useState(null);
  const [loadingDetalle, setLoadingDetalle] = useState(false);
  const [fotoGrande, setFotoGrande] = useState(null);
  const [modalRegistro, setModalRegistro] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [sending, setSending] = useState(false);
  const [qrGenerado, setQrGenerado] = useState(null);
  const savingRef = useRef(false);

  const { data: dataRaw, loading, refetch } = useFetch(() => api.get('/porteria/visitas-resumen'), []);
  // Residentes para el selector del formulario: se cargan solo al abrir el modal
  // (evita un request innecesario al montar la página de listado).
  const [residentesCargados, setResidentesCargados] = useState(false);
  const { data: residentesRaw } = useFetch(
    () => (residentesCargados ? api.get('/personas') : Promise.resolve([])),
    [residentesCargados]
  );
  const { tiposDoc } = useTiposDocumento();
  const { touch, fieldError } = useLiveValidation();

  const residentes = (residentesRaw?.items || []).slice().sort(
    (a, b) => (parseInt(a.numeroApartamento, 10) || 0) - (parseInt(b.numeroApartamento, 10) || 0)
  );

  const filtradas = (dataRaw?.items || dataRaw || []).filter((v) => {
    if (filtroEstado && (!v.estado || v.estado !== filtroEstado)) return false;
    if (!filtroFecha) return true;
    const fecha = (v.fechaVisita || '').slice(0, 10);
    return fecha === filtroFecha;
  });

  async function verDetalle(row) {
    setLoadingDetalle(true);
    try {
      const d = await api.get(`/visitas/${row.idVisita}/detalle`);
      setDetalle(d);
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setLoadingDetalle(false);
    }
  }

  async function registrarSalida(row) {
    try {
      await api.put(`/visitas/${row.idVisita}/salida`);
      setToast({ message: 'Salida registrada', type: 'success' });
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }

  async function cancelarVisita(row) {
    if (!window.confirm(`¿Cancelar la visita #${row.idVisita}?`)) return;
    try {
      await api.del(`/visitas/${row.idVisita}`);
      setToast({ message: 'Visita cancelada', type: 'success' });
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }

  function update(path, value) {
    setForm((f) => {
      const next = { ...f };
      next[path] = value;
      return next;
    });
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
    const rRes = valSelect(form.idResidente, 'Seleccione el residente autorizante');
    if (!rRes.ok) e.idResidente = rRes.mensaje;
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
    const rVal = valEntero(form.tiempoValidezMin, { positivo: true });
    if (!rVal.ok) e.tiempoValidezMin = 'El tiempo de validez debe ser un entero mayor que 0';
    const rPer = valEntero(form.cantidadPersonas, { positivo: true });
    if (!rPer.ok) e.cantidadPersonas = 'La cantidad de personas debe ser un entero mayor que 0';
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
    if (!validate()) return;
    savingRef.current = true;
    setSending(true);
    try {
      const d = {
        idResidente: Number(form.idResidente),
        tiempoValidezMin: Number(form.tiempoValidezMin),
        cantidadPersonas: Number(form.cantidadPersonas),
        visitante: {
          idTipoDoc: Number(form.tipoDoc),
          numeroDocumento: form.documento.trim(),
          nombres: form.nombres.trim(),
          apellidos: form.apellidos.trim(),
          telefono: form.telefono.trim(),
          email: form.email.trim(),
        },
        notas: form.notas.trim(),
      };
      if (form.tipoVehiculo === 'BICICLETA') {
        d.vehiculo = { tipo: form.tipoVehiculo, descripcion: form.descripcion.trim() };
      } else if (form.tipoVehiculo && form.placa.trim()) {
        d.vehiculo = { placa: form.placa.trim().toUpperCase(), tipo: form.tipoVehiculo };
      }
      const res = await api.post('/porteria/visitas', { unidadId: d.unidadId, visitanteId: d.visitante.idPersona, metodoIngreso: d.metodoIngreso || 'PEATONAL', motivo: d.motivo, fechaProgramada: d.fechaVisita, estado: 'PROGRAMADA' });
      setQrGenerado({ ...res, emailVisitante: d.visitante.email });
      setToast({ message: 'Visita registrada exitosamente', type: 'success' });
      setForm(emptyForm);
      setErrors({});
      refetch();
    } catch (err) {
      // Conservar los datos introducidos para permitir corregir y reintentar
      setToast({ message: err.message, type: 'error' });
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
    setForm(emptyForm);
  }

  function copiarQR() {
    if (!qrGenerado?.codigoQr) return;
    navigator.clipboard
      .writeText(qrGenerado.codigoQr)
      .then(() => setToast({ message: 'Código QR copiado al portapapeles', type: 'success' }))
      .catch(() => setToast({ message: 'No se pudo copiar el código', type: 'error' }));
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
    { key: 'idVisita', label: 'ID', width: 60 },
    { key: 'nombreVisitante', label: 'Visitante' },
    { key: 'documentoVisitante', label: 'Documento' },
    { key: 'numeroApartamento', label: 'Apartamento' },
    {
      key: 'fechaIngreso',
      label: 'Ingreso',
      render: (r) => formatDate(r.fechaIngreso || r.fechaVisita),
    },
    { key: 'fechaSalida', label: 'Salida', render: (r) => formatDate(r.fechaSalida) },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
    },
    {
      key: 'actions',
      label: 'Acciones',
      width: 160,
      render: (row) => (
        <div style={{ display: 'flex', gap: '4px' }}>
          <button
            onClick={(e) => {
              e.stopPropagation();
              verDetalle(row);
            }}
            className="btn btn-ghost btn-sm"
            aria-label="Ver detalle"
            title="Ver detalle"
          >
            <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>visibility</span>
          </button>
          {(row.estado === 'ACTIVA' || row.estado === 'PENDIENTE') && (
            <Button
              onClick={(e) => {
                e.stopPropagation();
                registrarSalida(row);
              }}
              style={{ padding: '4px 10px', fontSize: '11px' }}
            >
              Salida
            </Button>
          )}
          {row.estado !== 'FINALIZADA' && row.estado !== 'CANCELADA' && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                cancelarVisita(row);
              }}
              className="btn btn-ghost btn-sm"
              aria-label="Cancelar"
              title="Cancelar"
            >
              <span className="material-symbols-outlined" style={{ fontSize: '18px', color: 'var(--error)' }}>cancel</span>
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
        subtitle="Registro de visitas al edificio"
        action={
          <div className="filters">
            <Button icon="add" onClick={() => { setResidentesCargados(true); setModalRegistro(true); }}>
              Nueva visita
            </Button>
            <Select
              id="f-estado"
              aria-label="Filtrar por estado"
              value={filtroEstado}
              onChange={(e) => setFiltroEstado(e.target.value)}
              className="filter-select"
            >
              {ESTADOS.map((e) => (
                <option key={e || 'all'} value={e}>
                  {e || 'Todos'}
                </option>
              ))}
            </Select>
            <input
              id="f-fecha"
              type="date"
              aria-label="Filtrar por fecha"
              value={filtroFecha}
              onChange={(e) => setFiltroFecha(e.target.value)}
              className="form-control"
              style={{ width: '100%', maxWidth: '160px' }}
            />
          </div>
        }
      />
      <DataTable
        columns={columns}
        rows={filtradas}
        loading={loading}
                empty={{ icon: 'how_to_reg', title: 'No hay visitas', subtitle: 'Las visitas registradas aparecerán aquí.' }}
        keyField="idVisita"
        onRowClick={verDetalle}
      />

      <Modal open={modalRegistro} onClose={cerrarRegistro} title="Registrar Nueva Visita" size="lg">
        {qrGenerado ? (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '12px', padding: '16px 0' }}>
            <p style={{ fontWeight: 600 }}>QR generado</p>
            <div
              style={{
                fontSize: '20px',
                fontWeight: 800,
                fontFamily: 'monospace',
                wordBreak: 'break-all',
                textAlign: 'center',
                background: 'var(--primary)',
                color: 'white',
                padding: '16px',
                borderRadius: '12px',
                maxWidth: '100%',
              }}
            >
              {qrGenerado.codigoQr}
            </div>
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', justifyContent: 'center' }}>
              <Button size="sm" onClick={copiarQR}>
                Copiar QR
              </Button>
              {qrGenerado.emailVisitante && (
                <Button size="sm" variant="outline" onClick={compartirCorreo}>
                  Correo
                </Button>
              )}
              <Button size="sm" variant="outline" onClick={compartirTelegram}>
                Telegram
              </Button>
            </div>
            <Button variant="outline" onClick={() => setQrGenerado(null)}>
              Registrar otra visita
            </Button>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div className="form-row">
              <Select
                id="vis-residente"
                label="Residente Autorizante"
                value={form.idResidente}
                onChange={(e) => update('idResidente', e.target.value)}
                error={errors.idResidente}
                required
              >
                <option value="">Seleccione...</option>
                {residentes.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.numeroApartamento ? `${r.numeroApartamento} - ` : ''}
                    {r.nombres} {r.apellidos}
                  </option>
                ))}
              </Select>
              <Input
                id="vis-validez"
                label="Tiempo Validez (min)"
                type="number"
                min="1"
                value={form.tiempoValidezMin}
                onChange={(e) => update('tiempoValidezMin', e.target.value)}
                error={errors.tiempoValidezMin}
                required
              />
            </div>
            <div className="form-row">
              <Input
                id="vis-personas"
                label="Cantidad Personas"
                type="number"
                min="1"
                value={form.cantidadPersonas}
                onChange={(e) => update('cantidadPersonas', e.target.value)}
                error={errors.cantidadPersonas}
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
                error={errors.tipoDoc}
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
                value={form.documento}
                onChange={(e) => onDocumentoChange(e.target.value)}
                error={errors.documento}
                required
              />
            </div>
            <div className="form-row">
              <Input
                id="vis-nombres"
                label="Nombres"
                value={form.nombres}
                onChange={(e) => update('nombres', soloLetras(e.target.value, 25))}
                error={errors.nombres}
                required
              />
              <Input
                id="vis-apellidos"
                label="Apellidos"
                value={form.apellidos}
                onChange={(e) => update('apellidos', soloLetras(e.target.value, 25))}
                error={errors.apellidos}
                required
              />
            </div>
            <div className="form-row">
              <Input
                id="vis-telefono"
                label="Teléfono"
                value={form.telefono}
                onChange={(e) => update('telefono', soloNumeros(e.target.value, 10))}
                onBlur={() => touch('telefono')}
                error={fieldError('telefono', valTelefono(form.telefono, { required: false })) || errors.telefono}
              />
              <Input
                id="vis-email"
                label="Email"
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
                  error={errors.descripcion}
                  placeholder="Descripción del vehículo"
                />
              ) : (
                <Input
                  id="vis-placa"
                  label={form.tipoVehiculo === 'MOTO' ? 'Placa (Moto)' : 'Placa (Carro)'}
                  maxLength="10"
                  value={form.placa}
                  onChange={(e) => update('placa', e.target.value.toUpperCase().replace(/[^A-Z0-9\s]/g, ''))}
                  error={errors.placa}
                  placeholder="Ej: ABC 123"
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
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '8px' }}>
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

      <Modal open={!!detalle} onClose={() => setDetalle(null)} title="Detalle de Visita" size="md">
        {loadingDetalle && <p>Cargando...</p>}
        {detalle && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
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
              <span>{detalle.numeroApartamento}</span>
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
                  {detalle.tipoVehiculo} — {detalle.placaVehiculo}
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
              <span className="badge badge-info" style={{ alignSelf: 'flex-start' }}>
                Visitante Frecuente
              </span>
            )}
            {detalle.fotoCaptura && (
              <img
                  src={imageSrc(detalle.fotoCaptura)}
                  alt="Foto"
                  loading="lazy"
                style={{ maxWidth: '100%', borderRadius: '8px', marginTop: '8px', cursor: 'zoom-in' }}
                onClick={() => setFotoGrande(imageSrc(detalle.fotoCaptura))}
              />
            )}
          </div>
        )}
      </Modal>

      {fotoGrande && (
        <div
          onClick={() => setFotoGrande(null)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.85)',
            zIndex: 1000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'zoom-out',
          }}
        >
          <img
            src={fotoGrande}
            alt="Foto"
            style={{ maxWidth: '90%', maxHeight: '90%', borderRadius: '8px' }}
          />
        </div>
      )}

      <Toast toast={toast} />
    </div>
  );
}

