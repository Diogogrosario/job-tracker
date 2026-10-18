package com.jobtracker.email.review;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long> {
    List<ReviewItem> findAllByStatusOrderByCreatedAtAsc(ReviewStatus status);

    long countByStatus(ReviewStatus status);

    List<ReviewItem> findAllByActionAndStatus(ReviewAction action, ReviewStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from ReviewItem item where item.id = :id")
    Optional<ReviewItem> findByIdForUpdate(@Param("id") Long id);
}
