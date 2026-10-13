package com.saed.backend.authorization.service;

import com.saed.backend.authorization.service.PropertyDeletionChallengeService.ChallengeState;
import com.saed.backend.authorization.service.PropertyDeletionChallengeService.DeletionChallenge;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Storage of property-deletion challenges. Implementations must make {@link #registerAttempt} and
 * {@link #transition} atomic (conditional updates), because several requests, possibly on different instances,
 * can act on the same challenge at once.
 */
public interface DeletionChallengeStore {

    /** Stores a new PENDING challenge that expires after {@code ttl}. */
    void insert(DeletionChallenge challenge, Duration ttl);

    Optional<DeletionChallenge> find(String challengeId);

    /** Removes the challenge (an OTP that could not be delivered). */
    void delete(String challengeId);

    /** Removes the PENDING and VERIFIED challenges of the same property and user (a new request replaces them). */
    void revokeOpen(Long propertyId, Long userId);

    /** Drops expired and finished challenges so the store does not grow without bound. */
    void purgeStale(Instant now);

    /**
     * Counts one verification attempt only while the challenge is PENDING and returns the new attempt count, or 0
     * when the challenge is no longer PENDING (or does not exist).
     */
    int registerAttempt(String challengeId);

    /**
     * Moves the challenge from {@code from} to {@code to} only if it is currently in {@code from}; moves into
     * VERIFIED or CONSUMED also require that it has not expired. Returns whether this call performed the change.
     */
    boolean transition(String challengeId, ChallengeState from, ChallengeState to, Instant now);

    /** Number of stored challenges (diagnostics and tests). */
    int count();
}
