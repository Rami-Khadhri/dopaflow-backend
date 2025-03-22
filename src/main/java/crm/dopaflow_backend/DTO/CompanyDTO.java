package crm.dopaflow_backend.DTO;

import lombok.Data;

@Data
public class CompanyDTO {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private String status;
    private String address;
    private String website;
    private String industry;
    private String notes;
    private Long ownerId;
    private String ownerUsername;
    private String photoUrl;

    public CompanyDTO(Long id, String name, String email, String phone, String status, String address, String website, String industry, String notes, Long ownerId, String ownerUsername, String photoUrl) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.status = status;
        this.address = address;
        this.website = website;
        this.industry = industry;
        this.notes = notes;
        this.ownerId = ownerId;
        this.ownerUsername = ownerUsername;
        this.photoUrl = photoUrl;
    }


}