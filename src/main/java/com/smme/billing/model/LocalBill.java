package com.smme.billing.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A local (retail counter) bill: small receipt with MRP, rate, savings and UPI QR. Numbered 0001/2026 per year. */
@Entity
@Table(name = "local_bill")
public class LocalBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 20)
    private String billNo;

    private Integer billYear;
    private Integer seqNo;

    private LocalDate billDate;

    private String customerName;
    private String custMobile;
    /** CASH, UPI or BANK */
    private String paymentMode = "CASH";

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    private BigDecimal totalQty = BigDecimal.ZERO;
    private BigDecimal totalSale = BigDecimal.ZERO;
    private BigDecimal totalSavings = BigDecimal.ZERO;
    private BigDecimal netPayable = BigDecimal.ZERO;

    private boolean cancelled = false;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "localBill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<LocalBillItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBillNo() { return billNo; }
    public void setBillNo(String billNo) { this.billNo = billNo; }
    public Integer getBillYear() { return billYear; }
    public void setBillYear(Integer billYear) { this.billYear = billYear; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public LocalDate getBillDate() { return billDate; }
    public void setBillDate(LocalDate billDate) { this.billDate = billDate; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustMobile() { return custMobile; }
    public void setCustMobile(String custMobile) { this.custMobile = custMobile; }
    public BigDecimal getTotalQty() { return totalQty; }
    public void setTotalQty(BigDecimal totalQty) { this.totalQty = totalQty; }
    public BigDecimal getTotalSale() { return totalSale; }
    public void setTotalSale(BigDecimal totalSale) { this.totalSale = totalSale; }
    public BigDecimal getTotalSavings() { return totalSavings; }
    public void setTotalSavings(BigDecimal totalSavings) { this.totalSavings = totalSavings; }
    public BigDecimal getNetPayable() { return netPayable; }
    public void setNetPayable(BigDecimal netPayable) { this.netPayable = netPayable; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<LocalBillItem> getItems() { return items; }
    public void setItems(List<LocalBillItem> items) { this.items = items; }
}
