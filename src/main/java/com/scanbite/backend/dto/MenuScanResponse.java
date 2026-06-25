package com.scanbite.backend.dto;

import java.util.List;

public class MenuScanResponse {
    private boolean menuDetected;
    private double confidence;
    private List<ScannedMenuItem> items;

    public boolean isMenuDetected() {
        return menuDetected;
    }

    public void setMenuDetected(boolean menuDetected) {
        this.menuDetected = menuDetected;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public List<ScannedMenuItem> getItems() {
        return items;
    }

    public void setItems(List<ScannedMenuItem> items) {
        this.items = items;
    }
}
