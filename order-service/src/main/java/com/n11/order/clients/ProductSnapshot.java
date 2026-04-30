package com.n11.order.clients;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSnapshot(UUID id, String nameTr, BigDecimal priceGross) {}
