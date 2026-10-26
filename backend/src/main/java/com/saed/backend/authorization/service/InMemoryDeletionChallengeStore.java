package com.saed.backend.authorization.service;

import com.saed.backend.authorization.service.PropertyDeletionChallengeService.ChallengeState;
import com.saed.backend.authorization.service.PropertyDeletionChallengeService.DeletionChallenge;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Single-instance store used by tests. Production uses {@link JdbcDeletionChallengeStore}. */
final class InMemoryDeletionChallengeStore implements DeletionChallengeStore {

    private final Map<String, DeletionChallenge> challenges = new LinkedHashMap<>();

    @Override
    public synchronized void insert(DeletionChallenge challenge, Duration ttl) {
        challenges.put(challenge.getChallengeId(), challenge);
    }

    @Override
    public synchronized Optional<DeletionChallenge> find(String challengeId) {
        return Optional.ofNullable(challenges.get(challengeId));
    }

    @Override
    public synchronized void delete(String challengeId) {
        challenges.remove(challengeId);
    }

    @Override
    public synchronized void revokeOpen(Long propertyId, Long userId) {
        challenges.values().removeIf(c -> c.getPropertyId().equals(propertyId) && c.getUserId().equals(userId)
                && (c.getState() == ChallengeState.PENDING || c.getState() == ChallengeState.VERIFIED));
    }

    @Override
    public synchronized void purgeStale(Instant now) {
        challenges.values().removeIf(c -> now.isAfter(c.getExpiresAt())
                || c.getState() == ChallengeState.CONSUMED
                || c.getState() == ChallengeState.EXPIRED
                || c.getState() == ChallengeState.BLOCKED);
    }

    @Override
    public synchronized int registerAttempt(String challengeId) {
        DeletionChallenge current = challenges.get(challengeId);
        if (current == null || current.getState() != ChallengeState.PENDING) {
            return 0;
        }
        int attempts = current.getAttempts() + 1;
        challenges.put(challengeId, copy(current, attempts, current.getState()));
        return attempts;
    }

    @Override
    public synchronized boolean transition(String challengeId, ChallengeState from, ChallengeState to, Instant now) {
        DeletionChallenge current = challenges.get(challengeId);
        if (current == null || current.getState() != from) {
            return false;
        }
        boolean needsLiveChallenge = to == ChallengeState.VERIFIED || to == ChallengeState.CONSUMED;
        if (needsLiveChallenge && current.isExpired(now)) {
            return false;
        }
        challenges.put(challengeId, copy(current, current.getAttempts(), to));
        return true;
    }

    @Override
    public synchronized int count() {
        return challenges.size();
    }

    private static DeletionChallenge copy(DeletionChallenge c, int attempts, ChallengeState state) {
        return new DeletionChallenge(c.getChallengeId(), c.getPropertyId(), c.getOrganizationId(), c.getUserId(),
                c.getUserEmail(), c.getPropertyName(), c.getOtpHash(), c.getSalt(), c.getCreatedAt(), c.getExpiresAt(),
                attempts, state);
    }
}
