package crm.dopaflow_backend.Service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class PhotoUploadService {
    private static final String UPLOAD_DIR = "uploads/photos/";
    private static final String AVATAR_DIR = "uploads/avatars/";

    public PhotoUploadService() {
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            Files.createDirectories(Paths.get(AVATAR_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize upload directories", e);
        }
    }

    public String uploadProfilePhoto(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }

        String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(UPLOAD_DIR, filename);
        Files.write(filePath, file.getBytes());
        return "/photos/" + filename;
    }

    public String setDefaultAvatar(String avatarName) {
        return "/avatars/" + avatarName;
    }

    public void deletePhoto(String photoUrl) {
        if (photoUrl != null && !photoUrl.startsWith("/avatars/")) {
            try {
                Path filePath = Paths.get(UPLOAD_DIR + photoUrl.replace("/photos/", ""));
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                System.err.println("Failed to delete photo: " + e.getMessage());
            }
        }
    }
}