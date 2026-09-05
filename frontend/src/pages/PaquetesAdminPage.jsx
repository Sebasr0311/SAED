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
import {
  Breadcrumb,
  BreadcrumbList,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbSeparator,
  BreadcrumbPage,
} from '../components/ui/Breadcrumb.jsx';

const ESTADOS_FILTRO = [
  { id: 'TODOS', label: 'Todos los estados' },
  { id: 'RECIBIDO', label: 'En Custodia / Recibidos' },
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
    <PageContainer>
      {/* 1. Breadcrumb de Navegación */}
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink to="/dashboard">Panel de Control</BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>Custodia de Paquetería</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* 2. Cabecera Contextual Enterprise */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-border/60 pb-5">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5">
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              Custodia y Auditoría de Paquetería
            </h1>
            <span className="px-2 py-0.5 text-xs font-semibold bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 rounded border border-slate-200 dark:border-slate-700">
              ADMIN_PROPIEDAD
            </span>
          </div>
          <p className="text-sm text-muted-foreground">
            Monitoreo en tiempo real de encomiendas recibidas en garita, tiempos de custodia y trazabilidad de entrega.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => refetch()}
            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold rounded-lg bg-card border border-border hover:bg-muted text-foreground transition-colors shadow-sm min-h-[44px]"
            title="Refrescar lista"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-emerald-500' : ''}`} />
            Actualizar
          </button>
        </div>
      </div>

      {/* 3. Strip de Métricas de Auditoría */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          label="Total Encomiendas"
          value={stats.total}
          context="Histórico de recepciones"
          icon={Boxes}
          variant="neutral"
        />
        <MetricCard
          label="En Custodia Activa"
          value={stats.enCustodia}
          context="Pendientes de retiro"
          icon={Inbox}
          variant={stats.enCustodia > 0 ? 'warning' : 'primary'}
        />
        <MetricCard
          label="Entregados"
          value={stats.entregados}
          context="Despachados a residentes"
          icon={CheckCircle2}
          variant="success"
        />
        <MetricCard
          label="Tasa de Despacho"
          value={`${stats.tasa}%`}
          context="Efectividad de entrega"
          icon={Percent}
          variant="info"
        />
      </div>

      {/* 4. Barra de Filtros y Búsqueda */}
      <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3 bg-card p-4 rounded-xl border border-border/80 shadow-sm">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-2.5 w-4 h-4 text-muted-foreground" />
          <input
            type="text"
            placeholder="Buscar por apto, destinatario, empresa o guía..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-9 pr-8 py-2 text-sm rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
          />
          {search && (
            <button
              onClick={() => setSearch('')}
              className="absolute right-2.5 top-2.5 text-muted-foreground hover:text-foreground"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <select
            value={filtroEstado}
            onChange={(e) => setFiltroEstado(e.target.value)}
            className="px-3 py-2 text-xs font-medium rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
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
            className="px-3 py-2 text-xs font-medium rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
          >
            {TAMANOS_FILTRO.map((t) => (
              <option key={t.id} value={t.id}>
                {t.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* 5. Vista Desktop: Tabla Analítica */}
      <div className="hidden md:block bg-card rounded-xl border border-border/80 shadow-sm overflow-hidden">
        <table className="w-full text-left text-sm">
          <thead className="bg-muted/40 border-b border-border/80 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            <tr>
              <th className="px-4 py-3">Unidad</th>
              <th className="px-4 py-3">Destinatario</th>
              <th className="px-4 py-3">Empresa & Guía</th>
              <th className="px-4 py-3">Descripción</th>
              <th className="px-4 py-3">Recepción</th>
              <th className="px-4 py-3">Foto</th>
              <th className="px-4 py-3">Estado</th>
              <th className="px-4 py-3 text-right">Acción</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border/60">
            {filtrados.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-4 py-12 text-center text-muted-foreground">
                  <Inbox className="w-10 h-10 mx-auto text-muted-foreground/40 mb-2" />
                  <p className="font-semibold text-foreground">No se encontraron paquetes registrados</p>
                  <p className="text-xs text-muted-foreground mt-1">
                    {search ? 'Intente ajustar los términos de búsqueda o filtros.' : 'No hay encomiendas registradas.'}
                  </p>
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
                    className="hover:bg-muted/30 transition-colors cursor-pointer"
                  >
                    <td className="px-4 py-3 font-semibold text-foreground whitespace-nowrap">
                      Apto {p.numeroApartamento || 'N/A'}
                    </td>
                    <td className="px-4 py-3 text-foreground whitespace-nowrap">
                      {p.nombreResidente || p.nombreDestinatario || 'Residente'}
                    </td>
                    <td className="px-4 py-3 text-foreground whitespace-nowrap">
                      <span className="font-medium text-emerald-600 dark:text-emerald-400">
                        {p.empresaMensajeria || 'Mensajería'}
                      </span>
                      {p.numeroGuia && (
                        <span className="block text-xs font-mono text-muted-foreground">
                          {p.numeroGuia}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-foreground max-w-xs truncate">
                      <span>{p.descripcion || p.titulo || 'Sin descripción'}</span>
                      {p.tamano && (
                        <span className="ml-2 inline-block px-1.5 py-0.5 text-[10px] font-semibold bg-muted text-muted-foreground rounded">
                          {p.tamano}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap">
                      {formatDate(p.fechaRecepcion || p.fechaCreacion)}
                    </td>
                    <td className="px-4 py-3">
                      {p.fotoPaqueteUrl || p.fotoCaptura ? (
                        <img
                          src={imageSrc(p.fotoPaqueteUrl || p.fotoCaptura)}
                          alt="Foto del paquete"
                          className="w-10 h-10 object-cover rounded-lg border border-border"
                        />
                      ) : (
                        <span className="text-xs text-muted-foreground italic">Sin foto</span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                          isEntregado
                            ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                            : 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20'
                        }`}
                      >
                        {isEntregado ? 'Entregado' : 'En Custodia'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right whitespace-nowrap">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setDetalle(p);
                        }}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg border border-border bg-card hover:bg-muted text-foreground transition-colors min-h-[36px]"
                      >
                        <Eye className="w-3.5 h-3.5" />
                        Auditar
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
          <div className="bg-card rounded-xl border border-border/80 p-8 text-center text-muted-foreground">
            <Inbox className="w-10 h-10 mx-auto text-muted-foreground/40 mb-2" />
            <p className="font-semibold text-foreground">No se encontraron paquetes</p>
          </div>
        ) : (
          filtrados.map((p) => {
            const id = p.idPaquete || p.idMensaje;
            const isEntregado = p.entregado || p.estado === 'ENTREGADO';
            return (
              <div
                key={id}
                onClick={() => setDetalle(p)}
                className="bg-card rounded-xl border border-border/80 p-4 space-y-3 shadow-sm cursor-pointer"
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
                  <span
                    className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                      isEntregado
                        ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                        : 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20'
                    }`}
                  >
                    {isEntregado ? 'Entregado' : 'En Custodia'}
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span className="text-muted-foreground block">Mensajería:</span>
                    <span className="font-semibold text-foreground">
                      {p.empresaMensajeria || 'Mensajería'}
                    </span>
                    {p.numeroGuia && (
                      <span className="font-mono text-muted-foreground block text-[11px]">
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
                    <span className="ml-2 px-1.5 py-0.5 text-[10px] font-semibold bg-card border border-border rounded text-muted-foreground">
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
                    Ver Trazabilidad Completa
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* 7. Modal de Auditoría y Trazabilidad */}
      <Modal
        open={!!detalle}
        onClose={() => setDetalle(null)}
        title="Auditoría de Encomienda"
      >
        {detalle && (
          <div className="space-y-4 p-1">
            {(detalle.fotoPaqueteUrl || detalle.fotoCaptura) && (
              <div className="aspect-video w-full rounded-xl overflow-hidden border border-border bg-slate-950">
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
                <span className="font-mono text-foreground">{detalle.numeroGuia || 'Sin guía'}</span>
              </div>
            </div>

            <div className="p-3 rounded-lg bg-muted/40 border border-border/60 text-xs space-y-1">
              <span className="text-muted-foreground block">Descripción:</span>
              <p className="text-foreground">{detalle.descripcion || detalle.titulo}</p>
            </div>

            {/* Trazabilidad de Auditoría */}
            <div className="space-y-2 border-t border-border/60 pt-3">
              <h4 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Pista de Trazabilidad y Custodia
              </h4>
              <div className="space-y-2 text-xs">
                <div className="flex items-center justify-between p-2 rounded-lg bg-muted/30">
                  <span className="text-muted-foreground">Fecha de Recepción:</span>
                  <span className="font-medium text-foreground">
                    {formatDate(detalle.fechaRecepcion || detalle.fechaCreacion)}
                  </span>
                </div>

                {detalle.codigoRetiroPin && (
                  <div className="flex items-center justify-between p-2 rounded-lg bg-muted/30 font-mono">
                    <span className="text-muted-foreground font-sans">PIN de Seguridad:</span>
                    <span className="font-bold text-emerald-600 dark:text-emerald-400">
                      {detalle.codigoRetiroPin}
                    </span>
                  </div>
                )}

                <div className="flex items-center justify-between p-2 rounded-lg bg-muted/30">
                  <span className="text-muted-foreground">Estado Actual:</span>
                  <span
                    className={`font-semibold ${
                      detalle.entregado || detalle.estado === 'ENTREGADO'
                        ? 'text-emerald-600 dark:text-emerald-400'
                        : 'text-amber-600 dark:text-amber-400'
                    }`}
                  >
                    {detalle.entregado || detalle.estado === 'ENTREGADO' ? 'Entregado' : 'En Custodia'}
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
                className="px-4 py-2 text-xs font-semibold rounded-lg bg-muted hover:bg-muted/80 text-foreground min-h-[44px]"
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
