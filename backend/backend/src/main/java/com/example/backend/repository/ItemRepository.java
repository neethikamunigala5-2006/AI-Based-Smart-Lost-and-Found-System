package com.example.backend.repository;

import com.example.backend.entity.Item;
import com.example.backend.entity.ItemStatus;
import com.example.backend.entity.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findByType(ItemType type);

    List<Item> findByCategoryContainingIgnoreCase(String category);

    List<Item> findByLocationContainingIgnoreCase(String location);

    List<Item> findByStatus(ItemStatus status);

    @Query("""
            SELECT item FROM Item item
            WHERE (:type IS NULL OR item.type = :type)
              AND (:status IS NULL OR item.status = :status)
              AND (:category IS NULL OR LOWER(item.category) LIKE LOWER(CONCAT('%', :category, '%')))
              AND (:location IS NULL OR LOWER(item.location) LIKE LOWER(CONCAT('%', :location, '%')))
            ORDER BY item.id DESC
            """)
    List<Item> searchItems(
            @Param("type") ItemType type,
            @Param("status") ItemStatus status,
            @Param("category") String category,
            @Param("location") String location
    );
}
