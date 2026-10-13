package com.saed.backend.finanzas.service.impl;

import com.saed.backend.finanzas.service.WompiTransactionVerifier;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default verifier: no outbound call is made. Replace it with an implementation backed by the Wompi
 * transactions endpoint (private key) to confirm reference, status and amount before settlement.
 */
@Component
public class DisabledWompiTransactionVerifier implements WompiTransactionVerifier {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public Optional<VerifiedTransaction> find(String transactionId) {
        return Optional.empty();
    }
}
