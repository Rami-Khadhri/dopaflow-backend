package crm.dopaflow_backend.Service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentUploadService {
    private static final String UPLOAD_DIR = "uploads/attachments/";

    public AttachmentUploadService() {
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize attachment upload directory", e);
        }
    }
    public List<String> uploadAttachments(List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) return new ArrayList<>();
        List<String> filePaths = new ArrayList<>();
        for (MultipartFile file : files) {
            if (!file.isEmpty() && isValidFileType(file)) {
                String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
                Path filePath = Paths.get(UPLOAD_DIR, filename);
                Files.write(filePath, file.getBytes());
                filePaths.add("/attachments/" + filename);
            }
        }
        return filePaths;
    }

    private boolean isValidFileType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && (
                contentType.startsWith("image/") ||
                        contentType.equals("application/pdf") ||
                        contentType.equals("application/msword") ||
                        contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                        contentType.equals("application/vnd.ms-excel") ||
                        contentType.equals("text/csv")
        );
    }
    public void deleteAttachment(String attachmentUrl) {
        if (attachmentUrl != null) {
            try {
                Path filePath = Paths.get(UPLOAD_DIR + attachmentUrl.replace("/attachments/", ""));
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                System.err.println("Failed to delete attachment: " + e.getMessage());
            }
        }
    }
}