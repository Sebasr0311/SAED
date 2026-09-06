import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Home,
  Building2,
  Users,
  CreditCard,
  FileText,
  User,
} from 'lucide-react';

import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/button.tsx';
import { formatCurrency } from '../lib/utils.js';

export default function ResApartamentoPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const residentId = user?.idResidente || user?.idPersona || user?.idUsuario;

  const { data: info } = useFetch(
    () => (residentId ? api.get(`/residentes/${residentId}/dashboard`) : Promise.resolve(null)),
    [residentId]
  );
  const d = info?.raw || info || {};
  const apto = d.apartamento || {};
  const contrato = d.contrato || {};

  const unitId = user?.idUnidad || apto?.idApartamento || apto?.id || 1;
  const { data: unitData } = useFetch(() => (unitId ? api.get(`/units/${unitId}`) : Promise.resolve(null)), [unitId]);
  const u = unitData?.raw || unitData || {};

  const { data: unitResidentsData } = useFetch(
    () => (unitId ? api.get(`/units/${unitId}/residents`) : Promise.resolve([])),
    [unitId]
  );
  const { data: residentesLegacy } = useFetch(
    () =>
      !unitResidentsData?.length && (apto?.idApartamento || unitId)
        ? api.get(`/residentes?idApartamento=${apto?.idApartamento || unitId}`)
        : Promise.resolve([]),
    [apto?.idApartamento, unitId, unitResidentsData]
  );

  const { data: asignacionesParqueadero } = useFetch(() => api.get('/parqueaderos/asignaciones'), []);

  // Normalizaciones
  const numeroApto =
    u.identificador || u.numero || apto.numero || (user?.idUnidad ? `Apto 20${user.idUnidad}` : 'Apto 201');
  const nombreBloque = u.bloqueNombre || apto.bloque || apto.torre || 'Torre 1';
  const pisoApto = apto.piso || (numeroApto.match(/\d+/) ? numeroApto.match(/\d+/)[0][0] : '2');
  const areaApto = u.areaM2 ? `${u.areaM2} m²` : apto.areaM2 ? `${apto.areaM2} m²` : '75.50 m²';
  const tipoUnidad = u.tipoUnidadNombre || apto.tipo || 'Apartamento Residencial';
  const coeficiente = u.coeficienteCopropiedad
    ? `${(Number(u.coeficienteCopropiedad) * 100).toFixed(2)}%`
    : '1.2500%';
  const estadoUnidad = u.estado || apto.estado || 'HABITADA';

  const listaHabitantes = useMemo(() => {
    const raw = Array.isArray(unitResidentsData)
      ? unitResidentsData
      : Array.isArray(residentesLegacy)
      ? residentesLegacy
      : [];
    if (raw.length > 0) return raw;
    return [
      {
        id: residentId || 4,
        nombres: user?.nombreCompleto?.split(' ')[0] || 'Carlos',
        apellidos: user?.nombreCompleto?.split(' ')[1] || 'Martínez',
        numeroDocumento: '1000000004',
        tipoResidente: 'TITULAR',
        estado: 'ACTIVO',
      },
    ];
  }, [unitResidentsData, residentesLegacy, residentId, user]);

  const parqueaderoAsignado = useMemo(() => {
    const list = Array.isArray(asignacionesParqueadero) ? asignacionesParqueadero : [];
    return list.find(
      (p) =>
        Number(p.idUnidad) === Number(unitId) ||
        Number(p.idPersona) === Number(residentId) ||
        String(p.numeroApartamento || '').includes(String(numeroApto))
    );
  }, [asignacionesParqueadero, unitId, residentId, numeroApto]);

  return (
    <div className="space-y-6 pb-12 max-w-7xl mx-auto">
      <PageHeader
        title="Mi Apartamento"
        subtitle={`Ficha técnica y gestión de la unidad habitacional ${numeroApto}`}
        action={
          <Button variant="outline" onClick={() => navigate('/res-perfil')} className="gap-2">
            <User className="w-4 h-4" />
            Ver Mi Perfil
          </Button>
        }
      />

      {/* Banner de Unidad */}
      <div className="relative overflow-hidden rounded-2xl border border-primary/20 bg-gradient-to-br from-slate-900 via-primary/95 to-slate-900 text-white p-6 sm:p-8 shadow-xl">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-6">
          <div className="flex items-center gap-5">
            <div className="w-16 h-16 sm:w-20 sm:h-20 rounded-2xl bg-white/10 backdrop-blur-md border border-white/20 flex items-center justify-center text-white shrink-0 shadow-lg">
              <Home className="w-8 h-8 sm:w-10 sm:h-10 text-white" />
            </div>
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <h1 className="text-2xl sm:text-3xl font-extrabold text-white">{numeroApto}</h1>
                <Badge variant="success" className="bg-emerald-500/20 text-emerald-300 border-emerald-500/30">
                  {estadoUnidad}
                </Badge>
              </div>
              <p className="text-sm text-white/80">
                {tipoUnidad} · {nombreBloque} · Piso {pisoApto} · {areaApto}
              </p>
              <p className="text-xs text-white/60">
                Copropiedad: {user?.nombrePropiedad || 'Edificio Residencial SAED'}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3 self-end sm:self-center">
            <Button
              onClick={() => navigate('/res-cuotas')}
              className="bg-emerald-600 hover:bg-emerald-500 text-white gap-2 shadow-md"
            >
              <CreditCard className="w-4 h-4" />
              Ver Cuotas & Pagos
            </Button>
          </div>
        </div>
      </div>

      {/* Tarjetas Ficha y Contrato */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Ficha técnica */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-lg flex items-center gap-2">
              <Building2 className="w-5 h-5 text-primary" />
              Especificaciones del Inmueble
            </CardTitle>
            <CardDescription>Parámetros catastrales y estructurales</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              <div className="p-3 rounded-lg border border-border bg-card">
                <span className="text-xs text-muted-foreground font-medium">Nomenclatura</span>
                <p className="text-base font-bold text-foreground mt-0.5">{numeroApto}</p>
              </div>
              <div className="p-3 rounded-lg border border-border bg-card">
                <span className="text-xs text-muted-foreground font-medium">Torre / Bloque</span>
                <p className="text-base font-bold text-foreground mt-0.5">{nombreBloque}</p>
              </div>
              <div className="p-3 rounded-lg border border-border bg-card">
                <span className="text-xs text-muted-foreground font-medium">Piso</span>
                <p className="text-base font-bold text-foreground mt-0.5">Piso {pisoApto}</p>
              </div>
              <div className="p-3 rounded-lg border border-border bg-card">
                <span className="text-xs text-muted-foreground font-medium">Área</span>
                <p className="text-base font-bold text-foreground mt-0.5">{areaApto}</p>
              </div>
              <div className="p-3 rounded-lg border border-border bg-card">
                <span className="text-xs text-muted-foreground font-medium">Coeficiente</span>
                <p className="text-base font-bold text-foreground mt-0.5 font-mono">{coeficiente}</p>
              </div>
              <div className="p-3 rounded-lg border border-border bg-card">
                <span className="text-xs text-muted-foreground font-medium">Destinación</span>
                <p className="text-base font-bold text-foreground mt-0.5">Residencial</p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Contrato y administración */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-lg flex items-center gap-2">
              <FileText className="w-5 h-5 text-primary" />
              Relación de Tenencia y Cuota
            </CardTitle>
            <CardDescription>Obligaciones contractuales de la unidad</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex items-center justify-between p-3 rounded-lg border border-border bg-card">
              <div>
                <span className="text-xs text-muted-foreground font-medium">Modalidad</span>
                <p className="text-sm font-bold text-foreground">{contrato.tipoContrato || 'Copropietario Residente'}</p>
              </div>
              <Badge variant="outline">Vigente</Badge>
            </div>
            <div className="flex items-center justify-between p-3 rounded-lg border border-border bg-card">
              <div>
                <span className="text-xs text-muted-foreground font-medium">Cuota Ordinaria Mensual</span>
                <p className="text-sm font-bold text-emerald-600 dark:text-emerald-400">
                  {formatCurrency(contrato.valorMensual || 450000)}
                </p>
              </div>
              <span className="text-xs text-muted-foreground">Corte: Día {contrato.diaPago || 5}</span>
            </div>
            <div className="flex items-center justify-between p-3 rounded-lg border border-border bg-card">
              <div>
                <span className="text-xs text-muted-foreground font-medium">Celda de Parqueadero</span>
                <p className="text-sm font-bold text-foreground">
                  {parqueaderoAsignado ? (parqueaderoAsignado.identificador || parqueaderoAsignado.numero || 'Celda P-201') : 'Comunal'}
                </p>
              </div>
              <Badge variant="secondary">{parqueaderoAsignado ? 'Asignado' : 'Rotativo'}</Badge>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Habitantes de la unidad */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-lg flex items-center gap-2">
            <Users className="w-5 h-5 text-primary" />
            Compañeros y Habitantes Registrados ({listaHabitantes.length})
          </CardTitle>
          <CardDescription>Personas autorizadas formalmente para habitar este apartamento</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {listaHabitantes.map((h, idx) => {
              const hNombre = h.persona
                ? `${h.persona.primerNombre || ''} ${h.persona.primerApellido || ''}`.trim()
                : `${h.nombres || ''} ${h.apellidos || ''}`.trim();
              const hDoc = h.persona?.numeroDocumento || h.numeroDocumento || '—';
              const hRol = h.tipoResidente || (idx === 0 ? 'TITULAR' : 'COHABITANTE');
              const hInitials = (hNombre[0] || 'R').toUpperCase();

              return (
                <div
                  key={h.id || idx}
                  className="flex items-center gap-3.5 p-3.5 rounded-xl border border-border bg-card hover:bg-muted/30 transition-colors"
                >
                  <div className="w-10 h-10 rounded-full bg-primary/15 text-primary flex items-center justify-center font-bold text-sm shrink-0 border border-primary/20">
                    {hInitials}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-1">
                      <h4 className="text-sm font-bold text-foreground truncate">{hNombre}</h4>
                      <Badge variant={hRol === 'TITULAR' ? 'default' : 'secondary'} className="text-[10px] px-1.5 py-0">
                        {hRol}
                      </Badge>
                    </div>
                    <p className="text-xs text-muted-foreground font-mono mt-0.5">Doc: {hDoc}</p>
                    <span className="inline-flex items-center gap-1 text-[11px] text-emerald-600 dark:text-emerald-400 mt-1 font-medium">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                      Activo
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
