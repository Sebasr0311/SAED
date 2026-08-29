import React, { useState, useEffect } from 'react';
import { useAuth } from '../lib/AuthContext.jsx';
import { api } from '../lib/api.js';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { StatCard } from '../components/ui/StatCard.jsx';

export default function FlujoCajaPage() {
  const { token } = useAuth();
  const [resumen, setResumen] = useState(null);
  const [movimientos, setMovimientos] = useState([]);
  const [proyeccion, setProyeccion] = useState([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState('resumen');

  const cargarDatos = async () => {
    try {
      setLoading(true);
      const [resumenRes, movRes, proyRes] = await Promise.all([
        api.get('/flujo-caja/resumen'),
        api.get('/flujo-caja/movimientos?limite=20'),
        api.get('/flujo-caja/proyeccion')
      ]);
      setResumen(resumenRes.data?.data || resumenRes.data);
      setMovimientos(movRes.data?.data || movRes.data || []);
      setProyeccion(proyRes.data?.data || proyRes.data || []);
    } catch (e) {
      console.error('Error cargando flujo de caja:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    cargarDatos();
  }, []);

  const fmt = (v) => {
    const n = Number(v);
    if (isNaN(n)) return '$0';
    return '$' + n.toLocaleString('es-CO');
  };

  const fmtFecha = (f) => {
    if (!f) return '-';
    return new Date(f).toLocaleDateString('es-CO');
  };

  if (loading) return <div className="p-8 text-center">Cargando flujo de caja...</div>;

  return (
    <div className="max-w-7xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">Flujo de Caja</h1>

      {/* Resumen */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4 mb-6">
        <StatCard icon="account_balance" label="Saldo Actual" value={fmt(resumen?.saldoActual)} color="primary" />
        <StatCard icon="trending_up" label="Ingresos Totales" value={fmt(resumen?.totalIngresos)} color="success" />
        <StatCard icon="trending_down" label="Egresos Totales" value={fmt(resumen?.totalEgresos)} color="danger" />
        <StatCard icon="schedule" label="Ingresos Esperados" value={fmt(resumen?.ingresosEsperados)} color="warning" />
        <StatCard icon="receipt_long" label="Gastos Programados" value={fmt(resumen?.gastosProgramados)} color="warning" />
        <StatCard icon="show_chart" label="Proyección Saldo" value={fmt(resumen?.proyeccionSaldo)} color="primary" />
      </div>

      {/* Tabs */}
      <div className="flex gap-2 mb-6 border-b pb-2">
        {['resumen', 'movimientos', 'proyeccion'].map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 rounded-t-lg font-medium transition ${
              tab === t ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            {t === 'resumen' ? 'Resumen' : t === 'movimientos' ? 'Movimientos Recientes' : 'Proyección'}
          </button>
        ))}
      </div>

      {/* Tab: Resumen */}
      {tab === 'resumen' && (
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold mb-4">Resumen del Periodo</h2>
          <div className="grid grid-cols-2 gap-6">
            <div>
              <h3 className="font-medium text-green-700 mb-2">Ingresos</h3>
              <p>Confirmados: <strong>{fmt(resumen?.totalIngresos)}</strong></p>
              <p>Esperados (pendientes): <strong>{fmt(resumen?.ingresosEsperados)}</strong></p>
            </div>
            <div>
              <h3 className="font-medium text-red-700 mb-2">Egresos</h3>
              <p>Ejecutados: <strong>{fmt(resumen?.totalEgresos)}</strong></p>
              <p>Programados (pendientes): <strong>{fmt(resumen?.gastosProgramados)}</strong></p>
            </div>
          </div>
          <div className="mt-4 p-4 bg-gray-50 rounded">
            <p className="text-sm text-gray-600">Saldo actual: <strong className="text-lg">{fmt(resumen?.saldoActual)}</strong></p>
            <p className="text-sm text-gray-600">Proyección (saldo + esperados - programados): <strong className="text-lg">{fmt(resumen?.proyeccionSaldo)}</strong></p>
          </div>
        </div>
      )}

      {/* Tab: Movimientos */}
      {tab === 'movimientos' && (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-sm font-medium">Fecha</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Tipo</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Categoría</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Descripción</th>
                <th className="px-4 py-3 text-right text-sm font-medium">Monto</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Estado</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {movimientos.length === 0 ? (
                <tr><td colSpan={6} className="p-4 text-center text-gray-500">No hay movimientos registrados</td></tr>
              ) : movimientos.map((m, i) => (
                <tr key={i} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm">{fmtFecha(m.fecha)}</td>
                  <td className="px-4 py-3 text-sm">
                    <span className={`px-2 py-1 rounded text-xs font-medium ${
                      m.tipo === 'INGRESO' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                    }`}>
                      {m.tipo}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm">{m.categoria}</td>
                  <td className="px-4 py-3 text-sm">{m.descripcion}</td>
                  <td className={`px-4 py-3 text-sm text-right font-medium ${
                    m.tipo === 'INGRESO' ? 'text-green-600' : 'text-red-600'
                  }`}>
                    {m.tipo === 'INGRESO' ? '+' : '-'}{fmt(m.monto)}
                  </td>
                  <td className="px-4 py-3 text-sm">{m.estado}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Tab: Proyección */}
      {tab === 'proyeccion' && (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-sm font-medium">Fecha</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Tipo</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Categoría</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Descripción</th>
                <th className="px-4 py-3 text-right text-sm font-medium">Monto Pendiente</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Unidad</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {proyeccion.length === 0 ? (
                <tr><td colSpan={6} className="p-4 text-center text-gray-500">No hay proyecciones pendientes</td></tr>
              ) : proyeccion.map((p, i) => (
                <tr key={i} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm">{fmtFecha(p.fecha)}</td>
                  <td className="px-4 py-3 text-sm">
                    <span className={`px-2 py-1 rounded text-xs font-medium ${
                      p.tipo.includes('Ingreso') || p.tipo.includes('INGRESO')
                        ? 'bg-green-100 text-green-800' : 'bg-orange-100 text-orange-800'
                    }`}>
                      {p.tipo}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm">{p.categoria}</td>
                  <td className="px-4 py-3 text-sm">{p.descripcion}</td>
                  <td className="px-4 py-3 text-sm text-right font-medium text-gray-800">{fmt(p.monto)}</td>
                  <td className="px-4 py-3 text-sm">{p.unidad || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
