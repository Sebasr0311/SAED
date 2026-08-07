import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';

const emptyForm = {
  nombreVisitante: '',
  documento: '',
  tipoDocumento: 'CEDULA',
  motivo: '',
  idApartamento: '',
};

export default function ResVisitaPage() {
  const { user } = useAuth();
  const [form, setForm] = useState(emptyForm);
  const [sending, setSending] = useState(false);
  const [toast, setToast] = useState(null);

  function update(k, v) {
    setForm((f) => ({ ...f, [k]: v }));
  }

  async function send() {
    if (!form.nombreVisitante || !form.documento) {
      setToast({ message: 'Nombre y documento son obligatorios', type: 'error' });
      return;
    }
    setSending(true);
    try {
      await api.post('/visitas', { ...form, idResidente: user?.idResidente });
      setToast({ message: 'Visita registrada', type: 'success' });
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
      <div className="card max-w-xl space-y-4">
        <Input
          id="nombreVisitante"
          label="Nombre del visitante"
          value={form.nombreVisitante}
          onChange={(e) => update('nombreVisitante', e.target.value)}
          required
        />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Select
            id="tipoDocumento"
            label="Tipo documento"
            value={form.tipoDocumento}
            onChange={(e) => update('tipoDocumento', e.target.value)}
          >
            <option value="CEDULA">Cédula</option>
            <option value="PASAPORTE">Pasaporte</option>
            <option value="TARJETA_IDENTIDAD">Tarjeta Identidad</option>
          </Select>
          <Input
            id="documento"
            label="Número documento"
            value={form.documento}
            onChange={(e) => update('documento', e.target.value)}
            required
          />
        </div>
        <Input
          id="motivo"
          label="Motivo (opcional)"
          value={form.motivo}
          onChange={(e) => update('motivo', e.target.value)}
        />
        <Button onClick={send} loading={sending} icon="send">
          Registrar Visita
        </Button>
      </div>
      <Toast toast={toast} />
    </div>
  );
}
