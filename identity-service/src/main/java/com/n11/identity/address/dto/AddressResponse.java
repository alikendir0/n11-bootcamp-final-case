package com.n11.identity.address.dto;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        String title,
        String recipientName,
        String phone,
        String il,
        String ilce,
        String mahalle,
        String streetLine,
        String postalCode,
        boolean isDefault,
        Instant createdAt
) { }
