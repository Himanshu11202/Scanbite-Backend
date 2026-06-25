package com.scanbite.backend.controller;

import com.scanbite.backend.model.MenuItem;
import com.scanbite.backend.model.Cafe;
import com.scanbite.backend.model.MenuCategory;
import com.scanbite.backend.dto.BatchSaveRequest;
import com.scanbite.backend.dto.ScannedMenuItem;
import com.scanbite.backend.dto.MenuScanResponse;
import com.scanbite.backend.repository.CafeRepository;
import com.scanbite.backend.repository.MenuCategoryRepository;
import com.scanbite.backend.service.MenuService;
import com.scanbite.backend.service.MenuScannerService;
import com.scanbite.backend.utils.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
public class MenuController {
    private final MenuService menuService;
    private final MenuScannerService menuScannerService;
    private final CafeRepository cafeRepository;
    private final MenuCategoryRepository menuCategoryRepository;

    public MenuController(MenuService menuService,
                          MenuScannerService menuScannerService,
                          CafeRepository cafeRepository,
                          MenuCategoryRepository menuCategoryRepository) {
        this.menuService = menuService;
        this.menuScannerService = menuScannerService;
        this.cafeRepository = cafeRepository;
        this.menuCategoryRepository = menuCategoryRepository;
    }

    private void validateCafeOwnership(Cafe cafe) {
        if (SecurityUtils.isSuperAdmin()) return;
        String currentUsername = SecurityUtils.getCurrentUsername();
        if (currentUsername == null || cafe.getOwner() == null || !currentUsername.equals(cafe.getOwner().getUsername())) {
            throw new AccessDeniedException("You are not authorized to perform operations for this cafe.");
        }
    }

    @GetMapping
    public List<MenuItem> list(@RequestParam(value = "cafeId", required = false) Long cafeId) {
        if (cafeId != null) {
            return menuService.listByCafe(cafeId);
        }
        return menuService.listAll();
    }

    @GetMapping("/category/{cat}")
    public List<MenuItem> byCategory(@PathVariable String cat) { return menuService.listByCategory(cat); }

    @GetMapping("/{id}")
    public MenuItem get(@PathVariable Long id) { return menuService.getById(id); }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<?> create(@RequestBody MenuItem item) {
        if (item.getCafe() == null || item.getCafe().getId() == null) {
            return ResponseEntity.badRequest().body("Cafe ID is required");
        }
        Cafe cafe = cafeRepository.findById(item.getCafe().getId())
                .orElseThrow(() -> new com.scanbite.backend.exception.ResourceNotFoundException("Cafe", "id", item.getCafe().getId()));
        validateCafeOwnership(cafe);

        if (item.getName() == null || item.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Menu item name is required");
        }
        if (item.getPrice() < 0) {
            return ResponseEntity.badRequest().body("Price cannot be negative");
        }
        if (item.getSpicy() < 0 || item.getSpicy() > 5) {
            return ResponseEntity.badRequest().body("Spicy level must be between 0 and 5");
        }

        if (item.getCategory() != null && item.getCategory().getId() != null) {
            MenuCategory cat = menuCategoryRepository.findById(item.getCategory().getId()).orElse(null);
            if (cat != null) {
                boolean duplicate = menuService.listByCafe(cafe.getId()).stream()
                        .anyMatch(x -> x.getCategory() != null 
                                && x.getCategory().getId().equals(cat.getId()) 
                                && x.getName().equalsIgnoreCase(item.getName().trim()));
                if (duplicate) {
                    return ResponseEntity.badRequest().body("Menu item with name '" + item.getName() + "' already exists in category '" + cat.getName() + "'");
                }
                item.setCategory(cat);
            }
        }

        return ResponseEntity.ok(menuService.create(item));
    }

