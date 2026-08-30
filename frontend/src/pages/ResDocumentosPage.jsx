import React from 'react';
import { PageHeader } from '../components/ui/PageHeader';
import { useFetch } from '../lib/hooks';
import { api } from '../lib/api';

export default function ResDocumentosPage() {
  const { data, loading, error } = useFetch(() => api.get('/api/v1/documentos/residente');
  const documentos = data?.items || [];

  const categorias = [...new Set(documentos.map(d => d.categoria))];

  if (loading) return <div>Cargando biblioteca de documentos...</div>;
  if (error) return <div style={{ color: 'var(--error)' }}>{error.message}</div>;

  return (
    <div>
      <PageHeader
        title="Biblioteca de Documentos"
        subtitle="Reglamentos, manuales y actas públicas de la copropiedad"
      />

      {documentos.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '48px', color: 'var(--text-secondary)' }}>
          <span className="material-symbols-outlined" style={{ fontSize: '48px', opacity: 0.5 }}>folder_off</span>
          <p>No hay documentos públicos disponibles en este momento.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '32px', marginTop: '24px' }}>
          {categorias.map(categoria => (
            <div key={categoria}>
              <h3 style={{ borderBottom: '1px solid var(--border)', paddingBottom: '8px', marginBottom: '16px' }}>
                {categoria}
              </h3>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '16px' }}>
                {documentos.filter(d => d.categoria === categoria).map(doc => (
                  <div key={doc.idDocumento} style={{ background: 'var(--surface-hover)', padding: '16px', borderRadius: '12px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
                      <span className="material-symbols-outlined" style={{ fontSize: '32px', color: 'var(--primary)' }}>
                        {doc.archivoMimeType?.includes('pdf') ? 'picture_as_pdf' : 'description'}
                      </span>
                      <div style={{ flex: 1 }}>
                        <h4 style={{ margin: 0, fontSize: '16px' }}>{doc.titulo}</h4>
                        <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: 'var(--text-secondary)' }}>
                          Subido: {new Date(doc.fechaCreacion).toLocaleDateString()} • v{doc.numeroVersion || 1}
                        </p>
                      </div>
                    </div>
                    {doc.descripcion && (
                      <p style={{ margin: 0, fontSize: '14px', color: 'var(--text-secondary)' }}>{doc.descripcion}</p>
                    )}
                    <a 
                      href={doc.archivoUrl} 
                      target="_blank" 
                      rel="noopener noreferrer"
                      className="btn btn-ghost" 
                      style={{ marginTop: 'auto', alignSelf: 'flex-start' }}
                    >
                      <span className="material-symbols-outlined">download</span>
                      Descargar
                    </a>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
