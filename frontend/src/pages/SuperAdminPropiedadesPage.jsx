import { useEffect, useState, useMemo } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
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
import { toast } from 'sonner';

export default function SuperAdminPropiedadesPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [filtroEstado, setFiltroEstado] = useState('');
  const [filtroOcupacion, setFiltroOcupacion] = useState('');
  const [selectedProperty, setSelectedProperty] = useState(null);

  async function loadData() {
    try {
      setLoading(true);
      const res = await api.get('/properties');
      const list = res?.data || res || [];
      setItems(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error(err);
      toast.error('Error al cargar el inventario global de propiedades');
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

  if (loading) {
    return (
      <div className="p-6 space-y-6">
        <div className="flex justify-between items-center">
          <Skeleton className="h-8 w-64" />
          <Skeleton className="h-10 w-32" />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-24 rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-96 w-full rounded-xl" />
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      {/* Cabecera Contextual Enterprise */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-border/70 pb-5">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              Propiedades Globales — Monitoreo SaaS
            </h1>
            <Badge variant="outline" className="bg-primary/10 text-primary border-primary/20 font-semibold px-2.5 py-0.5">
              GLOBAL SCOPE
            </Badge>
            <Badge variant="secondary" className="font-semibold text-xs">
              SOLO OBSERVABILIDAD
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Supervisión transversal de todas las copropiedades registradas en la plataforma. La gestión operativa corresponde a cada Organización.
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={loadData}
          className="flex items-center gap-2 self-start md:self-auto min-h-[40px]"
        >
          <span className="material-symbols-outlined text-sm">refresh</span>
          Actualizar Catálogo
        </Button>
      </div>

      {/* KPI Cards Strip */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="border-border/80">
          <CardHeader className="flex flex-row items-center justify-between pb-1 pt-4">
            <CardTitle className="text-xs font-medium text-muted-foreground">Total Copropiedades</CardTitle>
            <span className="material-symbols-outlined text-primary text-lg">apartment</span>
          </CardHeader>
          <CardContent className="pb-4">
            <div className="text-2xl font-bold text-foreground">{stats.total}</div>
            <p className="text-[11px] text-muted-foreground mt-0.5">Inventario en plataforma</p>
          </CardContent>
        </Card>

        <Card className="border-border/80">
          <CardHeader className="flex flex-row items-center justify-between pb-1 pt-4">
            <CardTitle className="text-xs font-medium text-muted-foreground">Copropiedades Activas</CardTitle>
            <span className="material-symbols-outlined text-emerald-600 dark:text-emerald-400 text-lg">check_circle</span>
          </CardHeader>
          <CardContent className="pb-4">
            <div className="text-2xl font-bold text-emerald-600 dark:text-emerald-400">{stats.activas}</div>
            <p className="text-[11px] text-muted-foreground mt-0.5">En operación normal</p>
          </CardContent>
        </Card>

        <Card className="border-border/80">
          <CardHeader className="flex flex-row items-center justify-between pb-1 pt-4">
            <CardTitle className="text-xs font-medium text-muted-foreground">Inactivas / Suspendidas</CardTitle>
            <span className="material-symbols-outlined text-amber-500 text-lg">pause_circle</span>
          </CardHeader>
          <CardContent className="pb-4">
            <div className="text-2xl font-bold text-amber-600 dark:text-amber-400">{stats.inactivas}</div>
            <p className="text-[11px] text-muted-foreground mt-0.5">Operaciones congeladas</p>
          </CardContent>
        </Card>

        <Card className="border-border/80">
          <CardHeader className="flex flex-row items-center justify-between pb-1 pt-4">
            <CardTitle className="text-xs font-medium text-muted-foreground">Ciudades con Cobertura</CardTitle>
            <span className="material-symbols-outlined text-blue-500 text-lg">location_on</span>
          </CardHeader>
          <CardContent className="pb-4">
            <div className="text-2xl font-bold text-foreground">{stats.ciudades}</div>
            <p className="text-[11px] text-muted-foreground mt-0.5">Presencia geográfica</p>
          </CardContent>
        </Card>
      </div>

      {/* Barra de Filtros y Búsqueda */}
      <Card className="border-border/80">
        <CardContent className="p-4">
          <div className="flex flex-col md:flex-row gap-3">
            <div className="relative flex-1">
              <span className="material-symbols-outlined absolute left-3 top-2.5 text-muted-foreground text-sm">
                search
              </span>
              <Input
                placeholder="Buscar por propiedad, organización cliente, ciudad o dirección..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9 text-sm"
              />
            </div>

            <div className="flex gap-2">
              <select
                value={filtroEstado}
                onChange={(e) => setFiltroEstado(e.target.value)}
                className="px-3 py-2 text-xs rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
              >
                <option value="">Todos los estados</option>
                <option value="ACTIVA">Activa</option>
                <option value="INACTIVA">Inactiva</option>
              </select>

              <select
                value={filtroOcupacion}
                onChange={(e) => setFiltroOcupacion(e.target.value)}
                className="px-3 py-2 text-xs rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
              >
                <option value="">Cualquier ocupación</option>
                <option value="MIXTA">Mixta</option>
                <option value="PROPIETARIOS">Propietarios</option>
                <option value="ARRENDATARIOS">Arrendatarios</option>
              </select>

              {(search || filtroEstado || filtroOcupacion) && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setSearch('');
                    setFiltroEstado('');
                    setFiltroOcupacion('');
                  }}
                  className="text-xs"
                >
                  Limpiar
                </Button>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Tabla de Propiedades Globales */}
      <Card className="border-border/80 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="border-b border-border/80 bg-muted/30 text-muted-foreground font-medium uppercase text-[11px] tracking-wider">
                <th className="py-3.5 px-4 font-semibold">ID</th>
                <th className="py-3.5 px-4 font-semibold">Copropiedad</th>
                <th className="py-3.5 px-4 font-semibold">Organización Cliente</th>
                <th className="py-3.5 px-4 font-semibold">Ubicación</th>
                <th className="py-3.5 px-4 font-semibold">Ocupación Predominante</th>
                <th className="py-3.5 px-4 font-semibold text-center">Estado</th>
                <th className="py-3.5 px-4 font-semibold text-right">Observabilidad</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/60">
              {filtrados.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-muted-foreground">
                    <div className="flex flex-col items-center justify-center space-y-2">
                      <span className="material-symbols-outlined text-4xl text-muted-foreground/60">
                        apartment
                      </span>
                      <p className="text-sm font-medium">No se encontraron propiedades</p>
                      <p className="text-xs text-muted-foreground">
                        {search || filtroEstado || filtroOcupacion
                          ? 'Ajuste los filtros de búsqueda para visualizar resultados.'
                          : 'No hay propiedades registradas en el catálogo global.'}
                      </p>
                    </div>
                  </td>
                </tr>
              ) : (
                filtrados.map((p) => {
                  const isActiva = (p.estado || '').toUpperCase() === 'ACTIVA';
                  return (
                    <tr key={p.id} className="hover:bg-muted/40 transition-colors">
                      <td className="py-3 px-4 font-mono font-bold text-muted-foreground">#{p.id}</td>
                      <td className="py-3 px-4">
                        <div className="font-semibold text-sm text-foreground">{p.nombre}</div>
                        <div className="text-[11px] text-muted-foreground">{p.tipoPropiedadNombre || 'Copropiedad'}</div>
                      </td>
                      <td className="py-3 px-4">
                        <div className="font-medium text-foreground flex items-center gap-1.5">
                          <span className="material-symbols-outlined text-xs text-primary">domain</span>
                          {p.organizacionNombre || 'Organización Directa'}
                        </div>
                        {p.idOrganizacion && (
                          <div className="text-[10px] font-mono text-muted-foreground">ID Org: {p.idOrganizacion}</div>
                        )}
                      </td>
                      <td className="py-3 px-4">
                        <div className="text-foreground">{p.ciudad || 'No especificada'}</div>
                        <div className="text-[11px] text-muted-foreground truncate max-w-[200px]">{p.direccion || '-'}</div>
                      </td>
                      <td className="py-3 px-4">
                        <Badge variant="outline" className="font-mono text-[10px]">
                          {p.tipoOcupacionPredominante || 'MIXTA'}
                        </Badge>
                      </td>
                      <td className="py-3 px-4 text-center">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold border ${
                            isActiva
                              ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20'
                              : 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20'
                          }`}
                        >
                          {isActiva ? 'ACTIVA' : 'INACTIVA'}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setSelectedProperty(p)}
                          className="h-8 px-2.5 text-xs text-muted-foreground hover:text-foreground"
                          title="Ver ficha técnica de observabilidad"
                        >
                          <span className="material-symbols-outlined text-sm mr-1">visibility</span>
                          Ver Ficha
                        </Button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Modal Ficha de Observabilidad Global */}
      <Dialog open={!!selectedProperty} onOpenChange={(open) => !open && setSelectedProperty(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base">
              <span className="material-symbols-outlined text-primary">apartment</span>
              Ficha Técnica — {selectedProperty?.nombre}
            </DialogTitle>
            <DialogDescription className="text-xs">
              Metadatos del catálogo SaaS global. Esta vista es de solo lectura.
            </DialogDescription>
          </DialogHeader>

          {selectedProperty && (
            <div className="space-y-3 py-2 text-xs">
              <div className="grid grid-cols-2 gap-3 p-3 bg-muted/40 rounded-lg border border-border/80">
                <div>
                  <span className="text-muted-foreground block text-[10px] uppercase font-semibold">ID Catastral SaaS</span>
                  <span className="font-mono font-bold text-foreground">#{selectedProperty.id}</span>
                </div>
                <div>
                  <span className="text-muted-foreground block text-[10px] uppercase font-semibold">Estado Operativo</span>
                  <span
                    className={`font-semibold ${
                      (selectedProperty.estado || '').toUpperCase() === 'ACTIVA'
                        ? 'text-emerald-600 dark:text-emerald-400'
                        : 'text-amber-600 dark:text-amber-400'
                    }`}
                  >
                    {selectedProperty.estado || 'ACTIVA'}
                  </span>
                </div>
                <div>
                  <span className="text-muted-foreground block text-[10px] uppercase font-semibold">Organización Titular</span>
                  <span className="font-medium text-foreground">{selectedProperty.organizacionNombre || 'N/A'}</span>
                </div>
                <div>
                  <span className="text-muted-foreground block text-[10px] uppercase font-semibold">ID Organización</span>
                  <span className="font-mono text-foreground">#{selectedProperty.idOrganizacion || '-'}</span>
                </div>
              </div>

              <div className="p-3 bg-muted/40 rounded-lg border border-border/80 space-y-2">
                <div>
                  <span className="text-muted-foreground block text-[10px] uppercase font-semibold">Dirección & Ciudad</span>
                  <span className="text-foreground">
                    {selectedProperty.direccion || 'Sin dirección registrada'}, {selectedProperty.ciudad || 'Colombia'}
                  </span>
                </div>
                <div>
                  <span className="text-muted-foreground block text-[10px] uppercase font-semibold">Tipo de Ocupación Predominante</span>
                  <span className="font-medium text-foreground">{selectedProperty.tipoOcupacionPredominante || 'MIXTA'}</span>
                </div>
                {selectedProperty.tipoPropiedadNombre && (
                  <div>
                    <span className="text-muted-foreground block text-[10px] uppercase font-semibold">Tipología Inmobiliaria</span>
                    <span className="text-foreground">{selectedProperty.tipoPropiedadNombre}</span>
                  </div>
                )}
              </div>

              <div className="p-2.5 rounded-lg bg-blue-500/10 border border-blue-500/20 text-blue-700 dark:text-blue-300 text-[11px] flex items-start gap-2">
                <span className="material-symbols-outlined text-sm shrink-0 mt-0.5">info</span>
                <span>
                  La operación de unidades, habitantes, visitas y parqueaderos corresponde a la consola del <strong>Administrador de Propiedad</strong> y su supervisión corporativa a la <strong>Organización</strong>.
                </span>
              </div>
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setSelectedProperty(null)}>
              Cerrar Ficha
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
