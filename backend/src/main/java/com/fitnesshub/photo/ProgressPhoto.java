package com.fitnesshub.photo;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "progress_photos")
public class ProgressPhoto extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "taken_on", nullable = false)
    private LocalDate takenOn;

    @Column(columnDefinition = "text")
    private String caption;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Integer sizeBytes;

    /**
     * Never load this by listing entities - a gallery of 30 photos would pull
     * every byte into memory. Listing goes through
     * {@link ProgressPhotoRepository#findMetadataForClient} which projects the
     * metadata columns only; this field is read one photo at a time when the
     * image itself is served.
     */
    @Column(name = "image_data", nullable = false)
    private byte[] imageData;

    protected ProgressPhoto() {
    }

    public ProgressPhoto(UUID clientId, LocalDate takenOn, String caption,
                          String contentType, byte[] imageData) {
        this.clientId = clientId;
        this.takenOn = takenOn;
        this.caption = caption;
        this.contentType = contentType;
        this.imageData = imageData;
        this.sizeBytes = imageData.length;
    }

    public UUID getClientId() {
        return clientId;
    }

    public LocalDate getTakenOn() {
        return takenOn;
    }

    public String getCaption() {
        return caption;
    }

    public String getContentType() {
        return contentType;
    }

    public Integer getSizeBytes() {
        return sizeBytes;
    }

    public byte[] getImageData() {
        return imageData;
    }
}
