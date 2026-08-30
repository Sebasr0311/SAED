import React, { useState } from 'react';
import { useFetch } from '../lib/hooks';
import { api } from '../lib/api';
import { toast } from 'sonner';

export default function ResObrasPage() {
  const { data, loading, refetch } = useFetch(() => api.get('/api/v1/obras/mis-obras'));
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({ descripcion: '', fechaInicio: '', fechaFinEstimada: '', responsableObra: '', telefonoResponsable: '', depositoGarantia: 0 });

  const items = data?.items || (Array.isArray(data) ? data : []);

  const handleCreate = async () => {
    try {
      // NOTE: We don't send idUnidad here; the secured backend automatically injects it from the context
      await api.post('/api/v1/obras', form);
      toast.success('Solicitud de obra enviada correctamente');
      setModalOpen(false);
      refetch();
    } catch (e) {
      toast.error('Error al solicitar la obra: ' + e.message);
    }
  };

  if (loading) return <div>Cargando mis obras...</div>;

  return (
    <div className="page-container">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2>Mis Obras y Remodelaciones</h2>
        <button className="btn btn-primary" onClick={() => setModalOpen(true)}>Solicitar Permiso</button>
      </div>

      <div className="grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '20px' }}>
        {items.map(o => (
          <div key={o.idObra} className="card bg-base-100 shadow-xl border">
            <div className="card-body">
              <h2 className="card-title">
                {o.estado === 'APROBADA' ? '✅' : o.estado === 'FINALIZADA' ? '🏁' : o.estado === 'RECHAZADA' ? '❌' : '⏳'}
                {o.estado}
              </h2>
              <p><strong>Descripción:</strong> {o.descripcion}</p>
              <p><strong>Fechas:</strong> {o.fechaInicio} al {o.fechaFinEstimada}</p>
              <p><strong>Contratista:</strong> {o.responsableObra}</p>
            </div>
          </div>
        ))}
        {items.length === 0 && (
          <p style={{ gridColumn: '1 / -1', textAlign: 'center' }}>No tienes obras registradas.</p>
        )}
      </div>

      {modalOpen && (
        <div className="modal modal-open">
          <div className="modal-box">
            <h3 className="font-bold text-lg">Solicitar Permiso de Obra</h3>
            <p className="py-2 text-sm text-gray-500">Completa los datos de la remodelación para ser aprobada por la administración.</p>
            <div className="py-2 form-control">
              <label className="mt-2">Descripción</label>
              <textarea className="textarea textarea-bordered" value={form.descripcion} onChange={e => setForm({...form, descripcion: e.target.value})}></textarea>
              
              <div style={{ display: 'flex', gap: '10px', marginTop: '10px' }}>
                <div style={{ flex: 1 }}>
                  <label>Fecha Inicio</label>
                  <input type="date" className="input input-bordered w-full" value={form.fechaInicio} onChange={e => setForm({...form, fechaInicio: e.target.value})} />
                </div>
                <div style={{ flex: 1 }}>
                  <label>Fin Estimada</label>
                  <input type="date" className="input input-bordered w-full" value={form.fechaFinEstimada} onChange={e => setForm({...form, fechaFinEstimada: e.target.value})} />
                </div>
              </div>

              <label className="mt-2">Contratista / Responsable</label>
              <input type="text" className="input input-bordered" value={form.responsableObra} onChange={e => setForm({...form, responsableObra: e.target.value})} />
              
              <label className="mt-2">Teléfono Contratista</label>
              <input type="text" className="input input-bordered" value={form.telefonoResponsable} onChange={e => setForm({...form, telefonoResponsable: e.target.value})} />
            </div>
            <div className="modal-action">
              <button className="btn" onClick={() => setModalOpen(false)}>Cancelar</button>
              <button className="btn btn-primary" onClick={handleCreate}>Enviar Solicitud</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
