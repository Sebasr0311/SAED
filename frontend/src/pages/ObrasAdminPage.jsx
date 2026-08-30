import React, { useState } from 'react';
import { useFetch } from '../lib/hooks';
import { api } from '../lib/api';
import { toast } from 'sonner';

export default function ObrasAdminPage() {
  const { data, loading, refetch } = useFetch(() => api.get('/obras/admin'));
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({ idUnidad: '', descripcion: '', fechaInicio: '', fechaFinEstimada: '', responsableObra: '', telefonoResponsable: '', depositoGarantia: 0 });

  const items = data?.items || (Array.isArray(data) ? data : []);

  const handleCreate = async () => {
    try {
      await api.post('/obras', form);
      toast.success('Obra registrada correctamente');
      setModalOpen(false);
      refetch();
    } catch (e) {
      toast.error('Error al crear la obra: ' + e.message);
    }
  };

  const cambiarEstado = async (id, estadoStr) => {
    try {
      let url = '';
      if (estadoStr === 'APROBADA') url = `/api/v1/obras/${id}/aprobar`;
      if (estadoStr === 'RECHAZADA') url = `/api/v1/obras/${id}/rechazar`;
      if (estadoStr === 'FINALIZADA') url = `/api/v1/obras/${id}/finalizar`;
      
      await api.post(url);
      toast.success('Estado actualizado correctamente');
      refetch();
    } catch (e) {
      toast.error('Error al actualizar: ' + e.message);
    }
  };

  if (loading) return <div>Cargando obras...</div>;

  return (
    <div className="page-container">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2>Gestión de Obras y Remodelaciones</h2>
        <button className="btn btn-primary" onClick={() => setModalOpen(true)}>Registrar Obra</button>
      </div>

      <table className="table">
        <thead>
          <tr>
            <th>Unidad (ID)</th>
            <th>Descripción</th>
            <th>Fechas</th>
            <th>Contratista</th>
            <th>Estado</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          {items.map(o => (
            <tr key={o.idObra}>
              <td>{o.idUnidad}</td>
              <td>{o.descripcion}</td>
              <td>{o.fechaInicio} a {o.fechaFinEstimada}</td>
              <td>{o.responsableObra}<br/><small>{o.telefonoResponsable}</small></td>
              <td><span className={`badge badge-outline ${o.estado === 'APROBADA' ? 'badge-success' : o.estado === 'FINALIZADA' ? 'badge-info' : ''}`}>{o.estado}</span></td>
              <td>
                <div style={{ display: 'flex', gap: '8px' }}>
                  {o.estado === 'SOLICITADA' && (
                    <>
                      <button className="btn btn-sm btn-success" onClick={() => cambiarEstado(o.idObra, 'APROBADA')}>Aprobar</button>
                      <button className="btn btn-sm btn-error" onClick={() => cambiarEstado(o.idObra, 'RECHAZADA')}>Rechazar</button>
                    </>
                  )}
                  {o.estado === 'APROBADA' && (
                    <button className="btn btn-sm btn-info" onClick={() => cambiarEstado(o.idObra, 'FINALIZADA')}>Finalizar</button>
                  )}
                </div>
              </td>
            </tr>
          ))}
          {items.length === 0 && (
            <tr><td colSpan="6" style={{textAlign: 'center'}}>No hay obras registradas</td></tr>
          )}
        </tbody>
      </table>

      {modalOpen && (
        <div className="modal modal-open">
          <div className="modal-box">
            <h3 className="font-bold text-lg">Registrar Obra</h3>
            <div className="py-4 form-control">
              <label>ID Unidad</label>
              <input type="number" className="input input-bordered" value={form.idUnidad} onChange={e => setForm({...form, idUnidad: parseInt(e.target.value)})} />
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
              <button className="btn btn-primary" onClick={handleCreate}>Guardar</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
