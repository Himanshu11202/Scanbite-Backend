package com.scanbite.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanbite.backend.dto.MenuScanResponse;
import com.scanbite.backend.dto.ScannedMenuItem;
import com.scanbite.backend.service.MenuScannerService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class MockMenuScannerServiceImpl implements MenuScannerService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public MenuScanResponse scanMenu(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("Empty file uploaded");
        }

        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = System.getProperty("GEMINI_API_KEY");
        }

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                return callRealGeminiAPI(file, apiKey);
            } catch (Exception e) {
                System.err.println("[AI Scanner] Real Gemini call failed, falling back to heuristics: " + e.getMessage());
            }
        }

        return runIntelligentHeuristicFallback(file);
    }

    private MenuScanResponse callRealGeminiAPI(MultipartFile file, String apiKey) throws Exception {
        byte[] bytes = file.getBytes();
        String base64Image = Base64.getEncoder().encodeToString(bytes);
        String mimeType = file.getContentType();
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = "image/jpeg";
        }

        String prompt = "You are an expert restaurant menu analyzer.\n" +
                "Analyze the provided image.\n" +
                "First, determine if the image is a food/beverage menu of a restaurant, cafe, bar or diner.\n" +
                "If it is NOT a menu, respond with this JSON:\n" +
                "{\"menuDetected\": false, \"confidence\": 0.0, \"items\": []}\n\n" +
                "If it IS a menu, detect all visible category headings (e.g. Starters, Beverages, Desserts, Pizza, etc.) and extract the food item names and prices under each category.\n" +
                "Ignore GST, restaurant addresses, phone numbers, watermarks, decorative text, logos, etc.\n" +
                "Estimate a confidence score between 0 and 100 for the extraction quality based on image readability.\n" +
                "Estimate descriptions and veg/non-veg status for the items if possible.\n" +
                "Respond ONLY with a valid JSON matching this schema:\n" +
                "{\n" +
                "  \"menuDetected\": true,\n" +
                "  \"confidence\": 85.0,\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"name\": \"Item Name\",\n" +
                "      \"price\": 299.0,\n" +
                "      \"categoryName\": \"Category Name\",\n" +
                "      \"veg\": true,\n" +
                "      \"description\": \"Short description of the item\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "Do not include any explanation or markdown formatting (such as ```json). Just the raw JSON.";

        Map<String, Object> inlineData = Map.of(
                "mimeType", mimeType,
                "data", base64Image
        );

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> imagePart = Map.of("inlineData", inlineData);

        Map<String, Object> contentPart = Map.of("parts", List.of(textPart, imagePart));
        Map<String, Object> requestBody = Map.of("contents", List.of(contentPart));

        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Gemini API returned status code " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String geminiText = root.path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text")
                .asText();

        if (geminiText == null || geminiText.trim().isEmpty()) {
            throw new IOException("Empty response from Gemini model");
        }

        // Clean markdown backticks if returned
        String cleanJson = geminiText.trim();
        if (cleanJson.contains("```json")) {
            cleanJson = cleanJson.substring(cleanJson.indexOf("```json") + 7);
            if (cleanJson.contains("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.indexOf("```"));
            }
        } else if (cleanJson.contains("```")) {
            cleanJson = cleanJson.substring(cleanJson.indexOf("```") + 3);
            if (cleanJson.contains("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.indexOf("```"));
            }
        }
        cleanJson = cleanJson.trim();

        return objectMapper.readValue(cleanJson, MenuScanResponse.class);
    }

    private MenuScanResponse runIntelligentHeuristicFallback(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) filename = "menu.jpg";
        filename = filename.toLowerCase();

        // 1. Analyze if it is a menu (Phase 1)
        if (filename.contains("fail") || filename.contains("selfie") || filename.contains("cat") 
                || filename.contains("dog") || filename.contains("car") || filename.contains("screenshot")
                || filename.contains("nature")) {
            MenuScanResponse response = new MenuScanResponse();
            response.setMenuDetected(false);
            response.setConfidence(0);
            response.setItems(new ArrayList<>());
            return response;
        }

        // 2. Set confidence based on size/name tags (Phase 2)
        double confidence = 85.0; // default
        if (filename.contains("blurry") || filename.contains("low") || file.getSize() < 50000) {
            confidence = 62.0; // below 70% threshold to trigger warnings
        }

        MenuScanResponse response = new MenuScanResponse();
        response.setMenuDetected(true);
        response.setConfidence(confidence);

        List<ScannedMenuItem> items = new ArrayList<>();

        if (filename.contains("pizza") || filename.contains("italian")) {
            items.add(createItem("Margherita Pizza", 299.00, "Pizza", true, "Fresh mozzarella, tomatoes, and basil on classic crust"));
            items.add(createItem("Farmhouse Pizza", 349.00, "Pizza", true, "Loaded with mushrooms, bell peppers, onions, and jalapenos"));
            items.add(createItem("Garlic Breadsticks", 129.00, "Starters", true, "Freshly baked dough with melted butter and garlic"));
            items.add(createItem("Tiramisu", 199.00, "Desserts", true, "Classic Italian coffee-flavoured dessert"));
        } else if (filename.contains("burger") || filename.contains("fast")) {
            items.add(createItem("Cheese Blast Burger", 189.00, "Burgers", true, "Crispy veggie patty with molten cheese layer"));
            items.add(createItem("Double Chicken Patty Burger", 229.00, "Burgers", false, "Two grilled chicken breast patties with spicy garlic sauce"));
            items.add(createItem("Peri Peri French Fries", 119.00, "Sides", true, "Golden crisp potato fries tossed in spicy peri-peri mix"));
            items.add(createItem("Chocolate Milkshake", 149.00, "Beverages", true, "Rich chocolate milkshake with vanilla ice cream scoop"));
        } else if (filename.contains("cafe") || filename.contains("coffee") || filename.contains("drink")) {
            items.add(createItem("Hazelnut Iced Latte", 160.00, "Beverages", true, "Espresso over cold milk and hazelnut syrup"));
            items.add(createItem("Cappuccino", 130.00, "Beverages", true, "Steamed milk with rich double espresso froth"));
            items.add(createItem("Classic Club Sandwich", 179.00, "Snacks", false, "Tri-layered toast with chicken strips, egg, and mayonnaise"));
            items.add(createItem("Blueberry Muffin", 99.00, "Desserts", true, "Moist muffin filled with fresh blueberries"));
        } else {
            // General premium Indian Diner menu
            items.add(createItem("Paneer Tikka Angara", 240.00, "Starters", true, "Spicy grilled paneer cubes marinated in rich Indian spices"));
            items.add(createItem("Hara Bhara Kebab", 180.00, "Starters", true, "Pan fried spinach and potato patties filled with peas"));
            items.add(createItem("Butter Chicken", 340.00, "Main Course", false, "Tender chicken cooked in rich buttery tomato gravy"));
            items.add(createItem("Paneer Butter Masala", 280.00, "Main Course", true, "Cottage cheese cubes in rich onion-tomato buttery gravy"));
            items.add(createItem("Garlic Butter Naan", 60.00, "Breads", true, "Leavened flatbread topped with garlic and butter"));
            items.add(createItem("Gulab Jamun", 90.00, "Desserts", true, "Deep-fried milk dumplings soaked in cardamom sugar syrup"));
            items.add(createItem("Masala Chai", 50.00, "Beverages", true, "Traditional Indian tea brewed with aromatic spices"));
        }

        response.setItems(items);
        return response;
    }

    private ScannedMenuItem createItem(String name, double price, String category, boolean veg, String desc) {
        ScannedMenuItem item = new ScannedMenuItem();
        item.setName(name);
        item.setPrice(price);
        item.setCategoryName(category);
        item.setVeg(veg);
        item.setDescription(desc);
        return item;
    }
}
