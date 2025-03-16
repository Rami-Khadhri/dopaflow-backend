package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.Model.ChatHistory;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private static final Logger logger = LoggerFactory.getLogger(AIController.class);
    private final UserService userService;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final RestTemplate restTemplate;
    private final EntityManager entityManager;

    public AIController(UserService userService, RestTemplate restTemplate, EntityManager entityManager) {
        this.userService = userService;
        this.restTemplate = restTemplate;
        this.entityManager = entityManager;
    }

    // Helper method to generate a prompt based on the request type
    private String generatePrompt(String message, String username, String requestType) {
        if ("suggestion".equalsIgnoreCase(requestType)) {
            return "As a CRM assistant, suggest two concise and distinct search queries related to \"" + message + "\". " +
                    "Avoid repeating the query verbatim, keep your tone friendly and conversational, and separate suggestions with a newline (\\n).";
        } else { // Default to chat prompt
            return "You are a knowledgeable CRM assistant named DopaBot. Respond to the message: \"" + message + "\" " +
                    "with a clear, precise, and conversational reply. If you need more context, ask clarifying questions naturally " +
                    "without repeating the query.";
        }
    }

    // Save a chat message (both user and AI)
    private void saveChatHistory(User user, String sender, String text) {
        ChatHistory chatMessage = new ChatHistory(user, sender, text);
        entityManager.persist(chatMessage);
    }

    @PostMapping("/chat")
    @Transactional
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> body, Authentication authentication) {
        logger.info("Received POST to /api/ai/chat with message: {}, requestType: {}", body.get("message"), body.get("requestType"));

        // Check if authentication is valid
        if (authentication == null || !authentication.isAuthenticated()) {
            logger.warn("Authentication failed: No valid authentication provided");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Please log in to continue, bro!"));
        }

        String message = body.get("message");
        String requestType = body.getOrDefault("requestType", "chat");

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Yo, give me something to work with!"));
        }

        message = message.trim().substring(0, Math.min(20000, message.length()));

        // Get email from authentication
        String email = authentication.getName();
        if (email == null) {
            logger.warn("No email found in authentication, cannot proceed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid authentication—please log in again, bro!"));
        }

        // Fetch user using email
        User foundUser = userService.findUserByEmail(email);
        if (foundUser == null) {
            logger.error("User not found for email: {}", email);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found, bro! Please ensure you're registered."));
        }

        // Fetch user from DB using userId
        User user = entityManager.createQuery("SELECT u FROM User u WHERE u.id = :userId", User.class)
                .setParameter("userId", foundUser.getId())
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);

        if (user == null) {
            logger.error("User not found for userId: {}", foundUser.getId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found, bro! Please ensure you're registered."));
        }

        // Save the user’s original message
        saveChatHistory(user, "user", message);

        // Generate prompt based on request type
        String prompt = generatePrompt(message, foundUser.getUsername(), requestType);

        // Prepare Gemini API request
        String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey;
        Map<String, Object> request = new HashMap<>();
        request.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);
            Map<String, Object> bodyResponse = response.getBody();

            if (bodyResponse == null || !bodyResponse.containsKey("candidates")) {
                logger.error("Gemini API returned null or no candidates: {}", bodyResponse);
                throw new RuntimeException("API didn’t provide a usable response, bro!");
            }

            List<?> candidates = (List<?>) bodyResponse.get("candidates");
            if (candidates.isEmpty()) {
                logger.error("No candidates found in Gemini API response: {}", bodyResponse);
                throw new RuntimeException("No response candidates found!");
            }

            Map<String, Object> candidate = (Map<String, Object>) candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) candidate.get("content");
            if (content == null || !content.containsKey("parts")) {
                logger.error("Invalid content structure in Gemini response: {}", candidate);
                throw new RuntimeException("Response content structure is not as expected!");
            }

            List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
            if (parts.isEmpty() || !parts.get(0).containsKey("text")) {
                logger.error("No text found in Gemini response parts: {}", parts);
                throw new RuntimeException("No text available in the response!");
            }

            String aiResponse = parts.get(0).get("text").trim();
            aiResponse = aiResponse.substring(0, Math.min(20000, aiResponse.length()));

            // Ensure the response is precise and valid
            if (aiResponse.equals("undefined") || aiResponse.isEmpty() || aiResponse.contains("undefined")) {
                aiResponse = "Hmm, I didn’t catch that—could you please provide more details, " + foundUser.getUsername() + "?";
            } else if (aiResponse.length() < 3) {
                aiResponse = "Hey " + foundUser.getUsername() + ", I need a bit more context—what’s on your mind?";
            }

            // Save the AI response
            saveChatHistory(user, "ai", aiResponse);
            entityManager.flush();

            logger.info("AI response (first 50 chars): {}", aiResponse.substring(0, Math.min(50, aiResponse.length())));
            return ResponseEntity.ok(Map.of("response", aiResponse));
        } catch (Exception e) {
            logger.error("Error calling Gemini API: {}", e.getMessage(), e);
            String errorResponse = "Yo " + foundUser.getUsername() + ", something went wrong—let’s try that again!";
            saveChatHistory(user, "ai", errorResponse);
            entityManager.flush();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("response", errorResponse));
        }
    }

    @GetMapping("/chat")
    @Transactional
    public ResponseEntity<List<ChatHistory>> getChatHistory(Authentication authentication) {
        logger.info("Received GET to /api/ai/chat");

        // Check if authentication is valid
        if (authentication == null || !authentication.isAuthenticated()) {
            logger.warn("Authentication failed: No valid authentication provided");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(null);
        }

        // Get email from authentication
        String email = authentication.getName();
        if (email == null) {
            logger.warn("No email found in authentication, cannot proceed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(null);
        }

        // Fetch user using email
        User foundUser = userService.findUserByEmail(email);
        if (foundUser == null) {
            logger.error("User not found for email: {}", email);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        }

        logger.debug("Fetching chat history for username: {}", foundUser.getUsername());

        // Fetch user from DB using userId
        User user = entityManager.createQuery("SELECT u FROM User u WHERE u.id = :userId", User.class)
                .setParameter("userId", foundUser.getId())
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);

        if (user == null) {
            logger.error("User not found for userId: {}", foundUser.getId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        }

        List<ChatHistory> history = entityManager.createQuery(
                        "SELECT ch FROM ChatHistory ch WHERE ch.user = :user ORDER BY ch.timestamp ASC",
                        ChatHistory.class)
                .setParameter("user", user)
                .getResultList();

        if (history == null || history.isEmpty()) {
            logger.warn("No chat history found for user: {}", foundUser.getUsername());
            return ResponseEntity.ok(List.of());
        }

        return ResponseEntity.ok(history);
    }
}