package com.smme.billing.model;

import jakarta.persistence.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Shop running cost: rent, electricity, transport, salary, other. */
@Entity
@Table(name = "expense")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expenseDate;
    private String category;
    private BigDecimal amount;
    private String mode = "CASH";
    @Column(length = 300)
    private String note;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
