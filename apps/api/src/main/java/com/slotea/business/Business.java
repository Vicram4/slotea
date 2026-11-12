package com.slotea.business;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** El negocio es el tenant: no lleva {@code @TenantId}, su id se toma del JWT. */
@Entity
@Table(name = "business")
public class Business {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Business() {
    }

    public Business(String slug, String name, String timezone, String contactEmail, Instant createdAt) {
        this.slug = slug;
        this.name = name;
        this.timezone = timezone;
        this.contactEmail = contactEmail;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public boolean isDemo() {
        return demo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeTimezone(String timezone) {
        this.timezone = timezone;
    }

    public void changeContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }
}
