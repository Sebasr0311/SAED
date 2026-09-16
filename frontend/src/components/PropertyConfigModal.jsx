import { useState, useEffect } from 'react';
import api from '../lib/api.js';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle,
} from './ui/dialog.tsx';
import { Button } from './ui/Button.jsx';
import { Input } from './ui/input.tsx';
import { Label } from './ui/label.tsx';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from './ui/select.tsx';
import { Skeleton } from './ui/skeleton.tsx';
import { toast } from 'sonner';

/**
 * PropertyConfigModal — Panel de Configuración de Propiedad (GAP-CFG-05 & GAP-CFG-07).
 * Permite ajustar los parámetros operativos por copropiedad:
 * - Límite de convivientes por unidad
 * - Tolerancia de mora en días
 * - Habilitación de QR para visitas
 * - Habilitación de LPR vehicular
 * - Permiso de mascotas
 * - Expensa común por defecto
 * - Canal preferente de notificaciones
 */
export default function PropertyConfigModal({ propertyId, propertyName, isOpen, onClose }) {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [configs, setConfigs] = useState({
    LIMITE_CONVIVIENTES_POR_UNIDAD: '4',
    TOLERANCIA_MORA_DIAS: '30',
    HABILITA_QR: 'true',
    HABILITA_LPR: 'false',
    PERMITE_MASCOTAS: 'true',
    VALOR_EXPENSA_DEFECTO: '0',
    FORMATO_NOTIFICACION: 'EMAIL',
  });

  useEffect(() => {
    if (isOpen && propertyId) {
      loadConfig();
    }
  }, [isOpen, propertyId]);

  async function loadConfig() {
    setLoading(true);
    try {
      const res = await api.get(`/properties/${propertyId}/config`);
      const items = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : []);
      const map = {};
      items.forEach((item) => {
        if (item.clave) {
          map[item.clave.toUpperCase()] = item.valor;
        }
      });
      setConfigs((prev) => ({
        ...prev,
        ...map,
      }));
    } catch (err) {
      console.warn('Error loading property config, using defaults:', err);
    } finally {
      setLoading(false);
    }
  }

  function handleChange(key, val) {
    setConfigs((prev) => ({
      ...prev,
      [key]: val,
    }));
  }

  async function handleSave(e) {
    if (e) e.preventDefault();

    // Validaciones
    const limiteConvivientes = parseInt(configs.LIMITE_CONVIVIENTES_POR_UNIDAD, 10);
    if (isNaN(limiteConvivientes) || limiteConvivientes <= 0) {
      toast.error('El límite de convivientes debe ser un número entero mayor a 0');
      return;
    }

    const toleranciaMora = parseInt(configs.TOLERANCIA_MORA_DIAS, 10);
    if (isNaN(toleranciaMora) || toleranciaMora < 0) {
      toast.error('La tolerancia de mora debe ser un número entero mayor o igual a 0');
      return;
    }

    const expensa = parseFloat(configs.VALOR_EXPENSA_DEFECTO);
    if (isNaN(expensa) || expensa < 0) {
      toast.error('El valor de expensa debe ser un valor numérico positivo');
      return;
    }

    setSaving(true);
    try {
      await api.put(`/properties/${propertyId}/config`, configs);
      toast.success('Configuración de la copropiedad actualizada correctamente');
      if (onClose) onClose();
    } catch (err) {
      console.error('Error saving property config:', err);
      const msg = err?.response?.data?.message || err?.message || 'Error al guardar la configuración';
      toast.error(msg);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={isOpen} onOpenChange={(open) => { if (!open && onClose) onClose(); }}>
      <DialogContent className="sm:max-w-xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold text-foreground flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-xl">tune</span>
            Configuración Operativa
          </DialogTitle>
          <DialogDescription className="text-xs text-muted-foreground">
            Ajuste los parámetros y límites operativos para la copropiedad{' '}
            <strong className="text-foreground">{propertyName || `#${propertyId}`}</strong>.
          </DialogDescription>
        </DialogHeader>

        {loading ? (
          <div className="space-y-4 py-4">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : (
          <form onSubmit={handleSave} className="space-y-5 py-2">
            {/* Sección 1: Convivencia y Habitabilidad */}
            <div className="space-y-3 border-b border-border pb-4">
              <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <span className="material-symbols-outlined text-sm">group</span>
                Convivencia y Habitabilidad
              </h4>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="cfg-limite" className="text-xs font-semibold">
                    Límite Convivientes / Unidad *
                  </Label>
                  <Input
                    id="cfg-limite"
                    type="number"
                    min="1"
                    max="50"
                    value={configs.LIMITE_CONVIVIENTES_POR_UNIDAD}
                    onChange={(e) => handleChange('LIMITE_CONVIVIENTES_POR_UNIDAD', e.target.value)}
                    className="h-9 text-sm"
                    required
                  />
                  <p className="text-[11px] text-muted-foreground">
                    Máximo de convivientes activos por unidad (excluye titular).
                  </p>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="cfg-mascotas" className="text-xs font-semibold">
                    Permite Mascotas
                  </Label>
                  <Select
                    value={configs.PERMITE_MASCOTAS}
                    onValueChange={(val) => handleChange('PERMITE_MASCOTAS', val)}
                  >
                    <SelectTrigger id="cfg-mascotas" className="h-9 text-sm">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="true">Permitido</SelectItem>
                      <SelectItem value="false">Restringido</SelectItem>
                    </SelectContent>
                  </Select>
                  <p className="text-[11px] text-muted-foreground">
                    Habilita el módulo de registro de mascotas en unidades.
                  </p>
                </div>
              </div>
            </div>

            {/* Sección 2: Cartera y Finanzas */}
            <div className="space-y-3 border-b border-border pb-4">
              <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <span className="material-symbols-outlined text-sm">payments</span>
                Cartera y Finanzas
              </h4>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="cfg-mora" className="text-xs font-semibold">
                    Días Tolerancia Mora *
                  </Label>
                  <Input
                    id="cfg-mora"
                    type="number"
                    min="0"
                    max="180"
                    value={configs.TOLERANCIA_MORA_DIAS}
                    onChange={(e) => handleChange('TOLERANCIA_MORA_DIAS', e.target.value)}
                    className="h-9 text-sm"
                    required
                  />
                  <p className="text-[11px] text-muted-foreground">
                    Días de gracia antes de aplicar sanciones o recargos.
                  </p>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="cfg-expensa" className="text-xs font-semibold">
                    Expensa Común Base ($ COP)
                  </Label>
                  <Input
                    id="cfg-expensa"
                    type="number"
                    min="0"
                    step="1000"
                    value={configs.VALOR_EXPENSA_DEFECTO}
                    onChange={(e) => handleChange('VALOR_EXPENSA_DEFECTO', e.target.value)}
                    className="h-9 text-sm"
                  />
                  <p className="text-[11px] text-muted-foreground">
                    Cuota de administración por defecto para nuevas unidades.
                  </p>
                </div>
              </div>
            </div>

            {/* Sección 3: Seguridad y Acceso */}
            <div className="space-y-3 border-b border-border pb-4">
              <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <span className="material-symbols-outlined text-sm">security</span>
                Portería y Seguridad de Acceso
              </h4>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="cfg-qr" className="text-xs font-semibold">
                    Pases con Código QR
                  </Label>
                  <Select
                    value={configs.HABILITA_QR}
                    onValueChange={(val) => handleChange('HABILITA_QR', val)}
                  >
                    <SelectTrigger id="cfg-qr" className="h-9 text-sm">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="true">Habilitado</SelectItem>
                      <SelectItem value="false">Deshabilitado</SelectItem>
                    </SelectContent>
                  </Select>
                  <p className="text-[11px] text-muted-foreground">
                    Generación de códigos QR temporales para visitantes.
                  </p>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="cfg-lpr" className="text-xs font-semibold">
                    LPR Vehicular
                  </Label>
                  <Select
                    value={configs.HABILITA_LPR}
                    onValueChange={(val) => handleChange('HABILITA_LPR', val)}
                  >
                    <SelectTrigger id="cfg-lpr" className="h-9 text-sm">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="true">Habilitado</SelectItem>
                      <SelectItem value="false">Deshabilitado</SelectItem>
                    </SelectContent>
                  </Select>
                  <p className="text-[11px] text-muted-foreground">
                    Lectura automática de matrículas vehiculares en garita.
                  </p>
                </div>
              </div>
            </div>

            {/* Sección 4: Notificaciones */}
            <div className="space-y-3">
              <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <span className="material-symbols-outlined text-sm">notifications</span>
                Canal de Notificaciones
              </h4>

              <div className="space-y-1.5">
                <Label htmlFor="cfg-notif" className="text-xs font-semibold">
                  Canal Preferente
                </Label>
                <Select
                  value={configs.FORMATO_NOTIFICACION}
                  onValueChange={(val) => handleChange('FORMATO_NOTIFICACION', val)}
                >
                  <SelectTrigger id="cfg-notif" className="h-9 text-sm">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="EMAIL">Correo Electrónico (Email)</SelectItem>
                    <SelectItem value="SMS">Mensajes de Texto (SMS)</SelectItem>
                    <SelectItem value="INTERNO">Bandeja Interna SAED</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <DialogFooter className="pt-4 border-t border-border">
              <Button type="button" variant="outline" onClick={onClose} disabled={saving}>
                Cancelar
              </Button>
              <Button type="submit" disabled={saving}>
                {saving ? 'Guardando...' : 'Guardar Configuración'}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
