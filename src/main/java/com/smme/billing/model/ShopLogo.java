package com.smme.billing.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** The shop's uploaded logo. Kept in its own table so it is not loaded on every request. */
@Entity
@Table(name = "shop_logo")
public class ShopLogo {

    public static final int MAX_BYTES = 1024 * 1024;

    @Id
    private Long id = 1L;

    @Column(length = MAX_BYTES)
    private byte[] data;

    private String contentType;

    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
