import React, { useState, useEffect } from 'react';
import { api } from '../lib/api.js';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { toast } from 'sonner';

export default function CoarrendatariosPage() {
  const [contratos, setContratos] = useState([]);
  const [selectedContrato, setSelectedContrato] = useState(null);
  const [coarrendatarios, setCoarrendatarios] = useState([]);
  const [loading, setLoading] = useState(true);
  const [dialog, setDialog] = useState(false);
  const [form, setForm] = useState({ idPersona: '', tipoVinculo: 'COARRENDATARIO', esResponsablePago: 'N' });
  const [deleteTarget, setDeleteTarget] = useState(null);

  const cargarContratos = async () => {
    try {
      setLoading(true);
      const res = await api.get('/contratos');
      setContratos(res.data || []);
    } catch (e) {
      toast.error('No se pudieron cargar los contratos');
    } finally {
      setLoading(false);
    }
  };

  const cargarCoarrendatarios = async (idContrato) => {
    try {
      const res = await api.get(`/contratos-admin/coarrendatarios/${idContrato}`);
      setCoarrendatarios(res.data || []);
    } catch (e) {
      toast.error('No se pudieron cargar los coarrendatarios');
    }
  };

  useEffect(() => { cargarContratos(); }, []);

  useEffect(() => {
    if (selectedContrato) cargarCoarrendatarios(selectedContrato);
  }, [selectedContrato]);

  const crear = async () => {
    if (!form.idPersona) {
      toast.error('Seleccione una persona');
      return;
    }
    try {
      await api.post('/contratos-admin/coarrendatarios', {
        idContrato: selectedContrato,
        idPersona: Number(form.idPersona),
        tipoVinculo: form.tipoVinculo,
        esResponsablePago: form.esResponsablePago
      });
      toast.success('Coarrendatario agregado');
      setDialog(false);
      setForm({ idPersona: '', tipoVinculo: 'COARRENDATARIO', esResponsablePago: 'N' });
      cargarCoarrendatarios(selectedContrato);
    } catch (e) {
      toast.error('Error: ' + (e.message || 'Error desconocido'));
    }
  };

  const eliminar = async () => {
    if (!deleteTarget) return;
    try {
      await api.delete(`/contratos-admin/coarrendatarios/${deleteTarget.idContratoResidente}`);
      toast.success('Coarrendatario eliminado');
      setDeleteTarget(null);
      cargarCoarrendatarios(selectedContrato);
    } catch (e) {
      toast.error('Error al eliminar');
    }
  };

  const toggleEstado = async (c) => {
    const nuevo = c.estado === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO';
    try {
      await api.patch(`/contratos-admin/coarrendatarios/${c.idContratoResidente}/estado`, { estado: nuevo });
      toast.success(`Estado cambiado a ${nuevo}`);
      cargarCoarrendatarios(selectedContrato);
    } catch (e) {
      toast.error('Error');
    }
  };

  if (loading) return <div className="p-8 text-center">Cargando...</div>;

  return (
    <div className="max-w-6xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">Coarrendatarios</h1>

      {/* Selector de contrato */}
      <div className="mb-6">
        <label className="block text-sm font-medium mb-2">Seleccionar Contrato</label>
        <select
          value={selectedContrato || ''}
          onChange={(e) => setSelectedContrato(e.target.value ? Number(e.target.value) : null)}
          className="w-full md:w-96 border rounded-lg px-3 py-2"
        >
          <option value="">-- Seleccionar contrato --</option>
          {contratos.map((c) => (
            <option key={c.idContrato} value={c.idContrato}>
              {c.numeroContrato} - {c.nombreArrendatario} ({c.estado})
            </option>
          ))}
        </select>
      </div>

      {selectedContrato && (
        <>
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-lg font-semibold">Coarrendatarios del contrato</h2>
            <button onClick={() => setDialog(true)} className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700">
              + Agregar Coarrendatario
            </button>
          </div>

          {coarrendatarios.length === 0 ? (
            <div className="bg-card border border-border rounded-lg shadow-sm p-8 text-center text-muted-foreground">
              No hay coarrendatarios en este contrato
            </div>
          ) : (
            <div className="bg-card border border-border rounded-lg shadow-sm overflow-hidden">
              <table className="w-full">
                <thead className="bg-muted/50 border-b border-border">
                  <tr>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">ID Persona</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Vínculo</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Responsable Pago</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Estado</th>
                    <th className="px-4 py-3 text-right text-sm font-medium text-muted-foreground">Acciones</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {coarrendatarios.map((c) => (
                    <tr key={c.idContratoResidente} className="hover:bg-muted/50 transition-colors">
                      <td className="px-4 py-3 text-sm text-foreground">{c.idPersona}</td>
                      <td className="px-4 py-3 text-sm text-foreground">{c.tipoVinculo}</td>
                      <td className="px-4 py-3 text-sm text-foreground">{c.esResponsablePago === 'S' ? 'Sí' : 'No'}</td>
                      <td className="px-4 py-3 text-sm">
                        <span className={`px-2 py-1 rounded text-xs font-medium ${
                          c.estado === 'ACTIVO' ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20' : 'bg-destructive/10 text-destructive border border-destructive/20'
                        }`}>{c.estado}</span>
                      </td>
                      <td className="px-4 py-3 text-sm text-right space-x-2">
                        <button onClick={() => toggleEstado(c)} className="text-amber-500 hover:underline">
                          {c.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'}
                        </button>
                        <button onClick={() => setDeleteTarget(c)} className="text-destructive hover:underline">Eliminar</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {/* Dialog Crear */}
      {dialog && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="bg-card border border-border rounded-xl p-6 w-full max-w-md shadow-xl text-foreground">
            <h2 className="text-lg font-semibold mb-4 text-foreground">Agregar Coarrendatario</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1 text-foreground">ID Persona *</label>
                <input type="number" value={form.idPersona} onChange={(e) => setForm({ ...form, idPersona: e.target.value })}
                  className="w-full border border-border bg-background text-foreground rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary" placeholder="ID de la persona" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Tipo de Vínculo</label>
                <select value={form.tipoVinculo} onChange={(e) => setForm({ ...form, tipoVinculo: e.target.value })}
                  className="w-full border rounded-lg px-3 py-2">
                  <option value="COARRENDATARIO">Coarrendatario</option>
                  <option value="HABITANTE">Habitante</option>
                  <option value="OTRO">Otro</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Responsable de Pago</label>
                <select value={form.esResponsablePago} onChange={(e) => setForm({ ...form, esResponsablePago: e.target.value })}
                  className="w-full border rounded-lg px-3 py-2">
                  <option value="N">No</option>
                  <option value="S">Sí</option>
                </select>
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-6">
              <button onClick={() => setDialog(false)} className="px-4 py-2 border rounded-lg hover:bg-gray-50">Cancelar</button>
              <button onClick={crear} className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">Agregar</button>
            </div>
          </div>
        </div>
      )}

      <ConfirmDialog open={!!deleteTarget} title="Eliminar coarrendatario"
        message="¿Eliminar este coarrendatario del contrato?" onConfirm={eliminar} onCancel={() => setDeleteTarget(null)} />
    </div>
  );
}
