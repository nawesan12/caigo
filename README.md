# Caigo

App social para sumarse a planes a los que les falta gente, o completar los propios ("Hacé planes.
Conocé gente."). Mar del Plata y Villa Gesell. Cuarta app de Grupo Vimo.

- Handoff: `docs/handoff/` (no está en git: descomprimir `~/Trabajo/vimo/Tuki Mockups v2 Updates.zip`
  y renombrar `design_handoff_caigo` a `docs/handoff`). `Caigo Mockups v5.dc.html` es la fuente
  principal de la app; el prototipo suma flujos; Panel Lugares y Pases NFC son la web y Wallet.
  UI 100 % fiel al handoff, tipografía Geist en todo.
- `apps/api`: Go. Calca la forma de TengoLibre/Tu Local Web: stdlib mux, pgx, goose, roles sin
  BYPASSRLS (`caigo_app`, `caigo_migrador`). Lo privado de una persona va con RLS vía
  `db.Pool.EnUsuario` (`app.usuario_id`); el worker y los webhooks, con `EnSistema`. pgxpool sólo
  en `internal/plataforma/db` (test). Errores en problem+json (`internal/plataforma/problema`).
- `apps/web`: SvelteKit (landing, `/p/<slug>`, panel de lugares en `lugares.`, backoffice `/equipo`).
- `apps/ios` (Swift, iOS 17) y `apps/android` (Kotlin + Compose, minSdk 26). Nunca Expo.
- Login: Clerk (la app única de Grupo Vimo). SMS simulado hasta que haya proveedor.
- Producción: VPS compartido (`/opt/caigo`, api 127.0.0.1:4300, web 3500). Registro común en
  `/opt/VPS.md`. No tocar archivos de las otras apps; avisar antes de tocar Caddy o Docker.

## Comandos

```bash
infra/rehacer-base-dev.sh                       # Postgres en 127.0.0.1:5448, roles y migraciones
cd apps/api
go run ./cmd/api                                # :4300
go vet ./... && go test ./...                   # los de servidor/ usan la base de desarrollo
```

Todo a main. Antes de una migración en producción: pg_dump. No crear nada en cuentas de Apple,
Google, Clerk o Mercado Pago sin preguntarle a Nahuel.
