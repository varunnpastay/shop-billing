package com.smme.billing.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** An item the shop sells (saree, dress material, cloth...). Stock goes down with every bill and up with purchases. */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;
    private String code;
    private String category;
    private String sareeType;
    private String fabric;
    private String color;
    private String design;
    private String barcode;

    private String hsn;
    private String unit;

    private BigDecimal purchasePrice;
    /** Selling (retail) price – used on local bills. */
    private BigDecimal rate;
    /** Price for bulk / wholesale bills. */
    private BigDecimal wholesalePrice;
    private BigDecimal mrp;

    private BigDecimal stockQty;
    private BigDecimal minStock;
    private Integer piecesPerBundle;

    private Boolean hasImage;

    /** Stock is below (or at) the minimum the shop wants to keep. */
    public boolean isLowStock() {
        return minStock != null && minStock.signum() > 0 && stock().compareTo(minStock) <= 0;
    }

    public BigDecimal stock() {
        return stockQty == null ? BigDecimal.ZERO : stockQty;
    }

    /** Stock valued at purchase price (selling price when no purchase price is known). */
    public BigDecimal stockValue() {
        BigDecimal price = purchasePrice != null ? purchasePrice : (rate != null ? rate : BigDecimal.ZERO);
        return stock().signum() > 0 ? stock().multiply(price) : BigDecimal.ZERO;
    }

    /** "SVT-101 · Kanchipuram · Silk · Red · Peacock butta" – the item's details on one line. */
    public String getDetails() {
        return java.util.stream.Stream.of(code, sareeType, fabric, color, design)
                .filter(s -> s != null && !s.isBlank()).collect(java.util.stream.Collectors.joining(" · "));
    }

    /** "3 bundles + 4 pcs" when the item is packed in bundles. */
    public String getBundleText() {
        if (piecesPerBundle == null || piecesPerBundle <= 1 || stock().signum() <= 0) return "";
        long pcs = stock().longValue();
        long b = pcs / piecesPerBundle, rest = pcs % piecesPerBundle;
        return b + (b == 1 ? " bundle" : " bundles") + (rest > 0 ? " + " + rest + " pcs" : "");
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSareeType() { return sareeType; }
    public void setSareeType(String sareeType) { this.sareeType = sareeType; }
    public String getFabric() { return fabric; }
    public void setFabric(String fabric) { this.fabric = fabric; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getDesign() { return design; }
    public void setDesign(String design) { this.design = design; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public BigDecimal getWholesalePrice() { return wholesalePrice; }
    public void setWholesalePrice(BigDecimal wholesalePrice) { this.wholesalePrice = wholesalePrice; }
    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }
    public BigDecimal getStockQty() { return stockQty; }
    public void setStockQty(BigDecimal stockQty) { this.stockQty = stockQty; }
    public BigDecimal getMinStock() { return minStock; }
    public void setMinStock(BigDecimal minStock) { this.minStock = minStock; }
    public Integer getPiecesPerBundle() { return piecesPerBundle; }
    public void setPiecesPerBundle(Integer piecesPerBundle) { this.piecesPerBundle = piecesPerBundle; }
    public Boolean getHasImage() { return hasImage; }
    public void setHasImage(Boolean hasImage) { this.hasImage = hasImage; }
}
