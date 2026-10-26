import { useState, useMemo } from 'react';
import { Ban, Eye, Gavel, HandCoins } from 'lucide-react';
import { toast } from 'sonner';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Button as IconButton } from '../components/ui/button.tsx';
import { Modal } from '../components/ui/Modal.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Dialog, DialogContent, DialogTitle } from '../components/ui/dialog.tsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDate, imageSrc } from '../lib/utils.js';

const SELECT_CLASS =
  'h-10 rounded-md border border-input bg-surface px-3 text-sm text-on-surface shadow-xs transition-[border-color,box-shadow] duration-150 hover:border-keyline/70 focus-visible:outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25 sm:h-9';

const ESTADO_META = {
  IMPUESTA: { variant: 'warning', label: 'Impuesta' },
  PENDIENTE: { variant: 'warning', label: 'Pendiente' },
  EN_DESCARGOS: { variant: 'info', label: 'En descargos' },
  RATIFICADA: { variant: 'destructive', label: 'Ratificada' },
  CONDONADA: { variant: 'secondary', label: 'Condonada' },
  PAGADA: { variant: 'success', label: 'Pagada' },
  ANULADA: { variant: 'outline', label: 'Anulada' },
};

const TIPO_META = {
  RUIDO: { variant: 'warning', label: 'Ruido' },
  PARQUEADERO: { variant: 'info', label: 'Parqueadero' },
  MULTA_SANCION: { variant: 'destructive', label: 'Sanción' },
  MULTA_CONVIVENCIA: { variant: 'lavender', label: 'Convivencia' },
};

function EstadoBadge({ estado }) {
  const m = ESTADO_META[estado] || { variant: 'secondary', label: estado };
  return <Badge variant={m.variant}>{m.label}</Badge>;
}

function TipoBadge({ tipo }) {
  const m = TIPO_META[tipo] || { variant: 'secondary', label: tipo };
  return <Badge variant={m.variant}>{m.label}</Badge>;
}

const PAGE_SIZE = 10;

