import React, { useState, useEffect } from 'react';
import { api } from '../lib/api.js';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { toast } from 'sonner';

export default function ContratosProveedorPage() {
  const [contratos, setContratos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [dialog, setDialog] = useState(false);
  const [form, setForm] = useState({
    idProveedor: '', numeroContrato: '', objetoContrato: '', valorTotal: '',
    periodicidadPago: 'MENSUAL', fechaInicio: '', fechaFin: '', diasAlertaVenc: '30'
  });
  const [deleteTarget, setDeleteTarget] = useState(null);

  const cargar = async () => {
    try {
      setLoading(true);
      const res = await api.get('/contratos-admin/proveedores');
      setContratos(res.data?.data || res.data || []);
    } catch (e) {
      console.error('Error:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { cargar(); }, []);

  const crear = async () => {
    if (!form.idProveedor || !form.numeroContrato || !form.objetoContrato || !form.valorTotal || !form.fechaInicio || !form.fechaFin) {
      toast.error('Complete todos los campos obligatorios');
      return;
    }
    try {
      await api.post('/contratos-admin/proveedores', {
        idProveedor: Number(form.idProveedor),
        numeroContrato: form.numeroContrato,
        objetoContrato: form.objetoContrato,
        valorTotal: Number(form.valorTotal),
        periodicidadPago: form.periodicidadPago,
        fechaInicio: form.fechaInicio,
        fechaFin: form.fechaFin,
        diasAlertaVenc: Number(form.diasAlertaVenc)
      });
      toast.success('Contrato de proveedor creado');
      setDialog(false);
      setForm({ idProveedor: '', numeroContrato: '', objetoContrato: '', valorTotal: '', periodicidadPago: 'MENSUAL', fechaInicio: '', fechaFin: '', diasAlertaVenc: '30' });
      cargar();
    } catch (e) {
      toast.error('Error: ' + (e.response?.data?.message || e.message));
    }
  };

  const eliminar = async () => {
    if (!deleteTarget) return;
    try {
      await api.delete(`/contratos-admin/proveedores/${deleteTarget.idContratoProveedor}`);
      toast.success('Contrato eliminado');
      setDeleteTarget(null);
      cargar();
    } catch (e) {
      toast.error('Error al eliminar');
    }
  };

  const toggleEstado = async (c) => {
    const nuevo = c.estado === 'VIGENTE' ? 'FINALIZADO' : 'VIGENTE';
    try {
      await api.patch(`/contratos-admin/proveedores/${c.idContratoProveedor}/estado`, { estado: nuevo });
      toast.success(`Estado cambiado a ${nuevo}`);
      cargar();
    } catch (e) {
      toast.error('Error');
    }
  };

  const fmt = (v) => '$' + Number(v || 0).toLocaleString('es-CO');

  if (loading) return <div className="p-8 text-center">Cargando...</div>;

  return (
    <div className="max-w-6xl mx-auto p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">Contratos de Proveedor</h1>
        <button onClick={() => setDialog(true)} className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700">
          + Nuevo Contrato
        </button>
      </div>

      {contratos.length === 0 ? (
        <div className="bg-white rounded-lg shadow p-8 text-center text-gray-500">
          No hay contratos de proveedor registrados
        </div>
      ) : (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-sm font-medium">N° Contrato</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Objeto</th>
                <th className="px-4 py-3 text-right text-sm font-medium">Valor</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Periodicidad</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Inicio</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Fin</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Estado</th>
                <th className="px-4 py-3 text-right text-sm font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {contratos.map((c) => (
                <tr key={c.idContratoProveedor} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm font-medium">{c.numeroContrato}</td>
                  <td className="px-4 py-3 text-sm">{c.objetoContrato}</td>
                  <td className="px-4 py-3 text-sm text-right">{fmt(c.valorTotal)}</td>
                  <td className="px-4 py-3 text-sm">{c.periodicidadPago}</td>
                  <td className="px-4 py-3 text-sm">{c.fechaInicio}</td>
                  <td className="px-4 py-3 text-sm">{c.fechaFin}</td>
                  <td className="px-4 py-3 text-sm">
                    <span className={`px-2 py-1 rounded text-xs font-medium ${
                      c.estado === 'VIGENTE' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                    }`}>{c.estado}</span>
                  </td>
                  <td className="px-4 py-3 text-sm text-right space-x-2">
                    <button onClick={() => toggleEstado(c)} className="text-yellow-600 hover:underline">
                      {c.estado === 'VIGENTE' ? 'Finalizar' : 'Reactivar'}
                    </button>
                    <button onClick={() => setDeleteTarget(c)} className="text-red-600 hover:underline">Eliminar</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Dialog Crear */}
      {dialog && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-lg">
            <h2 className="text-lg font-semibold mb-4">Nuevo Contrato de Proveedor</h2>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">ID Proveedor *</label>
                <input type="number" value={form.idProveedor} onChange={(e) => setForm({ ...form, idProveedor: e.target.value })}
                  className="w-full border rounded-lg px-3 py-2" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">N° Contrato *</label>
                <input type="text" value={form.numeroContrato} onChange={(e) => setForm({ ...form, numeroContrato: e.target.value })}
                  className="w-full border rounded-lg px-3 py-2" />
              </div>
              <div className="col-span-2">
                <label className="block text-sm font-medium mb-1">Objeto del Contrato *</label>
                <input type="text" value={form.objetoContrato} onChange={(e) => setForm({ ...form, objetoContrato: e.target.value })}
                  className="w-full border rounded-lg px-3 py-2" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Valor Total *</label>
                <input type="number" value={form.valorTotal} onChange={(e) => setForm({ ...form, valorTotal: e.target.value })}
                  className="w-full border rounded-lg px-3 py-2" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Periodicidad</label>
                <select value={form.periodicidadPago} onChange={(e) => setForm({ ...form, periodicidadPago: e.target.value })}
                  className="w-full border rounded-lg px-3 py-2">
                  <option value="MENSUAL">Mensual</option>
                  <option value="TRIMESTRAL">Trimestral</option>
                  <option value="SEMESTRAL">Semestral</option>
                  <option value="ANUAL">Anual</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Fecha Inicio *</label>
                <input type="date" value={form.fechaInicio} onChange={(e) => setForm({ ...form, fechaInicio: e.target.value })}
                  className="w-full border rounded-lg px-3 py-2" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Fecha Fin *</label>
                <input type="date" value={form.fechaFin} onChange={(e) => setForm({ ...form, fechaFin: e.target.value })}
                  className="w-full border rounded-lg px-3 py-2" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Días Alerta Vencimiento</label>
                <input type="number" value={form.diasAlertaVenc} onChange={(e) => setForm({ ...form, diasAlertaVenc: e.target.value })}
                  className="w-full border rounded-lg px-3 py-2" />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-6">
              <button onClick={() => setDialog(false)} className="px-4 py-2 border rounded-lg hover:bg-gray-50">Cancelar</button>
              <button onClick={crear} className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">Crear</button>
            </div>
          </div>
        </div>
      )}

      <ConfirmDialog open={!!deleteTarget} title="Eliminar contrato"
        message="¿Eliminar este contrato de proveedor?" onConfirm={eliminar} onCancel={() => setDeleteTarget(null)} />
    </div>
  );
}
