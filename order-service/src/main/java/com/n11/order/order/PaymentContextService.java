package com.n11.order.order;

import com.n11.order.order.dto.PaymentContextDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class PaymentContextService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderShippingAddressRepository addressRepository;

    public PaymentContextService(OrderRepository orderRepository,
                                 OrderItemRepository orderItemRepository,
                                 OrderShippingAddressRepository addressRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public PaymentContextDto getPaymentContext(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(this::notFound);
        OrderShippingAddress address = addressRepository.findById(orderId).orElseThrow(this::notFound);
        var items = orderItemRepository.findByOrderId(orderId).stream()
            .map(item -> new PaymentContextDto.Item(
                item.getProductId(),
                item.getNameSnapshot(),
                item.getQty(),
                item.getUnitPrice()))
            .toList();
        if (items.isEmpty()) {
            throw notFound();
        }

        return new PaymentContextDto(
            order.getId(),
            order.getUserId(),
            order.getTotalAmount(),
            order.getCurrency(),
            new PaymentContextDto.ShippingAddress(
                address.getRecipientName(),
                address.getPhone(),
                address.getIl(),
                address.getIlce(),
                address.getMahalle(),
                address.getStreetLine(),
                address.getPostalCode(),
                address.getTitle()),
            items);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
