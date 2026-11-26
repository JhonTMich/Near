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

### Lugares por dirección y «En la calle»

- Un lugar se guarda con la ubicación actual o **buscando su dirección** (geocodificador de Android; solo el texto buscado va a ese servicio, nunca al servidor de Near). Su zona mide 100, 150, 300 o 500 m. La dirección y las coordenadas siguen guardadas solo en el teléfono; los lugares guardados antes conservan su zona de 150 m.
- **«En la calle»** (opcional, desactivado por defecto, visible cuando hay al menos un lugar): al salir del último lugar en el que estaba el teléfono se comparte «En la calle» hasta que empieza la siguiente actividad.
  - El horario tiene prioridad: durante una actividad no se comparte, y si ya estaba compartido se quita para que se vea la actividad. Al terminar cada actividad (un trabajo de WorkManager, `StreetStatusWorker`) se vuelve a decidir.
  - Un estado puesto a mano nunca se reemplaza ni se quita. Al llegar a un lugar, su estado reemplaza a «En la calle».
  - Si dos lugares se tocan, al salir de uno se comparte el del otro.
  - Al activar la opción se comprueba la ubicación actual; al desactivarla, o al quitar el último lugar, se retira solo «En la calle».
- Todo esto es local: no hay migraciones ni cambios en Supabase. Solo cambia cuándo la app llama a los estados que ya existían.

### «Yendo a …» y aviso de tardanza

- Junto al lugar, el teléfono comparte en `current_place.away_from` las categorías de actividad (`university`, `work`) de las que tiene un lugar guardado y en cuyo lugar no está ahora. No viajan coordenadas.
- Durante una actividad **presencial** de una de esas categorías, la app y el widget muestran «Yendo a IoT · Empezó a las 3:00» en lugar de «En clase». Al llegar a su lugar pasa a la actividad normal. Las actividades **virtuales**, y las de una categoría sin lugar guardado, nunca cambian.
- Requiere la migración `lugar_actual_categorias_pendientes`. Sin ella el lugar se sigue enviando sin la columna, y las versiones anteriores de la app la ignoran y muestran la actividad como antes.
- **Aviso de tardanza (solo para ti):** con «Antes de tus actividades» activado, a los 5 min de empezar una actividad presencial con lugar llega «IoT empezó hace 5 min · Aún no llegas a «En la universidad»». Antes de avisar se comprueba la ubicación, si hay permiso «Todo el tiempo». No se registra como tardanza en la asistencia.

## Pruebas

- Base de datos (PGlite, sin conexión al proyecto): `test_schedule_pauses`, `test_one_off_activities`, `test_free_alerts`, `test_plan_proposals` y `test_person_groups`, sobre `scripts/schema_harness.mjs`, que aplica todas las migraciones y simula Vault y pg_net.
- JVM: `SchedulePausesTest`, `OneOffActivityTest`, `FreeAlertPushTest`, `PlansTest`, `MessageRepliesTest`, `StatusUntilTest`, `PeopleRowsTest`, `AutoPlacesTest`, `StreetStatusTest`, `IcsImportTest` y `AccountExportTest`.
- Queda por probar en un teléfono real: geovallas, respuesta desde la notificación, los dos widgets y los pushes nuevos.

### Zona en el mapa

- Al añadir un lugar hay una tercera opción, **«En el mapa»**: el mapa se abre donde estás y el lugar se fija tocando el punto. Tras **buscar una dirección**, el mapa también sirve para ajustar el punto. El círculo muestra el tamaño de la zona (100 a 500 m).
- Usa Google Maps (Maps Compose). La clave `MAPS_API_KEY` se lee del secreto de GitHub al compilar el APK publicado, o de `local.properties` en local. **Nunca se sube al repositorio.** Sin clave la app compila igual y la opción del mapa no aparece, como en los PR.
- La clave está restringida en Google Cloud a **Maps SDK for Android** y al paquete `com.example.near` con las huellas SHA-1 de release (`C3:CA:AC:52:…:C5:67`) y debug. Mostrar el mapa en Android no tiene coste; el proyecto de Google Cloud necesita una cuenta de facturación.
- Si cambia el certificado de firma (por ejemplo, con una rotación de clave), hay que añadir su SHA-1 a la clave en Google Cloud o el mapa saldrá en gris.
