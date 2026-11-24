# Cancelación de planes compartidos

Un plan aceptado conserva los identificadores de las actividades creadas para cada participante.
Cualquiera de los dos puede cancelarlo mientras no haya terminado. La operación elimina ambas
copias y cambia el plan a `cancelled` en una sola transacción. Si falla una eliminación, se revierte todo.

## En la app

- En Personas → Planes, los planes aceptados ofrecen **Cancelar plan**.
- En Horario, el menú de una actividad vinculada ofrece **Cancelar plan**, con la misma confirmación.
- La confirmación explica que se quitará de ambos horarios y se avisará a la otra persona.
- Las propuestas pendientes siguen permitiendo que su autor las retire. El destinatario puede rechazarlas.

La cancelación usa identificadores, por lo que funciona aunque una copia se haya editado o eliminado
localmente. Una actividad independiente con el mismo nombre se conserva. Reintentar la cancelación
no repite eliminaciones ni avisos. Un guardado atrasado tampoco puede recrear una copia cancelada
mientras se conserve el registro del plan.

## Avisos y despliegue

La función `send-push` admite `plan_cancelled`. El aviso sustituye el del mismo plan y puede volver a
sonar para comunicar la cancelación. El plan también se actualiza en Realtime.

Desplegar primero `send-push`, aplicar `20261124154439_cancelar_planes_aceptados.sql` y publicar el APK.
Los clientes anteriores solo podían retirar propuestas pendientes.

La migración intenta vincular los planes aceptados anteriores por participante, nombre, lugar y
horario en su zona. Si encuentra varias coincidencias, no cancela por aproximación: devuelve `FM008`.
Las copias antiguas que ya habían cambiado esos datos pueden requerir revisión; no existía un vínculo
persistente que permita identificarlas con certeza. Los planes aceptados después de esta migración
guardan los identificadores desde su creación.

## Verificación

`scripts/test_plan_cancellation.mjs` comprueba permisos, ambas copias, cambios locales, reintentos,
reversión ante fallos, guardados atrasados y actualización de planes anteriores en distintas zonas.
`PlanCancellationUiTest` comprueba la confirmación desde Personas y desde Horario.
