package com.scanbite.backend.controller;

import com.scanbite.backend.model.CafeTable;
import com.scanbite.backend.model.OrderEntity;
import com.scanbite.backend.model.OrderItem;
import com.scanbite.backend.model.OrderStatus;
import com.scanbite.backend.model.User;
import com.scanbite.backend.repository.CafeTableRepository;
import com.scanbite.backend.repository.MenuItemRepository;
import com.scanbite.backend.repository.OrderRepository;
import com.scanbite.backend.repository.UserRepository;
import com.scanbite.backend.repository.CafeRepository;
import com.scanbite.backend.service.OrderService;
import com.scanbite.dto.OrderItemDto;
import com.scanbite.dto.OrderRequestDto;
import com.scanbite.backend.utils.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final MenuItemRepository menuItemRepository;
    private final CafeTableRepository tableRepository;
    private final UserRepository userRepository;
    private final CafeRepository cafeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public OrderController(OrderService orderService,
                           OrderRepository orderRepository,
                           MenuItemRepository menuItemRepository,
                           CafeTableRepository tableRepository,
                           UserRepository userRepository,
                           CafeRepository cafeRepository,
                           SimpMessagingTemplate messagingTemplate) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.menuItemRepository = menuItemRepository;
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
        this.cafeRepository = cafeRepository;
        this.messagingTemplate = messagingTemplate;
    }

    private void validateCafeIdAccess(Long cafeId) {
        if (SecurityUtils.isSuperAdmin()) return;
        String currentUsername = SecurityUtils.getCurrentUsername();
        if (currentUsername == null) {
            throw new AccessDeniedException("Unauthenticated user accessing orders.");
        }
        com.scanbite.backend.model.Cafe cafe = cafeRepository.findById(cafeId)
                .orElseThrow(() -> new com.scanbite.backend.exception.ResourceNotFoundException("Cafe", "id", cafeId));
        if (cafe.getOwner() == null || !currentUsername.equals(cafe.getOwner().getUsername())) {
            throw new AccessDeniedException("You are not authorized to access orders of this cafe.");
        }
    }

    private void validateOrderAccess(Long orderId) {
        if (SecurityUtils.isSuperAdmin()) return;
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new com.scanbite.backend.exception.ResourceNotFoundException("Order", "id", orderId));
        if (order.getCafe() == null) {
            throw new AccessDeniedException("Order has no cafe assigned.");
        }
        validateCafeIdAccess(order.getCafe().getId());
    }

    private void validateOrderAccessOrOwner(Long orderId) {
        if (SecurityUtils.isSuperAdmin()) return;
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new com.scanbite.backend.exception.ResourceNotFoundException("Order", "id", orderId));
        
        String currentUsername = SecurityUtils.getCurrentUsername();
        if (currentUsername == null) {
            throw new AccessDeniedException("Unauthenticated");
        }
        
        // If the customer placed the order, let them fetch it
        if (currentUsername.equals(order.getCustomerPhone())) {
            return;
        }
        
        // Else, must be the owner of the cafe
        if (order.getCafe() == null) {
            throw new AccessDeniedException("You are not authorized to view this order.");
        }
        validateCafeIdAccess(order.getCafe().getId());
    }

    @GetMapping
    public List<OrderEntity> list(@RequestParam(required = false) Long cafeId,
                                  @RequestParam(required = false) String status) {
        if (cafeId == null) {
            if (!SecurityUtils.isSuperAdmin()) {
                throw new AccessDeniedException("Cafe ID is required to query orders");
            }
            List<OrderEntity> list = orderService.listAll();
            return filterByStatus(list, status);
        }
        
        validateCafeIdAccess(cafeId);
        List<OrderEntity> list = orderRepository.findByCafe_Id(cafeId);
        return filterByStatus(list, status);
    }
    
    private List<OrderEntity> filterByStatus(List<OrderEntity> list, String status) {
        if (status != null && !status.isBlank()) {
            List<OrderEntity> filtered = new ArrayList<>();
            for (OrderEntity o : list) if (o.getStatus() != null && status.equalsIgnoreCase(o.getStatus().name())) filtered.add(o);
            return filtered;
        }
        return list;
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderEntity> getOrder(@PathVariable Long id) {
        validateOrderAccessOrOwner(id);
        OrderEntity order = orderService.getById(id);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/live")
    public List<OrderEntity> live(@RequestParam Long cafeId) {
        validateCafeIdAccess(cafeId);
        return orderRepository.findByCafe_Id(cafeId);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody OrderRequestDto request) {
        OrderEntity order = new OrderEntity();
        
        CafeTable table = tableRepository.findById(request.getTableId()).orElse(null);
        if (table != null) {
            order.setTable(table);
            if (table.getCafe() != null) order.setCafe(table.getCafe());
        }
        
        if (request.getCustomerId() != null) {
            User u = userRepository.findById(request.getCustomerId()).orElse(null);
            if (u != null) {
                // Assert that the customer placing the order matches the authenticated user token (unless SUPER_ADMIN)
                String currentUsername = SecurityUtils.getCurrentUsername();
                if (!SecurityUtils.isSuperAdmin() && currentUsername != null && !currentUsername.equals(u.getUsername())) {
                    throw new AccessDeniedException("You cannot place orders on behalf of another user");
                }
                order.setCustomerName(u.getFullName());
                order.setCustomerPhone(u.getUsername());
            }
        }

        List<OrderItem> items = new ArrayList<>();
        double subtotal = 0.0;
        if (request.getItems() != null) {
            for (OrderItemDto it : request.getItems()) {
                menuItemRepository.findById(it.getMenuItemId()).ifPresent(mi -> {
                    OrderItem oi = new OrderItem();
                    oi.setName(mi.getName()); oi.setQty(it.getQuantity()); oi.setPrice(mi.getPrice());
                    oi.setOrder(order);
                    items.add(oi);
                });
            }
            for (OrderItem oi : items) subtotal += oi.getPrice() * oi.getQty();
        }
        order.setItems(items);
        order.setSubtotal(subtotal);
        double calculatedTax = subtotal * 0.05;
        order.setTax(calculatedTax);
        order.setTotal(subtotal + calculatedTax);
        order.setPaid(false);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(OffsetDateTime.now());

        OrderEntity saved = orderService.create(order);
        try { messagingTemplate.convertAndSend("/topic/orders", saved); } catch (Exception e) {}
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody StatusRequest req) {
        validateOrderAccess(id);
        OrderEntity updated = orderService.updateStatus(id, req.status);
        try { messagingTemplate.convertAndSend("/topic/orders", updated); } catch (Exception e) {}
        return ResponseEntity.ok(updated);
    }
    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<?> confirmOrder(@PathVariable Long id, @RequestParam("prepTime") int prepTime) {
        validateOrderAccess(id);
        OrderEntity o = orderService.getById(id);
        o.setStatus(OrderStatus.PREPARING);
        o.setPrepTimeMinutes(prepTime);
        o.setConfirmedAt(OffsetDateTime.now());
        OrderEntity updated = orderRepository.save(o);
        try { messagingTemplate.convertAndSend("/topic/orders", updated); } catch (Exception e) {}
        return ResponseEntity.ok(updated);
    }


    @PutMapping("/{id}/paid")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<?> updatePaid(@PathVariable Long id, @RequestParam("paid") boolean paid) {
        validateOrderAccess(id);
        OrderEntity updated = orderService.markPaid(id, paid);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/history")
    public List<OrderEntity> history(@RequestParam(required = false) Long customerId,
                                     @RequestParam(required = false) String phone) {
        String currentUsername = SecurityUtils.getCurrentUsername();
        if (currentUsername == null) {
            throw new AccessDeniedException("Unauthenticated");
        }
        
        // If a customer is querying their history, force it to match their authenticated username
        if (!SecurityUtils.isSuperAdmin()) {
            if (phone != null && !phone.equals(currentUsername)) {
                throw new AccessDeniedException("You are not authorized to view this history.");
            }
            if (customerId != null) {
                User u = userRepository.findById(customerId).orElse(null);
                if (u != null && !u.getUsername().equals(currentUsername)) {
                    throw new AccessDeniedException("You are not authorized to view this history.");
                }
            }
        }
        
        if (customerId != null) {
            User u = userRepository.findById(customerId).orElse(null);
            if (u != null) return orderRepository.findByCustomerPhone(u.getUsername());
        }
        if (phone != null) return orderRepository.findByCustomerPhone(phone);
        return Collections.emptyList();
    }

    public static class StatusRequest { public String status; }
}
