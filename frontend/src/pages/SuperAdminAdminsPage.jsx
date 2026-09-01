import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { toast } from 'sonner';

export default function SuperAdminAdminsPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        const res = await api.get('/platform/admins');
        setItems(res?.data || res || []);
      } catch (err) {
        console.error(err);
        toast.error('Error al cargar administradores de plataforma');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      <div className="border-b border-border pb-6">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">Administradores de Plataforma</h1>
        <p className="text-muted-foreground mt-1 text-sm">
          Operadores globales y personal con acceso de control central a SAED.
        </p>
      </div>

      <Card className="border-border/80">
        <CardHeader>
          <CardTitle className="text-base font-semibold">Operadores Globales Activos</CardTitle>
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
              <span className="material-symbols-outlined text-4xl mb-2">admin_panel_settings</span>
              <p>No se encontraron administradores.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-border text-muted-foreground font-medium">
                    <th className="py-3 px-4">Usuario</th>
                    <th className="py-3 px-4">Nombre Completo</th>
                    <th className="py-3 px-4">Email</th>
                    <th className="py-3 px-4">Rol / Nivel</th>
                    <th className="py-3 px-4">Último Login</th>
                    <th className="py-3 px-4">Estado</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {items.map((adm, i) => (
                    <tr key={i} className="hover:bg-muted/50 transition-colors">
                      <td className="py-3 px-4 font-mono font-semibold text-foreground">
                        {adm.NOMBRE_USUARIO || adm.nombreUsuario}
                      </td>
                      <td className="py-3 px-4">
                        {adm.PRIMER_NOMBRE || adm.primerNombre} {adm.PRIMER_APELLIDO || adm.primerApellido}
                      </td>
                      <td className="py-3 px-4 text-muted-foreground">{adm.EMAIL || adm.email}</td>
                      <td className="py-3 px-4">
                        <Badge variant="outline" className="font-semibold text-primary border-primary/30">
                          {adm.ROL || 'SUPERADMIN'}
                        </Badge>
                      </td>
                      <td className="py-3 px-4 text-xs text-muted-foreground">
                        {adm.ULTIMO_LOGIN || 'Reciente'}
                      </td>
                      <td className="py-3 px-4">
                        <Badge className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
                          {adm.ESTADO || 'ACTIVO'}
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
