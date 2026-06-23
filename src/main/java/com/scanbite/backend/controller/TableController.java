package com.scanbite.backend.controller;

import com.scanbite.backend.service.CafeTableService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import com.scanbite.backend.model.CafeTable;
import com.scanbite.backend.repository.CafeTableRepository;
import com.scanbite.backend.repository.CafeRepository;
import com.scanbite.backend.utils.SecurityUtils;

@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final CafeTableService cafeTableService;
    private final CafeTableRepository cafeTableRepository;
    private final CafeRepository cafeRepository;

    public TableController(CafeTableService cafeTableService,
                           CafeTableRepository cafeTableRepository,
                           CafeRepository cafeRepository) {
        this.cafeTableService = cafeTableService;
        this.cafeTableRepository = cafeTableRepository;
        this.cafeRepository = cafeRepository;
    }

    private void validateCafeOwnership(com.scanbite.backend.model.Cafe cafe) {
        if (SecurityUtils.isSuperAdmin()) return;
        String currentUsername = SecurityUtils.getCurrentUsername();
        if (currentUsername == null || cafe.getOwner() == null || !currentUsername.equals(cafe.getOwner().getUsername())) {
            throw new AccessDeniedException("You are not authorized to perform operations for this cafe.");
        }
    }

    private void validateTableOwnership(Long tableId) {
        CafeTable table = cafeTableRepository.findById(tableId)
                .orElseThrow(() -> new com.scanbite.backend.exception.ResourceNotFoundException("CafeTable", "id", tableId));
        validateCafeOwnership(table.getCafe());
    }

    @GetMapping("/cafe/{cafeId}")
    public ResponseEntity<?> listByCafe(@PathVariable Long cafeId) {
        return ResponseEntity.ok(cafeTableRepository.findByCafe_Id(cafeId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<?> create(@RequestBody CafeTable table) {
        if (table.getCafe() == null || table.getCafe().getId() == null) {
            return ResponseEntity.badRequest().body("Cafe ID is required");
        }
        
        com.scanbite.backend.model.Cafe cafe = cafeRepository.findById(table.getCafe().getId()).orElse(null);
        if (cafe == null) {
            return ResponseEntity.badRequest().body("Cafe not found");
        }
        
        validateCafeOwnership(cafe);

        if (table.getTableNumber() == null || table.getTableNumber().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Table number is required");
        }
        
        String cleanTableNum = table.getTableNumber().trim();
        if (!cleanTableNum.matches("^\\d+$")) {
            return ResponseEntity.badRequest().body("Table number must be numeric");
        }

        if (cafeTableRepository.existsByCafe_IdAndTableNumber(cafe.getId(), cleanTableNum)) {
            return ResponseEntity.badRequest().body("Table number already exists in this cafe");
        }

        table.setTableNumber(cleanTableNum);
        table.setCafe(cafe);
        table.setStatus(com.scanbite.backend.model.TableStatus.AVAILABLE);
        CafeTable saved = cafeTableRepository.save(table);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        validateTableOwnership(id);
        cafeTableRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam("status") String statusStr) {
        validateTableOwnership(id);
        com.scanbite.backend.model.CafeTable table = cafeTableRepository.findById(id).orElse(null);
        if (table == null) return ResponseEntity.notFound().build();
        try {
            table.setStatus(com.scanbite.backend.model.TableStatus.valueOf(statusStr.toUpperCase()));
            com.scanbite.backend.model.CafeTable saved = cafeTableRepository.save(table);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status");
        }
    }

    @PostMapping("/{id}/qr")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<?> generateQr(@PathVariable("id") Long id) {
        validateTableOwnership(id);
        try {
            String publicUrl = cafeTableService.generateQrForTable(id);
            Map<String, String> body = new HashMap<>();
            body.put("qrImageUrl", publicUrl);
            body.put("download", "/api/tables/" + id + "/qr/download");
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/{id}/qr/download")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<byte[]> downloadQr(@PathVariable("id") Long id) {
        validateTableOwnership(id);
        try {
            byte[] image = cafeTableService.getQrImageForTable(id);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentLength(image.length);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=table-" + id + "-qr.png");
            return new ResponseEntity<>(image, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
