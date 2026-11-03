package com.xelastay.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.xelastay.audit.AuditService;
import com.xelastay.catalog.dto.LocationRequest;
import com.xelastay.catalog.dto.LocationResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationRepository locations;

    @Mock
    private AuditService audit;

    private final UUID hostId = UUID.randomUUID();
    private LocationService service;

    @BeforeEach
    void setUp() {
        service = new LocationService(locations, audit);
    }

    @Test
    void createTrimsAndRoundsCoordinatesToSevenDecimals() {
        when(locations.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocationResponse response = service.create(new LocationRequest(" Quetzaltenango ", "Quetzaltenango",
                " 4a calle 12-34, zona 1 ", new BigDecimal("14.834567891234"), new BigDecimal("-91.518")));

        assertThat(response.department()).isEqualTo("Quetzaltenango");
        assertThat(response.address()).isEqualTo("4a calle 12-34, zona 1");
        assertThat(response.latitude()).isEqualByComparingTo("14.8345679");
        assertThat(response.longitude().scale()).isEqualTo(7);
    }

    @Test
    void hostCannotEditALocationUsedByAnotherHost() {
        Location location = location();
        when(locations.findById(location.getId())).thenReturn(Optional.of(location));
        when(locations.isUsedByAnotherHost(location.getId(), hostId)).thenReturn(true);

        assertThatThrownBy(() -> service.update(hostId, false, location.getId(), request()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(locations, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void adminCanEditAnyLocation() {
        Location location = location();
        when(locations.findById(location.getId())).thenReturn(Optional.of(location));
        when(locations.save(location)).thenReturn(location);

        LocationResponse response = service.update(hostId, true, location.getId(), request());

        assertThat(response.municipality()).isEqualTo("Salcajá");
        verify(locations, never()).isUsedByAnotherHost(any(), any());
    }

    @Test
    void unknownLocationIs404() {
        UUID id = UUID.randomUUID();
        when(locations.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static LocationRequest request() {
        return new LocationRequest("Quetzaltenango", "Salcajá", "Calle principal", null, null);
    }

    private static Location location() {
        Location location = Location.create("Quetzaltenango", "Quetzaltenango", "Centro", null, null);
        ReflectionTestUtils.setField(location, "id", UUID.randomUUID());
        return location;
    }
}
