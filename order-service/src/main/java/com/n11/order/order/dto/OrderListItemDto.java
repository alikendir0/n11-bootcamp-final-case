package com.n11.order.order.dto;

import com.n11.order.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderListItemDto(UUID id, OrderStatus status, BigDecimal totalAmount, Instant createdAt) {}
