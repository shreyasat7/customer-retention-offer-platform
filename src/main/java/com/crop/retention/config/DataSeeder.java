package com.crop.retention.config;

import com.crop.retention.model.*;
import com.crop.retention.repository.RetentionOfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds synthetic demonstration data the first time the application starts against an
 * empty database (constraint C5 — no real customer data).
 *
 * <p>The seeder writes statuses directly rather than driving {@code OfferWorkflow},
 * because it is building a history that already happened; it is not a user action.</p>
 */
@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    ApplicationRunner seedDemoData(RetentionOfferRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                log.info("Database already contains {} offers - skipping seed", repository.count());
                return;
            }

            record Row(String name, String email, String account, Segment segment, int risk,
                       String monthly, OfferType type, String value, OfferStatus status,
                       String notes, int ageDays) {}

            List<Row> rows = List.of(
                new Row("Aarav Mehta", "aarav.mehta@example.com", "ACC-10231", Segment.PREMIUM, 88,
                        "2499.00", OfferType.DISCOUNT_PERCENT, "20.00", OfferStatus.ACCEPTED,
                        "Called about a competitor quote. Accepted a 20% hold for 12 months.", 26),
                new Row("Diya Krishnan", "diya.k@example.com", "ACC-10244", Segment.PLUS, 74,
                        "999.00", OfferType.FREE_MONTHS, "2.00", OfferStatus.ACCEPTED,
                        "Billing dispute resolved; two free months offered as goodwill.", 24),
                new Row("Rohan Pillai", "rohan.pillai@example.com", "ACC-10310", Segment.ENTERPRISE, 91,
                        "18500.00", OfferType.DISCOUNT_PERCENT, "15.00", OfferStatus.PENDING_APPROVAL,
                        "Large account, renewal in 3 weeks. Needs manager sign-off on 15%.", 2),
                new Row("Ishita Rao", "ishita.rao@example.com", "ACC-10377", Segment.BASIC, 42,
                        "399.00", OfferType.LOYALTY_CREDIT, "500.00", OfferStatus.DECLINED,
                        "Customer already committed to another provider.", 19),
                new Row("Kabir Shah", "kabir.shah@example.com", "ACC-10402", Segment.PREMIUM, 67,
                        "2499.00", OfferType.PLAN_UPGRADE, "1.00", OfferStatus.SENT,
                        "Offered a free tier upgrade for 6 months. Awaiting response.", 5),
                new Row("Ananya Nair", "ananya.nair@example.com", "ACC-10418", Segment.PLUS, 55,
                        "999.00", OfferType.DISCOUNT_PERCENT, "10.00", OfferStatus.APPROVED,
                        "Approved; agent to send today.", 3),
                new Row("Vikram Desai", "vikram.desai@example.com", "ACC-10455", Segment.ENTERPRISE, 83,
                        "24000.00", OfferType.DISCOUNT_PERCENT, "25.00", OfferStatus.REJECTED,
                        "25% exceeds the enterprise discount ceiling.", 8),
                new Row("Meera Joshi", "meera.joshi@example.com", "ACC-10470", Segment.BASIC, 61,
                        "399.00", OfferType.FREE_MONTHS, "1.00", OfferStatus.ACCEPTED,
                        "Single free month was enough to retain.", 15),
                new Row("Arjun Reddy", "arjun.reddy@example.com", "ACC-10488", Segment.PREMIUM, 79,
                        "2499.00", OfferType.DISCOUNT_PERCENT, "18.00", OfferStatus.PENDING_APPROVAL,
                        "Repeated support complaints; wants a goodwill discount.", 1),
                new Row("Sanya Kapoor", "sanya.kapoor@example.com", "ACC-10501", Segment.PLUS, 38,
                        "999.00", OfferType.LOYALTY_CREDIT, "1200.00", OfferStatus.DRAFT,
                        "Low risk - drafting a light offer before the renewal call.", 0),
                new Row("Neel Bhatt", "neel.bhatt@example.com", "ACC-10522", Segment.ENTERPRISE, 95,
                        "31000.00", OfferType.DISCOUNT_PERCENT, "12.00", OfferStatus.SENT,
                        "Critical risk. Executive sponsor has left the customer organisation.", 6),
                new Row("Tara Menon", "tara.menon@example.com", "ACC-10540", Segment.BASIC, 48,
                        "399.00", OfferType.DISCOUNT_PERCENT, "15.00", OfferStatus.ACCEPTED,
                        "Price-sensitive student plan.", 12),
                new Row("Yash Agarwal", "yash.agarwal@example.com", "ACC-10566", Segment.PREMIUM, 72,
                        "2499.00", OfferType.FREE_MONTHS, "3.00", OfferStatus.PENDING_APPROVAL,
                        "Three free months requested after an outage.", 4),
                new Row("Priya Varma", "priya.varma@example.com", "ACC-10589", Segment.PLUS, 86,
                        "1499.00", OfferType.DISCOUNT_PERCENT, "22.00", OfferStatus.DRAFT,
                        "High risk; gathering usage data before submitting.", 0),
                new Row("Dev Malhotra", "dev.malhotra@example.com", "ACC-10604", Segment.ENTERPRISE, 58,
                        "15000.00", OfferType.PLAN_UPGRADE, "1.00", OfferStatus.ACCEPTED,
                        "Upgraded to the next tier at no cost for the remaining term.", 30),
                new Row("Riya Sen", "riya.sen@example.com", "ACC-10622", Segment.BASIC, 90,
                        "299.00", OfferType.DISCOUNT_PERCENT, "30.00", OfferStatus.REJECTED,
                        "30% on a basic plan is below the margin floor.", 10),
                new Row("Aditya Kulkarni", "aditya.k@example.com", "ACC-10641", Segment.PREMIUM, 64,
                        "2999.00", OfferType.LOYALTY_CREDIT, "3000.00", OfferStatus.SENT,
                        "Long-standing customer; loyalty credit applied to next invoice.", 7),
                new Row("Nisha Iyer", "nisha.iyer@example.com", "ACC-10663", Segment.PLUS, 51,
                        "1199.00", OfferType.FREE_MONTHS, "1.00", OfferStatus.DECLINED,
                        "Moving to a family plan with another provider.", 21),
                new Row("Omkar Patil", "omkar.patil@example.com", "ACC-10680", Segment.ENTERPRISE, 77,
                        "21000.00", OfferType.DISCOUNT_PERCENT, "14.00", OfferStatus.APPROVED,
                        "Approved at 14%, one point under the ceiling.", 2),
                new Row("Zoya Khan", "zoya.khan@example.com", "ACC-10699", Segment.BASIC, 35,
                        "349.00", OfferType.DISCOUNT_PERCENT, "8.00", OfferStatus.ACCEPTED,
                        "Small discount closed it immediately.", 17),
                new Row("Harsh Vaidya", "harsh.vaidya@example.com", "ACC-10712", Segment.PREMIUM, 81,
                        "2799.00", OfferType.DISCOUNT_PERCENT, "20.00", OfferStatus.PENDING_APPROVAL,
                        "Escalated from support. Needs a decision before Friday.", 3),
                new Row("Lavanya Gupta", "lavanya.g@example.com", "ACC-10730", Segment.PLUS, 69,
                        "1099.00", OfferType.PLAN_UPGRADE, "1.00", OfferStatus.DRAFT,
                        "Considering an upgrade instead of a discount.", 0),
                new Row("Siddharth Jain", "sid.jain@example.com", "ACC-10755", Segment.ENTERPRISE, 46,
                        "17500.00", OfferType.LOYALTY_CREDIT, "15000.00", OfferStatus.ACCEPTED,
                        "Annual loyalty credit as part of the renewal.", 28),
                new Row("Kavya Suresh", "kavya.suresh@example.com", "ACC-10771", Segment.BASIC, 73,
                        "449.00", OfferType.FREE_MONTHS, "2.00", OfferStatus.SENT,
                        "Two free months after a service interruption.", 4)
            );

            LocalDateTime now = LocalDateTime.now();
            for (Row r : rows) {
                RetentionOffer o = new RetentionOffer();
                o.setCaseRef(null);
                o.setCustomerName(r.name());
                o.setCustomerEmail(r.email());
                o.setAccountId(r.account());
                o.setSegment(r.segment());
                o.setChurnRisk(r.risk());
                o.setMonthlyValue(new BigDecimal(r.monthly()));
                o.setOfferType(r.type());
                o.setOfferValue(new BigDecimal(r.value()));
                o.setNotes(r.notes());
                o.setStatus(r.status());

                LocalDateTime created = now.minusDays(r.ageDays()).minusHours(3);
                o.setCreatedBy("agent");
                o.setCreatedAt(created);
                o.setUpdatedAt(created.plusHours(1));

                if (r.status() != OfferStatus.DRAFT) {
                    o.setSubmittedBy("agent");
                    o.setSubmittedAt(created.plusHours(1));
                }
                if (r.status() == OfferStatus.APPROVED || r.status() == OfferStatus.REJECTED
                        || r.status() == OfferStatus.SENT || r.status() == OfferStatus.ACCEPTED
                        || r.status() == OfferStatus.DECLINED) {
                    o.setDecisionBy("manager");
                    o.setDecisionAt(created.plusHours(6));
                    o.setDecisionComment(r.status() == OfferStatus.REJECTED
                            ? "Outside the approved discount ceiling for this segment."
                            : "Within policy for this segment and risk level.");
                }
                if (r.status() == OfferStatus.SENT || r.status() == OfferStatus.ACCEPTED
                        || r.status() == OfferStatus.DECLINED) {
                    o.setSentBy("agent");
                    o.setSentAt(created.plusHours(8));
                }
                if (r.status() == OfferStatus.ACCEPTED || r.status() == OfferStatus.DECLINED) {
                    o.setOutcomeAt(created.plusDays(2));
                    o.setUpdatedAt(created.plusDays(2));
                }
                repository.save(o);
            }

            // assign case references in creation order
            int n = 0;
            for (RetentionOffer o : repository.findAll()) {
                o.setCaseRef(String.format("CRO-%04d", ++n));
                repository.save(o);
            }
            log.info("Seeded {} demonstration offers", rows.size());
        };
    }
}
