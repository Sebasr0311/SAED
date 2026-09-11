import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/Button.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { FileText, Printer, RefreshCw, AlertTriangle, Building, Users, Wallet } from 'lucide-react';

export default function OrgReportesPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [data, setData] = useState(null);

  async function loadReport() {
    try {
      setLoading(true);
      setError(null);
      const res = await api.get('/org/dashboard');
      setData(res?.data || res || {});
    } catch (err) {
      console.error('Error cargando informe de organización:', err);
      setError('No se pudo generar el reporte consolidado.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadReport();
  }, []);

  const formatCOP = (val) =>
    new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(val || 0);

  if (loading) {
    return (
      <div className="p-6 space-y-6 animate-fadeIn">
        <Skeleton className="h-10 w-64" />
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
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
          <Button variant="outline" size="sm" onClick={loadReport}>
            Reintentar
          </Button>
        </div>
      </div>
    );
  }

  const props = data?.propiedades || { total: 0, activas: 0, inactivas: 0 };
  const units = data?.unidades || { total: 0 };
  const admins = data?.administradores || { activos: 0 };
  const finanzas = data?.finanzas || { totalRecaudado: 0, carteraPendiente: 0 };
  const recientes = data?.propiedadesRecientes || [];
  const sub = data?.suscripcion || {};

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-5">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              Reportes Gerenciales Consolidados
            </h1>
            <Badge variant="outline" className="text-xs">
              INFORME EJECUTIVO
            </Badge>
          </div>
          <p className="text-xs sm:text-sm text-muted-foreground mt-1">
            Resumen consolidado de infraestructura, administración, licenciamiento y finanzas agregadas.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => window.print()} className="flex items-center gap-2">
            <Printer className="h-4 w-4" />
            <span>Imprimir Informe</span>
          </Button>
          <Button variant="outline" size="sm" onClick={loadReport} className="flex items-center gap-2">
            <RefreshCw className="h-4 w-4" />
            <span>Actualizar</span>
          </Button>
        </div>
      </div>

      {/* Resumen de Tres Pilares */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card className="border-border/80 shadow-xs">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center justify-between">
              <span>Infraestructura Gestionada</span>
              <Building className="h-4 w-4 text-primary" />
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-1">
            <div className="text-2xl font-bold tracking-tight text-foreground">{props.total} Propiedades</div>
            <p className="text-xs text-muted-foreground">
              {props.activas} activas · {units.total} unidades habitacionales
            </p>
          </CardContent>
        </Card>

        <Card className="border-border/80 shadow-xs">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center justify-between">
              <span>Talento y Licenciamiento</span>
              <Users className="h-4 w-4 text-primary" />
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-1">
            <div className="text-2xl font-bold tracking-tight text-foreground">
              {admins.activos} Administradores
            </div>
            <p className="text-xs text-muted-foreground">Plan: {sub.planNombre || 'SaaS Activo'}</p>
          </CardContent>
        </Card>

        <Card className="border-border/80 shadow-xs">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center justify-between">
              <span>Volumen Financiero Agregado</span>
              <Wallet className="h-4 w-4 text-primary" />
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-1">
            <div className="text-2xl font-bold tracking-tight text-foreground">
              {formatCOP(finanzas.totalRecaudado)}
            </div>
            <p className="text-xs text-muted-foreground">
              Cartera por cobrar: {formatCOP(finanzas.carteraPendiente)}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Tabla Resumen de Propiedades */}
      <Card className="border-border/80 shadow-xs">
        <CardHeader className="pb-3 border-b border-border/60">
          <CardTitle className="text-base font-bold flex items-center gap-2">
            <FileText className="h-4 w-4 text-primary" />
            Padrón de Copropiedades de la Organización
          </CardTitle>
        </CardHeader>
        <CardContent className="pt-4">
          <DataTable
            columns={[
              {
                key: 'nombre',
                label: 'Copropiedad',
                render: (r) => (
                  <div>
                    <span className="font-semibold text-foreground text-sm">{r.NOMBRE || r.nombre}</span>
                    <span className="block text-xs text-muted-foreground">{r.DIRECCION || r.direccion || ''}</span>
                  </div>
                ),
              },
              {
                key: 'ciudad',
                label: 'Ciudad',
                render: (r) => <span className="text-xs text-foreground">{r.CIUDAD || r.ciudad || '—'}</span>,
              },
              {
                key: 'unidades',
                label: 'Unidades',
                render: (r) => (
                  <Badge variant="outline" className="font-mono text-xs">
                    {r.TOTAL_UNIDADES || r.total_unidades || 0}
                  </Badge>
                ),
              },
              {
                key: 'admins',
                label: 'Admins Asignados',
                render: (r) => (
                  <span className="text-xs font-mono">{r.TOTAL_ADMINS || r.total_admins || 0}</span>
                ),
              },
              {
                key: 'estado',
                label: 'Estado',
                render: (r) => (
                  <Badge variant={(r.ESTADO || r.estado) === 'ACTIVA' ? 'success' : 'secondary'} className="text-[10px]">
                    {r.ESTADO || r.estado || 'ACTIVA'}
                  </Badge>
                ),
              },
            ]}
            rows={recientes}
            pageSize={10}
            empty={{
              icon: 'domain',
              title: 'No hay propiedades registradas',
              subtitle: 'Las propiedades dadas de alta en la organización se listarán aquí.',
            }}
            keyField="id_propiedad"
          />
        </CardContent>
      </Card>
    </div>
  );
}
