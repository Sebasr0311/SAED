import React, { useState, useEffect } from 'react';
import { api } from '../lib/api.js';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { toast } from 'sonner';
import { FileText, UserPlus, Users } from 'lucide-react';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Modal } from '../components/ui/Modal.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import { LoadingState } from '../components/ui/LoadingState.jsx';

const SELECT_CLS =
  'h-10 w-full rounded-md border border-input bg-surface px-3 text-sm text-on-surface transition-[border-color,box-shadow] duration-150 focus-visible:outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25';

const toList = (res) => {
  if (Array.isArray(res)) return res;
  const d = res?.data;
  if (Array.isArray(d)) return d;
  if (Array.isArray(d?.items)) return d.items;
  if (Array.isArray(d?.data)) return d.data;
  if (Array.isArray(res?.items)) return res.items;
  return [];
};

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
  const [contratosError, setContratosError] = useState(null);
  const [loadingCoar, setLoadingCoar] = useState(false);
  const [coarError, setCoarError] = useState(null);

  const cargarContratos = async () => {
    try {
      setLoading(true);
      setContratosError(null);
      const res = await api.get('/contratos');
      setContratos(toList(res));
    } catch (e) {
      setContratosError(e);
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
    setLoadingCoar(true);
    setCoarError(null);
    try {
      const res = await api.get(`/contratos-admin/coarrendatarios/${idContrato}`);
      const raw = res?.data?.data || res?.data || res || [];
      setCoarrendatarios(Array.isArray(raw) ? raw : []);
    } catch (e) {
      setCoarError(e);
      toast.error('No se pudieron cargar los coarrendatarios');
    } finally {
      setLoadingCoar(false);
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

  const VINCULO_LABEL = { COARRENDATARIO: 'Coarrendatario', OCUPANTE_AUTORIZADO: 'Ocupante autorizado' };

  return (
    <PageContainer maxWidth="max-w-6xl">
      <PageHeader
        title="Coarrendatarios y Ocupantes"
        subtitle="Vínculos contractuales, garantes y ocupantes autorizados en contratos de arrendamiento."
      />

      {/* Contract selector */}
      <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
        <label htmlFor="coar-contrato" className="mb-2 block text-sm font-semibold text-on-surface">
          Seleccionar Contrato de Arriendo
        </label>
        {loading && contratos.length === 0 ? (
          <LoadingState size="sm" message="Cargando contratos..." />
        ) : contratosError && contratos.length === 0 ? (
          <ErrorState
            compact
            title="No se pudieron cargar los contratos"
            message={contratosError?.message || 'Verifique su conexión e intente nuevamente.'}
            onRetry={cargarContratos}
          />
        ) : (
          <select
            id="coar-contrato"
            value={selectedContrato || ''}
            onChange={(e) => setSelectedContrato(e.target.value ? Number(e.target.value) : null)}
            className={SELECT_CLS + ' md:w-96'}
          >
            <option value="">-- Seleccionar contrato --</option>
            {contratos.map((c) => (
              <option key={c.idContrato} value={c.idContrato}>
                {c.numeroContrato} - {c.nombreArrendatario} ({c.estado})
              </option>
            ))}
          </select>
        )}
      </div>

      {!selectedContrato && !loading && !contratosError && (
        <EmptyState
          icon={FileText}
          title={contratos.length === 0 ? 'No hay contratos de arrendamiento' : 'Seleccione un contrato'}
          subtitle={
            contratos.length === 0
              ? 'Registre un contrato en el módulo de Contratos para vincular coarrendatarios.'
              : 'Elija un contrato para ver y gestionar sus participantes.'
          }
        />
      )}

      {selectedContrato && (
        <section className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-base font-bold text-on-surface">Participantes del Contrato</h2>
            <Button onClick={() => setDialog(true)}>
              <UserPlus className="h-4 w-4 mr-1.5" aria-hidden="true" />
              Agregar Coarrendatario
            </Button>
          </div>

          {loadingCoar && coarrendatarios.length === 0 ? (
            <div className="rounded-lg border border-border bg-card">
              <LoadingState message="Cargando participantes..." />
            </div>
          ) : coarError && coarrendatarios.length === 0 ? (
            <ErrorState
              title="No se pudieron cargar los coarrendatarios"
              message={coarError?.message || 'Verifique su conexión e intente nuevamente.'}
              onRetry={() => cargarCoarrendatarios(selectedContrato)}
            />
          ) : coarrendatarios.length === 0 ? (
            <EmptyState
              icon={Users}
              title="Sin coarrendatarios"
              subtitle="No hay coarrendatarios registrados en este contrato."
            >
              <div className="mt-4">
                <Button variant="outline" onClick={() => setDialog(true)}>
                  Agregar Coarrendatario
                </Button>
              </div>
            </EmptyState>
          ) : (
            <div className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px]">
                  <thead className="border-b border-border bg-surface-dim">
                    <tr>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-text-secondary">Persona</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-text-secondary">Vínculo</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-text-secondary">Responsable de pago</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-text-secondary">Contacto</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-text-secondary">Estado</th>
                      <th className="px-4 py-3 text-right text-xs font-semibold text-text-secondary">Acciones</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {coarrendatarios.map((c) => (
                      <tr key={c.idContratoResidente} className="transition-colors duration-150 hover:bg-surface-dim/60">
                        <td className="px-4 py-3 text-sm text-on-surface">
                          <div className="font-semibold">{c.nombrePersona || `ID #${c.idPersona}`}</div>
                          {c.numeroDocumento && (
                            <div className="text-xs text-text-secondary tabular-nums">Doc: {c.numeroDocumento}</div>
                          )}
                        </td>
                        <td className="px-4 py-3 text-sm">
                          <Badge variant="secondary">{VINCULO_LABEL[c.tipoVinculo] || c.tipoVinculo}</Badge>
                        </td>
                        <td className="px-4 py-3 text-sm">
                          {c.esResponsablePago === 'S' ? (
                            <span className="font-semibold text-tint-green-fg">Sí (solidario)</span>
                          ) : (
                            <span className="text-text-secondary">No</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-xs text-text-secondary">
                          <div className="tabular-nums">{c.telefono || '-'}</div>
                          <div>{c.email || '-'}</div>
                        </td>
                        <td className="px-4 py-3 text-sm">
                          <Badge variant={c.estado === 'ACTIVO' ? 'success' : 'destructive'}>
                            {c.estado === 'ACTIVO' ? 'Activo' : c.estado === 'INACTIVO' ? 'Inactivo' : c.estado}
                          </Badge>
                        </td>
                        <td className="px-4 py-3 text-right">
                          <div className="flex justify-end gap-1">
                            <Button variant="ghost" size="sm" onClick={() => toggleEstado(c)}>
                              {c.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'}
                            </Button>
                            <Button variant="ghost" size="sm" className="text-destructive hover:bg-tint-red-bg hover:text-destructive" onClick={() => setDeleteTarget(c)}>
                              Eliminar
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </section>
      )}

      {/* Create dialog */}
      <Modal
        open={dialog}
        onClose={() => setDialog(false)}
        title="Agregar Coarrendatario / Ocupante"
        size="md"
        footer={
          <>
            <Button variant="outline" onClick={() => setDialog(false)}>
              Cancelar
            </Button>
            <Button onClick={crear}>Agregar</Button>
          </>
        }
      >
        <div className="space-y-4">
          <div>
            <label htmlFor="coar-persona" className="mb-1 block text-sm font-semibold text-on-surface">
              Persona <span className="text-destructive" aria-hidden="true">*</span>
            </label>
            {personas.length > 0 ? (
              <select
                id="coar-persona"
                value={form.idPersona}
                onChange={(e) => setForm({ ...form, idPersona: e.target.value })}
                className={SELECT_CLS}
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
                id="coar-persona"
                type="number"
                value={form.idPersona}
                onChange={(e) => setForm({ ...form, idPersona: e.target.value })}
                className={SELECT_CLS}
                placeholder="ID de la persona"
              />
            )}
          </div>

          <div>
            <label htmlFor="coar-vinculo" className="mb-1 block text-sm font-semibold text-on-surface">Tipo de Vínculo</label>
            <select
              id="coar-vinculo"
              value={form.tipoVinculo}
              onChange={(e) => setForm({ ...form, tipoVinculo: e.target.value })}
              className={SELECT_CLS}
            >
              <option value="COARRENDATARIO">Coarrendatario (Garante / Solidario)</option>
              <option value="OCUPANTE_AUTORIZADO">Ocupante Autorizado</option>
            </select>
          </div>

          <div>
            <label htmlFor="coar-pago" className="mb-1 block text-sm font-semibold text-on-surface">¿Es Responsable de Pago?</label>
            <select
              id="coar-pago"
              value={form.esResponsablePago}
              onChange={(e) => setForm({ ...form, esResponsablePago: e.target.value })}
              className={SELECT_CLS}
            >
              <option value="N">No</option>
              <option value="S">Sí (Obligación solidaria)</option>
            </select>
          </div>

          <div className="pt-1">
            <label className="flex cursor-pointer items-center gap-2 text-sm text-on-surface">
              <input
                type="checkbox"
                checked={form.sincronizarHabitabilidad}
                onChange={(e) => setForm({ ...form, sincronizarHabitabilidad: e.target.checked })}
                className="h-4 w-4 rounded-[3px] border-input accent-[var(--primary)]"
              />
              <span>Sincronizar habitabilidad física en la unidad (Conviviente)</span>
            </label>
            <p className="ml-6 mt-1 text-xs text-text-secondary">
              Registra al participante como habitante de la unidad, sujeto a validación de cupo de capacidad.
            </p>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        title="Eliminar coarrendatario"
        message="¿Eliminar este coarrendatario del contrato de arrendamiento?"
        confirmLabel="Eliminar"
        danger
        onConfirm={eliminar}
        onClose={() => setDeleteTarget(null)}
      />
    </PageContainer>
  );
}
