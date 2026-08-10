import { useState } from 'react';
import { Modal } from './Modal.jsx';
import { Button } from './Button.jsx';
import { Input } from './Form.jsx';
import api from '../../lib/api.js';

/**
 * Modal que reverifica la contraseÃ±a del admin antes de una accion destructiva.
 * Uso: const confirmar = useConfirmPassword(); const ok = await confirmar.ask('eliminar X'); if (ok) { ... }
 */
export function ConfirmPasswordDialog({ open, onClose, onConfirmed, descripcion }) {
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [verificando, setVerificando] = useState(false);

  async function confirmar() {
    if (!password) {
      setError('La contraseÃ±a es obligatoria');
      return;
    }
    setVerificando(true);
    try {
      await api.post('/auth/verify-password', { password });
      setPassword('');
      setError('');
      onConfirmed();
    } catch {
      setError('ContraseÃ±a incorrecta. IntÃ©ntelo de nuevo.');
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
      title="Confirmar acciÃ³n"
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
        Para {descripcion || 'continuar con esta acciÃ³n'}, ingrese su contraseÃ±a de administrador.
      </p>
      <Input
        id="confirm-password"
        label="ContraseÃ±a"
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
