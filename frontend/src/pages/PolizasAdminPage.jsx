import { PageHeader } from '../components/ui/PageHeader';
import { DataTable } from '../components/ui/DataTable';
import { useFetch } from '../lib/hooks';
import { api } from '../lib/api';

export default function PolizasAdminPage() {
  const { data, loading, error } = useFetch(() => api.get('/seguros/polizas'));
  const items = Array.isArray(data) ? data : data?.items || [];
  
  const columns = [
    { key: 'idPoliza', label: 'ID' },
    { key: 'companiaAseguradora', label: 'Compañía' },
    { key: 'numeroPoliza', label: 'Número de Póliza' },
    { key: 'fechaVencimiento', label: 'Vencimiento' },
    { key: 'montoAsegurado', label: 'Monto Asegurado' }
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Administración de Pólizas de Seguro" description="Gestiona los seguros de la propiedad" />
      {error && <div className="text-red-500">{error.message || 'Error cargando datos'}</div>}
      <DataTable columns={columns} data={items} loading={loading} />
    </div>
  );
}
