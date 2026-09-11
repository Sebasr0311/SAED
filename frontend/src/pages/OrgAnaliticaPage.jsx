import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/Button.jsx';
import { BarChart3, TrendingUp, Building, Users, AlertTriangle, RefreshCw } from 'lucide-react';

export default function OrgAnaliticaPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [propiedades, setPropiedades] = useState([]);

  async function loadAnalytics() {
    try {
      setLoading(true);
      setError(null);
      const res = await api.get('/org/dashboard/analytics');
      const list = res?.data?.propiedades || res?.propiedades || [];
      setPropiedades(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error('Error cargando analitica:', err);
      // Fallback a dashboard reciente si analytics no retorna
      try {
        const dRes = await api.get('/org/dashboard');
        const list = dRes?.data?.propiedadesRecientes || dRes?.propiedadesRecientes || [];
        setPropiedades(Array.isArray(list) ? list : []);
      } catch (e) {
        setError('No se pudieron cargar las métricas analíticas.');
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadAnalytics();
  }, []);

  const formatCOP = (val) =>
    new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(val || 0);

  if (loading) {
    return (
      <div className="p-6 space-y-6 animate-fadeIn">
        <Skeleton className="h-10 w-64" />
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-32 rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-80 w-full rounded-xl" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6">
        <div className="bg-destructive/15 border border-destructive text-destructive p-4 rounded-xl flex items-center justify-between">
          <div className="flex items-center gap-3">
            <AlertTriangle className="h-5 w-5 shrink-0" />
            <p className="text-sm font-medium">{error}</p>
          </div>
          <Button variant="outline" size="sm" onClick={loadAnalytics}>
            Reintentar
          </Button>
        </div>
      </div>
    );
  }

  const maxUnidades = Math.max(...propiedades.map((p) => Number(p.UNIDADES || p.total_unidades) || 0), 1);

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-5">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              Analítica Comparativa de Cartera
            </h1>
            <Badge variant="outline" className="text-xs">
              BENCHMARK
            </Badge>
          </div>
          <p className="text-xs sm:text-sm text-muted-foreground mt-1">
            Comparativa de volumen, cobertura habitacional y rendimiento operativo entre copropiedades.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={loadAnalytics} className="flex items-center gap-2">
            <RefreshCw className="h-4 w-4" />
            <span>Actualizar</span>
          </Button>
        </div>
      </div>

      {/* Grid Comparativo */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Distribución de Unidades */}
        <Card className="border-border/80 shadow-xs">
          <CardHeader className="pb-3 border-b border-border/60">
            <CardTitle className="text-sm font-bold flex items-center gap-2">
              <Building className="h-4 w-4 text-primary" />
              Distribución de Unidades por Propiedad
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-4 space-y-4">
            {propiedades.length === 0 ? (
              <p className="text-xs text-muted-foreground text-center py-6">No hay propiedades activas.</p>
            ) : (
              propiedades.map((p, idx) => {
                const unidades = Number(p.UNIDADES || p.total_unidades) || 0;
                const pct = Math.round((unidades / maxUnidades) * 100);
                return (
                  <div key={p.ID_PROPIEDAD || p.id_propiedad || idx} className="space-y-1.5">
                    <div className="flex justify-between text-xs">
                      <span className="font-semibold text-foreground">{p.NOMBRE || p.nombre}</span>
                      <span className="font-mono text-muted-foreground">{unidades} unidades</span>
                    </div>
                    <div className="w-full bg-muted rounded-full h-2 overflow-hidden">
                      <div
                        className="bg-primary h-2 rounded-full transition-all duration-500"
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>
                );
              })
            )}
          </CardContent>
        </Card>

        {/* Rendimiento Financiero Comparado */}
        <Card className="border-border/80 shadow-xs">
          <CardHeader className="pb-3 border-b border-border/60">
            <CardTitle className="text-sm font-bold flex items-center gap-2">
              <TrendingUp className="h-4 w-4 text-primary" />
              Comparativa de Recaudo y Cartera
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-4 space-y-3">
            {propiedades.length === 0 ? (
              <p className="text-xs text-muted-foreground text-center py-6">No hay datos de recaudo.</p>
            ) : (
              propiedades.map((p, idx) => {
                const recaudo = Number(p.RECAUDO || p.total_recaudado) || 0;
                const cartera = Number(p.CARTERA || p.cartera_pendiente) || 0;
                return (
                  <div
                    key={p.ID_PROPIEDAD || p.id_propiedad || idx}
                    className="p-3 rounded-lg border border-border/70 bg-card/50 flex items-center justify-between"
                  >
                    <div>
                      <p className="font-semibold text-xs text-foreground">{p.NOMBRE || p.nombre}</p>
                      <p className="text-[11px] text-muted-foreground">{p.CIUDAD || p.ciudad || 'Colombia'}</p>
                    </div>
                    <div className="text-right">
                      <p className="font-mono font-medium text-xs text-emerald-600 dark:text-emerald-400">
                        {formatCOP(recaudo)}
                      </p>
                      <p className="text-[10px] text-muted-foreground font-mono">
                        Mora: {formatCOP(cartera)}
                      </p>
                    </div>
                  </div>
                );
              })
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
