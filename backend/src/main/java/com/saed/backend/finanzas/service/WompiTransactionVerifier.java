package com.saed.backend.finanzas.service;

import java.util.Optional;

/**
 * Authoritative lookup of a Wompi transaction by id, used to confirm a webhook event before it settles money.
 * The webhook signature only covers the properties Wompi lists in it and the signed list does not include the
 * payment reference, so a deployment holding the private API key should enable an implementation that asks the
 * provider. The default implementation is disabled (the signed checks alone apply).
 */
public interface WompiTransactionVerifier {

    /** Provider-confirmed view of a transaction. */
    record VerifiedTransaction(String reference, String status, long amountInCents) {}

    /** Whether lookups are performed; a disabled verifier is never consulted. */
    boolean enabled();

    /**
     * Returns the transaction as the provider reports it, or empty when the provider does not know it.
     * Transport failures must be thrown so the webhook is rolled back and retried by the provider.
     */
    Optional<VerifiedTransaction> find(String transactionId) throws Exception;
}
