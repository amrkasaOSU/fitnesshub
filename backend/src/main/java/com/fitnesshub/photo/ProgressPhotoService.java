package com.fitnesshub.photo;

import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.common.exception.ConflictException;
import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.photo.dto.ProgressPhotoDto;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Progress photos are the most sensitive thing this application stores - they
 * are pictures of someone's body. Two rules follow from that and are enforced
 * here rather than left to callers:
 *
 * <ul>
 *   <li>Every read and write resolves through {@link AuthorizationService}, so a
 *       photo is reachable only by the client it belongs to and their active
 *       coach. There is no public or unauthenticated path to the bytes.</li>
 *   <li>Only a client may upload or delete their own photos. A coach can look,
 *       but cannot add to or remove from someone else's gallery.</li>
 * </ul>
 */
@Service
public class ProgressPhotoService {

    /** Matches the CHECK constraint in V11 - formats a browser renders inline. */
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    /** A guard against one client filling the shared database, not a product limit. */
    private static final long MAX_PHOTOS_PER_CLIENT = 200;

    private final ProgressPhotoRepository repository;
    private final AuthorizationService authorizationService;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public ProgressPhotoService(ProgressPhotoRepository repository,
                                 AuthorizationService authorizationService,
                                 CurrentUser currentUser,
                                 AuditService auditService) {
        this.repository = repository;
        this.authorizationService = authorizationService;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ProgressPhotoDto> list(UUID requestedClientId) {
        UUID clientId = authorizationService.resolveClientId(requestedClientId);
        return repository.findMetadataForClient(clientId);
    }

    @Transactional
    public ProgressPhotoDto upload(MultipartFile file, LocalDate takenOn, String caption) {
        // resolveClientId with null resolves to the caller when they're a CLIENT
        // and rejects a coach, which is exactly the rule we want for uploads.
        UUID clientId = authorizationService.resolveClientId(null);

        if (file == null || file.isEmpty()) {
            throw new ConflictException("No image was uploaded.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new ConflictException("Photos must be a JPEG, PNG, or WebP image.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ConflictException("Photos must be 5 MB or smaller.");
        }
        if (repository.countByClientId(clientId) >= MAX_PHOTOS_PER_CLIENT) {
            throw new ConflictException("You've reached the " + MAX_PHOTOS_PER_CLIENT + " photo limit.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ConflictException("That image couldn't be read. Try uploading it again.");
        }

        ProgressPhoto saved = repository.save(new ProgressPhoto(
                clientId, takenOn == null ? LocalDate.now() : takenOn, caption,
                contentType.toLowerCase(), bytes));

        auditService.record(currentUser.id(), AuditAction.PROGRESS_PHOTO_UPLOADED,
                "ProgressPhoto", saved.getId());

        return new ProgressPhotoDto(saved.getId(), saved.getTakenOn(), saved.getCaption(),
                saved.getContentType(), saved.getSizeBytes(), saved.getCreatedAt());
    }

    /** Returns the raw image, after confirming the caller may see this client's data. */
    @Transactional(readOnly = true)
    public ProgressPhoto getImage(UUID photoId) {
        ProgressPhoto photo = repository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found."));
        authorizationService.assertCanAccessClient(photo.getClientId());
        return photo;
    }

    @Transactional
    public void delete(UUID photoId) {
        ProgressPhoto photo = repository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found."));
        // Deliberately stricter than assertCanAccessClient: a coach can view a
        // client's photos but must not be able to delete them.
        if (!photo.getClientId().equals(currentUser.id())) {
            throw new ConflictException("Only the client who uploaded a photo can delete it.");
        }
        repository.delete(photo);
        auditService.record(currentUser.id(), AuditAction.PROGRESS_PHOTO_DELETED,
                "ProgressPhoto", photoId);
    }
}
