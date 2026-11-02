-- Esquema inicial de aplicación. No crea objetos en auth ni habilita extensiones.
-- Supabase Auth debe existir antes de aplicar esta migración (FK profiles -> auth.users).

CREATE TABLE profiles (
  id UUID PRIMARY KEY REFERENCES auth.users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  full_name VARCHAR(150) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  phone VARCHAR(30), avatar_url TEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE roles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), name VARCHAR(50) NOT NULL UNIQUE,
  description TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE profile_roles (
  profile_id UUID NOT NULL REFERENCES profiles(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  role_id UUID NOT NULL REFERENCES roles(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (profile_id, role_id)
);
CREATE INDEX idx_profile_roles_role ON profile_roles(role_id);
CREATE TABLE property_categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), name VARCHAR(100) NOT NULL UNIQUE,
  description TEXT, active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE locations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), department VARCHAR(100) NOT NULL,
  municipality VARCHAR(120) NOT NULL, address TEXT NOT NULL,
  latitude NUMERIC(10,7) CHECK (latitude BETWEEN -90 AND 90),
  longitude NUMERIC(10,7) CHECK (longitude BETWEEN -180 AND 180),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE properties (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  host_id UUID NOT NULL REFERENCES profiles(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  category_id UUID NOT NULL REFERENCES property_categories(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  location_id UUID NOT NULL REFERENCES locations(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  title VARCHAR(180) NOT NULL, description TEXT,
  price_per_night NUMERIC(12,2) NOT NULL CHECK (price_per_night > 0), currency CHAR(3) NOT NULL DEFAULT 'GTQ',
  capacity INTEGER NOT NULL CHECK (capacity > 0), bedrooms INTEGER NOT NULL DEFAULT 0 CHECK (bedrooms >= 0),
  bathrooms NUMERIC(3,1) NOT NULL DEFAULT 0 CHECK (bathrooms >= 0),
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','PAUSED','REJECTED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_properties_host ON properties(host_id);
CREATE INDEX idx_properties_category_status ON properties(category_id, status);
CREATE INDEX idx_properties_location ON properties(location_id);
CREATE TABLE amenities (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), name VARCHAR(100) NOT NULL UNIQUE,
  description TEXT, active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE property_amenities (
  property_id UUID NOT NULL REFERENCES properties(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  amenity_id UUID NOT NULL REFERENCES amenities(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  PRIMARY KEY (property_id, amenity_id)
);
CREATE INDEX idx_property_amenities_amenity ON property_amenities(amenity_id);
CREATE TABLE property_images (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  property_id UUID NOT NULL REFERENCES properties(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  image_url TEXT NOT NULL, caption VARCHAR(255), display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
  is_cover BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_property_images_cover ON property_images(property_id) WHERE is_cover;
CREATE INDEX idx_property_images_display ON property_images(property_id, display_order);
CREATE TABLE availability_blocks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  property_id UUID NOT NULL REFERENCES properties(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  start_date DATE NOT NULL, end_date DATE NOT NULL, reason VARCHAR(255),
  created_by UUID REFERENCES profiles(id) ON UPDATE CASCADE ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), CHECK (end_date > start_date)
);
CREATE INDEX idx_availability_property_dates ON availability_blocks(property_id, start_date, end_date);
CREATE TABLE bookings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  property_id UUID NOT NULL REFERENCES properties(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  guest_id UUID NOT NULL REFERENCES profiles(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  check_in DATE NOT NULL, check_out DATE NOT NULL, guests_count INTEGER NOT NULL CHECK (guests_count > 0),
  nightly_price NUMERIC(12,2) NOT NULL CHECK (nightly_price >= 0),
  subtotal NUMERIC(12,2) NOT NULL CHECK (subtotal >= 0), fees NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (fees >= 0),
  total NUMERIC(12,2) NOT NULL CHECK (total >= 0), currency CHAR(3) NOT NULL DEFAULT 'GTQ',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','COMPLETED','REJECTED')),
  cancellation_reason TEXT, idempotency_key VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (check_out > check_in), UNIQUE (guest_id, idempotency_key)
);
CREATE INDEX idx_bookings_property_dates ON bookings(property_id, check_in, check_out);
CREATE INDEX idx_bookings_guest_created ON bookings(guest_id, created_at DESC);
CREATE INDEX idx_bookings_status_dates ON bookings(status, check_in, check_out);
CREATE TABLE payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id UUID NOT NULL REFERENCES bookings(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0), currency CHAR(3) NOT NULL,
  payment_method VARCHAR(40) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','REFUNDED')),
  transaction_reference VARCHAR(150), paid_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_payments_reference ON payments(transaction_reference) WHERE transaction_reference IS NOT NULL;
CREATE INDEX idx_payments_booking ON payments(booking_id);
CREATE TABLE reviews (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id UUID NOT NULL UNIQUE REFERENCES bookings(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  property_id UUID NOT NULL REFERENCES properties(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  reviewer_id UUID NOT NULL REFERENCES profiles(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5), comment TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reviews_property ON reviews(property_id);
CREATE TABLE conversations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  property_id UUID NOT NULL REFERENCES properties(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversations_property ON conversations(property_id);
CREATE TABLE conversation_participants (
  conversation_id UUID NOT NULL REFERENCES conversations(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  profile_id UUID NOT NULL REFERENCES profiles(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (conversation_id, profile_id)
);
CREATE INDEX idx_conversation_participants_profile ON conversation_participants(profile_id);
CREATE TABLE messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id UUID NOT NULL REFERENCES conversations(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  sender_id UUID NOT NULL REFERENCES profiles(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  content TEXT NOT NULL CHECK (length(trim(content)) > 0), read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_conversation_created ON messages(conversation_id, created_at);
CREATE TABLE notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id UUID NOT NULL REFERENCES profiles(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  type VARCHAR(50) NOT NULL, title VARCHAR(180) NOT NULL, message TEXT NOT NULL,
  read_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_profile_created ON notifications(profile_id, created_at DESC);
CREATE TABLE audit_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), actor_id UUID REFERENCES profiles(id) ON UPDATE CASCADE ON DELETE SET NULL,
  action VARCHAR(100) NOT NULL, entity_type VARCHAR(100) NOT NULL, entity_id UUID,
  old_values JSONB, new_values JSONB, ip_address INET, user_agent TEXT,
  source VARCHAR(30) NOT NULL CHECK (source IN ('SYSTEM','USER','AI','ADMIN')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE TABLE knowledge_documents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), title VARCHAR(200) NOT NULL, source TEXT NOT NULL,
  document_type VARCHAR(50) NOT NULL,
  -- PROPUESTA pendiente de aprobación de Giovanni: valores de estado de indexación.
  status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING','PROCESSING','INDEXED','FAILED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- VECTOR se agregará en migración posterior cuando proveedor, modelo, dimensión y extensión se confirmen.
CREATE TABLE knowledge_chunks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID NOT NULL REFERENCES knowledge_documents(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0), content TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (document_id, chunk_index)
);
CREATE INDEX idx_knowledge_chunks_document ON knowledge_chunks(document_id);
CREATE TABLE ai_interactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), profile_id UUID REFERENCES profiles(id) ON UPDATE CASCADE ON DELETE SET NULL,
  question TEXT NOT NULL, answer TEXT NOT NULL, sources JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_interactions_profile_created ON ai_interactions(profile_id, created_at DESC);
CREATE TABLE outbox_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  -- Clave de deduplicación por evento concreto (no por tipo de evento del agregado).
  event_key VARCHAR(150) NOT NULL UNIQUE,
  aggregate_type VARCHAR(100) NOT NULL, aggregate_id UUID NOT NULL,
  event_type VARCHAR(100) NOT NULL, payload JSONB NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PROCESSING','PUBLISHED','FAILED')),
  attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_events(created_at) WHERE status IN ('PENDING','FAILED');

-- security_invoker: las vistas aplican permisos y RLS del usuario que consulta, no del propietario.
CREATE VIEW reporting_bookings WITH (security_invoker = true) AS
SELECT b.id, b.property_id, p.title AS property_title, p.host_id, b.guest_id,
       l.department, l.municipality, c.name AS category, b.check_in, b.check_out,
       b.guests_count, b.subtotal, b.fees, b.total, b.currency, b.status, b.created_at
FROM bookings b JOIN properties p ON p.id = b.property_id
JOIN locations l ON l.id = p.location_id JOIN property_categories c ON c.id = p.category_id;

CREATE VIEW reporting_property_ratings WITH (security_invoker = true) AS
SELECT p.id AS property_id, p.title, COUNT(r.id) AS review_count,
       AVG(r.rating)::NUMERIC(3,2) AS average_rating
FROM properties p LEFT JOIN reviews r ON r.property_id = p.id GROUP BY p.id, p.title;

-- Seguridad: el acceso a estas tablas es exclusivamente desde el backend (Spring), nunca desde
-- la API de datos de Supabase (PostgREST). Se activa RLS sin políticas para que anon/authenticated
-- no puedan leer ni escribir aunque reciban privilegios por defecto. El propietario de las tablas
-- (rol usado por el backend/Flyway) no queda sujeto a RLS al no usar FORCE ROW LEVEL SECURITY.
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE profile_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE property_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE properties ENABLE ROW LEVEL SECURITY;
ALTER TABLE amenities ENABLE ROW LEVEL SECURITY;
ALTER TABLE property_amenities ENABLE ROW LEVEL SECURITY;
ALTER TABLE property_images ENABLE ROW LEVEL SECURITY;
ALTER TABLE availability_blocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE bookings ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversation_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge_chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai_interactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_events ENABLE ROW LEVEL SECURITY;

-- Se retiran los privilegios que Supabase concede por defecto a los roles de la API.
REVOKE ALL ON
  profiles, roles, profile_roles, property_categories, locations, properties, amenities,
  property_amenities, property_images, availability_blocks, bookings, payments, reviews,
  conversations, conversation_participants, messages, notifications, audit_logs,
  knowledge_documents, knowledge_chunks, ai_interactions, outbox_events,
  reporting_bookings, reporting_property_ratings
FROM anon, authenticated;
