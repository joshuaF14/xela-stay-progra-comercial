package com.xelastay.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.xelastay.catalog.dto.AmenityResponse;
import com.xelastay.catalog.dto.CatalogItemRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AmenityServiceTest {

    @Mock
    private AmenityRepository amenities;

    private AmenityService service;

    @BeforeEach
    void setUp() {
        service = new AmenityService(amenities);
    }

    @Test
    void publicListOnlyIncludesActive() {
        when(amenities.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(amenity("WiFi", true)));

        List<AmenityResponse> list = service.list(false);

        assertThat(list).extracting(AmenityResponse::name).containsExactly("WiFi");
        verify(amenities, never()).findAllByOrderByNameAsc();
    }

    @Test
    void renamingToAnExistingNameIs409() {
        Amenity amenity = amenity("Parqueo", true);
        when(amenities.findById(amenity.getId())).thenReturn(Optional.of(amenity));
        when(amenities.existsByNameIgnoreCaseAndIdNot("wifi", amenity.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.update(amenity.getId(), new CatalogItemRequest("wifi", null, true)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void createCanStartInactive() {
        when(amenities.existsByNameIgnoreCase("Jacuzzi")).thenReturn(false);
        when(amenities.saveAndFlush(any(Amenity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AmenityResponse response = service.create(new CatalogItemRequest("Jacuzzi", null, false));

        assertThat(response.active()).isFalse();
    }

    @Test
    void deactivateSetsActiveFalse() {
        Amenity amenity = amenity("Piscina", true);
        when(amenities.findById(amenity.getId())).thenReturn(Optional.of(amenity));

        service.deactivate(amenity.getId());

        assertThat(amenity.isActive()).isFalse();
        verify(amenities).save(amenity);
    }

    private static Amenity amenity(String name, boolean active) {
        Amenity amenity = Amenity.create(name, null, active);
        ReflectionTestUtils.setField(amenity, "id", UUID.randomUUID());
        return amenity;
    }
}
