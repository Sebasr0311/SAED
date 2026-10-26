import { useEffect, useState, useMemo } from 'react';
import { Building2, CheckCircle2, Eye, Info, MapPin, PauseCircle, RefreshCw, Search } from 'lucide-react';
import api from '../lib/api.js';
import { Card, CardContent } from '../components/ui/card.tsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Input } from '../components/ui/input.tsx';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog.tsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { toast } from 'sonner';

const FILTER_SELECT =
  'h-10 rounded-md border border-input bg-surface px-3 text-sm text-on-surface transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25';

function Dato({ label, children }) {
  return (
    <div>
      <dt className="text-xs font-semibold text-text-muted">{label}</dt>
      <dd className="mt-0.5 text-sm text-on-surface">{children}</dd>
    </div>
  );
}

export default function SuperAdminPropiedadesPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [search, setSearch] = useState('');
  const [filtroEstado, setFiltroEstado] = useState('');
  const [filtroOcupacion, setFiltroOcupacion] = useState('');
  const [selectedProperty, setSelectedProperty] = useState(null);

  async function loadData() {
    try {
      setLoading(true);
      setLoadError(null);
      const res = await api.get('/properties');
      const list = res?.data || res || [];
      setItems(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error(err);
      toast.error('Error al cargar el inventario global de propiedades');
      setLoadError(err?.message || 'No fue posible consultar el inventario de propiedades.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  const stats = useMemo(() => {
    const total = items.length;
    const activas = items.filter((p) => (p.estado || '').toUpperCase() === 'ACTIVA').length;
    const inactivas = total - activas;
    const ciudades = new Set(items.map((p) => p.ciudad).filter(Boolean)).size;
    return { total, activas, inactivas, ciudades };
  }, [items]);

  const filtrados = useMemo(() => {
    return items.filter((p) => {
      const matchSearch =
        !search.trim() ||
        (p.nombre || '').toLowerCase().includes(search.toLowerCase()) ||
        (p.organizacionNombre || '').toLowerCase().includes(search.toLowerCase()) ||
        (p.ciudad || '').toLowerCase().includes(search.toLowerCase()) ||
        (p.direccion || '').toLowerCase().includes(search.toLowerCase());

      const matchEstado = !filtroEstado || (p.estado || '').toUpperCase() === filtroEstado.toUpperCase();
      const matchOcupacion =
        !filtroOcupacion || (p.tipoOcupacionPredominante || '').toUpperCase() === filtroOcupacion.toUpperCase();

      return matchSearch && matchEstado && matchOcupacion;
    });
  }, [items, search, filtroEstado, filtroOcupacion]);

  const hayFiltros = Boolean(search || filtroEstado || filtroOcupacion);
  const limpiarFiltros = () => {
    setSearch('');
    setFiltroEstado('');
    setFiltroOcupacion('');
  };

  const header = (
    <PageHeader
      title="Propiedades globales"
      subtitle="Supervisión de solo lectura de todas las copropiedades registradas. La gestión operativa corresponde a cada organización."
      badge={<Badge variant="secondary">Solo lectura</Badge>}
      action={
        <Button variant="outline" size="sm" onClick={loadData} disabled={loading} className="min-h-10">
          <RefreshCw className="h-4 w-4" aria-hidden="true" />
          Actualizar catálogo
        </Button>
      }
    />
  );

  if (loading) {
    return (
      <PageContainer>
        {header}
        <div role="status" aria-busy="true" aria-label="Cargando propiedades" className="space-y-6">
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            {[1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-24 rounded-lg" />
            ))}
          </div>
          <Skeleton className="h-16 w-full rounded-lg" />
          <Skeleton className="h-96 w-full rounded-lg" />
        </div>
      </PageContainer>
    );
  }

  if (loadError) {
    return (
      <PageContainer>
        {header}
        <ErrorState title="No se pudo cargar el inventario" message={loadError} onRetry={loadData} />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      {header}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard label="Total de copropiedades" value={stats.total} context="Inventario en plataforma" icon={Building2} />
        <MetricCard
          label="Copropiedades activas"
          value={stats.activas}
          context="En operación"
          icon={CheckCircle2}
          variant="success"
        />
        <MetricCard
          label="Inactivas o suspendidas"
          value={stats.inactivas}
          context="Operación detenida"
          icon={PauseCircle}
          variant={stats.inactivas > 0 ? 'warning' : 'secondary'}
        />
        <MetricCard
          label="Ciudades con cobertura"
          value={stats.ciudades}
          context="Presencia geográfica"
          icon={MapPin}
          variant="info"
        />
      </div>

      <Card>
        <CardContent className="p-4">
          <div className="flex flex-col gap-3 md:flex-row">
            <div className="relative flex-1">
              <Search
                className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
                aria-hidden="true"
              />
              <Input
                type="search"
                aria-label="Buscar propiedades"
                placeholder="Buscar por propiedad, organización, ciudad o dirección..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="h-10 pl-9 text-sm"
              />
            </div>

            <div className="flex flex-wrap gap-2">
              <select
                aria-label="Filtrar por estado"
                value={filtroEstado}
                onChange={(e) => setFiltroEstado(e.target.value)}
                className={FILTER_SELECT}
              >
                <option value="">Todos los estados</option>
                <option value="ACTIVA">Activa</option>
                <option value="INACTIVA">Inactiva</option>
              </select>

              <select
                aria-label="Filtrar por ocupación"
                value={filtroOcupacion}
                onChange={(e) => setFiltroOcupacion(e.target.value)}
                className={FILTER_SELECT}
              >
                <option value="">Cualquier ocupación</option>
                <option value="MIXTA">Mixta</option>
                <option value="PROPIETARIOS">Propietarios</option>
                <option value="ARRENDATARIOS">Arrendatarios</option>
              </select>

              {hayFiltros && (
                <Button variant="ghost" size="sm" onClick={limpiarFiltros} className="h-10">
                  Limpiar
                </Button>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      {filtrados.length === 0 ? (
        <div className="table-container">
          <EmptyState
            icon={Building2}
            title="No se encontraron propiedades"
            subtitle={
              hayFiltros
                ? 'Ajusta los filtros de búsqueda para ver resultados.'
                : 'Aún no hay propiedades registradas en el catálogo global.'
            }
          >
            {hayFiltros && (
              <Button variant="outline" size="sm" className="mt-3" onClick={limpiarFiltros}>
                Limpiar filtros
              </Button>
            )}
          </EmptyState>
        </div>
      ) : (
        <div className="table-container max-h-[70vh] overflow-y-auto">
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">ID</th>
                <th scope="col">Copropiedad</th>
                <th scope="col">Organización cliente</th>
                <th scope="col">Ubicación</th>
                <th scope="col">Ocupación predominante</th>
                <th scope="col" className="text-center">Estado</th>
                <th scope="col" className="text-right">
                  <span className="sr-only">Acciones</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {filtrados.map((p) => {
                const isActiva = (p.estado || '').toUpperCase() === 'ACTIVA';
                return (
                  <tr key={p.id}>
                    <td className="whitespace-nowrap font-mono text-xs font-semibold text-text-secondary tabular-nums">
                      #{p.id}
                    </td>
                    <td className="min-w-[10rem]">
                      <div className="text-sm font-semibold text-on-surface">{p.nombre}</div>
                      <div className="text-xs text-text-muted">{p.tipoPropiedadNombre || 'Copropiedad'}</div>
                    </td>
                    <td className="min-w-[10rem]">
                      <div className="font-medium text-on-surface">{p.organizacionNombre || 'Organización directa'}</div>
                      {p.idOrganizacion && (
                        <div className="font-mono text-xs text-text-muted tabular-nums">ID org.: {p.idOrganizacion}</div>
                      )}
                    </td>
                    <td>
                      <div className="text-on-surface">{p.ciudad || 'No especificada'}</div>
                      <div className="max-w-[200px] truncate text-xs text-text-muted">{p.direccion || '—'}</div>
                    </td>
                    <td>
                      <Badge variant="outline">{p.tipoOcupacionPredominante || 'MIXTA'}</Badge>
                    </td>
                    <td className="text-center">
                      <Badge variant={isActiva ? 'success' : 'warning'}>{isActiva ? 'Activa' : 'Inactiva'}</Badge>
                    </td>
                    <td className="text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setSelectedProperty(p)}
                        className="h-10 px-2.5 text-xs sm:h-8"
                        aria-label={`Ver ficha de ${p.nombre}`}
                      >
                        <Eye className="h-4 w-4" aria-hidden="true" />
                        Ver ficha
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <Dialog open={!!selectedProperty} onOpenChange={(open) => !open && setSelectedProperty(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="pr-8">Ficha técnica: {selectedProperty?.nombre}</DialogTitle>
            <DialogDescription>Datos del catálogo global. Esta vista es de solo lectura.</DialogDescription>
          </DialogHeader>

          {selectedProperty && (
            <div className="space-y-3 py-1">
              <dl className="grid grid-cols-2 gap-3 rounded-lg border border-border bg-surface-container p-3">
                <Dato label="ID de propiedad">
                  <span className="font-mono font-bold tabular-nums">#{selectedProperty.id}</span>
                </Dato>
                <Dato label="Estado operativo">
                  <Badge variant={(selectedProperty.estado || '').toUpperCase() === 'ACTIVA' ? 'success' : 'warning'}>
                    {selectedProperty.estado || 'ACTIVA'}
                  </Badge>
                </Dato>
                <Dato label="Organización titular">{selectedProperty.organizacionNombre || 'N/A'}</Dato>
                <Dato label="ID de organización">
                  <span className="font-mono tabular-nums">#{selectedProperty.idOrganizacion || '-'}</span>
                </Dato>
              </dl>

              <dl className="space-y-2 rounded-lg border border-border bg-surface-container p-3">
                <Dato label="Dirección y ciudad">
                  {selectedProperty.direccion || 'Sin dirección registrada'}, {selectedProperty.ciudad || 'Colombia'}
                </Dato>
                <Dato label="Tipo de ocupación predominante">{selectedProperty.tipoOcupacionPredominante || 'MIXTA'}</Dato>
                {selectedProperty.tipoPropiedadNombre && (
                  <Dato label="Tipología">{selectedProperty.tipoPropiedadNombre}</Dato>
                )}
              </dl>

              <div className="flex items-start gap-2 rounded-md border border-tint-blue-fg/25 bg-tint-blue-bg p-3 text-xs text-tint-blue-fg">
                <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                <span>
                  La operación de unidades, habitantes, visitas y parqueaderos corresponde a la consola del{' '}
                  <strong>administrador de propiedad</strong>, y su supervisión corporativa a la{' '}
                  <strong>organización</strong>.
                </span>
              </div>
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setSelectedProperty(null)}>
              Cerrar ficha
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
