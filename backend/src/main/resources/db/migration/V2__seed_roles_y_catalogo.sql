-- Datos iniciales: roles del sistema y catálogo base. Solo INSERTs idempotentes: si una fila con el mismo
-- nombre ya existe (columna UNIQUE), se conserva tal cual. No crea ni altera objetos.

INSERT INTO roles (name, description) VALUES
  ('GUEST', 'Usuario registrado: busca alojamientos, reserva, paga, reseña y conversa con anfitriones.'),
  ('HOST',  'Anfitrión: publica y administra sus alojamientos y gestiona sus reservas.'),
  ('ADMIN', 'Administrador: gestiona usuarios, roles, catálogo, contenido y auditoría.')
ON CONFLICT (name) DO NOTHING;

INSERT INTO property_categories (name) VALUES
  ('Hotel'),
  ('Casa'),
  ('Apartamento'),
  ('Hostal'),
  ('Cabaña')
ON CONFLICT (name) DO NOTHING;

INSERT INTO amenities (name) VALUES
  ('WiFi'),
  ('Parqueo'),
  ('Cocina'),
  ('Agua caliente'),
  ('Desayuno incluido'),
  ('Se permiten mascotas'),
  ('Piscina'),
  ('Aire acondicionado')
ON CONFLICT (name) DO NOTHING;
