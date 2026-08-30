import React, { useState } from 'react';
import { PageHeader } from '../components/ui/PageHeader';
import { DataTable } from '../components/ui/DataTable';
import { Modal } from '../components/ui/Modal';
import { Button } from '../components/ui/Button';
import { useFetch } from '../lib/hooks';
import { api } from '../lib/api';
import { toast } from 'sonner';

const ESTADO_BADGE = {
  NOTIFICADA: 'badge-warning',
  EN_DESCARGOS: 'badge-info',
  ABSUELTA: 'badge-neutral',
  APLICADA: 'badge-error',
};

export default function ResSancionesPage() {
  const { data, loading, error, refetch } = useFetch(() => api.get('/sanciones/mis-sanciones'));
  const [detalle, setDetalle] = useState(null);
  const [descargos, setDescargos] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const rows = data?.items || [];

  const columns = [
    { key: 'numeroExpediente', label: 'Expediente', width: 140 },
    { key: 'tipoFalta', label: 'Motivo / Falta' },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
    }
  ];

  async function handleSubmitDescargos(e) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post(`/sanciones/${detalle.idSancion}/descargos`, { descargos });
      toast.success('Descargos enviados para revisión.');
      setDetalle(null);
      setDescargos('');
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Mis Procesos y Sanciones"
        subtitle="Consultá tus pliegos de cargos y ejerce tu derecho a la defensa"
      />

      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
        empty={{ icon: 'thumb_up', title: 'Todo en orden', subtitle: 'No tienes procesos sancionatorios activos.' }}
        error={error?.message}
        keyField="idSancion"
        onRowClick={setDetalle}
      />

      <Modal open={!!detalle} onClose={() => setDetalle(null)} title={`Expediente ${detalle?.numeroExpediente}`} size="md">
        {detalle && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div className="detail-row"><span>Estado</span><span className={`badge ${ESTADO_BADGE[detalle.estado]}`}>{detalle.estado}</span></div>
            <div className="detail-row"><span>Gravedad</span><span>{detalle.gravedad}</span></div>
            <div style={{ padding: '12px', background: 'var(--surface-hover)', borderRadius: '8px' }}>
              <strong style={{ fontSize: '12px' }}>Hechos Imputados:</strong>
              <p style={{ margin: '4px 0 0 0', fontSize: '14px' }}>{detalle.descripcionHechos}</p>
            </div>
            
            {detalle.estado === 'NOTIFICADA' && (
              <form onSubmit={handleSubmitDescargos} style={{ marginTop: '16px', display: 'flex', flexDirection: 'column', gap: '12px', borderTop: '1px solid var(--border)', paddingTop: '16px' }}>
                <div className="form-control">
                  <label className="label">Presentar Descargos (Defensa)</label>
                  <textarea 
                    className="input" 
                    style={{ minHeight: '100px', padding: '8px' }} 
                    placeholder="Escribe aquí tu versión de los hechos o justificación..."
                    value={descargos} 
                    onChange={e => setDescargos(e.target.value)} 
                    required 
                  />
                </div>
                <Button type="submit" loading={submitting}>Enviar Descargos</Button>
              </form>
            )}

            {detalle.estado === 'EN_DESCARGOS' && (
               <div style={{ padding: '12px', background: 'var(--info-bg)', color: 'var(--info-text)', borderRadius: '8px', marginTop: '16px' }}>
                 Tus descargos están en revisión por la administración. Recibirás la resolución final por este medio.
               </div>
            )}

            {detalle.resolucionFinal && (
              <div style={{ padding: '12px', background: 'var(--surface-hover)', borderLeft: '4px solid var(--primary)', borderRadius: '8px', marginTop: '16px' }}>
                <strong style={{ fontSize: '12px' }}>Resolución de la Administración:</strong>
                <p style={{ margin: '4px 0 0 0', fontSize: '14px' }}>{detalle.resolucionFinal}</p>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}

