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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.example.findme.data.model.ScheduleDto
import java.time.Instant
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
    /** How long a place lasts if the phone never reports leaving it; it is sent again every day while there. */
    const val PLACE_MINUTES = 4320L
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
    private val syncMutex = Mutex()
    private fun placesKey(owner: String) = stringPreferencesKey("auto_places_$owner")
    /** Last place successfully shared; retained until its replacement or removal reaches the server. */
    private fun markerKey(owner: String) = stringPreferencesKey("auto_status_$owner")
    private fun insideKey(owner: String) = stringPreferencesKey("auto_inside_$owner")
    private fun markedAtKey(owner: String) = stringPreferencesKey("auto_status_at_$owner")
    private fun legacyKey(owner: String) = stringPreferencesKey("auto_place_layer_$owner")

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
        }
        setInside(context, owner, inside(context, owner) - id)
        return updated
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
        PlaceStatusWorker.scheduleChecks(context, owner?.takeIf { places.isNotEmpty() })
        // Preserve known membership until Android reports changes, including when registering fails.
        if (owner != null) setInside(context, owner, inside(context, owner).filter { id -> places.any { it.id == id } }.toSet())
        if (places.isEmpty() || !hasLocation(context)) return
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

    /** What to share as your current place, decided without touching the network. */
    sealed interface Change {
        data class Share(val label: String, val placeId: String) : Change
        data object Clear : Change
        data object Nothing : Change
        /** Outside every saved place: «En la calle», next to whatever activity or status you have. */
        data class Street(val minutes: Long) : Change
    }

    /** The place shared outside every saved place, when the person turns it on. */
    const val STREET = "En la calle"
    /** Marker of the automatic «En la calle» place, so it is told apart from a saved place. */
    const val STREET_MARKER = "street"
    /** A place shared this long ago is sent again, so it does not expire while the phone stays there. */
    val REFRESH_AFTER: java.time.Duration = java.time.Duration.ofHours(24)

    /**
     * «En la calle» when the person is in none of their saved places. It goes next to the activity or the
     * status, so it is shared during activities too. Turning the option off removes only «En la calle».
     */
    fun street(enabled: Boolean, hasPlaces: Boolean, inside: Set<String>, marker: String?): Change = when {
        inside.isNotEmpty() -> Change.Nothing
        !enabled || !hasPlaces -> if (marker == STREET_MARKER) Change.Clear else Change.Nothing
        else -> Change.Street(PLACE_MINUTES)
    }

    /**
     * The place that matches where the phone is now, given what was shared last ([marker]: the saved place
     * or «En la calle» behind it; [stale] when that was over [REFRESH_AFTER] ago) and [street], what
     * «En la calle» decides when the phone is in no saved place. It depends only on the current state, so a
     * late or repeated run gives the same result. [inside] goes from the first place entered to the latest.
     */
    fun desired(places: List<AutoPlace>, inside: List<String>, marker: String?, street: Change, stale: Boolean = false): Change {
        val here = inside.mapNotNull { id -> places.find { it.id == id } }
        if (here.isNotEmpty()) {
            val latest = here.last()
            if (!stale && latest.id == marker) return Change.Nothing
            return Change.Share(latest.label, latest.id)
        }
        if (street is Change.Street) return if (!stale && marker == STREET_MARKER) Change.Nothing else street
        if (street != Change.Nothing) return street
        return if (marker != null && marker != STREET_MARKER) Change.Clear else Change.Nothing
    }

    /** The saved places the phone is in now, kept from the enter and exit transitions. */
    internal suspend fun inside(context: Context, owner: String): Set<String> =
        context.dataStore.data.first()[insideKey(owner)]?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()

    private suspend fun setInside(context: Context, owner: String, ids: Set<String>) {
        context.dataStore.edit { if (ids.isEmpty()) it.remove(insideKey(owner)) else it[insideKey(owner)] = ids.joinToString(",") }
    }

    private suspend fun apply(context: Context, owner: String, change: Change): Boolean {
        val repository = AccountRepository()
        when (change) {
            is Change.Share -> {
                repository.setPlace(change.label, PLACE_MINUTES)
                mark(context, owner, change.placeId)
            }
            is Change.Street -> {
                repository.setPlace(STREET, change.minutes)
                mark(context, owner, STREET_MARKER)
            }
            Change.Clear -> {
                repository.clearPlace()
                mark(context, owner, null)
            }
            Change.Nothing -> return false
        }
        return true
    }

    private suspend fun mark(context: Context, owner: String, marker: String?) {
        context.dataStore.edit { prefs ->
            if (marker == null) { prefs.remove(markerKey(owner)); prefs.remove(markedAtKey(owner)) }
            else { prefs[markerKey(owner)] = marker; prefs[markedAtKey(owner)] = Instant.now().toString() }
        }
    }

    /**
     * Up to 1.9.2 the place was saved as your shared status, hiding your schedule. Once per account this
     * removes that status if a place set it (setting a status by hand cleared the marker), so the place
     * moves to `current_place` on the next sync.
     */
    suspend fun moveLegacyStatus(context: Context, owner: String) {
        if (context.dataStore.data.first()[legacyKey(owner)] != null) return
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("AutoPlacesStreetStatus")
        if (context.dataStore.data.first()[markerKey(owner)] != null) {
            AccountRepository().clearStatus()
            mark(context, owner, null)
        }
        context.dataStore.edit { it[legacyKey(owner)] = "moved" }
    }

    /**
     * A transition, handled on the phone only: it notes which places the phone is in and leaves sharing the
     * place to [PlaceStatusWorker], which waits for network and retries. Sending it here, with a few seconds
     * and often no network (the phone switching between Wi-Fi and mobile data), lost it for good.
     */
    internal suspend fun handle(context: Context, entering: Boolean, placeIds: List<String>) {
        val owner = WidgetStore.data(context)?.owner ?: SupabaseClientProvider.client.auth.let { auth ->
            auth.awaitInitialization()
            auth.currentUserOrNull()?.id
        } ?: return
        val places = places(context, owner)
        val place = places.firstOrNull { it.id in placeIds } ?: return
        val previous = inside(context, owner) - placeIds.toSet()
        val inside = (if (entering) previous + placeIds else previous)
            .filter { id -> places.any { it.id == id } }.toSet()
        setInside(context, owner, inside)
        PlaceStatusWorker.enqueue(context, owner)
        // Arriving at a place may be arriving at an activity; this only reads the cache and enqueues the mark.
        if (entering) runCatching { com.example.findme.attendance.AttendanceTracker.check(context) }
        else runCatching { com.example.findme.attendance.AttendanceTracker.leftPlace(context, owner, place.id, inside) }
    }

    /** Shares the place that matches where the phone is now. Throws when the network fails, to retry. */
    internal suspend fun sync(context: Context, owner: String) = syncMutex.withLock {
        val auth = SupabaseClientProvider.client.auth
        auth.awaitInitialization()
        if (auth.currentUserOrNull()?.id != owner) return@withLock
        moveLegacyStatus(context, owner)
        val inside = inside(context, owner)
        val prefs = context.dataStore.data.first()
        val marker = prefs[markerKey(owner)]
        val stale = prefs[markedAtKey(owner)]?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?.let { java.time.Duration.between(it, Instant.now()) > REFRESH_AFTER } ?: true
        val places = places(context, owner)
        val street = street(AccountSettingsRepository(context).settings(owner).first().streetStatus,
            places.isNotEmpty(), inside, marker)
        val change = desired(places, inside.toList(), marker, street, stale)
        if (apply(context, owner, change)) GlanceWidgetWorker.enqueueRefresh(context)
    }

    /**
     * Decides the place again without a transition: when «En la calle» is turned on or off and when the last
     * place is removed. With [locate], first checks the current location against the places, in case no
     * transition arrived yet.
     */
    suspend fun reconcile(context: Context, locate: Boolean = false) {
        val auth = SupabaseClientProvider.client.auth
        auth.awaitInitialization()
        val owner = auth.currentUserOrNull()?.id ?: return
        if (!refreshInside(context, owner, locate)) return
        // Persist the retry before attempting the network, including when the app is about to close.
        PlaceStatusWorker.enqueue(context, owner)
        sync(context, owner)
    }

    /** Fresh fixes replace cached membership; without a fix an old location must not be renewed. */
    internal suspend fun refreshInside(context: Context, owner: String, locate: Boolean = true): Boolean {
        val saved = places(context, owner)
        if (saved.isEmpty()) {
            setInside(context, owner, emptySet())
            return true
        }
        if (!locate) return true
        val here = currentLocation(context) ?: return false
        setInside(context, owner, detectedPlaces(saved) { place ->
            FloatArray(1).also { Location.distanceBetween(here.latitude, here.longitude, place.latitude, place.longitude, it) }[0]
        })
        return true
    }

    /** Nearest matching zone goes last, matching the priority of the latest geofence entry. */
    internal fun detectedPlaces(places: List<AutoPlace>, distanceTo: (AutoPlace) -> Float): Set<String> =
        places.map { it to distanceTo(it) }.filter { (place, distance) -> distance <= place.radius }
            .sortedByDescending { it.second }.map { it.first.id }.toSet()
}

private suspend fun com.google.android.gms.tasks.Task<*>.awaitDone() = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWith(Result.failure(it)) }
    addOnCanceledListener { continuation.cancel() }
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
