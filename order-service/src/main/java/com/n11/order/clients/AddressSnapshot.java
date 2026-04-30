package com.n11.order.clients;

import java.util.UUID;

public record AddressSnapshot(
    UUID id, UUID userId, String recipientName, String phone,
    String il, String ilce, String mahalle, String streetLine, String postalCode, String title
) {}
