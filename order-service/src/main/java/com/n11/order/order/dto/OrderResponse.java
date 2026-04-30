package com.n11.order.order.dto;

import com.n11.order.order.OrderStatus;
import java.util.UUID;

public record OrderResponse(UUID orderId, OrderStatus status) {}
