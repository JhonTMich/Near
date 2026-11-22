# Funciones nuevas: pausas, planes, grupos y más

Diez funciones añadidas en la rama `feature/nuevas-funciones`, cada una en su propio commit.

| # | Función | Servidor | Android |
|---|---|---|---|
| 1 | Pausar el horario por fechas | `schedule_pauses`, avisos y disponibilidad | Horario → «Pausas del horario» |
| 2 | Actividades de una sola fecha | `personal_schedule.one_off_date`, cruces por fecha | «Cada semana / Una sola fecha» |
| 3 | «Avísame cuando esté libre» | `free_alerts`, `person_busy`, cron cada minuto | Botón en la tarjeta de la persona |
| 4 | Proponer un plan | `plan_proposals`, `answer_plan`, `cancel_plan` | Huecos de «Horas libres» y panel «Planes» |
| 5 | Responder desde la notificación | — (usa `connection_signals`) | Acción «Responder» en avisos de mensaje |
| 6 | Estado hasta una hora | — | «Hasta…» en el editor de estado |
| 7 | Widget «Near · Tu gente» | — | Widget nuevo, redimensionable |
| 8 | Estado automático por lugar | — (los lugares no salen del teléfono) | Perfil → «Estado automático por lugar» |
| 9 | Importar un calendario `.ics` | — | Horario → «Importar un calendario» |
| 10 | Grupos privados | `person_groups`, `person_group_members` | Chips de grupo en Personas |

## Antes de publicar el APK

La app funciona aunque falten las migraciones: cada sección nueva se lee por separado y, si su tabla no existe, simplemente no aparece. Aun así, aplica todo antes de distribuir la versión:

1. **Migraciones** (en orden): `pausas_de_horario`, `actividades_de_una_fecha`, `programar_limpieza_actividades_de_una_fecha`, `avisar_cuando_este_libre`, `programar_avisos_de_libre`, `proponer_planes` y `grupos_de_personas`.
   ```powershell
   npx supabase db push --remote --skip-vault
   ```
2. **Función `send-push`**: vuelve a desplegarla, porque ahora acepta los tipos `free`, `plan` y `plan_answer`.
   ```powershell
   npx supabase functions deploy send-push --remote=ymqsgxmpqushzghkoncg --use-api
   ```
3. **Trabajos de pg_cron** nuevos: `near-push-free-alerts` (cada minuto) y `near-purge-past-one-off` (diario). Los crean las migraciones `_programar_`.

## Compatibilidad

- Las versiones anteriores de la app muestran las actividades de una sola fecha como semanales. Al editarlas no pierden la fecha, porque esas versiones no envían la columna.
- `shared_schedules_for_availability` devuelve dos columnas más (`viewer_pauses`, `contact_pauses`); las versiones anteriores las ignoran.
- El estado automático por lugar necesita Google Play services y el permiso de ubicación «Todo el tiempo» para funcionar con Near cerrado. Android puede tardar unos minutos en detectar la llegada o la salida.

## Pruebas

- Base de datos (PGlite, sin conexión al proyecto): `test_schedule_pauses`, `test_one_off_activities`, `test_free_alerts`, `test_plan_proposals` y `test_person_groups`, sobre `scripts/schema_harness.mjs`, que aplica todas las migraciones y simula Vault y pg_net.
- JVM: `SchedulePausesTest`, `OneOffActivityTest`, `FreeAlertPushTest`, `PlansTest`, `MessageRepliesTest`, `StatusUntilTest`, `PeopleRowsTest`, `AutoPlacesTest`, `IcsImportTest` y `AccountExportTest`.
- Queda por probar en un teléfono real: geovallas, respuesta desde la notificación, los dos widgets y los pushes nuevos.
