package com.fitnesshub.photo;

import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.photo.dto.ProgressPhotoDto;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/progress-photos")
public class ProgressPhotoController {

    private final ProgressPhotoService service;

    public ProgressPhotoController(ProgressPhotoService service) {
        this.service = service;
    }

    /** Metadata for a gallery. A coach passes clientId; a client's own ID is implied. */
    @GetMapping
    public ApiResponse<List<ProgressPhotoDto>> list(@RequestParam(required = false) UUID clientId) {
        return ApiResponse.of(service.list(clientId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProgressPhotoDto>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) LocalDate takenOn,
            @RequestParam(required = false) String caption) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(service.upload(file, takenOn, caption)));
    }

    /**
     * The bytes. Behind the same session auth as everything else - there is no
     * shareable public URL for a progress photo. Marked private so a shared
     * proxy or CDN can never hold a copy.
     */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        ProgressPhoto photo = service.getImage(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(30)).cachePrivate())
                .body(photo.getImageData());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
