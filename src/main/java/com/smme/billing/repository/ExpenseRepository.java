package com.smme.billing.repository;

import com.smme.billing.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(LocalDate from, LocalDate to);
}
