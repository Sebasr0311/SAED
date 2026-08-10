import { useRef, useState } from 'react';
import { useFetch, useTiposDocumento, useLiveValidation } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { valNombre, valApellido, valDocumento, valTelefono, valEmail, valPlaca } from '../lib/validation.js';
import Toast from '../components/ui/Toast.jsx';
import { formatDate } from '../lib/utils.js';

export default function ResFrecuentesPage() {
  const { user } = useAuth();
  const { tiposDoc, error: errorTiposDoc } = useTiposDocumento();
  const { touch, fieldError } = useLiveValidation();
  const [toast, setToast] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({
    idTipoDoc: 1,
    numeroDocumento: '',
    nombres: '',
    apellidos: '',
    telefono: '',
    email: '',
    placa: '',
  });
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  // Guard anti doble-submit: mismo patron que VisitasPage (FASE 4.2-P2).
  const savingRef = useRef(false);
  const [search, setSearch] = useState('');

  // ==== QR de visita rapida para un frecuente (datos variables del dia) ====
  const [qrModal, setQrModal] = useState(null); // frecuente seleccionado o null
  const [qrForm, setQrForm] = useState({
    medioTransporte: 'A_PIE',
    placa: '',
    descripcion: '',
    cantidadPersonas: '1',
    tiempoValidezMin: '30',
    notas: '',
  });
  const [qrErrors, setQrErrors] = useState({});
  const [qrSending, setQrSending] = useState(false);
  const qrSendingRef = useRef(false);
  const [qrGenerado, setQrGenerado] = useState(null); // { codigoQr, mensaje, fechaExpiracion }
  const [confirmQuitar, setConfirmQuitar] = useState(null); // frecuente a quitar

  // Mapea el tipo de vehiculo del ultimo registro al select del modal
  function tipoAMedio(t) {
    if (t === 'VEHICULO') return 'CARRO';
    if (t === 'MOTO' || t === 'BICICLETA' || t === 'OTRO') return t;
    return 'A_PIE';
  }

  function abrirQr(frecuente) {
    setQrForm({
      medioTransporte: tipoAMedio(frecuente.ultimoTipoVehiculo),
      placa: frecuente.ultimaPlaca || '',
      descripcion: frecuente.ultimaDescripcionTipo || '',
      cantidadPersonas: '1',
      tiempoValidezMin: '30',
      notas: '',
    });
    setQrErrors({});
    setQrGenerado(null);
    setQrModal(frecuente);
  }

  function validateQr() {
    const e = {};
    const personas = Number(qrForm.cantidadPersonas);
    if (!qrForm.cantidadPersonas || Number.isNaN(personas) || personas < 1 || personas > 99) {
      e.cantidadPersonas = 'Debe ser entre 1 y 99 personas';
    }
    const validez = Number(qrForm.tiempoValidezMin);
    if (!qrForm.tiempoValidezMin || Number.isNaN(validez) || validez < 5 || validez > 60) {
      e.tiempoValidezMin = 'La validez debe ser entre 5 y 60 minutos';
    }
    if (qrForm.medioTransporte === 'CARRO' || qrForm.medioTransporte === 'MOTO') {
      const rPlaca = valPlaca(qrForm.placa, qrForm.medioTransporte === 'CARRO' ? 'CARRO' : 'MOTO');
      if (!rPlaca.ok) e.placa = rPlaca.mensaje;
    }
    if (qrForm.medioTransporte === 'BICICLETA' || qrForm.medioTransporte === 'OTRO') {
      if (!qrForm.descripcion.trim()) {
        e.descripcion = 'La descripción es requerida para ' + (qrForm.medioTransporte === 'BICICLETA' ? 'bicicleta' : 'otro medio');
      }
    }
    setQrErrors(e);
    return Object.keys(e).length === 0;
  }

  async function quitarFrecuente() {
    if (!confirmQuitar) return;
    try {
      await api.del(`/residentes/${user?.idResidente}/frecuentes/${confirmQuitar.idFrecuente}`);
      setToast({ message: 'Visitante frecuente quitado', type: 'success' });
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setConfirmQuitar(null);
    }
  }

  async function generarQr() {
    if (qrSendingRef.current) return; // doble submit
    if (!validateQr()) return;
    qrSendingRef.current = true;
    setQrSending(true);
    try {
      const tipoVehiculo = qrForm.medioTransporte === 'A_PIE' ? null
        : qrForm.medioTransporte === 'CARRO' ? 'VEHICULO'
        : qrForm.medioTransporte;
      const res = await api.post('/visitas/rapida', {
        idFrecuente: qrModal.idFrecuente,
        idVisitante: qrModal.idVisitante,
        cantidadPersonas: Number(qrForm.cantidadPersonas),
        tiempoValidezMin: Number(qrForm.tiempoValidezMin),
        tipoVehiculo,
        placa: qrForm.medioTransporte === 'CARRO' || qrForm.medioTransporte === 'MOTO' ? qrForm.placa.toUpperCase() : null,
        descripcionTipo: qrForm.medioTransporte === 'BICICLETA' || qrForm.medioTransporte === 'OTRO' ? qrForm.descripcion.trim() : null,
        notas: qrForm.notas.trim() || null,
      });
      setQrGenerado(res);
      setToast({ message: 'QR generado para ' + qrModal.nombreVisitante, type: 'success' });
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      qrSendingRef.current = false;
      setQrSending(false);
    }
  }

  const { data, loading, refetch } = useFetch(
    () => api.get(`/residentes/${user?.idResidente}/frecuentes`),
    [user]
  );

  // Carga paralela de las ultimas QR del residente para mostrar placas
  const { data: qrs } = useFetch(
    () => api.get(`/residentes/${user?.idResidente}/qr-activos`).catch(() => []),
    [user]
  );

  const filtrados = (data?.items || data || []).filter((f) => {
    if (!search) return true;
    const term = search.toLowerCase();
    return [f.nombreVisitante, f.documento, f.ultimaPlaca]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(term));
  });

  function validate() {
    const e = {};
    const rN = valNombre(form.nombres, 'El nombre');
    if (!rN.ok) e.nombres = rN.mensaje;
    const rA = valApellido(form.apellidos, 'El apellido');
    if (!rA.ok) e.apellidos = rA.mensaje;
    const codigoDoc = tiposDoc.find((t) => Number(t.idTipoDoc) === Number(form.idTipoDoc))?.codigo || '';
    const rD = valDocumento(form.numeroDocumento, codigoDoc, 'El documento');
    if (!rD.ok) e.numeroDocumento = rD.mensaje;
    const rTel = valTelefono(form.telefono, { required: false });
    if (!rTel.ok) e.telefono = rTel.mensaje;
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function save() {
    if (savingRef.current) return; // doble submit
    if (!validate()) return;
    savingRef.current = true;
    setSaving(true);
    try {
      // POST /api/visitantes crea el visitante; la vinculacion como frecuente
      // se hace via flujo de QR/libera visita
      await api.post('/visitantes', {
        idTipoDoc: Number(form.idTipoDoc),
        numeroDocumento: form.numeroDocumento.trim(),
        nombres: form.nombres.trim(),
        apellidos: form.apellidos.trim(),
        telefono: form.telefono.replace(/\D/g, '') || null,
        email: form.email.trim() || null,
        activo: true,
      });
      setToast({ message: 'Visitante creado. Para marcarlo como frecuente, genere un QR de "Visita Rápida" en /res-visita', type: 'success' });
      setForm({
        idTipoDoc: 1,
        numeroDocumento: '',
        nombres: '',
        apellidos: '',
        telefono: '',
        email: '',
        placa: '',
      });
      setModalOpen(false);
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  const columns = [
    { key: 'nombreVisitante', label: 'Nombre' },
    { key: 'documento', label: 'Documento' },
    { key: 'ultimaPlaca', label: 'Placa' },
    { key: 'ultimaVisita', label: 'Último Ingreso', render: (r) => formatDate(r.ultimaVisita) },
  ];

  return (
    <div>
      <PageHeader
        title="Visitantes Frecuentes"
        subtitle="Personas que te visitan regularmente"
        action={
          <div style={{ display: 'flex', gap: '8px' }}>
            <Input
              id="search" aria-label="Buscar"
              placeholder="Buscar..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <Button onClick={() => setModalOpen(true)}>+ Nuevo Visitante</Button>
          </div>
        }
      />
      <div className="frecuentes-grid">
        {loading && <div className="card empty-state">Cargando...</div>}
        {!loading && filtrados.length === 0 && (
          <div className="card empty-state">No tienes visitantes frecuentes</div>
        )}
        {filtrados.map((f) => (
          <div
            key={f.idFrecuente}
            className="frecuente-card"
            style={{ flexDirection: 'row', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}
          >
            <div
              style={{
                width: '48px',
                height: '48px',
                borderRadius: '50%',
                background: 'var(--border)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'var(--text-muted)',
                flexShrink: 0,
              }}
            >
              <span className="material-symbols-outlined">person</span>
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="name">{f.nombreVisitante || '—'}</div>
              <div className="meta">Doc: {f.documento || '—'}</div>
              {f.ultimaPlaca && <div className="meta">Placa: {f.ultimaPlaca}</div>}
              {f.ultimaVisita && <div className="meta">Última visita: {formatDate(f.ultimaVisita)}</div>}
            </div>
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', flexShrink: 0 }}>
              <Button
                variant="outline"
                onClick={() => abrirQr(f)}
                className="hover:!bg-accent-green hover:!text-white"
              >
                Generar QR
              </Button>
              <Button
                variant="outline"
                onClick={() => setConfirmQuitar(f)}
                aria-label={`Quitar a ${f.nombreVisitante || ''}`}
                title="Quitar frecuente"
                className="hover:!bg-btn-danger hover:!text-white"
              >
                <span className="material-symbols-outlined" aria-hidden="true" style={{ fontSize: '16px' }}>person_remove</span>
                Quitar
              </Button>
            </div>
          </div>
        ))}
      </div>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Nuevo Visitante"
        size="md"
        footer={
          <>
            <Button variant="outline" onClick={() => setModalOpen(false)} disabled={saving}>
              Cancelar
            </Button>
            <Button onClick={save} disabled={saving}>
              {saving ? 'Guardando...' : 'Guardar'}
            </Button>
          </>
        }
      >
        <div className="form-row">
          <Select
            id="idTipoDoc"
            label="Tipo Documento"
            value={form.idTipoDoc}
            onChange={(e) => setForm((f) => ({ ...f, idTipoDoc: Number(e.target.value) }))}
          >
            {tiposDoc.map((t) => (
              <option key={t.idTipoDoc ?? t.value} value={t.idTipoDoc ?? t.value}>
                {t.descripcion || t.nombre}
              </option>
            ))}
          </Select>
          {errorTiposDoc && !tiposDoc.length && (
            <p style={{ color: 'var(--error)', fontSize: '12px' }}>Error al cargar los tipos de documento</p>
          )}
          <Input
            id="numeroDocumento"
            label="Número Documento"
            value={form.numeroDocumento}
            onChange={(e) => setForm((f) => ({ ...f, numeroDocumento: e.target.value }))}
            error={errors.numeroDocumento}
          />
        </div>
        <div className="form-row">
          <Input
            id="nombres"
            label="Nombres"
            value={form.nombres}
            onChange={(e) => setForm((f) => ({ ...f, nombres: e.target.value }))}
            error={errors.nombres}
          />
          <Input
            id="apellidos"
            label="Apellidos"
            value={form.apellidos}
            onChange={(e) => setForm((f) => ({ ...f, apellidos: e.target.value }))}
            error={errors.apellidos}
          />
        </div>
        <div className="form-row">
          <Input
            id="telefono"
            label="Teléfono (opcional)"
            value={form.telefono}
            onChange={(e) => setForm((f) => ({ ...f, telefono: e.target.value }))}
            onBlur={() => touch('telefono')}
            error={fieldError('telefono', valTelefono(form.telefono, { required: false })) || errors.telefono}
          />
          <Input
            id="email"
            label="Email (opcional)"
            type="email"
            value={form.email}
            onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
            onBlur={() => touch('email')}
            error={fieldError('email', valEmail(form.email, { required: false })) || errors.email}
          />
        </div>
      </Modal>

      <Modal
        open={qrModal !== null}
        onClose={() => setQrModal(null)}
        title={qrGenerado ? 'QR generado' : `Generar QR — ${qrModal?.nombreVisitante || ''}`}
        size="md"
      >
        {qrGenerado ? (
          <div style={{ textAlign: 'center', padding: '8px 0' }}>
            <div style={{ fontSize: '14px', marginBottom: '4px' }}>{qrGenerado.mensaje || 'Visita registrada'}</div>
            <div
              style={{
                fontSize: '22px',
                fontWeight: 800,
                fontFamily: 'monospace',
                background: 'var(--primary)',
                color: 'white',
                borderRadius: '8px',
                padding: '16px',
                margin: '12px 0',
                wordBreak: 'break-all',
              }}
            >
              {qrGenerado.codigoQr}
            </div>
            <div style={{ fontSize: '12px', opacity: 0.7 }}>
              Compartí este código con {qrModal?.nombreVisitante} para que el portero lo escanee.
              {qrGenerado.fechaExpiracion ? ` Expira el ${formatDate(qrGenerado.fechaExpiracion)}.` : ''}
            </div>
            <div style={{ display: 'flex', gap: '8px', justifyContent: 'center', marginTop: '16px' }}>
              <Button variant="outline" onClick={() => setQrModal(null)}>
                Cerrar
              </Button>
              <Button onClick={() => abrirQr(qrModal)}>
                Generar otro QR
              </Button>
            </div>
          </div>
        ) : (
          <>
            <div className="form-group">
              <div className="meta" style={{ fontSize: '13px' }}>
                Visitante: <strong>{qrModal?.nombreVisitante}</strong> · Doc: {qrModal?.documento}
              </div>
            </div>
            <div className="form-row">
              <Select
                id="qr-medioTransporte"
                label="¿En qué viene?"
                value={qrForm.medioTransporte}
                onChange={(e) => setQrForm((f) => ({ ...f, medioTransporte: e.target.value }))}
              >
                <option value="CARRO">Carro</option>
                <option value="MOTO">Moto</option>
                <option value="BICICLETA">Bicicleta</option>
                <option value="A_PIE">A pie</option>
                <option value="OTRO">Otro</option>
              </Select>
              {(qrForm.medioTransporte === 'CARRO' || qrForm.medioTransporte === 'MOTO') && (
                <Input
                  id="qr-placa"
                  label="Placa"
                  value={qrForm.placa}
                  onChange={(e) => setQrForm((f) => ({ ...f, placa: e.target.value.toUpperCase() }))}
                  error={qrErrors.placa}
                />
              )}
              {(qrForm.medioTransporte === 'BICICLETA' || qrForm.medioTransporte === 'OTRO') && (
                <Input
                  id="qr-descripcion"
                  label={qrForm.medioTransporte === 'BICICLETA' ? 'Descripción de la bicicleta' : 'Descripción del medio'}
                  value={qrForm.descripcion}
                  onChange={(e) => setQrForm((f) => ({ ...f, descripcion: e.target.value }))}
                  error={qrErrors.descripcion}
                />
              )}
            </div>
            <div className="form-row">
              <Input
                id="qr-cantidadPersonas"
                label="Cantidad de personas"
                type="number"
                value={qrForm.cantidadPersonas}
                onChange={(e) => setQrForm((f) => ({ ...f, cantidadPersonas: e.target.value }))}
                error={qrErrors.cantidadPersonas}
              />
              <Input
                id="qr-tiempoValidezMin"
                label="Validez (minutos)"
                type="number"
                value={qrForm.tiempoValidezMin}
                onChange={(e) => setQrForm((f) => ({ ...f, tiempoValidezMin: e.target.value }))}
                error={qrErrors.tiempoValidezMin}
              />
            </div>
            <div className="form-group">
              <Input
                id="qr-notas"
                label="Motivo / descripción (opcional)"
                value={qrForm.notas}
                onChange={(e) => setQrForm((f) => ({ ...f, notas: e.target.value }))}
              />
            </div>
            <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end', marginTop: '4px' }}>
              <Button variant="outline" onClick={() => setQrModal(null)} disabled={qrSending}>
                Cancelar
              </Button>
              <Button onClick={generarQr} disabled={qrSending}>
                {qrSending ? 'Generando...' : 'Generar QR'}
              </Button>
            </div>
          </>
        )}
      </Modal>

      <ConfirmDialog
        open={!!confirmQuitar}
        onClose={() => setConfirmQuitar(null)}
        onConfirm={quitarFrecuente}
        title="Quitar visitante frecuente"
        message={`¿Quitar a ${confirmQuitar?.nombreVisitante || ''} de tus visitantes frecuentes? Podrás volver a generarle un QR más adelante.`}
        confirmLabel="Quitar"
        danger
      />

      <ConfirmDialog
        open={!!confirmQuitar}
        onClose={() => setConfirmQuitar(null)}
        onConfirm={quitarFrecuente}
        title="Quitar visitante frecuente"
        message={`¿Quitar a ${confirmQuitar?.nombreVisitante || ''} de tus visitantes frecuentes? Podrás volver a generarle un QR más adelante.`}
        confirmLabel="Quitar"
        danger
      />

      <Toast toast={toast} />
    </div>
  );
}
