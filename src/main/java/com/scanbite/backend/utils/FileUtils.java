package com.scanbite.backend.utils;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public class FileUtils {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    public static void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty or missing");
        }

        // 1. Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed limit of 5MB");
        }

        // 2. Check original filename
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Invalid filename");
        }

        // 3. Check extension
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Invalid file extension. Only jpg, jpeg, png, and webp are allowed");
        }

        // 4. Validate MIME type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Invalid file type. Only JPG, PNG, and WEBP images are allowed");
        }

        // 5. Basic executable header signatures check (magic bytes)
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length >= 2) {
                // MZ header for DOS/Windows PE Executable
                if (bytes[0] == 0x4D && bytes[1] == 0x5A) {
                    throw new IllegalArgumentException("Executable files are not allowed");
                }
                // ELF header for Unix/Linux Executable
                if (bytes.length >= 4 && bytes[0] == 0x7F && bytes[1] == 0x45 && bytes[2] == 0x4C && bytes[3] == 0x46) {
                    throw new IllegalArgumentException("Executable files are not allowed");
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read file bytes");
        }
    }

    public static String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1);
    }

    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "file_" + System.currentTimeMillis();
        }
        // Extract base name to bypass directory delimiters
        int lastUnixSlash = filename.lastIndexOf('/');
        int lastWindowsSlash = filename.lastIndexOf('\\');
        int index = Math.max(lastUnixSlash, lastWindowsSlash);
        String name = index == -1 ? filename : filename.substring(index + 1);
        
        // Clean special characters to prevent OS traversal injection
        return name.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
