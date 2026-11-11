package com.smme.billing.repository;

import com.smme.billing.model.Bill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long> {
    Optional<Bill> findByBillNo(Long billNo);
    boolean existsByBillNo(Long billNo);
    List<Bill> findTop10ByOrderByIdDesc();
    List<Bill> findByBillDateBetweenAndCustomerNameContainingIgnoreCaseOrderByBillNoDesc(LocalDate from, LocalDate to, String name);
    List<Bill> findByBillDateBetweenAndCancelledFalseOrderByBillNoAsc(LocalDate from, LocalDate to);
    List<Bill> findByPaidFalseAndCancelledFalseOrderByBillNoDesc();

    @org.springframework.data.jpa.repository.Query("select distinct b.byName from Bill b where b.byName is not null order by b.byName")
    List<String> findDistinctByNames();
}
