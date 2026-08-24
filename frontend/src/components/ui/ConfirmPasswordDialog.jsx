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
        <>
          <Button variant="outline" onClick={handleClose} disabled={verificando}>
            Cancelar
          </Button>
          <Button onClick={confirmar} disabled={verificando}>
            {verificando ? 'Verificando...' : 'Confirmar'}
          </Button>
        </>
      }
    >
      <p style={{ marginBottom: '12px', fontSize: '13px', color: 'var(--on-surface-variant)' }}>
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
