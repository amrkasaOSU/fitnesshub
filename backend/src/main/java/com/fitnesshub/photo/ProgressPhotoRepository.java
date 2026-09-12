package com.fitnesshub.photo;

import com.fitnesshub.photo.dto.ProgressPhotoDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProgressPhotoRepository extends JpaRepository<ProgressPhoto, UUID> {

    /**
     * Projects metadata straight into the DTO so a gallery listing never loads
     * image_data. Selecting the entity here would pull every photo's bytes into
     * memory to render a page of thumbnails.
     */
    @Query("""
            select new com.fitnesshub.photo.dto.ProgressPhotoDto(
                p.id, p.takenOn, p.caption, p.contentType, p.sizeBytes, p.createdAt)
            from ProgressPhoto p
            where p.clientId = :clientId
            order by p.takenOn desc, p.createdAt desc
            """)
    List<ProgressPhotoDto> findMetadataForClient(@Param("clientId") UUID clientId);

    long countByClientId(UUID clientId);
}
