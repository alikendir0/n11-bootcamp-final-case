package com.n11.order.order;

import com.n11.order.order.dto.OrderDetailDto;
import com.n11.order.order.dto.OrderListItemDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class OrderQueryService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderShippingAddressRepository addressRepository;

    public OrderQueryService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                             OrderShippingAddressRepository addressRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<OrderListItemDto> listForUser(UUID userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(o -> new OrderListItemDto(o.getId(), o.getStatus(), o.getTotalAmount(), o.getCreatedAt()))
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailDto getDetail(UUID userId, UUID orderId) {
        Order o = orderRepository.findById(orderId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Sipariş bulunamadı: " + orderId));
        if (!o.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sipariş bulunamadı: " + orderId);
        }
        var items = orderItemRepository.findByOrderId(orderId).stream()
            .map(oi -> new OrderDetailDto.Line(oi.getProductId(), oi.getNameSnapshot(), oi.getQty(), oi.getUnitPrice()))
            .toList();
        var addr = addressRepository.findById(orderId).orElse(null);
        OrderDetailDto.ShippingAddress addrDto = (addr == null) ? null
            : new OrderDetailDto.ShippingAddress(addr.getRecipientName(), addr.getPhone(), addr.getIl(),
                addr.getIlce(), addr.getMahalle(), addr.getStreetLine(), addr.getPostalCode(), addr.getTitle());
        return new OrderDetailDto(o.getId(), o.getUserId(), o.getStatus(), o.getTotalAmount(), o.getCurrency(),
            o.getCancelReason(), o.getCreatedAt(), o.getUpdatedAt(), items, addrDto);
    }
}
