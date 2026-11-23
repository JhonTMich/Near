package com.example.findme.places

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.findme.data.local.AccountSettingsRepository
import com.example.findme.data.local.dataStore
import com.example.findme.data.network.SupabaseClientProvider
import com.example.findme.data.repository.AccountRepository
import com.example.findme.data.repository.StatusRepository
import com.example.findme.widget.GlanceWidgetWorker
import com.example.findme.widget.WidgetStore
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.example.findme.data.model.ScheduleDto
import com.example.findme.domain.model.nextOccurrence
import com.example.findme.domain.model.occurrencesOn
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.coroutines.resume

/** A place saved on this phone. Its coordinates never leave the device; only [label] is shared as a status. */
@Serializable
data class AutoPlace(val id: String, val label: String, val latitude: Double, val longitude: Double, val radius: Float = AutoPlaces.RADIUS,
    /** The address it was found by, only to recognise it in the list; null when saved from the current location. */
    val address: String? = null,
    /** The activities held here: "university", "work" or [AutoPlaces.NO_CATEGORY]; null if never chosen. */
    val category: String? = null) {
    /** Places saved before categories existed follow their label, so «En la universidad» holds your classes. */
    val activityCategory: String? get() = when (category) {
        null -> AutoPlaces.suggestedCategory(label)
        in AutoPlaces.CATEGORIES -> category
        else -> null
    }
}

/**
 * Automatic status by place: arriving at a saved place shares its label («En casa»), leaving it removes
 * that status. A status the person shared by hand is never replaced or removed.
 */
object AutoPlaces {
    const val MAX = 5
    const val RADIUS = 150f
    /** Zone sizes in metres: a house or an office, up to a whole campus. Android recommends at least 100 m. */
    val RADIUS_CHOICES = listOf(100f, 150f, 300f, 500f)
    /** How long an automatic status lasts if the phone never reports leaving: the maximum for a status. */
    const val STATUS_MINUTES = 1440L
    val LABELS = listOf("En casa", "En la universidad", "En el trabajo", "En el gimnasio")
    /** Categories of activity a place can hold; free time has no fixed place. */
    val CATEGORIES = listOf("university", "work")
    const val NO_CATEGORY = "none"

    /** The category a label suggests, so «En la universidad» holds your classes without asking. */
    fun suggestedCategory(label: String): String? = when (label.trim().lowercase()) {
        "en la universidad" -> "university"
        "en el trabajo" -> "work"
        else -> null
    }

    /** The places where [schedule] happens: same category, and only for in-person activities. */
    fun placesFor(schedule: ScheduleDto, places: List<AutoPlace>): List<AutoPlace> =
        if (schedule.modality == "virtual" || schedule.category !in CATEGORIES) emptyList()
        else places.filter { it.activityCategory == schedule.category }
    private val json = Json { ignoreUnknownKeys = true }
    private fun placesKey(owner: String) = stringPreferencesKey("auto_places_$owner")
    /** Which place set the current automatic status, so leaving another place or a manual status is left alone. */
    private fun markerKey(owner: String) = stringPreferencesKey("auto_status_$owner")
    private fun insideKey(owner: String) = stringPreferencesKey("auto_inside_$owner")

    fun validateLabel(label: String): String? =
        if (label.isBlank() || label.trim().length > 60) "Escribe cómo quieres que se vea, de 1 a 60 caracteres." else null

