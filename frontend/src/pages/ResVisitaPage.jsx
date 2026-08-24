import { useEffect, useRef, useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch, useTiposDocumento, useLiveValidation } from '../lib/hooks.js';
import { valNombre, valApellido, valDocumento, valTelefono, valEmail, valPlaca } from '../lib/validation.js';

const emptyVisitante = {
  idTipoDoc: '',
  numeroDocumento: '',
  nombres: '',
  apellidos: '',
  telefono: '',
  email: '',
};

const emptyForm = {
  visitante: { ...emptyVisitante },
  motivo: '',
  tiempoValidezMin: 30,
  cantidadPersonas: 1,
  medioTransporte: 'CARRO',
  placa: '',
  descripcion: '',
  tipoVehiculo: 'CARRO',
};

export default function ResVisitaPage() {
  const { user } = useAuth();
  const { tiposDoc, error: errorTiposDoc } = useTiposDocumento();
  const { touch, fieldError } = useLiveValidation();
  const [toast, setToast] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [sending, setSending] = useState(false);
  // Guard anti doble-submit: mismo patron que VisitasPage (FASE 4.2-P2).
  // disabled={state} NO bloquea clicks sincronicos (re-render asincrono) — el ref es la barrera real.
  const sendingRef = useRef(false);
  const [qrGenerado, setQrGenerado] = useState(null);
  const [buscarDoc, setBuscarDoc] = useState('');
  const [buscarDocDebounced, setBuscarDocDebounced] = useState('');

  // Debounce de 400ms: evita un request por cada tecla al buscar visitante
  useEffect(() => {
    const t = setTimeout(() => setBuscarDocDebounced(buscarDoc), 400);
    return () => clearTimeout(t);
  }, [buscarDoc]);

  // Buscar visitante existente por documento
  const { data: visitanteExistente } = useFetch(
    () =>
      buscarDocDebounced.length >= 4
        ? api.get(`/visitas/buscar?documento=${encodeURIComponent(buscarDocDebounced)}`).catch(() => null)
        : Promise.resolve(null),
    [buscarDocDebounced]
  );
  const visitanteEncontrado = visitanteExistente?.raw || visitanteExistente;

  function update(path, value) {
    setForm((f) => {
      const keys = path.split('.');
      const next = { ...f };
      let cursor = next;
      for (let i = 0; i < keys.length - 1; i++) {
        cursor[keys[i]] = { ...cursor[keys[i]] };
        cursor = cursor[keys[i]];
      }
      cursor[keys[keys.length - 1]] = value;
      return next;
    });
  }

  function onDocumentoChange(doc) {
    update('visitante.numeroDocumento', doc);
    setBuscarDoc(doc);
  }
  // Si encuentra visitante existente, autollenar
  if (visitanteEncontrado && visitanteEncontrado.nombres && form.visitante.nombres === '' && form.visitante.numeroDocumento === visitanteEncontrado.numeroDocumento) {
    setForm((f) => ({
      ...f,
      visitante: {
        idTipoDoc: visitanteEncontrado.idTipoDoc || '',
        numeroDocumento: visitanteEncontrado.numeroDocumento || '',
        nombres: visitanteEncontrado.nombres || '',
        apellidos: visitanteEncontrado.apellidos || '',
        telefono: visitanteEncontrado.telefono || '',
        email: visitanteEncontrado.email || '',
      },
    }));
  }

  function validate() {
    const e = {};
    const rN = valNombre(form.visitante.nombres, 'El nombre del visitante');
    if (!rN.ok) e['visitante.nombres'] = rN.mensaje;
    const rA = valApellido(form.visitante.apellidos, 'El apellido del visitante');
    if (!rA.ok) e['visitante.apellidos'] = rA.mensaje;
    const codigoDoc = tiposDoc.find((t) => Number(t.idTipoDoc) === Number(form.visitante.idTipoDoc))?.codigo || '';
    const rD = valDocumento(form.visitante.numeroDocumento, codigoDoc, 'El documento del visitante');
    if (!rD.ok) e['visitante.numeroDocumento'] = rD.mensaje;
    if (!form.visitante.idTipoDoc && !visitanteEncontrado?.nombres) {
      e['visitante.idTipoDoc'] = 'Seleccione el tipo de documento del visitante';
    }
    const rTel = valTelefono(form.visitante.telefono, { required: false });
    if (!rTel.ok) e['visitante.telefono'] = rTel.mensaje;
    const rEmail = valEmail(form.visitante.email, { required: false });
    if (!rEmail.ok) e['visitante.email'] = rEmail.mensaje;
    if (form.medioTransporte === 'CARRO' || form.medioTransporte === 'MOTO' || form.medioTransporte === 'BICICLETA' || form.medioTransporte === 'OTRO') {
      const rPlaca = valPlaca(form.placa, form.medioTransporte === 'CARRO' ? 'CARRO' : form.medioTransporte === 'MOTO' ? 'MOTO' : 'OTRO');
      if (!rPlaca.ok) e.placa = rPlaca.mensaje;
    }
    if (form.medioTransporte === 'BICICLETA' || form.medioTransporte === 'OTRO') {
      if (!form.descripcion.trim()) e.descripcion = 'La descripción es requerida para ' + (form.medioTransporte === 'BICICLETA' ? 'bicicleta' : 'otro medio');
    }
    const validez = Number(form.tiempoValidezMin);
    if (!form.tiempoValidezMin || Number.isNaN(validez) || validez < 5 || validez > 60) {
      e.tiempoValidezMin = 'La validez debe ser entre 5 y 60 minutos';
    }
    const personas = Number(form.cantidadPersonas);
    if (!form.cantidadPersonas || Number.isNaN(personas) || personas < 1 || personas > 99) {
      e.cantidadPersonas = 'Debe ser entre 1 y 99 personas';
    }
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function send() {
    if (sendingRef.current) return; // doble submit
    if (!validate()) return;
    sendingRef.current = true;
    setSending(true);
    try {
      const payload = {
        visitante: { ...form.visitante },
        idResidente: user.idResidente,
        tiempoValidezMin: Number(form.tiempoValidezMin),
        cantidadPersonas: Number(form.cantidadPersonas),
        notas: form.motivo,
      };
      if (!payload.visitante.idTipoDoc) delete payload.visitante.idTipoDoc;
      // Solo incluir vehiculo si hay placa o descripcion; el backend explota si la key llega null
      if (form.medioTransporte === 'CARRO' || form.medioTransporte === 'MOTO') {
        payload.vehiculo = {
          placa: form.placa.toUpperCase(),
          // El enum TipoVehiculo no tiene CARRO: el backend hace TipoVehiculo.valueOf(...)
          tipo: form.medioTransporte === 'CARRO' ? 'VEHICULO' : form.medioTransporte,
        };
      } else if (form.medioTransporte === 'BICICLETA' || form.medioTransporte === 'OTRO') {
        payload.vehiculo = {
          tipo: form.medioTransporte,
          descripcion: form.descripcion.trim(),
        };
      }
      const res = await api.post('/visitas', payload);
      setQrGenerado(res);
      setToast({ message: 'Visita registrada, QR generado', type: 'success' });
      setForm(emptyForm);
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      sendingRef.current = false;
      setSending(false);
    }
  }

  return (
    <div>
      <PageHeader title="Registrar Visita" subtitle="Avisa al portero que viene una visita" />

      {qrGenerado && (
        <div className="card" style={{ background: 'var(--primary)', color: 'white', textAlign: 'center', marginBottom: '16px' }}>
          <div style={{ fontSize: '14px' }}>QR generado</div>
          <div style={{ fontSize: '28px', fontWeight: 800, marginTop: '8px', fontFamily: 'monospace' }}>
            {qrGenerado.codigoQr}
          </div>
          <div style={{ fontSize: '12px', opacity: 0.7, marginTop: '8px' }}>
            Comparte este codigo con tu visita para que el portero lo escanee
          </div>
          <Button
            variant="outline"
            onClick={() => setQrGenerado(null)}
            style={{ marginTop: '12px' }}
          >
            Generar otro
          </Button>
        </div>
      )}

      {!qrGenerado && (
        <div className="card" style={{ maxWidth: '720px' }}>
          <h3 className="card-title">Datos del visitante</h3>
          <div className="form-row">
            <Select
              id="idTipoDoc"
              label="Tipo Documento"
              value={form.visitante.idTipoDoc}
              onChange={(e) => update('visitante.idTipoDoc', Number(e.target.value))}
              error={errors['visitante.idTipoDoc']}
            >
              <option value="">Seleccione tipo de documento</option>
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
              value={form.visitante.numeroDocumento}
              onChange={(e) => onDocumentoChange(e.target.value)}
              error={errors['visitante.numeroDocumento']}
            />
          </div>
          {visitanteEncontrado && visitanteEncontrado.nombres && (
            <p style={{ fontSize: '12px', color: 'var(--accent-green)', marginTop: '-8px', marginBottom: '12px' }}>
              Visitante encontrado: {visitanteEncontrado.nombres} {visitanteEncontrado.apellidos}
            </p>
          )}
          <div className="form-row">
            <Input
              id="nombres"
              label="Nombres"
              value={form.visitante.nombres}
              onChange={(e) => update('visitante.nombres', e.target.value)}
              error={errors['visitante.nombres']}
            />
            <Input
              id="apellidos"
              label="Apellidos"
              value={form.visitante.apellidos}
              onChange={(e) => update('visitante.apellidos', e.target.value)}
              error={errors['visitante.apellidos']}
            />
          </div>
          <div className="form-row">
            <Input
              id="telefono"
              label="Teléfono (opcional)"
              value={form.visitante.telefono}
              onChange={(e) => update('visitante.telefono', e.target.value)}
              onBlur={() => touch('visitante.telefono')}
              error={fieldError('visitante.telefono', valTelefono(form.visitante.telefono, { required: false })) || errors['visitante.telefono']}
            />
            <Input
              id="email"
              label="Email (opcional)"
              type="email"
              value={form.visitante.email}
              onChange={(e) => update('visitante.email', e.target.value)}
              onBlur={() => touch('visitante.email')}
              error={fieldError('visitante.email', valEmail(form.visitante.email, { required: false })) || errors['visitante.email']}
            />
          </div>

          <h3 className="card-title mt-4">
            Detalles de la visita
          </h3>
          <div className="form-row">
            <Input
                id="tiempoValidezMin"
                label="Validez (minutos)"
                type="number"
                value={form.tiempoValidezMin}
                onChange={(e) => update('tiempoValidezMin', e.target.value)}
                error={errors.tiempoValidezMin}
              />
              <Input
                id="cantidadPersonas"
                label="Cantidad de personas"
                type="number"
                value={form.cantidadPersonas}
                onChange={(e) => update('cantidadPersonas', e.target.value)}
                error={errors.cantidadPersonas}
              />
          </div>
          <div className="form-row">
            <Select
              id="medioTransporte"
              label="¿En qué viene?"
              value={form.medioTransporte}
              onChange={(e) => update('medioTransporte', e.target.value)}
            >
              <option value="CARRO">Carro</option>
              <option value="MOTO">Moto</option>
              <option value="BICICLETA">Bicicleta</option>
              <option value="A_PIE">A pie</option>
              <option value="OTRO">Otro</option>
            </Select>
            {(form.medioTransporte === 'CARRO' || form.medioTransporte === 'MOTO') && (
              <Input
                id="placa"
                label="Placa"
                value={form.placa}
                onChange={(e) => update('placa', e.target.value.toUpperCase())}
                error={errors.placa}
              />
            )}
            {(form.medioTransporte === 'BICICLETA' || form.medioTransporte === 'OTRO') && (
              <Input
                id="descripcion"
                label={form.medioTransporte === 'BICICLETA' ? 'Descripción de la bicicleta' : 'Descripción del medio'}
                value={form.descripcion}
                onChange={(e) => update('descripcion', e.target.value)}
                error={errors.descripcion}
              />
            )}
          </div>
          <div className="form-group">
            <Input
              id="motivo"
              label="Motivo (opcional)"
              value={form.motivo}
              onChange={(e) => update('motivo', e.target.value)}
            />
          </div>
          <div className="form-group">
            <Button onClick={send} disabled={sending}>
              {sending ? 'Generando QR...' : 'Generar QR de visita'}
            </Button>
          </div>
        </div>
      )}
      <Toast toast={toast} />
    </div>
  );
}
