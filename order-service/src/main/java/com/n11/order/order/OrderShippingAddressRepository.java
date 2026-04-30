package com.n11.order.order;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrderShippingAddressRepository extends JpaRepository<OrderShippingAddress, UUID> {}
