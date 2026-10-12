package com.example.backend.controller;

import com.example.backend.dto.ClaimRequest;
import com.example.backend.dto.ClaimStatusRequest;
import com.example.backend.entity.Claim;
import com.example.backend.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<Claim> submitClaim(
            @Valid @RequestBody ClaimRequest request,
            Authentication authentication
    ) {
        Claim claim = claimService.submitClaim(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(claim);
    }

    @GetMapping("/item/{itemId}")
    public List<Claim> getClaimsForItem(@PathVariable Long itemId) {
        return claimService.getClaimsForItem(itemId);
    }

    @GetMapping("/user/{userId}")
    public List<Claim> getClaimsForUser(@PathVariable Long userId) {
        return claimService.getClaimsForUser(userId);
    }

    @GetMapping("/{id}")
    public Claim getClaimById(@PathVariable Long id) {
        return claimService.getClaimById(id);
    }

    @PutMapping("/{id}/status")
    public Claim updateClaimStatus(
            @PathVariable Long id,
            @Valid @RequestBody ClaimStatusRequest request
    ) {
        return claimService.updateClaimStatus(id, request);
    }
}
