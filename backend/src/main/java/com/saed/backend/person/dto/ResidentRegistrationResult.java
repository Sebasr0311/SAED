package com.saed.backend.person.dto;

/** Identifiers created by one resident registration. Null when the optional part was not requested. */
public record ResidentRegistrationResult(
        Long personId,
        Long guardianPersonId,
        Long contractId,
        Long residentLinkId,
        Long ownerLinkId,
        Long userId
) {
}
