package com.scanbite.backend.controller;

import com.scanbite.backend.model.User;
import com.scanbite.backend.repository.UserRepository;
import com.scanbite.backend.repository.RoleRepository;
import com.scanbite.backend.model.Role;
import com.scanbite.backend.security.JwtProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository userRepository, JwtProvider jwtProvider, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.roleRepository = roleRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        long start = System.currentTimeMillis();
        System.out.println("[PERF] Register request received at " + start);

        String username = body.get("username");
        String email = body.get("email");
        String mobileNumber = body.get("mobileNumber");
        String password = body.get("password");
        
        long dbStart = System.currentTimeMillis();
        boolean usernameExists = userRepository.existsByUsername(username);
        System.out.println("[PERF] DB query checkUsername took " + (System.currentTimeMillis() - dbStart) + " ms");
        if (usernameExists) {
            return ResponseEntity.badRequest().body("Username exists");
        }

        if (email != null && !email.trim().isEmpty()) {
            long dbEmailStart = System.currentTimeMillis();
            boolean emailExists = userRepository.existsByEmail(email);
            System.out.println("[PERF] DB query checkEmail took " + (System.currentTimeMillis() - dbEmailStart) + " ms");
            if (emailExists) {
                return ResponseEntity.badRequest().body("Email already registered");
            }
        }

        if (mobileNumber != null && !mobileNumber.trim().isEmpty()) {
            long dbMobileStart = System.currentTimeMillis();
            boolean mobileExists = userRepository.existsByMobileNumber(mobileNumber);
            System.out.println("[PERF] DB query checkMobile took " + (System.currentTimeMillis() - dbMobileStart) + " ms");
            if (mobileExists) {
                return ResponseEntity.badRequest().body("Mobile number already registered");
            }
        }

        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setMobileNumber(mobileNumber);
        
        long encodeStart = System.currentTimeMillis();
        u.setPassword(passwordEncoder.encode(password));
        System.out.println("[PERF] Password encode took " + (System.currentTimeMillis() - encodeStart) + " ms");
        
        u.setFullName(body.getOrDefault("fullName", username));

        // Accept optional role field (SUPER_ADMIN, CAFE_ADMIN, CUSTOMER). Default to CUSTOMER.
        String role = body.getOrDefault("role", "CUSTOMER").toUpperCase();
        String roleName;
        switch (role) {
            case "SUPER_ADMIN": roleName = "ROLE_SUPER_ADMIN"; break;
            case "CAFE_ADMIN": roleName = "ROLE_CAFE_ADMIN"; break;
            default: roleName = "ROLE_CUSTOMER";
        }
        
        long roleDbStart = System.currentTimeMillis();
        Role r = roleRepository.findByName(roleName).orElseGet(() -> roleRepository.save(new Role(roleName)));
        System.out.println("[PERF] DB query role lookup/save took " + (System.currentTimeMillis() - roleDbStart) + " ms");
        
        u.getRoles().add(r);
        
        long saveStart = System.currentTimeMillis();
        userRepository.save(u);
        System.out.println("[PERF] DB user save took " + (System.currentTimeMillis() - saveStart) + " ms");
        
        java.util.Set<String> roleNames = u.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet());
        
        long jwtStart = System.currentTimeMillis();
        String token = jwtProvider.generateToken(username, roleNames);
        System.out.println("[PERF] JWT generation took " + (System.currentTimeMillis() - jwtStart) + " ms");
        
        System.out.println("[PERF] Register total duration: " + (System.currentTimeMillis() - start) + " ms");
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        long start = System.currentTimeMillis();
        System.out.println("[PERF] Login request received at " + start);

        String username = body.get("username");
        String password = body.get("password");
        
        long dbStart = System.currentTimeMillis();
        var userOpt = userRepository.findByUsernameOrEmailOrMobileNumber(username, username, username);
        System.out.println("[PERF] DB query findUser took " + (System.currentTimeMillis() - dbStart) + " ms");
        
        return userOpt.map(u -> {
                    long encodeStart = System.currentTimeMillis();
                    boolean matches = passwordEncoder.matches(password, u.getPassword());
                    System.out.println("[PERF] Password encode match took " + (System.currentTimeMillis() - encodeStart) + " ms");
                    if (matches) {
                        java.util.Set<String> roleNames = u.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet());
                        long jwtStart = System.currentTimeMillis();
                        String token = jwtProvider.generateToken(u.getUsername(), roleNames);
                        System.out.println("[PERF] JWT generation took " + (System.currentTimeMillis() - jwtStart) + " ms");
                        System.out.println("[PERF] Login total duration: " + (System.currentTimeMillis() - start) + " ms");
                        return ResponseEntity.ok(Map.of("token", token));
                    } else {
                        System.out.println("[PERF] Login total duration (invalid credentials): " + (System.currentTimeMillis() - start) + " ms");
                        return ResponseEntity.status(401).body("Invalid credentials");
                    }
                }).orElseGet(() -> {
                    System.out.println("[PERF] Login total duration (user not found): " + (System.currentTimeMillis() - start) + " ms");
                    return ResponseEntity.status(404).body("User not found");
                });
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader(name = "Authorization", required = false) String header) {
        if (header == null || !header.startsWith("Bearer ")) return ResponseEntity.badRequest().body("Missing Authorization header");
        String token = header.substring(7);
        if (!jwtProvider.validateToken(token)) return ResponseEntity.status(401).body("Invalid token");
        String username = jwtProvider.getUsernameFromToken(token);
        java.util.Optional<User> uOpt = userRepository.findByUsername(username);
        if (uOpt.isEmpty()) {
            return ResponseEntity.status(401).body("User not found");
        }
        User u = uOpt.get();
        java.util.Set<String> roleNames = u.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet());
        return ResponseEntity.ok(Map.of(
            "id", u.getId(),
            "username", username,
            "email", u.getEmail() != null ? u.getEmail() : "",
            "mobileNumber", u.getMobileNumber() != null ? u.getMobileNumber() : "",
            "fullName", u.getFullName() != null ? u.getFullName() : "",
            "designation", u.getDesignation() != null ? u.getDesignation() : "",
            "ownerPhoto", u.getOwnerPhoto() != null ? u.getOwnerPhoto() : "",
            "roles", roleNames
        ));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> body, @RequestHeader(name = "Authorization", required = false) String header) {
        if (header == null || !header.startsWith("Bearer ")) return ResponseEntity.badRequest().body("Missing Authorization header");
        String token = header.substring(7);
        if (!jwtProvider.validateToken(token)) return ResponseEntity.status(401).body("Invalid token");
        String username = jwtProvider.getUsernameFromToken(token);
        java.util.Optional<User> uOpt = userRepository.findByUsername(username);
        if (uOpt.isEmpty()) return ResponseEntity.status(401).body("User not found");
        User u = uOpt.get();
        if (body.containsKey("fullName")) u.setFullName(body.get("fullName"));
        if (body.containsKey("email")) u.setEmail(body.get("email"));
        if (body.containsKey("mobileNumber")) u.setMobileNumber(body.get("mobileNumber"));
        if (body.containsKey("designation")) u.setDesignation(body.get("designation"));
        userRepository.save(u);
        return ResponseEntity.ok(u);
    }

    @PostMapping(value = "/profile/photo", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadProfilePhoto(@RequestParam("file") org.springframework.web.multipart.MultipartFile file, @RequestHeader(name = "Authorization", required = false) String header) throws java.io.IOException {
        if (header == null || !header.startsWith("Bearer ")) return ResponseEntity.badRequest().body("Missing Authorization header");
        String token = header.substring(7);
        if (!jwtProvider.validateToken(token)) return ResponseEntity.status(401).body("Invalid token");
        String username = jwtProvider.getUsernameFromToken(token);
        java.util.Optional<User> uOpt = userRepository.findByUsername(username);
        if (uOpt.isEmpty()) return ResponseEntity.status(401).body("User not found");
        User u = uOpt.get();
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body("File is empty");
        String uploadsBase = "uploads/owners/" + u.getId();
        java.nio.file.Path dir = java.nio.file.Path.of(uploadsBase);
        java.nio.file.Files.createDirectories(dir);
        String filename = System.currentTimeMillis() + "-" + file.getOriginalFilename();
        java.nio.file.Path target = dir.resolve(filename).toAbsolutePath();
        try (var in = file.getInputStream()) {
            java.nio.file.Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        String photoUrl = "/" + uploadsBase + "/" + filename;
        u.setOwnerPhoto(photoUrl);
        userRepository.save(u);
        return ResponseEntity.ok(Map.of("ownerPhoto", photoUrl));
    }
}
