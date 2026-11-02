Lee y sigue [AGENTS.md](AGENTS.md) antes de cualquier tarea.

Reglas críticas (resumen):

- Nada de Git: no commits, pushes, merges, rebases ni cambios o borrados de ramas.
- No ejecutar migraciones, no conectarse a Supabase ni correr Flyway; `FLYWAY_ENABLED=false`.
- No leer, mostrar ni copiar valores de `.env` ni contraseñas; solo nombres de variables.
- No instalar dependencias ni ejecutar `npm install` o `mvn` sin aprobación.
- No inventar endpoints, tablas, estados ni reglas de negocio. No cambiar el stack.
- Trabajar por bloques, mostrar el diff y esperar confirmación.
