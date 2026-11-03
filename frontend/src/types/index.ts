export interface PropertyCategory {
    id: string;
    name: string;
    description?: string;
    active: boolean;
}

export interface Location {
    id: string;
    department: string;
    municipality: string;
    address: string;
    latitude: number;
    longitude: number;
}

export interface Amenity {
    id: string;
    name: string;
    description?: string;
    active: boolean;
}

export interface PropertyImage {
    id: string;
    property_id: string;
    image_url: string;
    caption?: string;
    display_order: number;
    is_cover: boolean;
}

export interface Property {
    id: string;
    host_id: string;
    category_id: string;
    location_id: string;
    title: string;
    description?: string;
    price_per_night: number;
    currency: string;
    capacity: number;
    bedrooms: number;
    bathrooms: number;
    status: 'DRAFT' | 'PUBLISHED' | 'PAUSED' | 'REJECTED';
    location?: Location;
    category?: PropertyCategory;
    images?: PropertyImage[];
    amenities?: Amenity[];
}

export interface BookingPayload {
    property_id: string;
    check_in: string; // YYYY-MM-DD
    check_out: string; // YYYY-MM-DD
    guests_count: number;
}