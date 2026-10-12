package com.example.backend.repository;

import com.example.backend.entity.Claim;
import com.example.backend.entity.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    boolean existsByItemIdAndUserIdAndStatusIn(Long itemId, Long userId, Collection<ClaimStatus> statuses);

    List<Claim> findByItemIdOrderByCreatedAtDesc(Long itemId);

    List<Claim> findByItemIdAndStatus(Long itemId, ClaimStatus status);

    List<Claim> findByUserIdOrderByCreatedAtDesc(Long userId);
}