export default function MultasPage() {
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [detalle, setDetalle] = useState(null);
  const [loadingDetalle, setLoadingDetalle] = useState(false);
  const [fotoGrande, setFotoGrande] = useState(null);

  const { data, loading, error, refetch } = useFetch(() => api.get('/multas/todas'), []);

  const items = useMemo(() => (data?.items || data || []).filter((m) => !filtroEstado || m.estado === filtroEstado), [data, filtroEstado]);
  const totalPages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = items.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  async function verDetalle(row) {
    setLoadingDetalle(true);
    try {
      const d = await api.get(`/multas/${row.idMulta}`);
      setDetalle(d);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setLoadingDetalle(false);
    }
  }

  async function marcarPagada(row) {
    try {
      await api.put(`/multas/${row.idMulta}/pagar`, { metodoPago: 'EFECTIVO' });
      toast.success('Multa marcada como pagada');
      refetch().catch(() => {});
      if (detalle?.idMulta === row.idMulta) setDetalle(null);
    } catch (err) {
      toast.error(err.message);
    }
  }

  const [confirmAnular, setConfirmAnular] = useState(null);

  async function anular() {
    if (!confirmAnular) return;
    try {
      await api.put(`/multas/${confirmAnular.idMulta}/anular`);
      toast.success('Multa anulada');
      refetch().catch(() => {});
      if (detalle?.idMulta === confirmAnular.idMulta) setDetalle(null);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setConfirmAnular(null);
    }
  }

  const puedePagar = (estado) => estado === 'IMPUESTA' || estado === 'RATIFICADA' || estado === 'PENDIENTE';
  const puedeAnular = (estado) => estado !== 'ANULADA' && estado !== 'PAGADA' && estado !== 'CONDONADA';

  const columns = [
    { key: 'idMulta', label: 'ID', width: 60, cellClassName: 'tabular-nums text-text-muted' },
    { key: 'numeroApartamento', label: 'Apartamento', cellClassName: 'font-semibold' },
    { key: 'nombreResidente', label: 'Residente' },
    { key: 'tipo', label: 'Tipo', render: (r) => <TipoBadge tipo={r.tipo} /> },
    {
      key: 'monto',
      label: <span className="block text-right">Monto</span>,
      cellClassName: 'text-right tabular-nums font-semibold whitespace-nowrap',
      render: (r) => formatCurrency(r.monto),
    },
    {
      key: 'fechaCreacion',
      label: 'Fecha',
      cellClassName: 'tabular-nums whitespace-nowrap',
      render: (r) => formatDate(r.fechaCreacion),
    },
    { key: 'estado', label: 'Estado', render: (r) => <EstadoBadge estado={r.estado} /> },
    {
      key: 'actions',
      label: <span className="sr-only">Acciones</span>,
      width: 140,
      render: (row) => (
        <div className="flex justify-end gap-1">
          <IconButton
            variant="ghost"
            size="icon"
            className="h-10 w-10"
            onClick={(e) => {
              e.stopPropagation();
              verDetalle(row);
            }}
            aria-label={`Ver detalle de la multa ${row.idMulta}`}
            title="Ver detalle"
          >
            <Eye aria-hidden="true" />
          </IconButton>
          {puedePagar(row.estado) && (
            <IconButton
              variant="ghost"
              size="icon"
              className="h-10 w-10 text-tint-green-fg hover:text-tint-green-fg"
              onClick={(e) => {
                e.stopPropagation();
                marcarPagada(row);
              }}
              aria-label={`Marcar pagada la multa ${row.idMulta}`}
              title="Marcar pagada"
            >
              <HandCoins aria-hidden="true" />
            </IconButton>
          )}
          {puedeAnular(row.estado) && (
            <IconButton
              variant="ghost"
              size="icon"
              className="h-10 w-10 text-destructive hover:text-destructive"
              onClick={(e) => {
                e.stopPropagation();
                setConfirmAnular(row);
              }}
              aria-label={`Anular la multa ${row.idMulta}`}
              title="Anular"
            >
              <Ban aria-hidden="true" />
            </IconButton>
          )}
        </div>
      ),
    },
  ];

  const detailRows = detalle
    ? [
        ['ID', <span key="id" className="tabular-nums">{detalle.idMulta}</span>],
        ['Tipo', <TipoBadge key="tipo" tipo={detalle.tipo} />],
        ['Apartamento', detalle.numeroApartamento],
        ['Residente', detalle.nombreResidente],
        ['Monto', <span key="monto" className="font-bold tabular-nums">{formatCurrency(detalle.monto)}</span>],
        ['Estado', <EstadoBadge key="estado" estado={detalle.estado} />],
        ['Fecha', <span key="fecha" className="tabular-nums">{formatDate(detalle.fechaCreacion)}</span>],
        detalle.nombrePortero && ['Generada por', detalle.nombrePortero],
        detalle.motivo && ['Motivo', detalle.motivo],
        detalle.idSancionOrigen && ['Expediente de sanción', `#${detalle.idSancionOrigen}`],
        detalle.fechaAvisoRuido && ['Aviso de ruido previo', formatDate(detalle.fechaAvisoRuido)],
      ].filter(Boolean)
    : [];

  return (
    <div className="space-y-4">
      <PageHeader
        title="Multas"
        subtitle="Multas impuestas en la copropiedad, su estado de pago y anulaciones"
        action={
          <select
            id="f-estado"
            aria-label="Filtrar por estado"
            value={filtroEstado}
            onChange={(e) => {
              setFiltroEstado(e.target.value);
              setPage(0);
            }}
            className={SELECT_CLASS}
          >
            <option value="">Todos los estados</option>
            <option value="IMPUESTA">Impuestas</option>
            <option value="EN_DESCARGOS">En descargos</option>
            <option value="RATIFICADA">Ratificadas</option>
            <option value="CONDONADA">Condonadas</option>
            <option value="PAGADA">Pagadas</option>
            <option value="ANULADA">Anuladas</option>
          </select>
        }
      />
      {error ? (
        <ErrorState
          title="No se pudieron cargar las multas"
          message={error.message || 'Revise su conexión e intente de nuevo.'}
          onRetry={() => refetch().catch(() => {})}
        />
      ) : (
        <>
          <DataTable
            columns={columns}
            rows={rows}
            loading={loading}
            empty={{
              icon: Gavel,
              title: filtroEstado ? 'No hay multas en este estado' : 'No hay multas',
              subtitle: filtroEstado ? 'Cambie el filtro de estado para ver otras multas.' : 'Las multas generadas aparecerán aquí.',
            }}
            keyField="idMulta"
            onRowClick={verDetalle}
          />
          <Pagination
            page={safePage}
            totalPages={totalPages}
            totalItems={items.length}
            pageSize={PAGE_SIZE}
            onPageChange={setPage}
          />
        </>
      )}
      {loadingDetalle && (
        <p role="status" className="sr-only">
          Cargando detalle de la multa…
        </p>
      )}

      <Modal open={!!detalle} onClose={() => setDetalle(null)} title="Detalle de multa" size="md">
        {detalle && (
          <div className="space-y-4">
            <dl className="divide-y divide-border-subtle rounded-md border border-border">
              {detailRows.map(([label, value]) => (
                <div key={label} className="flex items-start justify-between gap-4 px-3 py-2 text-sm">
                  <dt className="shrink-0 text-text-secondary">{label}</dt>
                  <dd className="text-right text-on-surface">{value}</dd>
                </div>
              ))}
            </dl>
            {detalle.descripcion && (
              <div>
                <p className="text-xs font-semibold text-text-secondary">Descripción</p>
                <p className="mt-1 whitespace-pre-wrap text-sm text-on-surface">{detalle.descripcion}</p>
              </div>
            )}
            {detalle.fotoEvidencia && (
              <button
                type="button"
                onClick={() => setFotoGrande(imageSrc(detalle.fotoEvidencia))}
                className="block w-full cursor-zoom-in overflow-hidden rounded-md border border-border focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                aria-label="Ampliar foto de evidencia"
              >
                <img
                  src={imageSrc(detalle.fotoEvidencia)}
                  alt="Evidencia"
                  loading="lazy"
                  width="400"
                  height="300"
                  className="h-auto w-full object-cover"
                />
              </button>
            )}
            {puedeAnular(detalle.estado) && (
              <div className="flex flex-col gap-2 border-t border-border-subtle pt-4 sm:flex-row sm:justify-end">
                {puedePagar(detalle.estado) && (
                  <Button onClick={() => marcarPagada(detalle)}>Marcar pagada</Button>
                )}
                <Button variant="danger" onClick={() => setConfirmAnular(detalle)}>
                  Anular
                </Button>
              </div>
            )}
          </div>
        )}
      </Modal>

      <Dialog open={!!fotoGrande} onOpenChange={(o) => !o && setFotoGrande(null)}>
        <DialogContent className="max-w-4xl p-3">
          <DialogTitle className="sr-only">Foto de evidencia</DialogTitle>
          {fotoGrande && (
            <img
              src={fotoGrande}
              alt="Evidencia"
              width="800"
              height="600"
              className="mx-auto max-h-[80dvh] w-auto max-w-full rounded-md object-contain"
            />
          )}
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={!!confirmAnular}
        onClose={() => setConfirmAnular(null)}
        onConfirm={anular}
        title="Anular multa"
        message={`¿Anular la multa #${confirmAnular?.idMulta}? La multa dejará de ser exigible.`}
        confirmLabel="Anular"
        danger
      />
    </div>
  );
}
