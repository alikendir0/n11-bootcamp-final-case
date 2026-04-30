package com.n11.order.order;

import java.io.Serializable;
import java.util.UUID;

public record OrderItemId(UUID orderId, UUID productId) implements Serializable {}
