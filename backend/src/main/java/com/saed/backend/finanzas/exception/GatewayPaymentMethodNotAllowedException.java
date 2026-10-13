package com.saed.backend.finanzas.exception;

/**
 * Raised when a client tries to register a payment with a gateway method (for example
 * {@code PASARELA_WOMPI}) through an ordinary registration route. Gateway payments are only
 * settled by the verified provider webhook; client input can never approve them.
 *
 * <p>Extends {@link IllegalArgumentException} so the global handler maps it to HTTP 400.
 */
public class GatewayPaymentMethodNotAllowedException extends IllegalArgumentException {

    public GatewayPaymentMethodNotAllowedException() {
        super("Gateway payment methods cannot be registered manually; gateway payments are settled by the provider webhook only");
    }
}
