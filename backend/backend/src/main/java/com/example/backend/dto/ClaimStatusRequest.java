package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class ClaimStatusRequest {

    @NotBlank(message = "status is required.")
    private String status;

    public ClaimStatusRequest() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
