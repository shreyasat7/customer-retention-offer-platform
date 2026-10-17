package com.crop.retention.repository;

import com.crop.retention.model.OfferStatus;
import com.crop.retention.model.RetentionOffer;
import com.crop.retention.model.Segment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RetentionOfferRepository extends JpaRepository<RetentionOffer, Long> {

    /**
     * Combined search used by the offers screen. Every criterion is optional: a null
     * parameter disables that part of the filter, so the four controls on the search
     * bar can be mixed freely and always apply together (AND).
     */
    @Query("""
            SELECT o FROM RetentionOffer o
            WHERE (:q IS NULL
                   OR LOWER(o.customerName)  LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(o.customerEmail) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(o.caseRef)       LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:status  IS NULL OR o.status  = :status)
              AND (:segment IS NULL OR o.segment = :segment)
              AND (:minRisk IS NULL OR o.churnRisk >= :minRisk)
            """)
    Page<RetentionOffer> search(@Param("q") String q,
                                @Param("status") OfferStatus status,
                                @Param("segment") Segment segment,
                                @Param("minRisk") Integer minRisk,
                                Pageable pageable);

    long countByStatus(OfferStatus status);

    List<RetentionOffer> findByStatusOrderBySubmittedAtAsc(OfferStatus status);

    @Query("SELECT o.status, COUNT(o) FROM RetentionOffer o GROUP BY o.status")
    List<Object[]> countGroupedByStatus();

    boolean existsByCaseRef(String caseRef);
}
