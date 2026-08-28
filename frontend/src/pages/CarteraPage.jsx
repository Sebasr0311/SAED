import { useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import { api } from '../lib/api.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card.tsx';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '../components/ui/table.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { StatCard } from '../components/ui/StatCard.jsx';
import { toast } from 'sonner';

const fmtCOP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' });

const TABS = [
  { id: 'unidades', label: 'Cartera por Unidad', icon: 'apartment' },
  { id: 'resumen', label: 'Resumen', icon: 'summarize' },
  { id: 'antiguedad', label: 'Antigüedad de Cartera', icon: 'bar_chart' },
];

export default function CarteraPage() {
  const [tabActiva, setTabActiva] = useState('unidades');
  const [recalculando, setRecalculando] = useState(false);

  const { data: carteraData, loading: carteraLoading } = useFetch(
    () => api.get('/cartera'),
    [tabActiva === 'unidades']
  );
  const { data: resumenData, loading: resumenLoading } = useFetch(
    () => api.get('/cartera/resumen'),
    [tabActiva === 'resumen']
  );
  const { data: antiguedadData, loading: antiguedadLoading } = useFetch(
    () => api.get('/cartera/antiguedad'),
    [tabActiva === 'antiguedad']
  );

  const unidades = carteraData?.items || carteraData || [];
  const resumen = resumenData?.items || resumenData?.raw || resumenData || {};
  const antiguedad = antiguedadData?.items || antiguedadData || [];

  async function recalcular() {
    setRecalculando(true);
    try {
      await api.post('/cartera/recalcular');
      toast.success('Cartera recalculada exitosamente');
    } catch (err) {
      toast.error(err.message || 'Error al recalcular cartera');
    } finally {
      setRecalculando(false);
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Cartera"
        subtitle="Gestión de cartera y mora por unidad"
        action={
          <Button onClick={recalcular} disabled={recalculando}>
            <span className="material-symbols-outlined text-base mr-1">refresh</span>
            {recalculando ? 'Recalculando…' : 'Recalcular Cartera'}
          </Button>
        }
      />

      {/* Tabs */}
      <div className="flex gap-2 border-b pb-2">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => setTabActiva(t.id)}
            className={`flex items-center gap-1 px-3 py-1.5 text-sm rounded-t font-medium transition-colors ${
              tabActiva === t.id
                ? 'border-b-2 border-primary text-foreground'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            <span className="material-symbols-outlined text-base">{t.icon}</span>
            {t.label}
          </button>
        ))}
      </div>

      {/* TAB: Cartera por Unidad */}
      {tabActiva === 'unidades' && (
        <Card>
          <CardContent className="pt-6">
            {carteraLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
              </div>
            ) : unidades.length === 0 ? (
              <p className="py-8 text-center text-muted-foreground">No hay datos de cartera disponibles.</p>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Unidad</TableHead>
                      <TableHead className="text-right">Saldo Corriente</TableHead>
                      <TableHead className="text-right">Mora 30</TableHead>
                      <TableHead className="text-right">Mora 60</TableHead>
                      <TableHead className="text-right">Mora 90+</TableHead>
                      <TableHead className="text-right">Saldo Total</TableHead>
                      <TableHead>Estado</TableHead>
                      <TableHead>Fecha Corte</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {unidades.map((u, i) => (
                      <TableRow key={u.ID_CARTERA || u.id || i}>
                        <TableCell className="font-mono text-sm">{u.ID_UNIDAD || u.id_unidad}</TableCell>
                        <TableCell className="text-right">{fmtCOP.format(Number(u.SALDO_CORRIENTE || 0))}</TableCell>
                        <TableCell className="text-right text-amber-600">{fmtCOP.format(Number(u.SALDO_MORA_30 || 0))}</TableCell>
                        <TableCell className="text-right text-orange-600">{fmtCOP.format(Number(u.SALDO_MORA_60 || 0))}</TableCell>
                        <TableCell className="text-right text-red-600">{fmtCOP.format(Number(u.SALDO_MORA_90_MAS || 0))}</TableCell>
                        <TableCell className="text-right font-bold">{fmtCOP.format(Number(u.SALDO_TOTAL || 0))}</TableCell>
                        <TableCell>
                          <Badge variant={(u.ESTADO_CARTERA || 'AL_DIA') === 'AL_DIA' ? 'default' : 'destructive'}>
                            {(u.ESTADO_CARTERA || 'AL_DIA').replace('_', ' ')}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-xs whitespace-nowrap">{u.FECHA_CORTE || '-'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* TAB: Resumen */}
      {tabActiva === 'resumen' && (
        <>
          {resumenLoading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              {[1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-28" />)}
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              <StatCard icon="account_balance" value={fmtCOP.format(Number(resumen.TOTAL_CARTERA || 0))} label="Total Cartera" color="primary" />
              <StatCard icon="money_off" value={fmtCOP.format(Number(resumen.TOTAL_MORA || 0))} label="Total Mora" color="danger" />
              <StatCard icon="check_circle" value={resumen.COUNT_AL_DIA || 0} label="Unidades al día" color="success" />
              <StatCard icon="warning" value={(resumen.COUNT_MORA_LEVE || 0) + (resumen.COUNT_MORA_MEDIA || 0) + (resumen.COUNT_MORA_GRAVE || 0)} label="Unidades en mora" color="warning" />
            </div>
          )}
        </>
      )}

      {/* TAB: Antigüedad de Cartera */}
      {tabActiva === 'antiguedad' && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Distribución por Antigüedad</CardTitle>
          </CardHeader>
          <CardContent>
            {antiguedadLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
              </div>
            ) : antiguedad.length === 0 ? (
              <p className="py-8 text-center text-muted-foreground">No hay datos de antigüedad de cartera.</p>
            ) : (
              <div className="space-y-4">
                {antiguedad.map((a, i) => {
                  const rango = a.RANGO || `Rango ${i + 1}`;
                  const monto = Number(a.TOTAL_SALDO || 0);
                  const unidadesCount = Number(a.CANTIDAD_CUOTAS || 0);
                  const totalGeneral = Number(resumen.TOTAL_CARTERA || 1);
                  const pct = totalGeneral > 0 ? (monto / totalGeneral) * 100 : 0;
                  return (
                    <div key={i} className="space-y-1">
                      <div className="flex justify-between text-sm">
                        <span className="font-medium">{rango}</span>
                        <span className="text-muted-foreground">{unidadesCount} unidades — {fmtCOP.format(monto)}</span>
                      </div>
                      <div className="flex-1 h-3 bg-gray-200 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-primary rounded-full transition-all"
                          style={{ width: `${Math.min(pct, 100)}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
