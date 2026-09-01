import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { toast } from 'sonner';

export default function SuperAdminOrganizacionesPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({
    nombre: '',
    identificacionFiscal: '',
    emailContacto: '',
    telefono: '',
    pais: 'Colombia',
  });

  async function loadData() {
    try {
      setLoading(true);
      const res = await api.get('/organizations');
      const list = res?.data || res || [];
      setItems(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error(err);
      toast.error('Error al cargar organizaciones');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  async function handleCreate(e) {
    e.preventDefault();
    if (!form.nombre || !form.identificacionFiscal || !form.emailContacto) {
      toast.error('Por favor completa los campos requeridos');
      return;
    }
    try {
      await api.post('/organizations', form);
      toast.success('Organización creada exitosamente');
      setShowModal(false);
      setForm({ nombre: '', identificacionFiscal: '', emailContacto: '', telefono: '', pais: 'Colombia' });
      loadData();
    } catch (err) {
      console.error(err);
      toast.error('Error al crear la organización');
    }
  }

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Organizaciones SaaS</h1>
          <p className="text-muted-foreground mt-1 text-sm">
            Empresas, constructoras y administradoras de copropiedades registradas en SAED.
          </p>
        </div>
        <Button onClick={() => setShowModal(true)} className="gap-2">
          <span className="material-symbols-outlined text-sm">add_business</span>
          Nueva Organización
        </Button>
      </div>

      <Card className="border-border/80">
        <CardHeader>
          <CardTitle className="text-base font-semibold">Listado de Organizaciones</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-16 w-full rounded-lg" />
              ))}
            </div>
          ) : items.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground">
              <span className="material-symbols-outlined text-4xl mb-2">domain_disabled</span>
              <p>No hay organizaciones registradas.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-border text-muted-foreground font-medium">
                    <th className="py-3 px-4">ID</th>
                    <th className="py-3 px-4">Nombre</th>
                    <th className="py-3 px-4">NIT / ID Fiscal</th>
                    <th className="py-3 px-4">Contacto</th>
                    <th className="py-3 px-4">País</th>
                    <th className="py-3 px-4">Estado</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {items.map((org) => (
                    <tr key={org.id || org.idOrganizacion} className="hover:bg-muted/50 transition-colors">
                      <td className="py-3 px-4 font-mono text-xs">{org.id || org.idOrganizacion}</td>
                      <td className="py-3 px-4 font-semibold text-foreground">{org.nombre}</td>
                      <td className="py-3 px-4 text-muted-foreground font-mono">{org.identificacionFiscal || org.nit}</td>
                      <td className="py-3 px-4">{org.emailContacto || org.email}</td>
                      <td className="py-3 px-4">{org.pais || 'Colombia'}</td>
                      <td className="py-3 px-4">
                        <Badge variant={org.estado === 'ACTIVA' ? 'default' : 'secondary'} className={org.estado === 'ACTIVA' ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20' : ''}>
                          {org.estado || 'ACTIVA'}
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

      {/* Modal de Creación */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-fadeIn">
          <div className="bg-card w-full max-w-lg rounded-xl border border-border shadow-2xl p-6 space-y-5">
            <div className="flex justify-between items-center border-b border-border pb-4">
              <h2 className="text-xl font-bold text-foreground flex items-center gap-2">
                <span className="material-symbols-outlined text-primary">add_business</span>
                Registrar Organización
              </h2>
              <button onClick={() => setShowModal(false)} className="text-muted-foreground hover:text-foreground">
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>

            <form onSubmit={handleCreate} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">Nombre Comercial *</label>
                <input
                  type="text"
                  required
                  value={form.nombre}
                  onChange={(e) => setForm({ ...form, nombre: e.target.value })}
                  placeholder="Ej. Inversiones Residenciales S.A.S."
                  className="w-full px-3 py-2 rounded-lg border border-input bg-background text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">NIT / Identificación Fiscal *</label>
                <input
                  type="text"
                  required
                  value={form.identificacionFiscal}
                  onChange={(e) => setForm({ ...form, identificacionFiscal: e.target.value })}
                  placeholder="Ej. 901234567-8"
                  className="w-full px-3 py-2 rounded-lg border border-input bg-background text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">Email de Contacto *</label>
                  <input
                    type="email"
                    required
                    value={form.emailContacto}
                    onChange={(e) => setForm({ ...form, emailContacto: e.target.value })}
                    placeholder="contacto@organizacion.com"
                    className="w-full px-3 py-2 rounded-lg border border-input bg-background text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">País</label>
                  <input
                    type="text"
                    value={form.pais}
                    onChange={(e) => setForm({ ...form, pais: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg border border-input bg-background text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-border">
                <Button type="button" variant="outline" onClick={() => setShowModal(false)}>
                  Cancelar
                </Button>
                <Button type="submit">
                  Guardar Organización
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
