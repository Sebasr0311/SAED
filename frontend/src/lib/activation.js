import { api } from './api.js';

/**
 * Accounts are activated through a single-use link sent to the person's e-mail (DEC-13); the API never returns a
 * password. The resend endpoint is anonymous, rate limited, and answers the same whether or not the account exists.
 */
export function reenviarEnlaceActivacion(identificador) {
  return api.post('/auth/activar/solicitar-reenvio', { identificador });
}

export function mensajeEnlaceEnviado(email) {
  return `Se envió un enlace de activación a ${email}. El enlace es de un solo uso y vence en 48 horas.`;
}
