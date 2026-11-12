package com.smme.billing.model;

import jakarta.persistence.*;

/** Photo of an item; its id is the product id. Kept apart so item lists do not load the pictures. */
@Entity
@Table(name = "product_image")
public class ProductImage {

    @Id
    private Long id;

    @Column(length = ShopLogo.MAX_BYTES)
    private byte[] data;

    private String contentType;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
}
