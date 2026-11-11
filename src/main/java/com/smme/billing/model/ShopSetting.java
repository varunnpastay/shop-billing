package com.smme.billing.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "shop_setting")
public class ShopSetting {

    @Id
    private Long id = 1L;

    private String shopName;
    private String address;
    private String gstin;
    private String mobile;
    private String email;
    private String stateName;
    private String stateCode;
    private Long nextBillNo;
    private BigDecimal defaultGst;

    @Column(length = 1000)
    private String bankDetails;

    @Column(length = 1000)
    private String terms;

    /** Print the logo on bills (null on databases created before this column existed = yes). */
    private Boolean showLogo = Boolean.TRUE;

    // ---------- Local (retail) receipt ----------
    private String receiptTitle;
    private String receiptSubtitle;
    @Column(length = 500)
    private String receiptDeclaration;
    private String receiptFooter;
    private String upiId;
    private String instagram;
    /** Next local bill number for localBillYear (a new year starts again at 1). */
    private Integer nextLocalBillNo;
    private Integer localBillYear;
    /** Set once the receipt defaults have been filled in, so cleared fields stay cleared. */
    private Boolean receiptConfigured;

    // ---------- Wholesale (tax invoice) header ----------
    /** Shop name in Kannada (or any local language), printed above the English name. */
    private String shopNameLocal;
    /** Small line at the top centre, e.g. "|| SHRI GAJANANA PRASANNA ||". */
    private String invoiceBlessing;
    /** Address lines printed under the shop name on the wholesale bill. */
    @Column(length = 500)
    private String invoiceAddress;
    private String mobile2;
    private String jurisdiction;
    /** Set once the invoice defaults have been filled in, so cleared fields stay cleared. */
    private Boolean invoiceConfigured;

    public String getShopNameLocal() { return shopNameLocal; }
    public void setShopNameLocal(String shopNameLocal) { this.shopNameLocal = shopNameLocal; }
    public String getInvoiceBlessing() { return invoiceBlessing; }
    public void setInvoiceBlessing(String invoiceBlessing) { this.invoiceBlessing = invoiceBlessing; }
    public String getInvoiceAddress() { return invoiceAddress; }
    public void setInvoiceAddress(String invoiceAddress) { this.invoiceAddress = invoiceAddress; }
    public String getMobile2() { return mobile2; }
    public void setMobile2(String mobile2) { this.mobile2 = mobile2; }
    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }
    public Boolean getInvoiceConfigured() { return invoiceConfigured; }
    public void setInvoiceConfigured(Boolean invoiceConfigured) { this.invoiceConfigured = invoiceConfigured; }

    private String username;
    private String passwordHash;

    /** Up to two initials of the shop name, used as a logo when none is uploaded. */
    public String getInitials() {
        if (shopName == null || shopName.isBlank()) return "S";
        StringBuilder sb = new StringBuilder();
        for (String w : shopName.trim().split("[\\s.&,-]+")) {
            if (!w.isEmpty() && Character.isLetterOrDigit(w.charAt(0))) sb.append(Character.toUpperCase(w.charAt(0)));
            if (sb.length() == 2) break;
        }
        return sb.isEmpty() ? "S" : sb.toString();
    }

    public boolean isLogoOnBill() { return showLogo == null || showLogo; }
    public Boolean getShowLogo() { return showLogo == null ? Boolean.TRUE : showLogo; }
    public void setShowLogo(Boolean showLogo) { this.showLogo = showLogo; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getStateName() { return stateName; }
    public void setStateName(String stateName) { this.stateName = stateName; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public Long getNextBillNo() { return nextBillNo; }
    public void setNextBillNo(Long nextBillNo) { this.nextBillNo = nextBillNo; }
    public BigDecimal getDefaultGst() { return defaultGst; }
    public void setDefaultGst(BigDecimal defaultGst) { this.defaultGst = defaultGst; }
    public String getBankDetails() { return bankDetails; }
    public void setBankDetails(String bankDetails) { this.bankDetails = bankDetails; }
    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }
    public String getReceiptTitle() { return receiptTitle; }
    public void setReceiptTitle(String receiptTitle) { this.receiptTitle = receiptTitle; }
    public String getReceiptSubtitle() { return receiptSubtitle; }
    public void setReceiptSubtitle(String receiptSubtitle) { this.receiptSubtitle = receiptSubtitle; }
    public String getReceiptDeclaration() { return receiptDeclaration; }
    public void setReceiptDeclaration(String receiptDeclaration) { this.receiptDeclaration = receiptDeclaration; }
    public String getReceiptFooter() { return receiptFooter; }
    public void setReceiptFooter(String receiptFooter) { this.receiptFooter = receiptFooter; }
    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }
    public String getInstagram() { return instagram; }
    public void setInstagram(String instagram) { this.instagram = instagram; }
    public Integer getNextLocalBillNo() { return nextLocalBillNo; }
    public void setNextLocalBillNo(Integer nextLocalBillNo) { this.nextLocalBillNo = nextLocalBillNo; }
    public Integer getLocalBillYear() { return localBillYear; }
    public void setLocalBillYear(Integer localBillYear) { this.localBillYear = localBillYear; }
    public Boolean getReceiptConfigured() { return receiptConfigured; }
    public void setReceiptConfigured(Boolean receiptConfigured) { this.receiptConfigured = receiptConfigured; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
