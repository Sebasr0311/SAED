import { useState, useEffect, useRef } from 'react';
import { toast } from 'sonner';
import { Button } from '../components/ui/Button.jsx';
import { Input, Textarea, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { useFetch, useLiveValidation } from '../lib/hooks.js';
import { useAuth } from '../lib/AuthContext.jsx';
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
  (apartamentos?.items || apartamentos || []).forEach((a) => {
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
      <button
        type="button"
        className="multi-select-trigger"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
      >
        <span>{label}</span>
        <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>
          arrow_drop_down
        </span>
      </button>
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
          <button
            type="button"
            className={`multi-select-option ${isTodos ? 'selected' : ''}`}
            onClick={toggleTodos}
            aria-pressed={isTodos}
          >
            — Todos los apartamentos —
          </button>
          {pisos.map((piso) => {
            const idsDelPiso = porPiso[piso].map((a) => a.idApartamento);
            const pisoCompleto = !isTodos && idsDelPiso.every((id) => selectedIds.includes(id));
            return (
              <div key={piso}>
                <button
                  type="button"
                  className={`multi-select-option ${pisoCompleto ? 'selected' : ''}`}
                  style={{ fontWeight: 700 }}
                  onClick={() => togglePiso(piso)}
                  aria-pressed={pisoCompleto}
                >
                  Piso {piso} (completo)
                </button>
                {porPiso[piso].map((a) => (
                  <button
                    type="button"
                    key={a.idApartamento}
                    className={`multi-select-option ${!isTodos && selectedIds.includes(a.idApartamento) ? 'selected' : ''}`}
                    style={{ paddingLeft: '24px' }}
                    onClick={() => toggleApto(a.idApartamento)}
                    aria-pressed={!isTodos && selectedIds.includes(a.idApartamento)}
                  >
                    Apto {a.numero}
                  </button>
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
  const { user } = useAuth();
  const isOrgAdmin = user?.role === 'ADMIN_ORGANIZACION' || user?.role === 'SUPERADMIN';

  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({ titulo: '', cuerpo: '', idPropiedad: 'TODAS' });
  const [selectedApts, setSelectedApts] = useState('TODOS');
  const [sending, setSending] = useState(false);
  const { touch, touchAll, resetTouched, fieldError } = useLiveValidation();

  const { data: avisos, loading, error, refetch } = useFetch(() => api.get('/buzon/avisos'), []);
  const { data: apartamentos } = useFetch(() => api.get('/units'), []);
  const { data: propertiesRaw } = useFetch(() => api.get('/properties'), []);

  const properties = Array.isArray(propertiesRaw?.data) ? propertiesRaw.data
    : Array.isArray(propertiesRaw) ? propertiesRaw
    : [];

  const avisosRows = Array.isArray(avisos?.items) ? avisos.items
    : Array.isArray(avisos) ? avisos
    : [];

  const columns = [
    { key: 'idMensaje', label: 'ID', width: 60, render: (r) => r.ID_COMUNICADO || r.idMensaje || r.id },
    { key: 'propiedadNombre', label: 'Edificio / Conjunto', render: (r) => r.PROPIEDAD_NOMBRE || r.propiedadNombre || '-' },
    { key: 'numeroApartamento', label: 'Apartamento', render: (r) => r.numeroApartamento || r.TIPO_SEGMENTACION || 'Todos' },
    { key: 'titulo', label: 'Título', render: (r) => r.TITULO || r.titulo },
    { key: 'cuerpo', label: 'Mensaje', render: (r) => r.CONTENIDO || r.cuerpo || r.mensaje },
    { key: 'fechaCreacion', label: 'Fecha', render: (r) => formatDate(r.FECHA_PUBLICACION || r.fechaCreacion) },
  ];

  async function send() {
    touchAll(['titulo', 'cuerpo']);
    if (!form.titulo.trim() || !form.cuerpo.trim()) {
      toast.error('Título y mensaje son obligatorios');
      return;
    }
    setSending(true);
    try {
      const payload = { titulo: form.titulo, mensaje: form.cuerpo };
      if (selectedApts !== 'TODOS' && selectedApts.length > 0) {
        payload.idApartamentos = selectedApts;
      }
      if (form.idPropiedad && form.idPropiedad !== 'TODAS') {
        payload.idPropiedad = Number(form.idPropiedad);
      }
      await api.post('/buzon/aviso', payload);
      toast.success('Aviso enviado');
      setForm({ titulo: '', cuerpo: '', idPropiedad: 'TODAS' });
      setSelectedApts('TODOS');
      resetTouched();
      setModalOpen(false);
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSending(false);
    }
  }

  function handleCloseModal() {
    setModalOpen(false);
    resetTouched();
    setForm({ titulo: '', cuerpo: '', idPropiedad: 'TODAS' });
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
        rows={avisosRows}
        loading={loading}
        empty={{ icon: 'campaign', title: 'No hay avisos enviados', subtitle: 'Los avisos que envíes a los residentes aparecerán aquí.' }}
        error={error?.message}
        keyField="idMensaje"
        pageSize={10}
      />

      <Modal
        open={modalOpen}
        onClose={handleCloseModal}
        title="Nuevo Aviso"
        size="lg"
        footer={
          <>
            <Button variant="outline" onClick={handleCloseModal} disabled={sending}>
              Cancelar
            </Button>
            <Button onClick={send} disabled={sending}>
              {sending ? 'Enviando...' : 'Enviar Comunicado'}
            </Button>
          </>
        }
      >
        {isOrgAdmin && properties.length > 0 && (
          <div className="form-group">
            <Select
              id="aviso-propiedad"
              label="Edificio o Propiedad Destino *"
              value={form.idPropiedad}
              onChange={(e) => setForm((f) => ({ ...f, idPropiedad: e.target.value }))}
            >
              <option value="TODAS">🏢 Todas las propiedades de la organización</option>
              {properties.map((p) => (
                <option key={p.id} value={p.id}>
                  📍 {p.nombre} {p.ciudad ? `(${p.ciudad})` : ''}
                </option>
              ))}
            </Select>
            <p className="text-[11px] text-muted-foreground mt-1">
              Elija si este comunicado se enviará a todos los edificios de la organización o solo a uno en particular.
            </p>
          </div>
        )}
        <div className="form-group">
          <label>Apartamentos</label>
          <ApartamentoMultiSelect
            apartamentos={apartamentos?.items || apartamentos || []}
            selected={selectedApts}
            onChange={setSelectedApts}
          />
        </div>
        <div className="form-group">
          <Input
            id="titulo"
            label="Título del comunicado"
            placeholder="Ej. Mantenimiento programado de tanques de agua"
            value={form.titulo}
            onChange={(e) => setForm((f) => ({ ...f, titulo: e.target.value }))}
            onBlur={() => touch('titulo')}
            error={fieldError('titulo', form.titulo.trim() ? { ok: true } : { ok: false, mensaje: 'El título es obligatorio' })}
            required
          />
        </div>
        <div className="form-group">
          <Textarea
            id="cuerpo"
            label="Mensaje del comunicado"
            placeholder="Escriba aquí los detalles del comunicado para los residentes..."
            rows={5}
            value={form.cuerpo}
            onChange={(e) => setForm((f) => ({ ...f, cuerpo: e.target.value }))}
            onBlur={() => touch('cuerpo')}
            error={fieldError('cuerpo', form.cuerpo.trim() ? { ok: true } : { ok: false, mensaje: 'El mensaje es obligatorio' })}
            required
          />
        </div>
      </Modal>
    </div>
  );
}
