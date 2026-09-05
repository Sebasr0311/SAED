import { useState } from 'react';
import { Modal } from './Modal.jsx';
import { Button } from './Button.jsx';
import { Input } from './Form.jsx';
import api from '../../lib/api.js';

/**
 * Modal que reverifica la contraseña del admin antes de una accion destructiva.
 * Uso: const confirmar = useConfirmPassword(); const ok = await confirmar.ask('eliminar X'); if (ok) { ... }
 */
export function ConfirmPasswordDialog({ open, onClose, onConfirmed, descripcion }) {
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [verificando, setVerificando] = useState(false);

  async function confirmar() {
    if (!password) {
      setError('La contraseña es obligatoria');
      return;
    }
    setVerificando(true);
    try {
      await api.post('/auth/verify-password', { password });
      setPassword('');
      setError('');
      onConfirmed();
    } catch {
      setError('Contraseña incorrecta. Inténtelo de nuevo.');
      setPassword('');
    } finally {
      setVerificando(false);
    }
  }

  function handleClose() {
    setPassword('');
    setError('');
    onClose();
  }

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="Confirmar acción"
      footer={
        <div className="flex items-center justify-end gap-2">
          <Button
            variant="outline"
            onClick={handleClose}
            disabled={verificando}
            className="text-xs min-h-[44px] sm:min-h-9"
          >
            Cancelar
          </Button>
          <Button
            variant="primary"
            onClick={confirmar}
            disabled={verificando}
            className="text-xs min-h-[44px] sm:min-h-9"
          >
            {verificando ? 'Verificando...' : 'Confirmar'}
          </Button>
        </div>
      }
    >
      <p className="mb-3 text-xs sm:text-sm text-muted-foreground">
        Para {descripcion || 'continuar con esta acción'}, ingrese su contraseña de administrador.
      </p>
      <Input
        id="confirm-password"
        label="Contraseña"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={error}
        autoFocus
        onKeyDown={(e) => e.key === 'Enter' && confirmar()}
      />
    </Modal>
  );
}
