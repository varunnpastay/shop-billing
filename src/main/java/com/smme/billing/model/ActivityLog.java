package com.smme.billing.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One line of the activity history: price changes, stock adjustments, cancellations, deletions... */
@Entity
@Table(name = "activity_log")
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime at;
    private String userName;
    private String action;
    @Column(length = 1000)
    private String details;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getAt() { return at; }
    public void setAt(LocalDateTime at) { this.at = at; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
