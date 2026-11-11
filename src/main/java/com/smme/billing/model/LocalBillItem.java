package com.smme.billing.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "local_bill_item")
public class LocalBillItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String name;

    private BigDecimal qty;
    private BigDecimal mrp;
    private BigDecimal rate;
    private BigDecimal amount;

    @ManyToOne
    @JoinColumn(name = "local_bill_id")
    private LocalBill localBill;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalBill getLocalBill() { return localBill; }
    public void setLocalBill(LocalBill localBill) { this.localBill = localBill; }
}