    @PostMapping(value = "/{id}/image", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<?> uploadImage(@PathVariable Long id, @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        MenuItem existing = menuService.getById(id);
        validateCafeOwnership(existing.getCafe());
        return ResponseEntity.ok(menuService.uploadImage(id, file));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody MenuItem patch) {
        MenuItem existing = menuService.getById(id);
        validateCafeOwnership(existing.getCafe());

        if (patch.getName() != null && patch.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Menu item name cannot be blank");
        }
        if (patch.getPrice() < 0) {
            return ResponseEntity.badRequest().body("Price cannot be negative");
        }
        if (patch.getSpicy() < 0 || patch.getSpicy() > 5) {
            return ResponseEntity.badRequest().body("Spicy level must be between 0 and 5");
        }

        String newName = patch.getName() != null ? patch.getName().trim() : existing.getName();
        Long catId = (patch.getCategory() != null) ? patch.getCategory().getId() : (existing.getCategory() != null ? existing.getCategory().getId() : null);
        if (catId != null) {
            boolean duplicate = menuService.listByCafe(existing.getCafe().getId()).stream()
                .anyMatch(x -> !x.getId().equals(id) 
                    && x.getCategory() != null 
                    && x.getCategory().getId().equals(catId) 
                    && x.getName().equalsIgnoreCase(newName));
            if (duplicate) {
                return ResponseEntity.badRequest().body("Another item with name '" + newName + "' already exists in this category");
            }
        }

        if (patch.getCategory() != null && patch.getCategory().getId() != null) {
            MenuCategory cat = menuCategoryRepository.findById(patch.getCategory().getId()).orElse(null);
            patch.setCategory(cat);
        } else {
            patch.setCategory(null);
        }

        return ResponseEntity.ok(menuService.update(id, patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        MenuItem existing = menuService.getById(id);
        validateCafeOwnership(existing.getCafe());
        menuService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/scan", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<?> scanMenu(@RequestParam("file") MultipartFile file) {
        try {
            MenuScanResponse result = menuScannerService.scanMenu(file);
            if (!result.isMenuDetected()) {
                return ResponseEntity.badRequest().body("No menu detected in this image. Please upload a clear menu photo.");
            }
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CAFE_ADMIN')")
    public ResponseEntity<?> batchSave(@RequestBody BatchSaveRequest request) {
        Cafe cafe = cafeRepository.findById(request.getCafeId()).orElse(null);
        if (cafe == null) {
            return ResponseEntity.badRequest().body("Cafe not found");
        }
        validateCafeOwnership(cafe);

        // 1. Validate empty names & negative prices
        for (ScannedMenuItem itemDto : request.getItems()) {
            if (itemDto.getName() == null || itemDto.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Menu item name cannot be empty in batch request");
            }
            if (itemDto.getPrice() < 0) {
                return ResponseEntity.badRequest().body("Price cannot be negative in batch request");
            }
        }

        // 2. Validate duplicates in the request items list itself
        java.util.Set<String> uniqueKeys = new java.util.HashSet<>();
        for (ScannedMenuItem itemDto : request.getItems()) {
            String key = (itemDto.getCategoryName() != null ? itemDto.getCategoryName().trim().toLowerCase() : "uncategorized") + ":" + itemDto.getName().trim().toLowerCase();
            if (!uniqueKeys.add(key)) {
                return ResponseEntity.badRequest().body("Duplicate item '" + itemDto.getName() + "' in category '" + itemDto.getCategoryName() + "' found in the request.");
            }
        }

        // 3. Validate duplicates against the database
        for (ScannedMenuItem itemDto : request.getItems()) {
            String finalCatName = (itemDto.getCategoryName() != null ? itemDto.getCategoryName().trim() : "Uncategorized");
            MenuCategory category = menuCategoryRepository.findByCafe_IdOrderBySortOrder(cafe.getId())
                .stream()
                .filter(c -> c.getName().equalsIgnoreCase(finalCatName))
                .findFirst()
                .orElse(null);
            if (category != null) {
                boolean duplicateInDb = menuService.listByCafe(cafe.getId()).stream()
                    .anyMatch(x -> x.getCategory() != null 
                        && x.getCategory().getId().equals(category.getId()) 
                        && x.getName().equalsIgnoreCase(itemDto.getName().trim()));
                if (duplicateInDb) {
                    return ResponseEntity.badRequest().body("Item '" + itemDto.getName() + "' already exists in category '" + finalCatName + "' in the database");
                }
            }
        }

        java.util.List<MenuItem> savedItems = new java.util.ArrayList<>();
        for (ScannedMenuItem itemDto : request.getItems()) {
            String catName = itemDto.getCategoryName();
            if (catName == null || catName.trim().isEmpty()) {
                catName = "Uncategorized";
            }
            final String finalCatName = catName.trim();
            MenuCategory category = menuCategoryRepository.findByCafe_IdOrderBySortOrder(cafe.getId())
                .stream()
                .filter(c -> c.getName().equalsIgnoreCase(finalCatName))
                .findFirst()
                .orElseGet(() -> {
                    MenuCategory newCat = new MenuCategory();
                    newCat.setName(finalCatName);
                    newCat.setCafe(cafe);
                    newCat.setSortOrder(0);
                    return menuCategoryRepository.save(newCat);
                });

            MenuItem item = new MenuItem();
            item.setName(itemDto.getName().trim());
            item.setPrice(itemDto.getPrice());
            item.setVeg(itemDto.isVeg());
            item.setDescription(itemDto.getDescription());
            item.setCafe(cafe);
            item.setCategory(category);
            item.setAvailable(true);
            savedItems.add(menuService.create(item));
        }
        return ResponseEntity.ok(savedItems);
    }
}
