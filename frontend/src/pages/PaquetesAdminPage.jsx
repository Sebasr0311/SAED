import { useState, useMemo } from 'react';
import {
  Inbox,
  CheckCircle2,
  Search,
  X,
  RefreshCw,
  Eye,
  Boxes,
  Percent,
} from 'lucide-react';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useFetch } from '../lib/hooks.js';
import { formatDate, imageSrc } from '../lib/utils.js';
import PageContainer from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { Badge } from '../components/ui/badge.tsx';

const ESTADOS_FILTRO = [
  { id: 'TODOS', label: 'Todos los estados' },
  { id: 'RECIBIDO', label: 'En custodia' },
  { id: 'ENTREGADO', label: 'Entregados' },
];

const TAMANOS_FILTRO = [
  { id: 'TODOS', label: 'Todos los tamaños' },
  { id: 'SOBRE', label: 'Sobre' },
  { id: 'PEQUENO', label: 'Pequeño' },
  { id: 'MEDIANO', label: 'Mediano' },
  { id: 'GRANDE', label: 'Grande' },
  { id: 'VOLUMINOSO', label: 'Voluminoso' },
];

export default function PaquetesAdminPage() {
  const api = useTenantApi();
  const [search, setSearch] = useState('');
  const [filtroEstado, setFiltroEstado] = useState('TODOS');
  const [filtroTamano, setFiltroTamano] = useState('TODOS');
  const [detalle, setDetalle] = useState(null);

  const {
    data: paquetesRaw,
    loading,
    error: loadError,
    refetch,
  } = useFetch(() => api.get('/paquetes'), []);

  const all = useMemo(() => {
    const list = paquetesRaw?.items || paquetesRaw || [];
    return Array.isArray(list) ? list : [];
  }, [paquetesRaw]);

  // Filtrado reactivo
  const filtrados = useMemo(() => {
    return all.filter((p) => {
      // Filtro de Estado
      const isEntregado = p.entregado || p.estado === 'ENTREGADO';
      if (filtroEstado === 'RECIBIDO' && isEntregado) return false;
      if (filtroEstado === 'ENTREGADO' && !isEntregado) return false;

      // Filtro de Tamaño
      if (filtroTamano !== 'TODOS' && p.tamano !== filtroTamano) return false;

      // Búsqueda libre
      if (!search.trim()) return true;
      const term = search.toLowerCase();
      const numApto = String(p.numeroApartamento || '').toLowerCase();
      const resName = String(p.nombreResidente || p.nombreDestinatario || '').toLowerCase();
      const emp = String(p.empresaMensajeria || '').toLowerCase();
      const guia = String(p.numeroGuia || '').toLowerCase();
      const desc = String(p.descripcion || p.titulo || '').toLowerCase();

      return (
        numApto.includes(term) ||
        resName.includes(term) ||
        emp.includes(term) ||
        guia.includes(term) ||
        desc.includes(term)
      );
    });
  }, [all, search, filtroEstado, filtroTamano]);

  // Métricas
  const stats = useMemo(() => {
    const total = all.length;
    const entregados = all.filter((p) => p.entregado || p.estado === 'ENTREGADO').length;
    const enCustodia = total - entregados;
    const tasa = total > 0 ? Math.round((entregados / total) * 100) : 0;
    return { total, entregados, enCustodia, tasa };
  }, [all]);

  return (
    <PageContainer
      breadcrumbs={[
        { label: 'Panel de control', href: '/dashboard' },
        { label: 'Paquetería', active: true },
      ]}
    >
      <PageHeader
        title="Custodia de paquetería"
        subtitle="Encomiendas recibidas en la garita, tiempo en custodia y entregas confirmadas con PIN."
        action={
          <button type="button" onClick={() => refetch()} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-md border border-input/60 bg-surface px-3.5 text-sm font-semibold text-on-surface shadow-xs transition-colors duration-150 hover:border-primary/70 hover:bg-surface-container sm:min-h-10" aria-label="Actualizar lista de paquetes">
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
            Actualizar
          </button>
        }
      />

      {/* 3. Strip de Métricas de Auditoría */}
      <div className="grid grid-cols-1 gap-3 min-[420px]:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Total de encomiendas"
          value={stats.total}
          context="Recepciones registradas"
          icon={Boxes}
          variant="secondary"
        />
        <MetricCard
          label="En custodia"
          value={stats.enCustodia}
          context="Pendientes de retiro"
          icon={Inbox}
          variant={stats.enCustodia > 0 ? 'warning' : 'primary'}
        />
        <MetricCard
          label="Entregados"
          value={stats.entregados}
          context="Entregados a residentes"
          icon={CheckCircle2}
          variant="success"
        />
        <MetricCard
          label="Tasa de entrega"
          value={`${stats.tasa}%`}
          context="Entregados sobre recibidos"
          icon={Percent}
          variant="info"
        />
      </div>

      {/* 4. Barra de Filtros y Búsqueda */}
      <div className="flex flex-col items-stretch justify-between gap-3 rounded-lg border border-border bg-surface p-3 shadow-sm sm:p-4 md:flex-row md:items-center">
        <div className="relative flex-1 max-w-md">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" aria-hidden="true" />
          <input
            type="search"
            aria-label="Buscar paquetes"
            placeholder="Apto, destinatario, empresa o guía"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 pl-9 pr-10"
          />
          {search && (
            <button
              type="button"
              onClick={() => setSearch('')}
              aria-label="Limpiar búsqueda"
              className="absolute right-0 top-0 flex h-10 w-10 items-center justify-center text-text-secondary hover:text-on-surface"
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <select
            value={filtroEstado}
            onChange={(e) => setFiltroEstado(e.target.value)}
            aria-label="Filtrar por estado"
            className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 w-auto px-3"
          >
            {ESTADOS_FILTRO.map((e) => (
              <option key={e.id} value={e.id}>
                {e.label}
              </option>
            ))}
          </select>

          <select
            value={filtroTamano}
            onChange={(e) => setFiltroTamano(e.target.value)}
            aria-label="Filtrar por tamaño"
            className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 w-auto px-3"
          >
            {TAMANOS_FILTRO.map((t) => (
              <option key={t.id} value={t.id}>
                {t.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {loading && !paquetesRaw ? (
        <div className="rounded-lg border border-border bg-surface shadow-sm">
          <LoadingState message="Cargando encomiendas..." />
        </div>
      ) : loadError && !paquetesRaw ? (
        <ErrorState
          title="No se pudieron cargar las encomiendas"
          message={loadError.message || 'Revise la conexión e intente de nuevo.'}
          onRetry={refetch}
        />
      ) : (
      <>
      {/* 5. Vista Desktop: Tabla Analítica Responsiva */}
      <div className="hidden max-h-[70vh] overflow-auto rounded-lg border border-border bg-surface shadow-sm md:block">
        <table className="w-full min-w-[920px] text-left text-sm">
          <thead className="sticky top-0 z-10 border-b border-border bg-surface-container text-xs font-bold text-text-secondary">
            <tr>
              <th className="px-4 py-3.5 w-28 whitespace-nowrap">Unidad</th>
              <th className="px-4 py-3.5 min-w-[150px]">Destinatario</th>
              <th className="px-4 py-3.5 min-w-[140px]">Empresa & Guía</th>
              <th className="px-4 py-3.5 min-w-[180px] max-w-xs">Descripción</th>
              <th className="px-4 py-3.5 w-28 whitespace-nowrap">Recepción</th>
              <th className="px-4 py-3.5 w-20 text-center">Foto</th>
              <th className="px-4 py-3.5 w-32 text-center whitespace-nowrap">Estado</th>
              <th className="px-4 py-3.5 w-28 text-right whitespace-nowrap sticky right-0 bg-surface-container">
                Acción
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border/60">
            {filtrados.length === 0 ? (
              <tr>
                <td colSpan={8} className="p-0">
                  <EmptyState
                    icon={Inbox}
                    title={search || filtroEstado !== 'TODOS' || filtroTamano !== 'TODOS' ? 'Ningún paquete coincide con los filtros' : 'Aún no hay encomiendas registradas'}
                    subtitle={
                      search || filtroEstado !== 'TODOS' || filtroTamano !== 'TODOS'
                        ? 'Cambie la búsqueda o los filtros.'
                        : 'Las encomiendas que reciba la portería aparecerán aquí.'
                    }
                    className="py-10"
                  />
                </td>
              </tr>
            ) : (
              filtrados.map((p) => {
                const id = p.idPaquete || p.idMensaje;
                const isEntregado = p.entregado || p.estado === 'ENTREGADO';
                return (
                  <tr
                    key={id}
                    onClick={() => setDetalle(p)}
                    className="group cursor-pointer transition-colors hover:bg-surface-container"
                  >
                    <td className="px-4 py-3 font-semibold text-foreground whitespace-nowrap">
                      Apto {p.numeroApartamento || 'N/A'}
                    </td>
                    <td className="px-4 py-3 text-foreground whitespace-nowrap font-medium">
                      {p.nombreResidente || p.nombreDestinatario || 'Residente'}
                    </td>
                    <td className="px-4 py-3 text-foreground whitespace-nowrap">
                      <span className="font-medium text-on-surface">
                        {p.empresaMensajeria || 'Mensajería'}
                      </span>
                      {p.numeroGuia && (
                        <span className="block text-xs tabular-nums text-muted-foreground">
                          {p.numeroGuia}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-foreground max-w-xs truncate">
                      <span>{p.descripcion || p.titulo || 'Sin descripción'}</span>
                      {p.tamano && (
                        <Badge variant="secondary" className="ml-2">
                          {p.tamano}
                        </Badge>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap tabular-nums">
                      {formatDate(p.fechaRecepcion || p.fechaCreacion)}
                    </td>
                    <td className="px-4 py-3 text-center">
                      {p.fotoPaqueteUrl || p.fotoCaptura ? (
                        <img
                          src={imageSrc(p.fotoPaqueteUrl || p.fotoCaptura)}
                          alt="Foto del paquete"
                          className="w-10 h-10 object-cover rounded-lg border border-border inline-block"
                        />
                      ) : (
                        <span className="text-xs text-muted-foreground italic">Sin foto</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-center whitespace-nowrap">
                      <Badge variant={isEntregado ? 'success' : 'warning'}>{isEntregado ? 'Entregado' : 'En custodia'}</Badge>
                    </td>
                    <td className="px-4 py-3 text-right whitespace-nowrap sticky right-0 bg-surface transition-colors group-hover:bg-surface-container">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setDetalle(p);
                        }}
                        type="button"
                        className="inline-flex h-9 items-center gap-1.5 rounded-md border border-input/60 bg-surface px-3 text-xs font-semibold text-on-surface transition-colors hover:border-primary/70 hover:bg-surface-container"
                        title="Ver trazabilidad del paquete"
                      >
                        <Eye className="h-3.5 w-3.5" aria-hidden="true" />
                        Ver detalle
                      </button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* 6. Vista Móvil: Tarjetas Adaptativas */}
      <div className="md:hidden space-y-3">
        {filtrados.length === 0 ? (
          <EmptyState icon={Inbox} title="No se encontraron paquetes" className="rounded-lg border border-border bg-surface py-8" />
        ) : (
          filtrados.map((p) => {
            const id = p.idPaquete || p.idMensaje;
            const isEntregado = p.entregado || p.estado === 'ENTREGADO';
            return (
              <div
                key={id}
                onClick={() => setDetalle(p)}
                className="cursor-pointer space-y-3 rounded-lg border border-border bg-surface p-4 shadow-sm"
              >
                <div className="flex items-center justify-between border-b border-border/60 pb-2">
                  <div className="flex items-center gap-2">
                    <span className="text-base font-bold text-foreground">
                      Apto {p.numeroApartamento || 'N/A'}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      • {p.nombreResidente || p.nombreDestinatario || 'Residente'}
                    </span>
                  </div>
                  <Badge variant={isEntregado ? 'success' : 'warning'}>{isEntregado ? 'Entregado' : 'En custodia'}</Badge>
                </div>

                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span className="text-muted-foreground block">Mensajería:</span>
                    <span className="font-semibold text-foreground">
                      {p.empresaMensajeria || 'Mensajería'}
                    </span>
                    {p.numeroGuia && (
                      <span className="tabular-nums text-muted-foreground block text-xs">
                        {p.numeroGuia}
                      </span>
                    )}
                  </div>
                  <div>
                    <span className="text-muted-foreground block">Recepción:</span>
                    <span className="text-foreground">
                      {formatDate(p.fechaRecepcion || p.fechaCreacion)}
                    </span>
                  </div>
                </div>

                <div className="text-xs bg-muted/40 p-2.5 rounded-lg text-foreground">
                  <span className="font-medium">{p.descripcion || p.titulo || 'Sin descripción'}</span>
                  {p.tamano && (
                    <span className="ml-2 px-1.5 py-0.5 text-xs font-semibold bg-card border border-border rounded text-muted-foreground">
                      {p.tamano}
                    </span>
                  )}
                </div>

                <div className="flex items-center justify-end pt-1">
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      setDetalle(p);
                    }}
                    className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold rounded-lg bg-card border border-border hover:bg-muted text-foreground min-h-[44px]"
                  >
                    <Eye className="w-3.5 h-3.5" />
                    Ver detalle
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>
      </>
      )}

      {/* 7. Modal de Auditoría y Trazabilidad */}
      <Modal
        open={!!detalle}
        onClose={() => setDetalle(null)}
        title="Detalle de la encomienda"
      >
        {detalle && (
          <div className="space-y-4 p-1">
            {(detalle.fotoPaqueteUrl || detalle.fotoCaptura) && (
              <div className="aspect-video w-full rounded-xl overflow-hidden border border-border bg-surface-dim">
                <img
                  src={imageSrc(detalle.fotoPaqueteUrl || detalle.fotoCaptura)}
                  alt="Foto de la encomienda"
                  className="w-full h-full object-contain cursor-zoom-in"
                  onClick={() => window.open(imageSrc(detalle.fotoPaqueteUrl || detalle.fotoCaptura), '_blank')}
                />
              </div>
            )}

            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="p-2.5 rounded-lg bg-muted/40 border border-border/60">
                <span className="text-muted-foreground block">Apartamento:</span>
                <span className="font-bold text-foreground text-sm">
                  Apto {detalle.numeroApartamento || 'N/A'}
                </span>
              </div>
              <div className="p-2.5 rounded-lg bg-muted/40 border border-border/60">
                <span className="text-muted-foreground block">Destinatario:</span>
                <span className="font-semibold text-foreground">
                  {detalle.nombreResidente || detalle.nombreDestinatario || 'Residente'}
                </span>
              </div>
              <div className="p-2.5 rounded-lg bg-muted/40 border border-border/60">
                <span className="text-muted-foreground block">Mensajería:</span>
                <span className="font-semibold text-foreground">{detalle.empresaMensajeria || 'N/A'}</span>
              </div>
              <div className="p-2.5 rounded-lg bg-muted/40 border border-border/60">
                <span className="text-muted-foreground block">Número de Guía:</span>
                <span className="tabular-nums text-foreground">{detalle.numeroGuia || 'Sin guía'}</span>
              </div>
            </div>

            <div className="p-3 rounded-lg bg-muted/40 border border-border/60 text-xs space-y-1">
              <span className="text-muted-foreground block">Descripción:</span>
              <p className="text-foreground">{detalle.descripcion || detalle.titulo}</p>
            </div>

            {/* Trazabilidad de Auditoría */}
            <div className="space-y-2 border-t border-border/60 pt-3">
              <h4 className="text-xs font-semibold text-muted-foreground">
                Trazabilidad
              </h4>
              <div className="space-y-2 text-xs">
                <div className="flex items-center justify-between p-2 rounded-lg bg-muted/30">
                  <span className="text-muted-foreground">Fecha de Recepción:</span>
                  <span className="font-medium text-foreground">
                    {formatDate(detalle.fechaRecepcion || detalle.fechaCreacion)}
                  </span>
                </div>

                <div className="flex items-center justify-between p-2 rounded-lg bg-muted/30">
                  <span className="text-muted-foreground">Estado Actual:</span>
                  <span
                    className={`font-semibold ${
                      detalle.entregado || detalle.estado === 'ENTREGADO'
                        ? 'text-tint-green-fg'
                        : 'text-tint-amber-fg'
                    }`}
                  >
                    {detalle.entregado || detalle.estado === 'ENTREGADO' ? 'Entregado' : 'En custodia'}
                  </span>
                </div>

                {detalle.fechaEntrega && (
                  <div className="flex items-center justify-between p-2 rounded-lg bg-muted/30">
                    <span className="text-muted-foreground">Fecha de Entrega:</span>
                    <span className="font-medium text-foreground">
                      {formatDate(detalle.fechaEntrega)}
                    </span>
                  </div>
                )}
              </div>
            </div>

            <div className="pt-2 border-t border-border/60 flex justify-end">
              <button
                type="button"
                onClick={() => setDetalle(null)}
                className="inline-flex min-h-11 items-center justify-center gap-2 rounded-md border border-input/60 bg-surface px-3.5 text-sm font-semibold text-on-surface shadow-xs transition-colors duration-150 hover:border-primary/70 hover:bg-surface-container sm:min-h-10"
              >
                Cerrar
              </button>
            </div>
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}
