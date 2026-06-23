package com.scanbite.backend.controller;

import com.scanbite.backend.model.Cafe;
import com.scanbite.backend.model.CafeTable;
import com.scanbite.backend.model.ServiceRequest;
import com.scanbite.backend.repository.CafeRepository;
import com.scanbite.backend.repository.CafeTableRepository;
import com.scanbite.backend.repository.ServiceRequestRepository;
import com.scanbite.backend.utils.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/service-requests")
public class ServiceRequestController {
    private final ServiceRequestRepository serviceRequestRepository;
    private final CafeRepository cafeRepository;
    private final CafeTableRepository tableRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ServiceRequestController(ServiceRequestRepository serviceRequestRepository,
                                    CafeRepository cafeRepository,
                                    CafeTableRepository tableRepository,
                                    SimpMessagingTemplate messagingTemplate) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.cafeRepository = cafeRepository;
        this.tableRepository = tableRepository;
        this.messagingTemplate = messagingTemplate;
    }

    private void validateCafeOwnership(Long cafeId) {
        if (SecurityUtils.isSuperAdmin()) return;
        String currentUsername = SecurityUtils.getCurrentUsername();
        if (currentUsername == null) {
            throw new AccessDeniedException("Unauthenticated user accessing service requests.");
        }
        Cafe cafe = cafeRepository.findById(cafeId)
                .orElseThrow(() -> new com.scanbite.backend.exception.ResourceNotFoundException("Cafe", "id", cafeId));
        if (cafe.getOwner() == null || !currentUsername.equals(cafe.getOwner().getUsername())) {
            throw new AccessDeniedException("You do not own this cafe.");
        }
    }

    @GetMapping
    public List<ServiceRequest> list(@RequestParam Long cafeId, @RequestParam(required = false) String status) {
        validateCafeOwnership(cafeId);
        if (status != null && !status.isBlank()) {
            return serviceRequestRepository.findByCafe_IdAndStatus(cafeId, status.toUpperCase());
        }
        return serviceRequestRepository.findByCafe_Id(cafeId);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("tableId") || !body.containsKey("requestType")) {
            return ResponseEntity.badRequest().body("tableId and requestType are required");
        }
        
        Long tableId = ((Number) body.get("tableId")).longValue();
        String requestType = (String) body.get("requestType");

        CafeTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new com.scanbite.backend.exception.ResourceNotFoundException("CafeTable", "id", tableId));
        
        ServiceRequest req = new ServiceRequest();
        req.setTable(table);
        req.setCafe(table.getCafe());
        req.setRequestType(requestType.toUpperCase());
        req.setStatus("PENDING");
        req.setCreatedAt(OffsetDateTime.now());

        ServiceRequest saved = serviceRequestRepository.save(req);
        
        // Broadcast over WebSocket!
        try {
            messagingTemplate.convertAndSend("/topic/services", saved);
        } catch (Exception e) {
            System.err.println("Failed to broadcast service request via WebSocket: " + e.getMessage());
        }

        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<?> complete(@PathVariable Long id) {
        ServiceRequest req = serviceRequestRepository.findById(id)
                .orElseThrow(() -> new com.scanbite.backend.exception.ResourceNotFoundException("ServiceRequest", "id", id));
        validateCafeOwnership(req.getCafe().getId());
        
        req.setStatus("COMPLETED");
        ServiceRequest saved = serviceRequestRepository.save(req);
        
        // Broadcast completion update over WebSocket so the dashboard updates in real-time
        try {
            messagingTemplate.convertAndSend("/topic/services", saved);
        } catch (Exception e) {
            System.err.println("Failed to broadcast service request completion via WebSocket: " + e.getMessage());
        }
        
        return ResponseEntity.ok(saved);
    }
}
