import React, { useState } from 'react';
import { useFetch } from '../lib/hooks';
import { api } from '../lib/api';
import { toast } from 'sonner';

export default function IncidentesAdminPage() {
  const { data, loading, refetch } = useFetch(() => api.get('/incidentes/admin'));
  const [modalOpen, setModalOpen] = useState(false);
  const [cierreOpen, setCierreOpen] = useState(false);
  const [selectedIncidente, setSelectedIncidente] = useState(null);
  const [conclusiones, setConclusiones] = useState('');
  
  const [form, setForm] = useState({ 
    titulo: '', 
    tipoIncidente: 'DAÑO_INFRAESTRUCTURA', 
    nivelSeveridad: 'MODERADA', 
    descripcionHechos: '', 
    requirioAutoridades: 'N', 
    entidadAutoridad: '' 
  });

  const items = data?.items || (Array.isArray(data) ? data : []);

  const handleCreate = async () => {
    try {
      await api.post('/incidentes', form);
      toast.success('Incidente reportado correctamente');
      setModalOpen(false);
      refetch();
    } catch (e) {
      toast.error('Error al reportar el incidente: ' + e.message);
    }
  };

  const handleCerrar = async () => {
    try {
      await api.post(`/api/v1/incidentes/${selectedIncidente.idIncidente}/cerrar`, { conclusiones });
      toast.success('Incidente cerrado correctamente');
      setCierreOpen(false);
      setSelectedIncidente(null);
      setConclusiones('');
      refetch();
    } catch (e) {
      toast.error('Error al cerrar: ' + e.message);
    }
  };

  if (loading) return <div>Cargando incidentes...</div>;

  return (
    <div className="page-container">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2>Gestión de Incidentes y Bitácora</h2>
        <button className="btn btn-error" onClick={() => setModalOpen(true)}>Reportar Incidente</button>
      </div>

      <table className="table">
        <thead>
          <tr>
            <th>Fecha / ID</th>
            <th>Título y Tipo</th>
            <th>Nivel</th>
            <th>Autoridades</th>
            <th>Estado</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          {items.map(i => (
            <tr key={i.idIncidente}>
              <td>{new Date(i.fechaHoraIncidente).toLocaleString()}<br/><small>ID: {i.idIncidente}</small></td>
              <td><strong>{i.titulo}</strong><br/>{i.tipoIncidente}</td>
              <td>
                <span className={`badge ${i.nivelSeveridad === 'CRITICA' ? 'badge-error' : i.nivelSeveridad === 'ALTA' ? 'badge-warning' : 'badge-info'}`}>
                  {i.nivelSeveridad}
                </span>
              </td>
              <td>{i.requirioAutoridades === 'S' ? `Sí (${i.entidadAutoridad})` : 'No'}</td>
              <td><span className={`badge badge-outline ${i.estado === 'CERRADO' ? 'badge-success' : ''}`}>{i.estado}</span></td>
              <td>
                {i.estado === 'REPORTADO' && (
                  <button className="btn btn-sm btn-success" onClick={() => { setSelectedIncidente(i); setCierreOpen(true); }}>Cerrar Caso</button>
                )}
              </td>
            </tr>
          ))}
          {items.length === 0 && (
            <tr><td colSpan="6" style={{textAlign: 'center'}}>No hay incidentes reportados</td></tr>
          )}
        </tbody>
      </table>

      {modalOpen && (
        <div className="modal modal-open">
          <div className="modal-box">
            <h3 className="font-bold text-lg text-error">Reportar Nuevo Incidente</h3>
            <div className="py-4 form-control">
              <label>Título Breve</label>
              <input type="text" className="input input-bordered" value={form.titulo} onChange={e => setForm({...form, titulo: e.target.value})} />
              
              <div style={{ display: 'flex', gap: '10px', marginTop: '10px' }}>
                <div style={{ flex: 1 }}>
                  <label>Tipo</label>
                  <select className="select select-bordered w-full" value={form.tipoIncidente} onChange={e => setForm({...form, tipoIncidente: e.target.value})}>
                    <option value="DAÑO_INFRAESTRUCTURA">Daño de Infraestructura</option>
                    <option value="SEGURIDAD">Vulneración de Seguridad</option>
                    <option value="CONVIVENCIA">Problema de Convivencia</option>
                    <option value="ACCIDENTE">Accidente</option>
                  </select>
                </div>
                <div style={{ flex: 1 }}>
                  <label>Severidad</label>
                  <select className="select select-bordered w-full" value={form.nivelSeveridad} onChange={e => setForm({...form, nivelSeveridad: e.target.value})}>
                    <option value="BAJA">Baja</option>
                    <option value="MODERADA">Moderada</option>
                    <option value="ALTA">Alta</option>
                    <option value="CRITICA">Crítica</option>
                  </select>
                </div>
              </div>

              <label className="mt-2">Descripción de los Hechos</label>
              <textarea className="textarea textarea-bordered h-24" value={form.descripcionHechos} onChange={e => setForm({...form, descripcionHechos: e.target.value})}></textarea>
              
              <label className="mt-2 flex items-center gap-2 cursor-pointer">
                <input type="checkbox" className="checkbox" checked={form.requirioAutoridades === 'S'} onChange={e => setForm({...form, requirioAutoridades: e.target.checked ? 'S' : 'N'})} />
                <span>¿Requirió presencia de autoridades? (Policía, Bomberos, Ambulancia)</span>
              </label>

              {form.requirioAutoridades === 'S' && (
                <>
                  <label className="mt-2">Entidad que asistió</label>
                  <input type="text" className="input input-bordered" value={form.entidadAutoridad} onChange={e => setForm({...form, entidadAutoridad: e.target.value})} />
                </>
              )}
            </div>
            <div className="modal-action">
              <button className="btn" onClick={() => setModalOpen(false)}>Cancelar</button>
              <button className="btn btn-error" onClick={handleCreate}>Registrar Incidente</button>
            </div>
          </div>
        </div>
      )}

      {cierreOpen && selectedIncidente && (
        <div className="modal modal-open">
          <div className="modal-box">
            <h3 className="font-bold text-lg">Cerrar Incidente #{selectedIncidente.idIncidente}</h3>
            <p className="text-sm">Por favor indica las conclusiones o cómo se resolvió el incidente antes de cerrarlo.</p>
            <textarea className="textarea textarea-bordered w-full h-24 mt-4" placeholder="Conclusiones..." value={conclusiones} onChange={e => setConclusiones(e.target.value)}></textarea>
            <div className="modal-action">
              <button className="btn" onClick={() => setCierreOpen(false)}>Cancelar</button>
              <button className="btn btn-success" onClick={handleCerrar} disabled={!conclusiones}>Cerrar Definitivamente</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
