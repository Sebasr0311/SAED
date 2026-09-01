import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { toast } from 'sonner';

export default function SuperAdminPlanesPage() {
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        const res = await api.get('/platform/plans');
        setPlans(res?.data || res || []);
      } catch (err) {
        console.error(err);
        toast.error('Error al cargar planes');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      <div className="border-b border-border pb-6">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">Planes SaaS</h1>
        <p className="text-muted-foreground mt-1 text-sm">
          Niveles de suscripción y límites para organizaciones clientes de SAED.
        </p>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-64 rounded-xl" />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {plans.map((p) => (
            <Card key={p.id} className="border-border/80 hover:shadow-lg transition-all flex flex-col justify-between">
              <CardHeader className="space-y-2">
                <div className="flex justify-between items-center">
                  <Badge variant="outline" className="font-semibold">{p.nombre}</Badge>
                  <Badge className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">{p.estado}</Badge>
                </div>
                <CardTitle className="text-2xl font-bold text-foreground">
                  ${(p.precioMensual || 0).toLocaleString('es-CO')}
                  <span className="text-xs font-normal text-muted-foreground"> / mes</span>
                </CardTitle>
                <p className="text-xs text-muted-foreground">{p.descripcion}</p>
              </CardHeader>

              <CardContent className="space-y-4">
                <div className="border-t border-border pt-4 space-y-2 text-xs text-muted-foreground">
                  <div className="flex justify-between">
                    <span>Máx. Propiedades:</span>
                    <span className="font-semibold text-foreground">{p.maxPropiedades}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Máx. Unidades:</span>
                    <span className="font-semibold text-foreground">{p.maxUnidades}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Máx. Usuarios:</span>
                    <span className="font-semibold text-foreground">{p.maxUsuarios}</span>
                  </div>
                </div>

                <div className="border-t border-border pt-3">
                  <span className="text-xs font-semibold text-foreground block mb-2">Módulos Incluidos:</span>
                  <div className="flex flex-wrap gap-1.5">
                    {(p.modulos || []).map((m, idx) => (
                      <span key={idx} className="inline-block px-2 py-0.5 rounded text-[10px] bg-secondary text-secondary-foreground font-mono">
                        {m}
                      </span>
                    ))}
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
