import { useState, useEffect, useRef } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Textarea } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate } from '../lib/utils.js';

function ApartamentoMultiSelect({ apartamentos, selected, onChange }) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const ref = useRef(null);

  useEffect(() => {
    function onClickOutside(e) {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    }
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  const porPiso = {};
  (apartamentos || []).forEach((a) => {
    if (!porPiso[a.piso]) porPiso[a.piso] = [];
    porPiso[a.piso].push(a);
  });

  const isTodos = selected === 'TODOS';
  const selectedIds = Array.isArray(selected) ? selected : [];

  function toggleTodos() {
    onChange(isTodos ? [] : 'TODOS');
  }
  function togglePiso(piso) {
    const idsDelPiso = porPiso[piso].map((a) => a.idApartamento);
    const todosSeleccionados = idsDelPiso.every((id) => selectedIds.includes(id));
    if (todosSeleccionados) {
      onChange(selectedIds.filter((id) => !idsDelPiso.includes(id)));
    } else {
      const nuevos = new Set([...selectedIds, ...idsDelPiso]);
      onChange(Array.from(nuevos));
    }
  }
  function toggleApto(id) {
    if (selectedIds.includes(id)) {
      onChange(selectedIds.filter((x) => x !== id));
    } else {
      onChange([...selectedIds, id]);
    }
  }

  const label = isTodos
    ? 'Todos los apartamentos'
    : selectedIds.length === 0
      ? 'Seleccionar apartamentos'
      : `${selectedIds.length} apartamento(s) seleccionados`;

  const pisos = Object.keys(porPiso)
    .map(Number)
    .sort((a, b) => a - b)
    .filter((p) => !search || String(p).includes(search));

  return (
    <div className="multi-select" ref={ref}>
      <div className="multi-select-trigger" onClick={() => setOpen((o) => !o)}>
        <span>{label}</span>
        <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>
          arrow_drop_down
        </span>
      </div>
      {open && (
        <div className="multi-select-dropdown">
          <div style={{ padding: '8px' }}>
            <input
              type="text"
              placeholder="Buscar piso..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="form-control"
              style={{ fontSize: '12px' }}
            />
          </div>
          <div className={`multi-select-option ${isTodos ? 'selected' : ''}`} onClick={toggleTodos}>
            — Todos los apartamentos —
          </div>
          {pisos.map((piso) => {
            const idsDelPiso = porPiso[piso].map((a) => a.idApartamento);
            const pisoCompleto = !isTodos && idsDelPiso.every((id) => selectedIds.includes(id));
            return (
              <div key={piso}>
                <div
                  className={`multi-select-option ${pisoCompleto ? 'selected' : ''}`}
                  style={{ fontWeight: 700 }}
                  onClick={() => togglePiso(piso)}
                >
                  Piso {piso} (completo)
                </div>
                {porPiso[piso].map((a) => (
                  <div
                    key={a.idApartamento}
                    className={`multi-select-option ${!isTodos && selectedIds.includes(a.idApartamento) ? 'selected' : ''}`}
                    style={{ paddingLeft: '24px' }}
                    onClick={() => toggleApto(a.idApartamento)}
                  >
                    Apto {a.numero}
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default function AvisosPage() {
  const [toast, setToast] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({ titulo: '', cuerpo: '' });
  const [selectedApts, setSelectedApts] = useState('TODOS');
  const [sending, setSending] = useState(false);

  const { data: avisos, loading, refetch } = useFetch(() => api.get('/buzon/avisos'), []);
  const { data: apartamentos } = useFetch(() => api.get('/apartamentos?size=500'), []);

  const columns = [
    { key: 'idMensaje', label: 'ID', width: 60 },
    { key: 'numeroApartamento', label: 'Apartamento', render: (r) => r.numeroApartamento || 'Todos' },
    { key: 'titulo', label: 'Título' },
    { key: 'cuerpo', label: 'Mensaje' },
    { key: 'fechaCreacion', label: 'Fecha', render: (r) => formatDate(r.fechaCreacion) },
  ];

  async function send() {
    if (!form.titulo.trim() || !form.cuerpo.trim()) {
      setToast({ message: 'Título y mensaje son obligatorios', type: 'error' });
      return;
    }
    setSending(true);
    try {
      const payload = { titulo: form.titulo, cuerpo: form.cuerpo };
      if (selectedApts !== 'TODOS' && selectedApts.length > 0) {
        payload.idApartamentos = selectedApts;
      }
      await api.post('/buzon/aviso', payload);
      setToast({ message: 'Aviso enviado', type: 'success' });
      setForm({ titulo: '', cuerpo: '' });
      setSelectedApts('TODOS');
      setModalOpen(false);
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setSending(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Avisos"
        subtitle="Comunicados generales a residentes"
        action={<Button onClick={() => setModalOpen(true)}>+ Nuevo Aviso</Button>}
      />
      <DataTable
        columns={columns}
        rows={avisos || []}
        loading={loading}
        empty="No hay avisos enviados"
        keyField="idMensaje"
      />

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Nuevo Aviso"
        size="lg"
        footer={
          <>
            <Button variant="outline" onClick={() => setModalOpen(false)} disabled={sending}>
              Cancelar
            </Button>
            <Button onClick={send} disabled={sending}>
              {sending ? 'Enviando...' : 'Enviar'}
            </Button>
          </>
        }
      >
        <div className="form-group">
          <label>Apartamentos</label>
          <ApartamentoMultiSelect
            apartamentos={apartamentos?.items || []}
            selected={selectedApts}
            onChange={setSelectedApts}
          />
        </div>
        <div className="form-group">
          <Input
            id="titulo"
            label="Título"
            value={form.titulo}
            onChange={(e) => setForm((f) => ({ ...f, titulo: e.target.value }))}
          />
        </div>
        <div className="form-group">
          <Textarea
            id="cuerpo"
            label="Mensaje"
            rows={5}
            value={form.cuerpo}
            onChange={(e) => setForm((f) => ({ ...f, cuerpo: e.target.value }))}
          />
        </div>
      </Modal>
      <Toast toast={toast} />
    </div>
  );
}
