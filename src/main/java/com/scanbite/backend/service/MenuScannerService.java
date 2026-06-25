package com.scanbite.backend.service;

import com.scanbite.backend.dto.MenuScanResponse;
import com.scanbite.backend.dto.ScannedMenuItem;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface MenuScannerService {
    MenuScanResponse scanMenu(MultipartFile file) throws IOException;
}
