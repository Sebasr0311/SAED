import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Textarea, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate } from '../lib/utils.js';

export default function AvisosPage() {
  const [page] = useState(0);
  const [toast, setToast] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({ titulo: '', cuerpo: '', idApartamento: '' });
  const [sending, setSending] = useState(false);

  const { data, loading, refetch } = useFetch(() => api.get(`/avisos?page=${page}&size=20`), [page]);
  const { data: apartamentos } = useFetch(() => api.get('/apartamentos?size=200'), []);

  const columns = [
    { key: 'idAviso', label: 'ID', width: 60 },
    { key: 'titulo', label: 'Título' },
    { key: 'cuerpo', label: 'Mensaje' },
    { key: 'fecha', label: 'Fecha', render: (r) => formatDate(r.fecha) },
  ];

  async function send() {
    if (!form.titulo.trim() || !form.cuerpo.trim()) {
      setToast({ message: 'Título y mensaje son obligatorios', type: 'error' });
      return;
    }
    setSending(true);
    try {
      await api.post('/avisos', {
        titulo: form.titulo,
        cuerpo: form.cuerpo,
        idApartamento: form.idApartamento || null,
      });
      setToast({ message: 'Aviso enviado', type: 'success' });
      setForm({ titulo: '', cuerpo: '', idApartamento: '' });
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
        action={
          <Button onClick={() => setModalOpen(true)}>+ Nuevo Aviso</Button>
        }
      />
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No hay avisos enviados"
        keyField="idAviso"
      />
      <Pagination
        page={page}
        totalPages={data?.totalPages || 1}
        totalItems={data?.totalItems}
        pageSize={20}
        onPageChange={() => {}}
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
            <Button onClick={send} disabled={sending}>{sending ? 'Enviando...' : 'Enviar'}</Button>
          </>
        }
      >
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
        <div className="form-group">
          <Select
            id="idApartamento"
            label="Apartamento (opcional, vacío = todos)"
            value={form.idApartamento}
            onChange={(e) => setForm((f) => ({ ...f, idApartamento: e.target.value }))}
          >
            <option value="">— Todos los apartamentos —</option>
            {(apartamentos?.items || []).map((a) => (
              <option key={a.idApartamento} value={a.idApartamento}>
                Apto {a.numero}
              </option>
            ))}
          </Select>
        </div>
      </Modal>
      <Toast toast={toast} />
    </div>
  );
}
