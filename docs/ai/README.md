# Documentos de ejemplo para el agente de IA

Base de conocimiento inicial del asistente de Xela Stay. Son documentos **de ejemplo** para la demostración del
RAG; las condiciones de devolución y los horarios no son una política acordada del proyecto.

| Archivo | documentType |
|---|---|
| `politicas-de-cancelacion.md` | `POLITICA` |
| `reglas-de-la-casa.md` | `REGLAS` |
| `preguntas-frecuentes.md` | `FAQ` |

## Cargarlos

Requiere un token de un usuario con rol `ADMIN` (`$TOKEN`) y el backend en `http://localhost:8080`:

```bash
for doc in politicas-de-cancelacion:POLITICA reglas-de-la-casa:REGLAS preguntas-frecuentes:FAQ; do
  curl -s -X POST http://localhost:8080/api/v1/ai/documents \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@docs/ai/${doc%%:*}.md" -F "documentType=${doc##*:}"
  echo
done
```

Cada carga crea una fila en `knowledge_documents`, sus fragmentos en `knowledge_chunks` (unas 800 palabras con
100 de solape) y un registro en `audit_logs` con `source = 'ADMIN'`.

## Probar el agente

```bash
curl -s -X POST http://localhost:8080/api/v1/ai/chat \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"question":"¿Hasta cuándo puedo cancelar sin costo?"}'
```

La respuesta trae `answer` (con citas como [1]) y `sources` (documento, fragmento y alojamientos usados).
Cada pregunta queda en `ai_interactions` y en `audit_logs` con `source = 'AI'`.
