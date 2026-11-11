package com.smme.billing.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "bill_item")
public class BillItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String particulars;

    /** Second line under the item name on the bill, e.g. "100 color + 10 black". */
    @Column(length = 300)
    private String details;

    private String hsn;
    private BigDecimal qty;
    private String unit;
    private BigDecimal rate;
    private BigDecimal amount;

    @ManyToOne
    @JoinColumn(name = "bill_id")
    private Bill bill;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getParticulars() { return particulars; }
    public void setParticulars(String particulars) { this.particulars = particulars; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Bill getBill() { return bill; }
    public void setBill(Bill bill) { this.bill = bill; }
}
