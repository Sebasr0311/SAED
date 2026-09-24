import React, { useState } from 'react';
import { useFetch } from '../lib/hooks';
import { api } from '../lib/api';
import { toast } from 'sonner';

export default function IncidentesAdminPage() {
  const { data, loading, refetch } = useFetch(() => api.get('/incidentes/admin'));
  
  // Modals state
  const [modalOpen, setModalOpen] = useState(false);
  const [cierreOpen, setCierreOpen] = useState(false);
  const [investigacionOpen, setInvestigacionOpen] = useState(false);
  const [escalarOpen, setEscalarOpen] = useState(false);
  const [reabrirOpen, setReabrirOpen] = useState(false);
  const [involucradosOpen, setInvolucradosOpen] = useState(false);

  const [selectedIncidente, setSelectedIncidente] = useState(null);
  const [conclusiones, setConclusiones] = useState('');
  const [motivoReapertura, setMotivoReapertura] = useState('');

  // Investigation form
  const [invForm, setInvForm] = useState({
    investigadorAsignado: '',
    lineasInvestigacion: '',
    hallazgos: '',
    concluir: false,
  });

  // Escalation form
  const [escForm, setEscForm] = useState({
    justificacionEscalamiento: '',
    sancionSugerida: '',
  });

  // Involucrados state
  const [involucradosList, setInvolucradosList] = useState([]);
  const [invLoading, setInvLoading] = useState(false);
  const [newInvolucrado, setNewInvolucrado] = useState({
    rolInvolucrado: 'TESTIGO',
    idPersona: '',
    nombreIdentificacionExterna: '',
    declaracionRendida: '',
  });

  // Create incident form
  const [form, setForm] = useState({ 
    titulo: '', 
    tipoIncidente: 'DANO_BIEN_COMUN', 
    nivelSeveridad: 'MODERADA', 
    descripcionHechos: '', 
    requirioAutoridades: 'N', 
    entidadAutoridad: '' 
  });

  const items = data?.items || (Array.isArray(data) ? data : []);

  const handleCreate = async () => {
    try {
      await api.post('/incidentes', { ...form, fechaHoraIncidente: new Date().toISOString() });
      toast.success('Incidente reportado correctamente');
      setModalOpen(false);
      setForm({ 
        titulo: '', 
        tipoIncidente: 'DANO_BIEN_COMUN', 
        nivelSeveridad: 'MODERADA', 
        descripcionHechos: '', 
        requirioAutoridades: 'N', 
        entidadAutoridad: '' 
      });
      refetch();
    } catch (e) {
      toast.error('Error al reportar el incidente: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleCerrar = async () => {
    try {
      await api.post(`/incidentes/${selectedIncidente.idIncidente}/cerrar`, { conclusiones });
      toast.success('Incidente cerrado correctamente');
      setCierreOpen(false);
      setSelectedIncidente(null);
      setConclusiones('');
      refetch();
    } catch (e) {
      toast.error('Error al cerrar: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleReabrir = async () => {
    try {
      await api.post(`/incidentes/${selectedIncidente.idIncidente}/reabrir`, { motivoReapertura });
      toast.success('Incidente reabierto correctamente (en investigación)');
      setReabrirOpen(false);
      setSelectedIncidente(null);
      setMotivoReapertura('');
      refetch();
    } catch (e) {
      toast.error('Error al reabrir: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleInvestigacion = async () => {
    try {
      const endpoint = invForm.concluir 
        ? `/incidentes/${selectedIncidente.idIncidente}/investigacion/concluir`
        : selectedIncidente.estado === 'REPORTADO'
          ? `/incidentes/${selectedIncidente.idIncidente}/investigacion/iniciar`
          : `/incidentes/${selectedIncidente.idIncidente}/investigacion`;

      const method = (!invForm.concluir && selectedIncidente.estado !== 'REPORTADO') ? api.put : api.post;
      await method(endpoint, {
        investigadorAsignado: invForm.investigadorAsignado,
        lineasInvestigacion: invForm.lineasInvestigacion,
        hallazgos: invForm.hallazgos,
      });

      toast.success(invForm.concluir ? 'Investigación concluida' : 'Investigación actualizada');
      setInvestigacionOpen(false);
      setSelectedIncidente(null);
      refetch();
    } catch (e) {
      toast.error('Error en investigación: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleEscalar = async () => {
    try {
      await api.post(`/incidentes/${selectedIncidente.idIncidente}/escalar`, escForm);
      toast.success('Incidente escalado a comité de sanciones exitosamente');
      setEscalarOpen(false);
      setSelectedIncidente(null);
      setEscForm({ justificacionEscalamiento: '', sancionSugerida: '' });
      refetch();
    } catch (e) {
      toast.error('Error al escalar: ' + (e.response?.data?.message || e.message));
    }
  };

  const loadInvolucrados = async (incidente) => {
    setSelectedIncidente(incidente);
    setInvLoading(true);
    setInvolucradosOpen(true);
    try {
      const res = await api.get(`/incidentes/${incidente.idIncidente}/involucrados`);
      setInvolucradosList(res?.items || (Array.isArray(res) ? res : []));
    } catch (e) {
      toast.error('Error cargando involucrados: ' + (e.response?.data?.message || e.message));
      setInvolucradosList([]);
    } finally {
      setInvLoading(false);
    }
  };

  const handleAddInvolucrado = async () => {
    try {
      const payload = {
        rolInvolucrado: newInvolucrado.rolInvolucrado,
        declaracionRendida: newInvolucrado.declaracionRendida || null,
      };
      if (newInvolucrado.idPersona) {
        payload.idPersona = Number(newInvolucrado.idPersona);
      }
      if (newInvolucrado.nombreIdentificacionExterna) {
        payload.nombreIdentificacionExterna = newInvolucrado.nombreIdentificacionExterna;
      }

      await api.post(`/incidentes/${selectedIncidente.idIncidente}/involucrados`, payload);
      toast.success('Involucrado añadido correctamente');
      setNewInvolucrado({
        rolInvolucrado: 'TESTIGO',
        idPersona: '',
        nombreIdentificacionExterna: '',
        declaracionRendida: '',
      });
      loadInvolucrados(selectedIncidente);
    } catch (e) {
      toast.error('Error al añadir involucrado: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleDeleteInvolucrado = async (idInvolucrado) => {
    try {
      await api.delete(`/incidentes/${selectedIncidente.idIncidente}/involucrados/${idInvolucrado}`);
      toast.success('Involucrado removido');
      loadInvolucrados(selectedIncidente);
    } catch (e) {
      toast.error('Error removiendo involucrado: ' + (e.response?.data?.message || e.message));
    }
  };

  const getEstadoBadgeClass = (estado) => {
    switch (estado) {
      case 'CERRADO': return 'badge-success';
      case 'EN_INVESTIGACION': return 'badge-warning';
      case 'ESCALADO_A_SANCION': return 'badge-error';
      case 'ACCION_TOMADA': return 'badge-primary';
      default: return 'badge-info';
    }
  };

  if (loading) return <div className="p-6">Cargando incidentes y bitácora de seguridad...</div>;

  return (
    <div className="page-container p-6">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <div>
          <h2 className="text-2xl font-bold">Gestión de Incidentes y Bitácora</h2>
          <p className="text-sm text-gray-500">Pipeline formal de incidentes, investigación interna, involucrados y escalamiento.</p>
        </div>
        <button className="btn btn-error" onClick={() => setModalOpen(true)}>Reportar Incidente</button>
      </div>

      <div className="overflow-x-auto bg-base-100 rounded-box shadow border border-base-200">
        <table className="table w-full">
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
              <tr key={i.idIncidente} className="hover">
                <td>
                  <span className="font-medium">{new Date(i.fechaHoraIncidente).toLocaleString()}</span>
                  <br/>
                  <small className="text-gray-400">ID: #{i.idIncidente}</small>
                </td>
                <td>
                  <strong>{i.titulo}</strong>
                  <br/>
                  <span className="text-xs text-gray-500">{i.tipoIncidente}</span>
                  {i.investigadorAsignado && (
                    <div className="text-xs text-blue-600 mt-1">
                      Investigador: {i.investigadorAsignado}
                    </div>
                  )}
                </td>
                <td>
                  <span className={`badge ${i.nivelSeveridad === 'CRITICA' ? 'badge-error' : i.nivelSeveridad === 'GRAVE' ? 'badge-warning' : 'badge-info'}`}>
                    {i.nivelSeveridad}
                  </span>
                </td>
                <td>{i.requirioAutoridades === 'S' ? `Sí (${i.entidadAutoridad || 'Asistida'})` : 'No'}</td>
                <td>
                  <span className={`badge ${getEstadoBadgeClass(i.estado)}`}>
                    {i.estado}
                  </span>
                </td>
                <td>
                  <div className="flex flex-wrap gap-1">
                    {/* Involucrados */}
                    <button 
                      className="btn btn-xs btn-outline" 
                      onClick={() => loadInvolucrados(i)}
                      title="Gestionar involucrados"
                    >
                      Involucrados
                    </button>

                    {/* Investigacion */}
                    {i.estado !== 'CERRADO' && (
                      <button 
                        className="btn btn-xs btn-info"
                        onClick={() => {
                          setSelectedIncidente(i);
                          setInvForm({
                            investigadorAsignado: i.investigadorAsignado || '',
                            lineasInvestigacion: i.lineasInvestigacion || '',
                            hallazgos: i.hallazgosInvestigacion || '',
                            concluir: false,
                          });
                          setInvestigacionOpen(true);
                        }}
                      >
                        {i.estado === 'REPORTADO' ? 'Investigar' : 'Actualizar Inv.'}
                      </button>
                    )}

                    {/* Escalar */}
                    {i.estado !== 'CERRADO' && i.estado !== 'ESCALADO_A_SANCION' && (
                      <button 
                        className="btn btn-xs btn-warning"
                        onClick={() => {
                          setSelectedIncidente(i);
                          setEscForm({ justificacionEscalamiento: '', sancionSugerida: '' });
                          setEscalarOpen(true);
                        }}
                      >
                        Escalar
                      </button>
                    )}

                    {/* Cerrar */}
                    {i.estado !== 'CERRADO' && (
                      <button 
                        className="btn btn-xs btn-success" 
                        onClick={() => { setSelectedIncidente(i); setCierreOpen(true); }}
                      >
                        Cerrar
                      </button>
                    )}

                    {/* Reabrir */}
                    {i.estado === 'CERRADO' && (
                      <button 
                        className="btn btn-xs btn-secondary" 
                        onClick={() => { setSelectedIncidente(i); setReabrirOpen(true); }}
                      >
                        Reabrir
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {items.length === 0 && (
              <tr><td colSpan="6" style={{textAlign: 'center', padding: '30px'}}>No hay incidentes reportados</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Modal: Reportar Incidente */}
      {modalOpen && (
        <div className="modal modal-open">
          <div className="modal-box max-w-lg">
            <h3 className="font-bold text-lg text-error">Reportar Nuevo Incidente</h3>
            <div className="py-4 form-control space-y-3">
              <div>
                <label className="label label-text font-semibold">Título Breve</label>
                <input type="text" className="input input-bordered w-full" value={form.titulo} onChange={e => setForm({...form, titulo: e.target.value})} placeholder="Ej: Ruido excesivo Torre 2 Apto 301" />
              </div>
              
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label label-text font-semibold">Tipo</label>
                  <select className="select select-bordered w-full" value={form.tipoIncidente} onChange={e => setForm({...form, tipoIncidente: e.target.value})}>
                    <option value="DANO_BIEN_COMUN">Daño a Bien Común</option>
                    <option value="SEGURIDAD_HURTO">Seguridad (Hurto)</option>
                    <option value="CONVIVENCIA_RUIDO">Convivencia (Ruido)</option>
                    <option value="CONVIVENCIA_DISPUTA">Convivencia (Disputa)</option>
                    <option value="ACCESO_NO_AUTORIZADO">Acceso No Autorizado</option>
                    <option value="ACCIDENTE_PERSONA">Accidente de Persona</option>
                    <option value="FALLA_CRITICA_INFRAESTRUCTURA">Falla Crítica Infraestructura</option>
                    <option value="OTRO">Otro</option>
                  </select>
                </div>
                <div>
                  <label className="label label-text font-semibold">Severidad</label>
                  <select className="select select-bordered w-full" value={form.nivelSeveridad} onChange={e => setForm({...form, nivelSeveridad: e.target.value})}>
                    <option value="LEVE">Leve</option>
                    <option value="MODERADA">Moderada</option>
                    <option value="GRAVE">Grave</option>
                    <option value="CRITICA">Crítica</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="label label-text font-semibold">Descripción de los Hechos</label>
                <textarea className="textarea textarea-bordered h-24 w-full" value={form.descripcionHechos} onChange={e => setForm({...form, descripcionHechos: e.target.value})} placeholder="Detalle lo sucedido con claridad..."></textarea>
              </div>
              
              <label className="flex items-center gap-2 cursor-pointer pt-2">
                <input type="checkbox" className="checkbox checkbox-error" checked={form.requirioAutoridades === 'S'} onChange={e => setForm({...form, requirioAutoridades: e.target.checked ? 'S' : 'N'})} />
                <span className="text-sm">¿Requirió presencia de autoridades? (Policía, Bomberos)</span>
              </label>

              {form.requirioAutoridades === 'S' && (
                <div>
                  <label className="label label-text font-semibold">Entidad que asistió</label>
                  <input type="text" className="input input-bordered w-full" value={form.entidadAutoridad} onChange={e => setForm({...form, entidadAutoridad: e.target.value})} placeholder="Policía Nacional Cuadrante..." />
                </div>
              )}
            </div>
            <div className="modal-action">
              <button className="btn" onClick={() => setModalOpen(false)}>Cancelar</button>
              <button className="btn btn-error" onClick={handleCreate} disabled={!form.titulo || !form.descripcionHechos}>Registrar Incidente</button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Cerrar Incidente */}
      {cierreOpen && selectedIncidente && (
        <div className="modal modal-open">
          <div className="modal-box">
            <h3 className="font-bold text-lg text-success">Cerrar Incidente #{selectedIncidente.idIncidente}</h3>
            <p className="text-sm py-2">Por favor indica las conclusiones o cómo se resolvió el incidente antes de cerrarlo.</p>
            <textarea className="textarea textarea-bordered w-full h-24" placeholder="Conclusiones y acciones finales tomadas..." value={conclusiones} onChange={e => setConclusiones(e.target.value)}></textarea>
            <div className="modal-action">
              <button className="btn" onClick={() => setCierreOpen(false)}>Cancelar</button>
              <button className="btn btn-success" onClick={handleCerrar} disabled={!conclusiones}>Cerrar Definitivamente</button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Reabrir Incidente */}
      {reabrirOpen && selectedIncidente && (
        <div className="modal modal-open">
          <div className="modal-box">
            <h3 className="font-bold text-lg text-secondary">Reabrir Incidente #{selectedIncidente.idIncidente}</h3>
            <p className="text-sm py-2">El incidente volverá al estado <strong>EN_INVESTIGACION</strong>. Indica el motivo de la reapertura:</p>
            <textarea className="textarea textarea-bordered w-full h-24" placeholder="Motivo de la reapertura (nuevas pruebas, apelación, etc.)..." value={motivoReapertura} onChange={e => setMotivoReapertura(e.target.value)}></textarea>
            <div className="modal-action">
              <button className="btn" onClick={() => setReabrirOpen(false)}>Cancelar</button>
              <button className="btn btn-secondary" onClick={handleReabrir} disabled={!motivoReapertura}>Confirmar Reapertura</button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Investigación */}
      {investigacionOpen && selectedIncidente && (
        <div className="modal modal-open">
          <div className="modal-box max-w-lg">
            <h3 className="font-bold text-lg text-info">Gestión de Investigación — #{selectedIncidente.idIncidente}</h3>
            <div className="py-4 space-y-3">
              <div>
                <label className="label label-text font-semibold">Investigador Asignado</label>
                <input type="text" className="input input-bordered w-full" value={invForm.investigadorAsignado} onChange={e => setInvForm({...invForm, investigadorAsignado: e.target.value})} placeholder="Nombre del oficial o administrador..." />
              </div>
              <div>
                <label className="label label-text font-semibold">Líneas de Investigación</label>
                <textarea className="textarea textarea-bordered w-full h-20" value={invForm.lineasInvestigacion} onChange={e => setInvForm({...invForm, lineasInvestigacion: e.target.value})} placeholder="Revisión de cámaras CCTV, entrevistas..."></textarea>
              </div>
              <div>
                <label className="label label-text font-semibold">Hallazgos de la Investigación</label>
                <textarea className="textarea textarea-bordered w-full h-24" value={invForm.hallazgos} onChange={e => setInvForm({...invForm, hallazgos: e.target.value})} placeholder="Hallazgos preliminares o definitivos..."></textarea>
              </div>
              <label className="flex items-center gap-2 cursor-pointer pt-2">
                <input type="checkbox" className="checkbox checkbox-success" checked={invForm.concluir} onChange={e => setInvForm({...invForm, concluir: e.target.checked})} />
                <span className="text-sm font-semibold">Concluir formalmente la etapa de investigación</span>
              </label>
            </div>
            <div className="modal-action">
              <button className="btn" onClick={() => setInvestigacionOpen(false)}>Cancelar</button>
              <button className="btn btn-info" onClick={handleInvestigacion}>Guardar Investigación</button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Escalamiento */}
      {escalarOpen && selectedIncidente && (
        <div className="modal modal-open">
          <div className="modal-box">
            <h3 className="font-bold text-lg text-warning">Escalar Incidente #{selectedIncidente.idIncidente} a Sanción</h3>
            <p className="text-sm py-2">Transfiere el caso a la Junta Administradora o Comité de Convivencia para trámite de sanción formal.</p>
            <div className="py-2 space-y-3">
              <div>
                <label className="label label-text font-semibold">Justificación del Escalamiento</label>
                <textarea className="textarea textarea-bordered w-full h-24" value={escForm.justificacionEscalamiento} onChange={e => setEscForm({...escForm, justificacionEscalamiento: e.target.value})} placeholder="Motivo por el cual amerita apertura de sanción..."></textarea>
              </div>
              <div>
                <label className="label label-text font-semibold">Sanción Sugerida (Opcional)</label>
                <input type="text" className="input input-bordered w-full" value={escForm.sancionSugerida} onChange={e => setEscForm({...escForm, sancionSugerida: e.target.value})} placeholder="Multa económica tipo 1, llamado de atención escrito..." />
              </div>
            </div>
            <div className="modal-action">
              <button className="btn" onClick={() => setEscalarOpen(false)}>Cancelar</button>
              <button className="btn btn-warning" onClick={handleEscalar} disabled={!escForm.justificacionEscalamiento}>Escalar a Sanción</button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Involucrados */}
      {involucradosOpen && selectedIncidente && (
        <div className="modal modal-open">
          <div className="modal-box max-w-2xl">
            <h3 className="font-bold text-lg">Involucrados en Incidente #{selectedIncidente.idIncidente}</h3>
            <p className="text-sm text-gray-500 mb-4">{selectedIncidente.titulo}</p>

            {/* List of current involucrados */}
            <div className="mb-6">
              <h4 className="font-semibold text-sm mb-2">Personas Vinculadas al Caso</h4>
              {invLoading ? (
                <div className="py-4 text-center text-sm">Cargando involucrados...</div>
              ) : involucradosList.length === 0 ? (
                <div className="py-4 text-center text-sm text-gray-400 bg-base-200 rounded">No hay involucrados registrados en este caso.</div>
              ) : (
                <div className="space-y-2 max-h-48 overflow-y-auto">
                  {involucradosList.map((inv) => (
                    <div key={inv.idInvolucrado} className="p-3 border rounded-lg flex justify-between items-start bg-base-100">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-sm">
                            {inv.nombreCompleto || inv.nombreIdentificacionExterna || 'Anónimo'}
                          </span>
                          <span className="badge badge-sm badge-outline">{inv.rolInvolucrado}</span>
                          {inv.idPersona ? (
                            <span className="badge badge-sm badge-info">Residente</span>
                          ) : (
                            <span className="badge badge-sm badge-ghost">Externo</span>
                          )}
                        </div>
                        {inv.declaracionRendida && (
                          <p className="text-xs text-gray-600 mt-1 italic">"{inv.declaracionRendida}"</p>
                        )}
                      </div>
                      <button 
                        className="btn btn-ghost btn-xs text-error"
                        onClick={() => handleDeleteInvolucrado(inv.idInvolucrado)}
                      >
                        Eliminar
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Add Involucrado Form */}
            <div className="border-t pt-4">
              <h4 className="font-semibold text-sm mb-3">Vincular Nueva Persona</h4>
              <div className="grid grid-cols-2 gap-3 mb-3">
                <div>
                  <label className="label label-text text-xs font-medium">Rol en el Incidente</label>
                  <select 
                    className="select select-bordered select-sm w-full"
                    value={newInvolucrado.rolInvolucrado}
                    onChange={e => setNewInvolucrado({...newInvolucrado, rolInvolucrado: e.target.value})}
                  >
                    <option value="AFECTADO">Afectado / Víctima</option>
                    <option value="TESTIGO">Testigo</option>
                    <option value="PRESUNTO_RESPONSABLE">Presunto Responsable</option>
                    <option value="INTERVINIENTE">Interviniente</option>
                    <option value="OTRO">Otro</option>
                  </select>
                </div>
                <div>
                  <label className="label label-text text-xs font-medium">ID Persona (Si es Residente)</label>
                  <input 
                    type="number" 
                    className="input input-bordered input-sm w-full" 
                    placeholder="ID Persona..."
                    value={newInvolucrado.idPersona}
                    onChange={e => setNewInvolucrado({...newInvolucrado, idPersona: e.target.value})}
                  />
                </div>
              </div>

              <div className="mb-3">
                <label className="label label-text text-xs font-medium">Nombre / Identificación Externa (Si no está en el sistema)</label>
                <input 
                  type="text" 
                  className="input input-bordered input-sm w-full" 
                  placeholder="Ej: Juan Pérez (Visitante CC 10203040)"
                  value={newInvolucrado.nombreIdentificacionExterna}
                  onChange={e => setNewInvolucrado({...newInvolucrado, nombreIdentificacionExterna: e.target.value})}
                />
              </div>

              <div className="mb-3">
                <label className="label label-text text-xs font-medium">Declaración o Testimonio (Opcional)</label>
                <textarea 
                  className="textarea textarea-bordered textarea-sm w-full h-16" 
                  placeholder="Manifestación o declaración rendida por la persona..."
                  value={newInvolucrado.declaracionRendida}
                  onChange={e => setNewInvolucrado({...newInvolucrado, declaracionRendida: e.target.value})}
                ></textarea>
              </div>

              <button 
                className="btn btn-sm btn-primary w-full"
                onClick={handleAddInvolucrado}
                disabled={!newInvolucrado.idPersona && !newInvolucrado.nombreIdentificacionExterna}
              >
                Añadir Involucrado
              </button>
            </div>

            <div className="modal-action">
              <button className="btn" onClick={() => setInvolucradosOpen(false)}>Cerrar</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
