package com.smme.billing.repository;

import com.smme.billing.model.LocalBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LocalBillRepository extends JpaRepository<LocalBill, Long> {

    Optional<LocalBill> findByBillNo(String billNo);

    @Query("select max(b.seqNo) from LocalBill b where b.billYear = :year")
    Integer maxSeqForYear(@Param("year") Integer year);

    List<LocalBill> findTop200ByOrderByIdDesc();

    List<LocalBill> findTop10ByOrderByIdDesc();

    List<LocalBill> findByBillDateBetweenAndCancelledFalseOrderByIdAsc(LocalDate from, LocalDate to);

    List<LocalBill> findByBillDateBetweenOrderByIdDesc(LocalDate from, LocalDate to);
}
