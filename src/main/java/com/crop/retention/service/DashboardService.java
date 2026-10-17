package com.crop.retention.service;

import com.crop.retention.model.OfferStatus;
import com.crop.retention.model.RetentionOffer;
import com.crop.retention.repository.RetentionOfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Builds the figures shown on the summary dashboard (backlog item M9).
 *
 * <p>Everything is derived from the offer table on each request. With a demo-sized
 * dataset that is far simpler — and easier to prove correct — than maintaining
 * incrementally updated counters.</p>
 */
@Service
public class DashboardService {

    private final RetentionOfferRepository repository;

    public DashboardService(RetentionOfferRepository repository) {
        this.repository = repository;
    }

    /** Everything the dashboard template needs, assembled in one pass over the data. */
    public record Summary(
            long totalOffers,
            long awaitingApproval,
            BigDecimal acceptanceRate,
            BigDecimal retainedMonthlyValue,
            BigDecimal valueAtRisk,
            long decidedOffers,
            Map<OfferStatus, Long> byStatus,
            long maxStatusCount,
            List<RiskBand> riskBands,
            List<Waiting> oldestWaiting
    ) {}

    public record RiskBand(String name, String cssClass, long count, int percent) {}

    public record Waiting(Long id, String caseRef, String customerName, String segment,
                          int churnRisk, String riskCssClass, long daysWaiting) {}

    @Transactional(readOnly = true)
    public Summary summary() {
        List<RetentionOffer> all = repository.findAll();
        long total = all.size();

        Map<OfferStatus, Long> byStatus = new EnumMap<>(OfferStatus.class);
        for (OfferStatus s : OfferStatus.values()) {
            byStatus.put(s, 0L);
        }

        BigDecimal retained = BigDecimal.ZERO;
        BigDecimal atRisk = BigDecimal.ZERO;
        long low = 0, medium = 0, high = 0, critical = 0;

        for (RetentionOffer o : all) {
            byStatus.merge(o.getStatus(), 1L, Long::sum);

            if (o.getStatus() == OfferStatus.ACCEPTED) {
                retained = retained.add(o.getRetainedMonthlyValue());
            }
            // anything still moving through the pipeline is revenue not yet secured
            if (!o.getStatus().isTerminal()) {
                atRisk = atRisk.add(o.getMonthlyValue() == null ? BigDecimal.ZERO : o.getMonthlyValue());
            }

            switch (o.getRiskBand()) {
                case "Critical" -> critical++;
                case "High" -> high++;
                case "Medium" -> medium++;
                default -> low++;
            }
        }

        long accepted = byStatus.get(OfferStatus.ACCEPTED);
        long declined = byStatus.get(OfferStatus.DECLINED);
        long decided = accepted + declined;
        BigDecimal acceptanceRate = decided == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(accepted * 100.0 / decided).setScale(1, RoundingMode.HALF_UP);

        long maxStatusCount = byStatus.values().stream().mapToLong(Long::longValue).max().orElse(1L);
        if (maxStatusCount == 0) {
            maxStatusCount = 1L;
        }

        List<RiskBand> bands = List.of(
                new RiskBand("Critical", "critical", critical, percent(critical, total)),
                new RiskBand("High", "high", high, percent(high, total)),
                new RiskBand("Medium", "medium", medium, percent(medium, total)),
                new RiskBand("Low", "low", low, percent(low, total))
        );

        LocalDateTime now = LocalDateTime.now();
        List<Waiting> waiting = new ArrayList<>();
        for (RetentionOffer o : repository.findByStatusOrderBySubmittedAtAsc(OfferStatus.PENDING_APPROVAL)) {
            long days = o.getSubmittedAt() == null ? 0
                    : Math.max(0, Duration.between(o.getSubmittedAt(), now).toDays());
            waiting.add(new Waiting(o.getId(), o.getCaseRef(), o.getCustomerName(),
                    o.getSegment().getLabel(), o.getChurnRisk(), o.getRiskCssClass(), days));
            if (waiting.size() == 5) {
                break;
            }
        }

        return new Summary(total, byStatus.get(OfferStatus.PENDING_APPROVAL), acceptanceRate,
                retained.setScale(0, RoundingMode.HALF_UP), atRisk.setScale(0, RoundingMode.HALF_UP),
                decided, byStatus, maxStatusCount, bands, waiting);
    }

    private static int percent(long part, long total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }
}
