package com.scanbite.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<?> checkHealth() {
        boolean dbConnected = false;
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                dbConnected = true;
            }
        } catch (Exception e) {
            System.err.println("[HEALTH CHECK ERROR] Database connection failed: " + e.getMessage());
        }

        if (dbConnected) {
            return ResponseEntity.ok(Map.of("status", "UP"));
        } else {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
        }
    }
}
