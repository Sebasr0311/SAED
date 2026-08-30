import React, { useState } from 'react';
import { useFetch } from '../lib/hooks';
import { api } from '../lib/api';
import { toast } from 'sonner';

export default function ResIncidentesPage() {
  const { data, loading, refetch } = useFetch(() => api.get('/incidentes/mis-incidentes'));
  const [modalOpen, setModalOpen] = useState(false);
  
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
      // NOTE: backend forcefully sets idUnidad and nullifies idPorteria/idZonaComun for residents
      await api.post('/incidentes', form);
      toast.success('Incidente reportado a la administración');
      setModalOpen(false);
      refetch();
    } catch (e) {
      toast.error('Error al reportar: ' + e.message);
    }
  };

  if (loading) return <div>Cargando incidentes...</div>;

  return (
    <div className="page-container">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2>Mis Reportes e Incidentes</h2>
        <button className="btn btn-error" onClick={() => setModalOpen(true)}>Reportar Incidente</button>
      </div>

      <div className="grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '20px' }}>
        {items.map(i => (
          <div key={i.idIncidente} className="card bg-base-100 shadow-xl border">
            <div className="card-body">
              <h2 className="card-title text-error">
                {i.titulo}
              </h2>
              <span className={`badge ${i.estado === 'CERRADO' ? 'badge-success' : 'badge-outline'}`}>{i.estado}</span>
              <p><strong>Tipo:</strong> {i.tipoIncidente}</p>
              <p className="text-sm mt-2">{i.descripcionHechos}</p>
              {i.estado === 'CERRADO' && (
                <div className="bg-gray-100 p-2 rounded mt-2 text-sm">
                  <strong>Conclusiones:</strong> {i.conclusionesCierre}
                </div>
              )}
            </div>
          </div>
        ))}
        {items.length === 0 && (
          <p style={{ gridColumn: '1 / -1', textAlign: 'center' }}>No has reportado incidentes.</p>
        )}
      </div>

      {modalOpen && (
        <div className="modal modal-open">
          <div className="modal-box">
            <h3 className="font-bold text-lg text-error">Reportar Incidente en Mi Unidad</h3>
            <p className="py-2 text-sm text-gray-500">Usa este formulario para reportar daños, accidentes o problemas de seguridad que ocurran dentro de tu apartamento.</p>
            <div className="py-2 form-control">
              <label>Título Breve</label>
              <input type="text" className="input input-bordered" value={form.titulo} onChange={e => setForm({...form, titulo: e.target.value})} />
              
              <label className="mt-2">Tipo de Problema</label>
              <select className="select select-bordered w-full" value={form.tipoIncidente} onChange={e => setForm({...form, tipoIncidente: e.target.value})}>
                <option value="DAÑO_INFRAESTRUCTURA">Daño de Infraestructura</option>
                <option value="SEGURIDAD">Vulneración de Seguridad</option>
                <option value="CONVIVENCIA">Problema de Convivencia</option>
                <option value="ACCIDENTE">Accidente</option>
              </select>

              <label className="mt-2">Descripción de los Hechos</label>
              <textarea className="textarea textarea-bordered h-24" value={form.descripcionHechos} onChange={e => setForm({...form, descripcionHechos: e.target.value})}></textarea>
              
              <label className="mt-2 flex items-center gap-2 cursor-pointer">
                <input type="checkbox" className="checkbox" checked={form.requirioAutoridades === 'S'} onChange={e => setForm({...form, requirioAutoridades: e.target.checked ? 'S' : 'N'})} />
                <span>¿Requirió presencia de Policía o Bomberos?</span>
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
              <button className="btn btn-error" onClick={handleCreate}>Enviar Reporte</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
