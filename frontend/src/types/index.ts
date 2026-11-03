export interface PageResponse<T> {
    content: T[];
    pageNumber: number;
    pageSize: number;
    totalElements: number;
    totalPages: number;
    last: boolean;
}

export interface LocationSummary {
    id: string;
    department: string;
    municipality: string;
    address: string;
    latitude: number;
    longitude: number;
}

export interface PropertySummary {
    id: string;
    title: string;
    pricePerNight: number;
    currency: string;
    capacity: number;
    bedrooms: number;
    bathrooms: number;
    coverImageUrl?: string;
    municipality?: string;
    latitude?: number;
    longitude?: number;
    ratingAverage?: number;
    reviewsCount?: number;
}

export interface PropertyDetail {
    id: string;
    hostId: string;
    categoryId: string;
    title: string;
    description: string;
    pricePerNight: number;
    currency: string;
    capacity: number;
    bedrooms: number;
    bathrooms: number;
    status: string;
    location: LocationSummary;
    images: Array<{
        id: string;
        imageUrl: string;
        isCover: boolean;
        displayOrder: number;
    }>;
}

export interface CreateBookingRequest {
    propertyId: string;
    checkIn: string;
    checkOut: string;
    guestsCount: number;
}

export interface BookingResponse {
    id: string;
    propertyId: string;
    propertyTitle?: string;
    checkIn: string;
    checkOut: string;
    guestsCount: number;
    totalPrice: number;
    currency: string;
    status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'REJECTED';
}

export interface ReviewResponse {
    id: string;
    bookingId: string;
    rating: number;
    comment: string;
    reviewerName?: string;
    createdAt?: string;
}