package com.xelastay.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.xelastay.audit.AuditService;
import com.xelastay.property.dto.PropertyImageRequest;
import com.xelastay.property.dto.PropertyImageResponse;
import com.xelastay.property.dto.PropertyImageUpdateRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PropertyImageServiceTest {

    @Mock
    private PropertyService propertyService;

    @Mock
    private PropertyImageRepository images;

    @Mock
    private AuditService audit;

    private final UUID hostId = UUID.randomUUID();
    private PropertyImageService service;
    private Property property;

    @BeforeEach
    void setUp() {
        service = new PropertyImageService(propertyService, images, audit);
        property = PropertyFixtures.property(hostId, PropertyStatus.DRAFT);
        when(propertyService.requireEditable(hostId, false, property.getId())).thenReturn(property);
    }

    @Test
    void firstImageBecomesTheCover() {
        when(images.countByPropertyId(property.getId())).thenReturn(0L);
        when(images.maxDisplayOrder(property.getId())).thenReturn(-1);
        when(images.save(any(PropertyImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PropertyImageResponse image = service.add(hostId, false, property.getId(),
                new PropertyImageRequest("https://img.example.com/1.jpg", null, null, null));

        assertThat(image.cover()).isTrue();
        assertThat(image.displayOrder()).isZero();
        verify(images, never()).clearCover(any());
    }

    @Test
    void newCoverReplacesTheCurrentOne() {
        when(images.countByPropertyId(property.getId())).thenReturn(2L);
        when(images.save(any(PropertyImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PropertyImageResponse image = service.add(hostId, false, property.getId(),
                new PropertyImageRequest("https://img.example.com/2.jpg", "Sala", 5, true));

        assertThat(image.cover()).isTrue();
        assertThat(image.displayOrder()).isEqualTo(5);
        verify(images).clearCover(property.getId());
    }

    @Test
    void markingCoverClearsThePreviousFirst() {
        PropertyImage image = image(false, 1);
        when(images.findByIdAndPropertyId(image.getId(), property.getId())).thenReturn(Optional.of(image));
        when(images.save(image)).thenReturn(image);

        service.update(hostId, false, property.getId(), image.getId(), new PropertyImageUpdateRequest(null, 3, true));

        verify(images).clearCover(property.getId());
        assertThat(image.isCover()).isTrue();
        assertThat(image.getDisplayOrder()).isEqualTo(3);
    }

    @Test
    void publishedPropertyKeepsAtLeastOneImage() {
        property.changeStatus(PropertyStatus.PUBLISHED);
        PropertyImage image = image(true, 0);
        when(images.findByIdAndPropertyId(image.getId(), property.getId())).thenReturn(Optional.of(image));
        when(images.countByPropertyId(property.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(hostId, false, property.getId(), image.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(images, never()).delete(any());
    }

    @Test
    void deletingTheCoverPromotesTheNextImage() {
        PropertyImage cover = image(true, 0);
        PropertyImage next = image(false, 1);
        when(images.findByIdAndPropertyId(cover.getId(), property.getId())).thenReturn(Optional.of(cover));
        when(images.findByPropertyIdOrderByDisplayOrderAscCreatedAtAsc(property.getId())).thenReturn(List.of(next));

        service.delete(hostId, false, property.getId(), cover.getId());

        verify(images).delete(cover);
        assertThat(next.isCover()).isTrue();
        verify(images).save(next);
    }

    private PropertyImage image(boolean cover, int order) {
        PropertyImage image = PropertyImage.create(property.getId(), "https://img.example.com/x.jpg", null, order,
                cover);
        ReflectionTestUtils.setField(image, "id", UUID.randomUUID());
        return image;
    }
}
