import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { toast } from 'sonner';

export default function SuperAdminMembresiasPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        const res = await api.get('/platform/memberships');
        setItems(res?.data || res || []);
      } catch (err) {
        console.error(err);
        toast.error('Error al cargar membresías');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      <div className="border-b border-border pb-6">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">Membresías SaaS</h1>
        <p className="text-muted-foreground mt-1 text-sm">
          Suscripciones activas, facturación y estados contractuales de las organizaciones.
        </p>
      </div>

      <Card className="border-border/80">
        <CardHeader>
          <CardTitle className="text-base font-semibold">Estado de Membresías</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-3">
              {[1, 2].map((i) => (
                <Skeleton key={i} className="h-16 w-full rounded-lg" />
              ))}
            </div>
          ) : items.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground">
              <span className="material-symbols-outlined text-4xl mb-2">card_membership</span>
              <p>No hay membresías registradas.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-border text-muted-foreground font-medium">
                    <th className="py-3 px-4">ID</th>
                    <th className="py-3 px-4">Organización</th>
                    <th className="py-3 px-4">Plan Asignado</th>
                    <th className="py-3 px-4">Vigencia</th>
                    <th className="py-3 px-4">Valor Mensual</th>
                    <th className="py-3 px-4">Estado</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {items.map((m) => (
                    <tr key={m.id} className="hover:bg-muted/50 transition-colors">
                      <td className="py-3 px-4 font-mono text-xs">{m.id}</td>
                      <td className="py-3 px-4 font-semibold text-foreground">{m.organizacionNombre}</td>
                      <td className="py-3 px-4">
                        <Badge variant="outline">{m.planNombre}</Badge>
                      </td>
                      <td className="py-3 px-4 text-xs text-muted-foreground">
                        {m.fechaInicio} al {m.fechaFin}
                      </td>
                      <td className="py-3 px-4 font-mono font-medium">
                        ${(m.valorMensual || 0).toLocaleString('es-CO')}
                      </td>
                      <td className="py-3 px-4">
                        <Badge className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
                          {m.estado}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
