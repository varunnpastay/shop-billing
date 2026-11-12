package com.smme.billing.model;

import jakarta.persistence.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bill")
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long billNo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate billDate;

    private String customerName;
    @Column(length = 500)
    private String customerAddress;
    private String customerGstin;
    private String customerPhone;

    private String billType = "CASH";        // CASH or CREDIT
    private String taxType = "CGST_SGST";    // CGST_SGST, IGST, NONE
    private BigDecimal gstPercent = new BigDecimal("18");

    private BigDecimal subTotal = BigDecimal.ZERO;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal taxableAmount = BigDecimal.ZERO;
    private BigDecimal cgst = BigDecimal.ZERO;
    private BigDecimal sgst = BigDecimal.ZERO;
    private BigDecimal igst = BigDecimal.ZERO;
    private BigDecimal roundOff = BigDecimal.ZERO;
    private BigDecimal grandTotal = BigDecimal.ZERO;

    private boolean paid = true;
    private LocalDate paidDate;
    private boolean cancelled = false;

    @Column(length = 1000)
    private String notes;

    private LocalDateTime createdAt;

    /** "By:" on the bill – who sold / booked the order (salesman or agent). */
    private String byName;

    /** How the money came in: CASH, UPI or BANK. */
    private String paymentMode = "CASH";
    /** Part-payments received against a credit bill (from customer receipts). */
    private BigDecimal amountReceived;

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
    public BigDecimal getAmountReceived() { return amountReceived; }
    public void setAmountReceived(BigDecimal amountReceived) { this.amountReceived = amountReceived; }

    /** Amount still to be collected on this bill. */
    public BigDecimal getBalance() {
        if (cancelled || paid || grandTotal == null) return BigDecimal.ZERO;
        BigDecimal b = grandTotal.subtract(amountReceived == null ? BigDecimal.ZERO : amountReceived);
        return b.signum() > 0 ? b : BigDecimal.ZERO;
    }

    public String getByName() { return byName; }
    public void setByName(String byName) { this.byName = byName; }

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<BillItem> items = new ArrayList<>();

    public String getHalfGstLabel() {
        return gstPercent == null ? "0" : gstPercent.divide(new BigDecimal("2")).stripTrailingZeros().toPlainString();
    }

    public String getGstLabel() {
        return gstPercent == null ? "0" : gstPercent.stripTrailingZeros().toPlainString();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBillNo() { return billNo; }
    public void setBillNo(Long billNo) { this.billNo = billNo; }
    public LocalDate getBillDate() { return billDate; }
    public void setBillDate(LocalDate billDate) { this.billDate = billDate; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerAddress() { return customerAddress; }
    public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }
    public String getCustomerGstin() { return customerGstin; }
    public void setCustomerGstin(String customerGstin) { this.customerGstin = customerGstin; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getBillType() { return billType; }
    public void setBillType(String billType) { this.billType = billType; }
    public String getTaxType() { return taxType; }
    public void setTaxType(String taxType) { this.taxType = taxType; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }
    public BigDecimal getSubTotal() { return subTotal; }
    public void setSubTotal(BigDecimal subTotal) { this.subTotal = subTotal; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; }
    public BigDecimal getCgst() { return cgst; }
    public void setCgst(BigDecimal cgst) { this.cgst = cgst; }
    public BigDecimal getSgst() { return sgst; }
    public void setSgst(BigDecimal sgst) { this.sgst = sgst; }
    public BigDecimal getIgst() { return igst; }
    public void setIgst(BigDecimal igst) { this.igst = igst; }
    public BigDecimal getRoundOff() { return roundOff; }
    public void setRoundOff(BigDecimal roundOff) { this.roundOff = roundOff; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
    public LocalDate getPaidDate() { return paidDate; }
    public void setPaidDate(LocalDate paidDate) { this.paidDate = paidDate; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<BillItem> getItems() { return items; }
    public void setItems(List<BillItem> items) { this.items = items; }
}
