# Asistencia y «Terminar ahora»

Se apoya en los lugares automáticos (ver [funciones-nuevas.md](funciones-nuevas.md#lugares-por-dirección-y-en-la-calle)):
cada lugar indica qué actividades se hacen allí, así que no hace falta fijar un lugar a cada actividad.

## Lugares por categoría

- Al guardar un lugar se elige **Actividades en este lugar**: Universidad, Trabajo o Ninguna. Se sugiere por el
  estado elegido («En la universidad» → Universidad, «En el trabajo» → Trabajo).
- Las actividades **presenciales** de esa categoría se asocian a todos los lugares de la categoría (dos campus, dos
  oficinas). Las virtuales y las de tiempo libre no tienen lugar.
- Los lugares guardados antes de esta versión siguen su etiqueta; los que no la tienen quedan sin categoría.

## «Terminar ahora»

- En **Hoy**, la actividad en curso tiene el botón **Terminar ahora** (con confirmación). Termina a la hora actual
  solo esa fecha, con el mismo cambio por fecha que «Solo esta fecha → Reprogramar», así que tu gente te ve libre y
  se puede deshacer con «Restaurar horario habitual». Si acaba de empezar, se cancela esa fecha.
- Si sales del lugar de la actividad con al menos 15 minutos por delante (y no estás en otro lugar de su categoría),
  una notificación pregunta **¿Terminó …?** con el botón **Terminar ahora**. Solo con la asistencia activada.
- Un turno nocturno que empezó ayer no se puede terminar después de la medianoche: el servidor solo admite cambios
  por fecha desde hoy.

## Asistencia (opcional, desactivada por defecto)

- Perfil → **Asistencia** → «Registrar mi asistencia». Cuenta desde el día en que se activa.
- **Automática:** una actividad presencial con un lugar de su categoría cuenta como asistida si el teléfono está en
  ese lugar desde 15 minutos antes de empezar hasta que termina. Se comprueba al llegar a un lugar y al abrirse esa
  ventana (`AttendanceCheckWorker`). Una fecha terminada sin marca cuenta como **falta**.
- **A mano:** cualquier fecha de una clase o turno (también virtuales) se marca con «Asistí» o «Falté». Una marca a
  mano nunca la reemplaza la automática; «Quitar» vuelve al cálculo automático. Las actividades sin lugar quedan
  «sin marcar» en vez de contar como falta.
- La marca se envía con WorkManager en cuanto hay red (`AttendanceUploadWorker`), así que funciona sin conexión.
- Solo tú ves tu asistencia. Se incluye en «Exportar mis datos».

## Servidor

Migración `20261123120000_asistencia_de_actividades.sql`:

- Tabla `activity_attendance` (`user_id`, `schedule_id`, `occurrence_date`, `status`, `source`, `updated_at`), con
  RLS: cada persona solo lee y borra sus filas.
- Se escribe solo con `record_attendance(activity_id, on_date, mark, origin)`: la actividad debe ser tuya o una que
  sigues (`effective_personal_schedule`), la fecha no puede ser futura ni de hace más de un año, y una marca
  automática no reemplaza una manual.
- Las faltas no se guardan. Las filas de hace más de unos 400 días se borran al añadir nuevas, y al borrar una
  actividad (o dejar de seguirla) se borra su asistencia.

Consumo en el plan gratuito: unos 200 bytes por fila con índices. Con 20 actividades por semana son unas 1 000 filas
al año por persona (~0,2 MB), y como mucho un año guardado. La app solo descarga las filas desde que se activó.

### Antes de publicar el APK

```powershell
npx supabase db push --remote --skip-vault
```

Sin la migración la app sigue funcionando: la sección de asistencia queda vacía y las marcas se reintentan unas
veces y luego se descartan.

## Pruebas

- Base de datos: `node scripts/test_activity_attendance.mjs` (permisos, fechas, prioridad de la marca manual,
  actividades seguidas y borrado). `test_migrations_replay` incluye la tabla nueva.
- JVM: `AttendanceTest` (categorías, ventana de asistencia, faltas, terminar ahora, salida anticipada, exportación).
- Queda por probar en un teléfono real: llegar a la universidad antes de una clase, salir antes de tiempo y terminarla
  desde la notificación, marcar a mano y usar «Terminar ahora» en Hoy.
