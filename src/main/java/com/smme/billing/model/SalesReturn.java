package com.smme.billing.model;

import jakarta.persistence.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Goods a customer brought back: stock goes back up and the refund is counted against sales. */
@Entity
@Table(name = "sales_return")
public class SalesReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate returnDate;
    /** Bill number the goods were sold on (wholesale number or local 0001/2026). */
    private String billRef;
    private String customerName;
    @Column(length = 300)
    private String productName;
    private BigDecimal qty;
    private BigDecimal refund;
    private String mode = "CASH";
    @Column(length = 300)
    private String reason;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
    public String getBillRef() { return billRef; }
    public void setBillRef(String billRef) { this.billRef = billRef; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public BigDecimal getRefund() { return refund; }
    public void setRefund(BigDecimal refund) { this.refund = refund; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
