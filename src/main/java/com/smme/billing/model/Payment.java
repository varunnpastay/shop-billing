package com.smme.billing.model;

import jakarta.persistence.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Money received from a customer (kind CUSTOMER, partyName = customer name as written on the bills)
 * or paid to a supplier (kind SUPPLIER, partyId = supplier id).
 */
@Entity
@Table(name = "payment")
public class Payment {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String SUPPLIER = "SUPPLIER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String kind;
    private Long partyId;
    private String partyName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate payDate;
    private BigDecimal amount;
    /** CASH, UPI or BANK */
    private String mode = "CASH";
    @Column(length = 300)
    private String note;
    /** Set when the payment came from "Mark as Paid" on one wholesale bill. */
    private Long billId;
    private LocalDateTime createdAt;

    public Long getBillId() { return billId; }
    public void setBillId(Long billId) { this.billId = billId; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public String getPartyName() { return partyName; }
    public void setPartyName(String partyName) { this.partyName = partyName; }
    public LocalDate getPayDate() { return payDate; }
    public void setPayDate(LocalDate payDate) { this.payDate = payDate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
