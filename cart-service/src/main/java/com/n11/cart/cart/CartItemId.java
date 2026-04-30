package com.n11.cart.cart;

import java.io.Serializable;
import java.util.UUID;

public record CartItemId(UUID userId, UUID productId) implements Serializable {}
