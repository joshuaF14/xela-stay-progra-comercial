package com.xelastay.property;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Imagen de un alojamiento (tabla property_images de V1). El índice único de V1 permite una sola portada. */
@Entity
@Table(name = "property_images")
public class PropertyImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_id", nullable = false, updatable = false)
    private UUID propertyId;

    @Column(name = "image_url", nullable = false, columnDefinition = "text")
    private String imageUrl;

    @Column(name = "caption")
    private String caption;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_cover", nullable = false)
    private boolean cover;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    protected PropertyImage() {
    }

    public static PropertyImage create(UUID propertyId, String imageUrl, String caption, int displayOrder,
            boolean cover) {
        PropertyImage image = new PropertyImage();
        image.propertyId = propertyId;
        image.imageUrl = imageUrl;
        image.caption = caption;
        image.displayOrder = displayOrder;
        image.cover = cover;
        image.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        return image;
    }

    public void changeCaption(String caption) {
        this.caption = caption;
    }

    public void changeDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void changeCover(boolean cover) {
        this.cover = cover;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getCaption() {
        return caption;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isCover() {
        return cover;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
