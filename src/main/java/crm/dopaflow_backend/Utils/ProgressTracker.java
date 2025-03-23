package crm.dopaflow_backend.Utils;

import lombok.Getter;

// Helper class for tracking progress
@Getter
public class ProgressTracker {
    private double progress = 0.0;

    public void updateProgress(double percentage) {
        this.progress = Math.min(100.0, Math.max(0.0, percentage));
    }

}