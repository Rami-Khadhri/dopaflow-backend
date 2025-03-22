package crm.dopaflow_backend.Service;

import java.util.ArrayList;
import java.util.List;

public class ImportResult<T> {
    private List<T> savedEntities;
    private int created;
    private int updated;
    private int skipped;

    public ImportResult() {
        this.savedEntities = new ArrayList<>();
        this.created = 0;
        this.updated = 0;
        this.skipped = 0;
    }

    // Getters and setters
    public List<T> getSavedEntities() { return savedEntities; }
    public void setSavedEntities(List<T> savedEntities) { this.savedEntities = savedEntities; }
    public int getCreated() { return created; }
    public void setCreated(int created) { this.created = created; }
    public int getUpdated() { return updated; }
    public void setUpdated(int updated) { this.updated = updated; }
    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }
}