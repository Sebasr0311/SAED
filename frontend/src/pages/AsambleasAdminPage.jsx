import React, { useState } from 'react';
import { PageHeader } from '../components/ui/PageHeader';
import { DataTable } from '../components/ui/DataTable';
import { useFetch } from '../lib/hooks';
import { api } from '../lib/api';

export default function AsambleasAdminPage() {
  const { data, loading, error } = useFetch(() => api.get('/asambleas'));

  const columnas = [
    { header: 'ID', accessorKey: 'idAsamblea' },
    { header: 'Título', accessorKey: 'titulo' },
    { header: 'Tipo', accessorKey: 'tipo' },
    { header: 'Estado', accessorKey: 'estado' },
    { header: 'Modalidad', accessorKey: 'modalidad' },
    { header: 'Fecha 1ra Conv.', accessorKey: 'fechaHoraPrimeraConv' }
  ];

  if (loading) return <div>Cargando asambleas...</div>;
  if (error) return <div className="text-error">{error.message}</div>;

  return (
    <div className="space-y-6">
      <PageHeader 
        title="Gestión de Asambleas" 
        description="Módulo para crear y administrar asambleas (Fantasma)."
      />

      <div className="bg-base-100 p-4 rounded-box shadow-sm">
        <DataTable 
          data={data || []}
          columns={columnas}
        />
      </div>
    </div>
  );
}
