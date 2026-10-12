package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class ClaimRequest {

    @NotNull(message = "itemId is required.")
    @Positive(message = "itemId must be a positive number.")
    private Long itemId;

    @Positive(message = "lostItemId must be a positive number.")
    private Long lostItemId;

    @NotBlank(message = "verificationDetails are required.")
    @Size(max = 2000, message = "verificationDetails must be 2000 characters or fewer.")
    private String verificationDetails;

    public ClaimRequest() {
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getLostItemId() {
        return lostItemId;
    }

    public void setLostItemId(Long lostItemId) {
        this.lostItemId = lostItemId;
    }

    public String getVerificationDetails() {
        return verificationDetails;
    }

    public void setVerificationDetails(String verificationDetails) {
        this.verificationDetails = verificationDetails;
    }
}
