package com.smme.billing.repository;

import com.smme.billing.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByKindAndPartyIdOrderByPayDateDescIdDesc(String kind, Long partyId);
    List<Payment> findByKindAndPartyNameIgnoreCaseOrderByPayDateDescIdDesc(String kind, String partyName);
    List<Payment> findByKindOrderByPayDateDescIdDesc(String kind);
    List<Payment> findByKindAndPayDateBetweenOrderByPayDateAscIdAsc(String kind, LocalDate from, LocalDate to);
    List<Payment> findByBillId(Long billId);
}