    fun hasLocation(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Geofences only fire with the app closed when location is allowed "all the time" (Android 10+). */
    fun hasBackground(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun places(context: Context, owner: String): List<AutoPlace> =
        context.dataStore.data.first()[placesKey(owner)]?.let { decode(it) }.orEmpty()

    fun decode(value: String): List<AutoPlace> =
        runCatching { json.decodeFromString(ListSerializer(AutoPlace.serializer()), value) }.getOrDefault(emptyList())

    fun encode(places: List<AutoPlace>): String = json.encodeToString(ListSerializer(AutoPlace.serializer()), places)

    suspend fun add(context: Context, owner: String, label: String, latitude: Double, longitude: Double,
        radius: Float = RADIUS, address: String? = null, category: String? = suggestedCategory(label)): List<AutoPlace> {
        validateLabel(label)?.let { throw IllegalArgumentException(it) }
        require(radius in RADIUS_CHOICES) { "Elige el tamaño de la zona." }
        val current = places(context, owner)
        require(current.size < MAX) { "Puedes guardar hasta $MAX lugares. Quita uno para añadir otro." }
        val updated = current + AutoPlace(UUID.randomUUID().toString(), label.trim(), latitude, longitude, radius,
            address?.trim()?.takeIf { it.isNotEmpty() }, category?.takeIf { it in CATEGORIES } ?: NO_CATEGORY)
        context.dataStore.edit { it[placesKey(owner)] = encode(updated) }
        register(context, owner)
        return updated
    }

    /** An address found by the phone's geocoder, ready to be saved as a place. */
    data class Found(val address: String, val latitude: Double, val longitude: Double)

    /**
     * Looks up [query] («Av. Universitaria 1801, Lima») with Android's geocoder. Only the text goes to the
     * geocoding service of the phone; nothing is sent to Near's server. Empty when there is no geocoder.
     */
    suspend fun search(context: Context, query: String): List<Found> {
        val text = query.trim()
        if (text.length < 3 || !android.location.Geocoder.isPresent()) return emptyList()
        val geocoder = android.location.Geocoder(context)
        val addresses: List<android.location.Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocationName(text, 5, object : android.location.Geocoder.GeocodeListener {
                        override fun onGeocode(found: MutableList<android.location.Address>) { continuation.resume(found) }
                        override fun onError(message: String?) { continuation.resume(emptyList()) }
                    })
                }
            }.orEmpty()
        } else kotlinx.coroutines.withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            runCatching { geocoder.getFromLocationName(text, 5) }.getOrNull().orEmpty()
        }
        return addresses.filter { it.hasLatitude() && it.hasLongitude() }.map { address ->
            val line = (0..address.maxAddressLineIndex).mapNotNull { address.getAddressLine(it) }.joinToString(", ")
            Found(line.ifBlank { text }, address.latitude, address.longitude)
        }.distinctBy { it.latitude to it.longitude }
    }

    suspend fun remove(context: Context, owner: String, id: String): List<AutoPlace> {
        val updated = places(context, owner).filterNot { it.id == id }
        context.dataStore.edit { prefs ->
            prefs[placesKey(owner)] = encode(updated)
            if (prefs[markerKey(owner)] == id) prefs.remove(markerKey(owner))
        }
        register(context, owner)
        return updated
    }

    /** A status set or removed by hand takes over: leaving the place must not remove it. */
    suspend fun forgetAutomaticStatus(context: Context, owner: String) {
        context.dataStore.edit { it.remove(markerKey(owner)) }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, 7_301, Intent(context, PlaceTransitionReceiver::class.java), flags)
    }

    /** Replaces this phone's geofences with the places of [owner]; with no owner it only removes them. */
    @SuppressLint("MissingPermission")
    suspend fun register(context: Context, owner: String?) {
        val client = LocationServices.getGeofencingClient(context)
        runCatching { client.removeGeofences(pendingIntent(context)).awaitDone() }
        val places = owner?.let { places(context, it) }.orEmpty()
        // The initial enter trigger reports again the places the phone is in now.
        if (owner != null) setInside(context, owner, emptySet())
        if (places.isEmpty() || !hasLocation(context)) { StreetStatusWorker.cancel(context); return }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(places.map { place ->
                Geofence.Builder().setRequestId(place.id)
                    .setCircularRegion(place.latitude, place.longitude, place.radius)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
            }).build()
        runCatching { client.addGeofences(request, pendingIntent(context)).awaitDone() }
            .onFailure { android.util.Log.w("AutoPlaces", "Geofences not registered (${it.javaClass.simpleName})") }
    }

    /** After a reboot or an update the system forgets the geofences; the cached account says whose to restore. */
    suspend fun restore(context: Context) = register(context, WidgetStore.data(context)?.owner)

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        if (!hasLocation(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { continuation ->
                val cancel = CancellationTokenSource()
                continuation.invokeOnCancellation { cancel.cancel() }
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resume(null) }
            }
        }
    }

    /** What to do when the phone enters or leaves a place, decided without touching the network. */
    sealed interface Change {
        data class Share(val label: String, val placeId: String) : Change
        data object Clear : Change
        data object Nothing : Change
        /** Outside every saved place and with no activity now: «En la calle» until the next one starts. */
        data class Street(val minutes: Long) : Change
    }

    /** The status shared outside every saved place, when the person turns it on. */
    const val STREET = "En la calle"
    /** Marker of the automatic «En la calle» status, so it is told apart from a place or a manual status. */
    const val STREET_MARKER = "street"

    /**
     * Your own activities as they matter for «En la calle»: [busyUntil] is the end of the activity in
     * progress (the latest one if several overlap), [nextStart]/[nextEnd] the window of the next one.
     */
    data class Agenda(val busyUntil: Instant?, val nextStart: Instant?, val nextEnd: Instant?) {
        /** When «En la calle» has to be decided again: when the current activity ends, or the next one does. */
        val nextCheck: Instant? get() = busyUntil ?: nextEnd
    }

    fun agenda(schedules: List<ScheduleDto>, now: ZonedDateTime): Agenda {
        val busyUntil = schedules.occurrencesOn(now.toLocalDate(), now.zone)
            .filter { !it.window.start.isAfter(now) && it.window.end.isAfter(now) }
            .maxOfOrNull { it.window.end.toInstant() }
        val next = schedules.mapNotNull { it.nextOccurrence(now) }.minByOrNull { it.window.start.toInstant() }
        return Agenda(busyUntil, next?.window?.start?.toInstant(), next?.window?.end?.toInstant())
    }

    /**
     * «En la calle» when the person is in none of their saved places. The schedule wins: during an
     * activity nothing is shared, so people see the activity, and the status ends when the next one starts.
     * A status shared by hand is never touched; turning the option off removes only its own status.
     */
    fun street(enabled: Boolean, hasPlaces: Boolean, inside: Set<String>, marker: String?, manualStatus: Boolean,
        agenda: Agenda, now: Instant): Change {
        val ours = marker == STREET_MARKER
        if (manualStatus || inside.isNotEmpty()) return Change.Nothing
        if (!enabled || !hasPlaces || agenda.busyUntil != null) return if (ours) Change.Clear else Change.Nothing
        val minutes = agenda.nextStart?.let { java.time.Duration.between(now, it).toMinutes() } ?: STATUS_MINUTES
        return if (minutes < 1) { if (ours) Change.Clear else Change.Nothing }
            else Change.Street(minutes.coerceAtMost(STATUS_MINUTES))
    }

    /**
     * [manualStatus]: an own status is active and was not set by a place. [marker]: the place that set the
     * current automatic status, if any.
     */
    fun decide(entering: Boolean, place: AutoPlace, marker: String?, manualStatus: Boolean): Change = when {
        entering && manualStatus -> Change.Nothing
        entering -> Change.Share(place.label, place.id)
        marker == place.id -> Change.Clear
        else -> Change.Nothing
    }

    /** The saved places the phone is in now, kept from the enter and exit transitions. */
    internal suspend fun inside(context: Context, owner: String): Set<String> =
        context.dataStore.data.first()[insideKey(owner)]?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()

    private suspend fun setInside(context: Context, owner: String, ids: Set<String>) {
        context.dataStore.edit { if (ids.isEmpty()) it.remove(insideKey(owner)) else it[insideKey(owner)] = ids.joinToString(",") }
    }

    /** What «En la calle» needs to know about the owner, read from the cache so it works offline. */
    private class Situation(val owner: String, val marker: String?, val manualStatus: Boolean, val agenda: Agenda)

    private suspend fun situation(context: Context, owner: String): Situation {
        val marker = context.dataStore.data.first()[markerKey(owner)]
        val now = Instant.now()
        val data = WidgetStore.data(context)?.takeIf { it.owner == owner }
        val ownActive = data?.statuses.orEmpty().any { status ->
            status.userId == owner && status.isActive && StatusRepository.parseIsoToInstant(status.expiresAt)?.isAfter(now) == true
        }
        val zone = data?.profiles?.find { it.id == owner }?.timezone
            ?.let { runCatching { java.time.ZoneId.of(it) }.getOrNull() } ?: java.time.ZoneId.systemDefault()
        val agenda = agenda(data?.schedules.orEmpty().filter { it.userId == owner }, ZonedDateTime.now(zone))
        return Situation(owner, marker, ownActive && marker == null, agenda)
    }

    private suspend fun apply(context: Context, owner: String, change: Change): Boolean {
        val repository = AccountRepository()
        when (change) {
            is Change.Share -> {
                repository.setStatus(change.label, STATUS_MINUTES)
                context.dataStore.edit { it[markerKey(owner)] = change.placeId }
            }
            is Change.Street -> {
                repository.setStatus(STREET, change.minutes)
                context.dataStore.edit { it[markerKey(owner)] = STREET_MARKER }
            }
            Change.Clear -> {
                repository.clearStatus()
                context.dataStore.edit { it.remove(markerKey(owner)) }
            }
            Change.Nothing -> return false
        }
        return true
    }

    private suspend fun streetChange(context: Context, owner: String, inside: Set<String>, situation: Situation,
        fromWorker: Boolean = false): Change {
        val settings = AccountSettingsRepository(context).settings(owner).first()
        val change = street(settings.streetStatus, places(context, owner).isNotEmpty(), inside, situation.marker,
            situation.manualStatus, situation.agenda, Instant.now())
        // While the option is on, decide again when the activity in progress (or the next one) ends.
        if (settings.streetStatus) StreetStatusWorker.schedule(context, situation.agenda.nextCheck, fromWorker)
        else if (!fromWorker) StreetStatusWorker.cancel(context)
        return change
    }

    internal suspend fun handle(context: Context, entering: Boolean, placeIds: List<String>) {
        val auth = SupabaseClientProvider.client.auth
        auth.awaitInitialization()
        val owner = auth.currentUserOrNull()?.id ?: return
        val places = places(context, owner)
        val place = places.firstOrNull { it.id in placeIds } ?: return
        val inside = (if (entering) inside(context, owner) + placeIds else inside(context, owner) - placeIds.toSet())
            .filter { id -> places.any { it.id == id } }.toSet()
        setInside(context, owner, inside)
        val situation = situation(context, owner)
        val change = when {
            entering -> decide(true, place, situation.marker, situation.manualStatus)
            // Two places that touch: leaving one while still in the other shares the other.
            inside.isNotEmpty() && situation.marker == place.id ->
                places.first { it.id in inside }.let { Change.Share(it.label, it.id) }
            else -> streetChange(context, owner, inside, situation).takeUnless { it == Change.Nothing }
                ?: decide(false, place, situation.marker, situation.manualStatus)
        }
        // Arriving at a place may be arriving at an activity; this only reads the cache and enqueues the mark.
        if (entering) runCatching { com.example.findme.attendance.AttendanceTracker.check(context) }
        if (apply(context, owner, change)) GlanceWidgetWorker.enqueueRefresh(context)
    }

    /**
     * Decides «En la calle» again without a transition: when an activity ends and when the option changes.
     * With [locate], first checks the current location against the places, in case no transition arrived yet.
     */
    suspend fun reconcile(context: Context, locate: Boolean = false, fromWorker: Boolean = false) {
        val auth = SupabaseClientProvider.client.auth
        auth.awaitInitialization()
        val owner = auth.currentUserOrNull()?.id ?: return
        if (locate) currentLocation(context)?.let { here ->
            setInside(context, owner, places(context, owner).filter { place ->
                FloatArray(1).also { Location.distanceBetween(here.latitude, here.longitude, place.latitude, place.longitude, it) }[0] <= place.radius
            }.map { it.id }.toSet())
        }
        val change = streetChange(context, owner, inside(context, owner), situation(context, owner), fromWorker)
        if (apply(context, owner, change)) GlanceWidgetWorker.enqueueRefresh(context)
    }
}

private suspend fun com.google.android.gms.tasks.Task<*>.awaitDone() = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { continuation.resume(Unit) }
}

/** Receives the enter and exit transitions of the saved places. Not exported: only Play services' PendingIntent reaches it. */
class PlaceTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val entering = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> true
            Geofence.GEOFENCE_TRANSITION_EXIT -> false
            else -> return
        }
        val ids = event.triggeringGeofences.orEmpty().map { it.requestId }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeoutOrNull(9_000) { AutoPlaces.handle(context.applicationContext, entering, ids) } }
            catch (_: Exception) { }
            finally { pending.finish() }
        }
    }
}
