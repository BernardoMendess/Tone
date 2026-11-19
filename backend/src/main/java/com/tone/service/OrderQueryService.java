package com.tone.service;

import com.tone.domain.entity.Order;
import com.tone.domain.entity.OrderItem;
import com.tone.domain.entity.PaymentSplit;
import com.tone.dto.OrderDtos;
import com.tone.dto.PageResponse;
import com.tone.exception.ApiException;
import com.tone.repository.OrderItemRepository;
import com.tone.repository.OrderRepository;
import com.tone.repository.PaymentSplitRepository;
import com.tone.service.mapper.OrderMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A4: Paginated order listing with batch-loaded items and splits (3 queries total, zero N+1).
 */
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentSplitRepository paymentSplitRepository;

    public OrderQueryService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentSplitRepository paymentSplitRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentSplitRepository = paymentSplitRepository;
    }

    public PageResponse<OrderDtos.OrderDto> getUserOrders(UUID buyerId, Pageable pageable) {
        Pageable effective = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 50),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<Order> page = orderRepository.findByBuyerId(buyerId, effective);
        if (page.isEmpty()) {
            return PageResponse.of(List.of(), page);
        }

        List<UUID> orderIds = page.getContent().stream().map(Order::getId).toList();
        Map<UUID, List<OrderItem>> itemsByOrder = orderItemRepository.findByOrderIdInWithProduct(orderIds).stream()
                .collect(Collectors.groupingBy(oi -> oi.getOrder().getId()));
        Map<UUID, List<PaymentSplit>> splitsByOrder = paymentSplitRepository.findByOrderIdInWithCreator(orderIds).stream()
                .collect(Collectors.groupingBy(ps -> ps.getOrder().getId()));

        List<OrderDtos.OrderDto> content = page.getContent().stream()
                .map(o -> OrderMapper.toDto(
                        o,
                        itemsByOrder.getOrDefault(o.getId(), List.of()),
                        splitsByOrder.getOrDefault(o.getId(), List.of())
                ))
                .toList();

        return PageResponse.of(content, page);
    }

    public OrderDtos.OrderDto getOrder(UUID buyerId, UUID orderId) {
        Order order = orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        return OrderMapper.toDto(
                order,
                orderItemRepository.findByOrderIdWithProduct(order.getId()),
                paymentSplitRepository.findByOrderIdWithCreator(order.getId())
        );
    }
}
