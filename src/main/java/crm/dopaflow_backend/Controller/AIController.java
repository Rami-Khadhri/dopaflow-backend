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
import java.util.stream.Collectors;

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

    private List<ChatHistory> getUserChatHistory(User user) {
        return entityManager.createQuery(
                        "SELECT ch FROM ChatHistory ch WHERE ch.user = :user ORDER BY ch.timestamp ASC",
                        ChatHistory.class)
                .setParameter("user", user)
                .getResultList();
    }

    private String generatePrompt(String message, String username, String requestType, List<ChatHistory> history) {
        StringBuilder prompt = new StringBuilder();
        boolean useHistory = false;

        // Check if the message implies continuing the context
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("based on our last chat") || lowerMessage.contains("continue") || lowerMessage.contains("follow up")) {
            useHistory = true;
        }

        // Include history only if explicitly referenced
        if (useHistory && !history.isEmpty()) {
            prompt.append("Chat history (for context only, use it if relevant):\n");
            List<ChatHistory> recentHistory = history.stream()
                    .skip(Math.max(0, history.size() - 5))
                    .collect(Collectors.toList());
            for (ChatHistory chat : recentHistory) {
                prompt.append(chat.getSender())
                        .append(": ")
                        .append(chat.getText())
                        .append("\n");
            }
            prompt.append("Respond to the following message using this context where applicable:\n");
        } else {
            prompt.append(" Respond to the user query in few lines but if prompt should have a rich answer feel free : \"")
                    .append(message);
        }

        if ("suggestion".equalsIgnoreCase(requestType)) {
            prompt.append("Provide 2-3 highly relevant, detailed search queries related to \"")
                    .append(message)
                    .append("\". Include a concise description for each (e.g., purpose or expected results), separated by \\n.");
        }

        return prompt.toString();
    }

    private void saveChatHistory(User user, String sender, String text) {
        ChatHistory chatMessage = new ChatHistory(user, sender, text);
        entityManager.persist(chatMessage);
    }

    @PostMapping("/chat")
    @Transactional
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> body, Authentication authentication) {
        logger.info("Received POST to /api/ai/chat with message: {}, requestType: {}", body.get("message"), body.get("requestType"));

        if (authentication == null || !authentication.isAuthenticated()) {
            logger.warn("Authentication failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Log in, bro!"));
        }

        String message = body.get("message");
        String requestType = body.getOrDefault("requestType", "chat");

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Say something, dude!"));
        }

        logger.debug("Raw message: '{}', length: {}", message, message.length());

        message = message.trim();
        int maxLength = Math.min(20000, message.length());
        if (maxLength > 0) {
            message = message.substring(0, maxLength);
        } else {
            logger.warn("Message is empty after trimming: '{}'", message);
            return ResponseEntity.badRequest().body(Map.of("error", "Message is empty after trimming!"));
        }

        String email = authentication.getName();
        User user = userService.findUserByEmail(email);

        if (user == null) {
            logger.error("User not found for email: {}", email);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Who are you, ghost?"));
        }

        List<ChatHistory> history = getUserChatHistory(user);
        saveChatHistory(user, "user", message);

        String prompt = generatePrompt(message, user.getUsername(), requestType, history);

        String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey;
        Map<String, Object> request = new HashMap<>();
        request.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        request.put("generationConfig", Map.of(
                "maxOutputTokens", 2048, // Increased for longer responses
                "temperature", 0.7,     // Balances creativity and precision
                "topP", 0.9            // Focus on high-quality token selection
        ));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);
            Map<String, Object> bodyResponse = response.getBody();

            if (bodyResponse == null || !bodyResponse.containsKey("candidates")) {
                logger.error("Gemini API bailed: {}", bodyResponse);
                throw new RuntimeException("API’s drunk!");
            }

            List<?> candidates = (List<?>) bodyResponse.get("candidates");
            if (candidates.isEmpty()) {
                logger.error("No candidates from Gemini: {}", bodyResponse);
                throw new RuntimeException("Nothing to say!");
            }

            Map<String, Object> candidate = (Map<String, Object>) candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) candidate.get("content");
            if (content == null || !content.containsKey("parts")) {
                logger.error("Gemini response is trash: {}", candidate);
                // Retry with a simpler prompt if initial response fails
                String fallbackPrompt = "You are Grok. Answer '" + message + "' with a clear, detailed response.";
                Map<String, Object> fallbackRequest = new HashMap<>();
                fallbackRequest.put("contents", List.of(Map.of("parts", List.of(Map.of("text", fallbackPrompt)))));
                fallbackRequest.put("generationConfig", Map.of("maxOutputTokens", 2048, "temperature", 0.7, "topP", 0.9));
                ResponseEntity<Map> fallbackResponse = restTemplate.postForEntity(apiUrl, new HttpEntity<>(fallbackRequest, headers), Map.class);
                Map<String, Object> fallbackBody = fallbackResponse.getBody();
                if (fallbackBody != null && fallbackBody.containsKey("candidates") && !((List<?>) fallbackBody.get("candidates")).isEmpty()) {
                    content = (Map<String, Object>) ((List<?>) fallbackBody.get("candidates")).get(0);
                } else {
                    throw new RuntimeException("Fallback failed too!");
                }
            }

            List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
            if (parts.isEmpty() || !parts.get(0).containsKey("text")) {
                logger.error("No text in Gemini parts: {}", parts);
                throw new RuntimeException("Where’s the meat?");
            }

            String aiResponse = parts.get(0).get("text").trim();
            int aiResponseMaxLength = Math.min(30000, aiResponse.length());
            if (aiResponseMaxLength > 0) {
                aiResponse = aiResponse.substring(0, aiResponseMaxLength);
            } else {
                aiResponse = "Hey bro, I couldn’t generate a solid answer—give me more to work with!";
            }

            saveChatHistory(user, "ai", aiResponse);
            entityManager.flush();

            logger.info("AI response (first 50 chars): {}", aiResponse.substring(0, Math.min(50, aiResponse.length())));
            return ResponseEntity.ok(Map.of("response", aiResponse));
        } catch (Exception e) {
            logger.error("Gemini API choked: {}", e.getMessage(), e);
            String errorResponse = "AI crashed, bro! Check your input or try again later.";
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

        if (authentication == null || !authentication.isAuthenticated()) {
            logger.warn("Authentication failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        String email = authentication.getName();
        User user = userService.findUserByEmail(email);

        if (user == null) {
            logger.error("User not found for email: {}", email);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        List<ChatHistory> history = getUserChatHistory(user);
        return ResponseEntity.ok(history.isEmpty() ? List.of() : history);
    }
}