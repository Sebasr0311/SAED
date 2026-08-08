import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';
import { valPlaca, formatMiles } from '../lib/utils.js';

const TIPOS_DOC = [
  { value: 1, nombre: 'C.C.' },
  { value: 2, nombre: 'C.E.' },
  { value: 4, nombre: 'Pasaporte' },
  { value: 5, nombre: 'T.I.' },
];

const emptyVisitante = {
  idTipoDoc: 1,
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
  const [toast, setToast] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [sending, setSending] = useState(false);
  const [qrGenerado, setQrGenerado] = useState(null);
  const [buscarDoc, setBuscarDoc] = useState('');

  // Buscar visitante existente por documento
  const { data: visitanteExistente } = useFetch(
    () =>
      buscarDoc.length >= 4
        ? api.get(`/visitas/buscar?documento=${encodeURIComponent(buscarDoc)}`).catch(() => null)
        : Promise.resolve(null),
    [buscarDoc]
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
        idTipoDoc: visitanteEncontrado.idTipoDoc || 1,
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
    if (!form.visitante.nombres.trim()) e['visitante.nombres'] = 'Requerido';
    if (!form.visitante.apellidos.trim()) e['visitante.apellidos'] = 'Requerido';
    if (!form.visitante.numeroDocumento.trim()) e['visitante.numeroDocumento'] = 'Requerido';
    if (form.medioTransporte === 'CARRO' || form.medioTransporte === 'MOTO') {
      if (!valPlaca(form.placa, form.medioTransporte === 'MOTO' ? 'MOTO' : 'CARRO'))
        e.placa = 'Formato de placa inválido';
    }
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function send() {
    if (!validate()) return;
    setSending(true);
    try {
      const payload = {
        visitante: form.visitante,
        idResidente: user.idResidente,
        tiempoValidezMin: form.tiempoValidezMin,
        cantidadPersonas: form.cantidadPersonas,
        motivo: form.motivo,
      };
      // Solo incluir vehiculo si hay placa; el backend explota si la key llega null
      if (form.medioTransporte === 'CARRO' || form.medioTransporte === 'MOTO') {
        payload.vehiculo = { placa: form.placa.toUpperCase(), tipo: form.medioTransporte };
      }
      const res = await api.post('/visitas', payload);
      setQrGenerado(res);
      setToast({ message: 'Visita registrada, QR generado', type: 'success' });
      setForm(emptyForm);
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setSending(false);
    }
  }

  return (
    <div>
      <PageHeader title="Registrar Visita" subtitle="Avisa al portero que viene una visita" />

      {qrGenerado && (
        <div className="card" style={{ background: '#0f2044', color: 'white', textAlign: 'center', marginBottom: '16px' }}>
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
            >
              {TIPOS_DOC.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.nombre}
                </option>
              ))}
            </Select>
            <Input
              id="numeroDocumento"
              label="Número Documento"
              value={form.visitante.numeroDocumento}
              onChange={(e) => onDocumentoChange(e.target.value)}
              error={errors['visitante.numeroDocumento']}
            />
          </div>
          {visitanteEncontrado && visitanteEncontrado.nombres && (
            <p style={{ fontSize: '12px', color: '#10B981', marginTop: '-8px', marginBottom: '12px' }}>
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
            />
            <Input
              id="email"
              label="Email (opcional)"
              type="email"
              value={form.visitante.email}
              onChange={(e) => update('visitante.email', e.target.value)}
            />
          </div>

          <h3 className="card-title" style={{ marginTop: '16px' }}>
            Detalles de la visita
          </h3>
          <div className="form-row">
            <Input
              id="tiempoValidezMin"
              label="Validez (minutos)"
              type="number"
              value={form.tiempoValidezMin}
              onChange={(e) => update('tiempoValidezMin', Number(e.target.value))}
            />
            <Input
              id="cantidadPersonas"
              label="Cantidad de personas"
              type="number"
              value={form.cantidadPersonas}
              onChange={(e) => update('cantidadPersonas', Number(e.target.value))}
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
