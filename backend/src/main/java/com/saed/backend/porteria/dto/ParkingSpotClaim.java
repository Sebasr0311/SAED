package com.saed.backend.porteria.dto;

/** A visitor parking spot claimed atomically (DISPONIBLE to ASIGNADO). */
public record ParkingSpotClaim(Long idParqueadero, String numeroParqueadero) {}
