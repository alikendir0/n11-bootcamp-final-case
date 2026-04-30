package com.n11.order.order.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateOrderRequest(@NotNull UUID addressId, @NotNull String paymentMethod) {}
