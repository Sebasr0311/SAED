import React, { useState, useEffect } from 'react';
import { api } from '../lib/api.js';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { toast } from 'sonner';

export default function CoarrendatariosPage() {
  const [contratos, setContratos] = useState([]);
  const [personas, setPersonas] = useState([]);
  const [selectedContrato, setSelectedContrato] = useState(null);
  const [coarrendatarios, setCoarrendatarios] = useState([]);
  const [loading, setLoading] = useState(true);
  const [dialog, setDialog] = useState(false);
  const [form, setForm] = useState({
    idPersona: '',
    tipoVinculo: 'COARRENDATARIO',
    esResponsablePago: 'N',
    sincronizarHabitabilidad: false,
  });
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

  const cargarPersonas = async () => {
    try {
      const res = await api.get('/personas?page=0&size=200');
      const items = res?.data?.items || res?.items || (Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : []);
      setPersonas(items);
    } catch (e) {
      console.warn('No se pudo cargar censo de personas', e);
    }
  };

  const cargarCoarrendatarios = async (idContrato) => {
    try {
      const res = await api.get(`/contratos-admin/coarrendatarios/${idContrato}`);
      const raw = res?.data?.data || res?.data || res || [];
      setCoarrendatarios(Array.isArray(raw) ? raw : []);
    } catch (e) {
      toast.error('No se pudieron cargar los coarrendatarios');
    }
  };

  useEffect(() => {
    cargarContratos();
    cargarPersonas();
  }, []);

  useEffect(() => {
    if (selectedContrato) cargarCoarrendatarios(selectedContrato);
    else setCoarrendatarios([]);
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
        esResponsablePago: form.esResponsablePago,
        sincronizarHabitabilidad: form.sincronizarHabitabilidad,
      });
      toast.success('Coarrendatario agregado exitosamente');
      setDialog(false);
      setForm({ idPersona: '', tipoVinculo: 'COARRENDATARIO', esResponsablePago: 'N', sincronizarHabitabilidad: false });
      cargarCoarrendatarios(selectedContrato);
    } catch (e) {
      toast.error('Error: ' + (e.response?.data?.message || e.message || 'Error desconocido'));
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
      toast.error('Error al eliminar: ' + (e.response?.data?.message || e.message));
    }
  };

  const toggleEstado = async (c) => {
    const nuevo = c.estado === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO';
    try {
      await api.patch(`/contratos-admin/coarrendatarios/${c.idContratoResidente}/estado`, { estado: nuevo });
      toast.success(`Estado cambiado a ${nuevo}`);
      cargarCoarrendatarios(selectedContrato);
    } catch (e) {
      toast.error('Error al cambiar estado');
    }
  };

  if (loading) return <div className="p-8 text-center text-muted-foreground">Cargando contratos...</div>;

  return (
    <div className="max-w-6xl mx-auto p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold tracking-tight text-foreground">Coarrendatarios y Ocupantes</h1>
        <p className="text-sm text-muted-foreground">
          Gestión de vínculos contractuales, garantes y ocupantes autorizados en contratos de arrendamiento.
        </p>
      </div>

      {/* Selector de contrato */}
      <div className="mb-6 bg-card border border-border p-4 rounded-xl shadow-sm">
        <label className="block text-sm font-medium mb-2 text-foreground">Seleccionar Contrato de Arriendo</label>
        <select
          value={selectedContrato || ''}
          onChange={(e) => setSelectedContrato(e.target.value ? Number(e.target.value) : null)}
          className="w-full md:w-96 border border-border bg-background text-foreground rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
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
            <h2 className="text-lg font-semibold text-foreground">Participantes del Contrato</h2>
            <button
              onClick={() => setDialog(true)}
              className="bg-primary text-primary-foreground px-4 py-2 rounded-lg hover:bg-primary/90 text-sm font-medium transition-colors shadow-sm"
            >
              + Agregar Coarrendatario
            </button>
          </div>

          {coarrendatarios.length === 0 ? (
            <div className="bg-card border border-border rounded-lg shadow-sm p-8 text-center text-muted-foreground">
              No hay coarrendatarios registrados en este contrato.
            </div>
          ) : (
            <div className="bg-card border border-border rounded-lg shadow-sm overflow-hidden">
              <table className="w-full">
                <thead className="bg-muted/50 border-b border-border">
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground">Persona</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground">Vínculo</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground">Responsable Pago</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground">Contacto</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground">Estado</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-muted-foreground">Acciones</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {coarrendatarios.map((c) => (
                    <tr key={c.idContratoResidente} className="hover:bg-muted/50 transition-colors">
                      <td className="px-4 py-3 text-sm text-foreground">
                        <div className="font-medium">{c.nombrePersona || `ID #${c.idPersona}`}</div>
                        {c.numeroDocumento && (
                          <div className="text-xs text-muted-foreground">Doc: {c.numeroDocumento}</div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm text-foreground">
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-secondary text-secondary-foreground">
                          {c.tipoVinculo}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-foreground">
                        {c.esResponsablePago === 'S' ? (
                          <span className="text-emerald-600 dark:text-emerald-400 font-medium">Sí (Solidario)</span>
                        ) : (
                          <span className="text-muted-foreground">No</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
                        <div>{c.telefono || '-'}</div>
                        <div>{c.email || '-'}</div>
                      </td>
                      <td className="px-4 py-3 text-sm">
                        <span className={`px-2 py-1 rounded text-xs font-medium ${
                          c.estado === 'ACTIVO'
                            ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                            : 'bg-destructive/10 text-destructive border border-destructive/20'
                        }`}>
                          {c.estado}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-right space-x-2">
                        <button
                          onClick={() => toggleEstado(c)}
                          className="text-amber-600 dark:text-amber-400 hover:underline text-xs"
                        >
                          {c.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'}
                        </button>
                        <button
                          onClick={() => setDeleteTarget(c)}
                          className="text-destructive hover:underline text-xs font-medium"
                        >
                          Eliminar
                        </button>
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
            <h2 className="text-lg font-semibold mb-4 text-foreground">Agregar Coarrendatario / Ocupante</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1 text-foreground">Persona *</label>
                {personas.length > 0 ? (
                  <select
                    value={form.idPersona}
                    onChange={(e) => setForm({ ...form, idPersona: e.target.value })}
                    className="w-full border border-border bg-background text-foreground rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  >
                    <option value="">-- Seleccionar persona del censo --</option>
                    {personas.map((p) => {
                      const pId = p.id || p.idPersona;
                      const pNom = p.nombres || `${p.primerNombre || ''} ${p.primerApellido || ''}`.trim() || `Persona #${pId}`;
                      const pDoc = p.numeroDocumento ? ` (Doc: ${p.numeroDocumento})` : '';
                      return (
                        <option key={pId} value={pId}>
                          {pNom}{pDoc}
                        </option>
                      );
                    })}
                  </select>
                ) : (
                  <input
                    type="number"
                    value={form.idPersona}
                    onChange={(e) => setForm({ ...form, idPersona: e.target.value })}
                    className="w-full border border-border bg-background text-foreground rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                    placeholder="ID de la persona"
                  />
                )}
              </div>

              <div>
                <label className="block text-sm font-medium mb-1 text-foreground">Tipo de Vínculo</label>
                <select
                  value={form.tipoVinculo}
                  onChange={(e) => setForm({ ...form, tipoVinculo: e.target.value })}
                  className="w-full border border-border bg-background text-foreground rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  <option value="COARRENDATARIO">Coarrendatario (Garante / Solidario)</option>
                  <option value="OCUPANTE_AUTORIZADO">Ocupante Autorizado</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium mb-1 text-foreground">¿Es Responsable de Pago?</label>
                <select
                  value={form.esResponsablePago}
                  onChange={(e) => setForm({ ...form, esResponsablePago: e.target.value })}
                  className="w-full border border-border bg-background text-foreground rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  <option value="N">No</option>
                  <option value="S">Sí (Obligación solidaria)</option>
                </select>
              </div>

              <div className="pt-2">
                <label className="flex items-center gap-2 cursor-pointer text-sm text-foreground">
                  <input
                    type="checkbox"
                    checked={form.sincronizarHabitabilidad}
                    onChange={(e) => setForm({ ...form, sincronizarHabitabilidad: e.target.checked })}
                    className="rounded border-border text-primary focus:ring-primary h-4 w-4"
                  />
                  <span>Sincronizar habitabilidad física en la unidad (Conviviente)</span>
                </label>
                <p className="text-xs text-muted-foreground mt-1 ml-6">
                  Registra al participante como habitante de la unidad, sujeto a validación de cupo de capacidad.
                </p>
              </div>
            </div>

            <div className="flex justify-end gap-2 mt-6">
              <button
                onClick={() => setDialog(false)}
                className="px-4 py-2 border border-border rounded-lg hover:bg-muted text-sm font-medium transition-colors"
              >
                Cancelar
              </button>
              <button
                onClick={crear}
                className="px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 text-sm font-medium transition-colors shadow-sm"
              >
                Agregar
              </button>
            </div>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={!!deleteTarget}
        title="Eliminar coarrendatario"
        message="¿Eliminar este coarrendatario del contrato de arrendamiento?"
        onConfirm={eliminar}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}
