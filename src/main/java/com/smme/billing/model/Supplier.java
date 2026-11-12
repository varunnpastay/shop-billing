package com.smme.billing.model;

import jakarta.persistence.*;

/** A textile supplier the shop buys stock from. */
@Entity
@Table(name = "supplier")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;
    private String phone;
    @Column(length = 500)
    private String address;
    private String gstin;
    @Column(length = 500)
    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
