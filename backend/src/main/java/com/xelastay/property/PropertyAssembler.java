package com.xelastay.property;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.xelastay.catalog.AmenityRepository;
import com.xelastay.catalog.AmenityService;
import com.xelastay.catalog.LocationService;
import com.xelastay.catalog.dto.AmenityResponse;
import com.xelastay.common.ProfileNameReader;
import com.xelastay.property.dto.PropertyDetailResponse;
import com.xelastay.property.dto.PropertyDetailResponse.HostRef;
import com.xelastay.property.dto.PropertyImageResponse;
import com.xelastay.property.dto.PropertySummaryResponse;
import com.xelastay.property.dto.PropertySummaryResponse.CategoryRef;

import org.springframework.stereotype.Component;

/**
 * Arma las respuestas de alojamientos con pocas consultas: portadas y calificaciones de toda la página a la vez.
 * Debe usarse dentro de una transacción (categoría y ubicación son LAZY).
 */
@Component
public class PropertyAssembler {

    private final PropertyImageRepository images;
    private final PropertyAmenityRepository propertyAmenities;
    private final AmenityRepository amenities;
    private final PropertyRatingReader ratings;
    private final ProfileNameReader profileNames;

    public PropertyAssembler(PropertyImageRepository images, PropertyAmenityRepository propertyAmenities,
            AmenityRepository amenities, PropertyRatingReader ratings, ProfileNameReader profileNames) {
        this.images = images;
        this.propertyAmenities = propertyAmenities;
        this.amenities = amenities;
        this.ratings = ratings;
        this.profileNames = profileNames;
    }

    public List<PropertySummaryResponse> summaries(List<Property> properties) {
        if (properties.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = properties.stream().map(Property::getId).toList();
        // findForCovers ordena por portada y luego por orden: la primera de cada alojamiento es su tarjeta.
        Map<UUID, String> covers = new LinkedHashMap<>();
        for (PropertyImage image : images.findForCovers(ids)) {
            covers.putIfAbsent(image.getPropertyId(), image.getImageUrl());
        }
        Map<UUID, PropertyRatingReader.Rating> ratingById = ratings.find(ids);
        return properties.stream().map(property -> {
            PropertyRatingReader.Rating rating = ratingById.getOrDefault(property.getId(),
                    PropertyRatingReader.Rating.NONE);
            return new PropertySummaryResponse(property.getId(), property.getHostId(), property.getTitle(),
                    property.getDescription(), property.getPricePerNight(), property.getCurrency(),
                    property.getCapacity(), property.getBedrooms(), property.getBathrooms(), property.getStatus(),
                    category(property), LocationService.toResponse(property.getLocation()),
                    covers.get(property.getId()), rating.average(), rating.count(), property.getCreatedAt(),
                    property.getUpdatedAt());
        }).toList();
    }

    public PropertyDetailResponse detail(Property property) {
        List<PropertyImageResponse> imageList = images
                .findByPropertyIdOrderByDisplayOrderAscCreatedAtAsc(property.getId()).stream()
                .map(PropertyAssembler::image)
                .toList();
        List<AmenityResponse> amenityList = amenities.findAllById(propertyAmenities.findAmenityIds(property.getId()))
                .stream()
                .map(AmenityService::toResponse)
                .sorted(Comparator.comparing(AmenityResponse::name))
                .toList();
        String hostName = profileNames.findNames(List.of(property.getHostId())).get(property.getHostId());
        PropertyRatingReader.Rating rating = ratings.find(List.of(property.getId()))
                .getOrDefault(property.getId(), PropertyRatingReader.Rating.NONE);
        return new PropertyDetailResponse(property.getId(), property.getTitle(), property.getDescription(),
                property.getPricePerNight(), property.getCurrency(), property.getCapacity(), property.getBedrooms(),
                property.getBathrooms(), property.getStatus(), category(property),
                LocationService.toResponse(property.getLocation()), new HostRef(property.getHostId(), hostName),
                imageList, amenityList, rating.average(), rating.count(), property.getCreatedAt(),
                property.getUpdatedAt());
    }

    public static PropertyImageResponse image(PropertyImage image) {
        return new PropertyImageResponse(image.getId(), image.getImageUrl(), image.getCaption(),
                image.getDisplayOrder(), image.isCover(), image.getCreatedAt());
    }

    private static CategoryRef category(Property property) {
        return new CategoryRef(property.getCategory().getId(), property.getCategory().getName());
    }
}
