package com.smme.billing.repository;

import com.smme.billing.model.SalesReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {
    List<SalesReturn> findByReturnDateBetweenOrderByReturnDateDescIdDesc(LocalDate from, LocalDate to);
}
