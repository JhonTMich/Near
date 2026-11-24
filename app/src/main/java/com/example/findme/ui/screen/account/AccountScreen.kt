@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.findme.ui.screen.account

import android.app.NotificationManager
import android.content.ClipData
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.findme.BuildConfig
import com.example.findme.data.local.AccountAlert
import com.example.findme.data.local.AppTheme
import com.example.findme.ui.screen.UpdateState
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.findme.data.model.*
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.Registration
import com.example.findme.domain.model.Username
import com.example.findme.domain.model.InvitationCode
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.domain.model.conflictMessage
import com.example.findme.domain.model.findScheduleConflict
import com.example.findme.domain.model.formatScheduleTime
import com.example.findme.domain.model.validateSchedule
import com.example.findme.domain.model.shiftScheduleEnd
import com.example.findme.domain.model.occurrencesOn
import com.example.findme.data.model.ScheduleException
import com.example.findme.data.model.SharedSchedules
import com.example.findme.domain.model.commonFreeTime
import com.example.findme.domain.model.FreeSlot
import com.example.findme.domain.model.Plans
import com.example.findme.domain.model.StatusUntil
import com.example.findme.domain.model.covering
import com.example.findme.domain.model.range
import com.example.findme.domain.model.validatePause
import com.example.findme.domain.model.validateOneOffDate
import kotlinx.coroutines.CancellationException
import java.time.Instant
import com.example.findme.domain.model.timeRangeLabel
import com.example.findme.ui.components.*
import com.example.findme.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.findme.R
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.shape.GenericShape

private val days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
private val dayLetters = listOf("L", "M", "X", "J", "V", "S", "D")
private val hourFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private fun AccountProfile.zone() = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())

@Composable
fun AccountScreen(viewModel: AccountViewModel, updates: UpdateState = UpdateState(),
    onCheckUpdates: () -> Unit = {}, onInstallUpdate: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            com.example.findme.push.NearMessagingService.observeAccountScreen { viewModel.observeWhileVisible() }
        }
    }
    AccountContent(state, viewModel, updates, onCheckUpdates, onInstallUpdate)
}

@Composable
fun AccountContent(state: AccountState, actions: AccountActions,
    updates: UpdateState = UpdateState(), onCheckUpdates: () -> Unit = {}, onInstallUpdate: () -> Unit = {}) {
    if (state.initializing) {
        Box(Modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                BrandMark(Modifier.size(88.dp))
                CircularProgressIndicator(Modifier.size(24.dp), color = BrandAccent, strokeWidth = 2.dp)
            }
        }
        return
    }
    // A recovery link already signed in; nothing of the account opens until the new password is saved.
    if (state.signedIn && state.passwordRecovery) {
        NewPasswordPage(state, actions)
        return
    }
    var tab by rememberSaveable(state.signedIn) { mutableStateOf("Mi día") }
    LaunchedEffect(state.peopleRevision, state.signedIn) { if (state.peopleRevision > 0 && state.signedIn) tab = "Personas" }
    LaunchedEffect(state.scheduleRevision, state.signedIn) { if (state.scheduleRevision > 0 && state.signedIn) tab = "Horario" }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it) } }
    Box(Modifier.fillMaxSize().nearBackdrop()) {
    Scaffold(containerColor = Color.Transparent, contentColor = TextPrimary, snackbarHost = { NearSnackbarHost(snackbar) }, topBar = {
        // Signed in, the bar is its own band; on the sign-in screen it sits on the backdrop with the mark.
        Column(Modifier.fillMaxWidth().then(if (state.signedIn) Modifier.background(BarBg) else Modifier)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.signedIn) BrandMark(Modifier.size(40.dp))
                Text("Near", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                if (state.signedIn) {
                    Surface(onClick = actions::refresh, enabled = !state.busy, shape = CircleShape, color = Color.Transparent,
                        contentColor = TextSecondary, border = BorderStroke(1.dp, CardBorder), modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, "Actualizar", Modifier.size(20.dp))
                        }
                    }
                }
            }
            if (state.signedIn) HorizontalDivider(color = CardBorder)
        }
    }, bottomBar = {
        if (state.signedIn && state.profile?.needsProfile == false) {
            Column(Modifier.fillMaxWidth().background(BarBg).navigationBarsPadding()) {
                HorizontalDivider(color = CardBorder)
                NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0), modifier = Modifier.heightIn(min = 68.dp)) {
                    listOf(Triple("Mi día", "Hoy", Icons.Default.Today), Triple("Horario", "Horario", Icons.Default.CalendarMonth),
                        Triple("Personas", "Personas", Icons.Default.PeopleOutline), Triple("Cuenta", "Perfil", Icons.Default.PersonOutline)).forEach { (id, title, icon) ->
                        NavigationBarItem(selected = tab == id, onClick = { tab = id },
                            modifier = Modifier.testTag("near-tab-$title"),
                            icon = { Icon(icon, null, Modifier.size(21.dp)) },
                            label = { Text(title, maxLines = 1, style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (tab == id) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = AccentContainer,
                                unselectedIconColor = TextSecondary, unselectedTextColor = TextSecondary))
                    }
                }
            }
        }
    }) { padding ->
        Crossfade(tab, modifier = Modifier.fillMaxSize().padding(padding), label = "accountNavigation") { activeTab ->
            // The visible height lets the sign-in panel fill the screen exactly instead of overflowing it.
            BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            Column(Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                state.error?.let { error -> Panel {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error)
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } }
                if (state.signedIn && state.syncIssue) Panel {
                    Text(
                        if (state.profile == null) "No pudimos cargar tu perfil."
                        else "Sincronización incompleta. Puedes seguir consultando los datos disponibles.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = actions::refresh, enabled = !state.busy) { Text("Reintentar sincronización") }
                }
                if (!state.signedIn) LoginForm(state, actions, viewport)
                else if (state.profile == null) {
                    Panel {
                        if (!state.syncIssue) Text("Cargando tu perfil…", color = TextSecondary)
                        OutlinedButton(onClick = actions::logout, enabled = !state.busy) { Text("Cerrar sesión") }
                    }
                }
                else if (state.profile.needsProfile) CompleteProfile(state, actions)
                else when (activeTab) {
                    "Mi día" -> MyDay(state, actions, onOpenSchedule = { tab = "Horario" })
                    "Horario" -> ScheduleManager(state, actions)
                    "Personas" -> People(state, actions)
                    else -> AccountProfilePanel(state, actions, updates, onCheckUpdates, onInstallUpdate)
                }
            }
            }
        }
    }
    // The sign-in panel runs to the bottom edge, so the navigation bar area takes its colour too.
    if (!state.signedIn && state.confirmationEmail == null) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        .windowInsetsBottomHeight(WindowInsets.navigationBars).background(LoginPanel))
    }
}

/**
 * Shown after a recovery link signs the person in. It fills the screen and has no way around it:
 * either the new password is saved or the person signs out, so the link never replaces the password.
 */
@Composable
private fun NewPasswordPage(state: AccountState, actions: AccountActions) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    val mismatch = confirmation.isNotEmpty() && confirmation != password
    val toggle: @Composable () -> Unit = {
        IconButton(onClick = { visible = !visible }) {
            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "Ocultar contraseña" else "Mostrar contraseña")
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppSurface, AppBackground), endY = 1400f))) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
            val viewport = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp)) {
                LoginStage(viewport) {
                    Text("Elige una contraseña nueva", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text("Guárdala para terminar de recuperar tu cuenta. De 8 a 128 caracteres; la usarás con tu nombre de usuario para entrar.",
                        color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    Field(password, { password = it.take(128) }, "Nueva contraseña", leading = Icons.Default.Lock,
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        hidden = !visible, trailing = toggle, enabled = !state.busy)
                    Field(confirmation, { confirmation = it.take(128) }, "Confirmar contraseña", leading = Icons.Default.Lock,
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        hidden = !visible, trailing = toggle, enabled = !state.busy, isError = mismatch)
                    Text(state.error ?: if (mismatch) "Las contraseñas no coinciden." else "Hasta que la guardes no se abrirá tu cuenta.",
                        color = if (state.error != null || mismatch) MaterialTheme.colorScheme.error else TextMuted,
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { actions.setRecoveredPassword(password, confirmation) },
                        enabled = !state.busy && password.length in 8..128 && password == confirmation,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(52.dp), shape = RoundedCornerShape(18.dp)) {
                        Text(if (state.busy) "Guardando…" else "Guardar contraseña", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = actions::cancelPasswordRecovery, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Cancelar y cerrar sesión", color = TextSecondary)
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars).background(LoginPanel))
    }
}

/** Seconds until an email of the same kind can be sent again; 0 when it can be sent now. */
@Composable
private fun resendSecondsLeft(sentAt: Long?): Int {
    if (sentAt == null) return 0
    var now by remember(sentAt) { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(sentAt) {
        while (now - sentAt < EMAIL_RESEND_SECONDS * 1000L) {
            delay(1000 - (now - sentAt) % 1000)
            now = android.os.SystemClock.elapsedRealtime()
        }
    }
    return ((EMAIL_RESEND_SECONDS * 1000L - (now - sentAt) + 999) / 1000).toInt().coerceAtLeast(0)
}

private fun countdown(seconds: Int) = "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)

@Composable
private fun PageHeading(eyebrow: String, title: String, subtitle: String, trailing: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (eyebrow.isNotBlank()) Eyebrow(eyebrow, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).testTag("near-heading-$title").semantics { heading() },
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            trailing?.let { Text(it, color = TextSecondary, style = MonoLabel) }
        }
        if (subtitle.isNotBlank()) Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    NearCard(padding = PaddingValues(18.dp), spacing = 14.dp, content = content)
}

@Composable
private fun SectionHeading(icon: ImageVector, title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBubble(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun LoginForm(state: AccountState, actions: AccountActions, viewport: Dp) {
    var mode by rememberSaveable(state.loginRevision) { mutableStateOf("login") }
    var username by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.confirmationEmail) {
        if (state.confirmationEmail != null) { password = ""; confirmation = "" }
    }
    state.confirmationEmail?.let { pendingEmail ->
        PageHeading("Un paso más", "Revisa tu correo", "Confirma tu dirección antes de iniciar sesión.")
        Panel {
            SectionHeading(Icons.Default.MarkEmailRead, "Tu cuenta, a un paso", pendingEmail)
            Text("Abre el enlace de confirmación y después vuelve a Near. Revisa también la carpeta de spam.", color = TextSecondary)
            Button(onClick = { mode = "login"; actions.finishRegistration() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Ya confirmé, iniciar sesión")
            }
            val wait = resendSecondsLeft(state.confirmationSentAt)
            OutlinedButton(onClick = { actions.resendConfirmation(pendingEmail) }, enabled = !state.busy && wait == 0, modifier = Modifier.fillMaxWidth()) {
                Text(if (wait > 0) "Reenviar confirmación en ${countdown(wait)}" else "Reenviar confirmación")
            }
        }
        return
    }
    val registering = mode == "register"
    val recovering = mode == "recover"
    // Recovery reuses the email-only layout of "resend confirmation".
    val resending = mode == "resend" || recovering
    val title = when { registering -> "Tu gente, más cerca."; recovering -> "Recupera tu acceso."; resending -> "Confirma tu cuenta."; else -> "Qué bueno verte." }
    val subtitle = when { registering -> "Crea tu cuenta y confirma tu correo para empezar."
        recovering -> "Te enviaremos un enlace al correo de tu cuenta para elegir una contraseña nueva."
        resending -> "Te enviaremos otro enlace al correo con el que empezaste tu registro."
        else -> "Tu horario y tu gente, en un mismo lugar." }
    val passwordToggle: @Composable () -> Unit = {
        IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña")
        }
    }
    LoginStage(viewport) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        if (registering) Field(name, { name = it.take(80) }, "Nombre visible", leading = Icons.Default.Badge,
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        if (registering || resending) Field(email, { email = it.take(254) }, "Correo electrónico", leading = Icons.Default.MailOutline,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false))
        if (!resending) Field(username, { username = it.take(24) }, "Nombre de usuario", leading = Icons.Default.PersonOutline,
            keyboard = KeyboardOptions(autoCorrectEnabled = false))
        if (registering) Text("De 3 a 24 letras sin tildes, números o guion bajo. Será tu usuario para entrar.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        if (!resending) Field(password, { password = it.take(128) }, "Contraseña", leading = Icons.Default.Lock,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            hidden = !passwordVisible, trailing = passwordToggle, enabled = !state.busy)
        if (registering) {
            Field(confirmation, { confirmation = it.take(128) }, "Confirmar contraseña", leading = Icons.Default.Lock,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                hidden = !passwordVisible, trailing = passwordToggle, enabled = !state.busy,
                isError = confirmation.isNotEmpty() && password != confirmation)
            Text(if (confirmation.isNotEmpty() && password != confirmation) "Las contraseñas no coinciden." else "Usa entre 8 y 128 caracteres. Confirma tu correo para empezar.",
                color = if (confirmation.isNotEmpty() && password != confirmation) MaterialTheme.colorScheme.error else TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        val valid = when {
            registering -> Registration.error(name, email, username, password, confirmation) == null
            resending -> Registration.isEmail(email)
            else -> Username.isValid(username) && password.isNotBlank()
        }
        // Sending another link right away would hit the server's limit; count down to when it is allowed.
        val sent = state.recoveryEmailSent?.takeIf { recovering }
        val wait = when {
            recovering -> resendSecondsLeft(sent?.at)
            mode == "resend" -> resendSecondsLeft(state.confirmationSentAt)
            else -> 0
        }
        if (sent != null) Text("Enviamos el enlace a ${sent.address}. Ábrelo en este teléfono y revisa también la carpeta de spam.",
            color = TextSecondary, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Button(onClick = {
            when { registering -> actions.register(name, email, username, password, confirmation)
                recovering -> actions.requestPasswordReset(email)
                resending -> actions.resendConfirmation(email)
                else -> actions.login(username, password) }
        }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(52.dp), enabled = !state.busy && valid && wait == 0, shape = RoundedCornerShape(18.dp)) {
            Text(when { state.busy -> "Un momento…"; registering -> "Registrarme"
                wait > 0 -> "Reenviar en ${countdown(wait)}"
                recovering -> if (sent != null) "Reenviar enlace" else "Enviar enlace"
                resending -> "Enviar confirmación"; else -> "Entrar" },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (mode == "login") TextButton(onClick = { mode = "recover"; password = ""; passwordVisible = false }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("¿Olvidaste tu contraseña?", color = TextSecondary)
        }
        if (recovering) TextButton(onClick = { mode = "login" }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Volver a iniciar sesión", color = MaterialTheme.colorScheme.primary)
        }
        TextButton(onClick = { mode = when (mode) { "login", "resend" -> "register"; else -> "login" }; password = ""; confirmation = ""; passwordVisible = false }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(when (mode) { "login" -> "Crear cuenta"; "resend" -> "Volver al registro"; else -> "Ya tengo cuenta" },
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        if (registering) TextButton(onClick = { mode = "resend"; password = ""; confirmation = ""; passwordVisible = false }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("No recibí mi confirmación", color = TextMuted)
        }
    }
}

/** Darker panel under the sign-in screens; its top edge arcs like the ledge the mascot peeks over in the icon. */
private val LedgeShape = GenericShape { size, _ ->
    val depth = size.width * .08f
    moveTo(0f, depth)
    quadraticTo(size.width / 2, -depth, size.width, depth)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

/** The mascot peeking over the arched panel: head behind it, paws resting on it, the form inside. */
@Composable
private fun LoginStage(viewport: Dp, content: @Composable ColumnScope.() -> Unit) {
    // Fill what is left of the visible area: the page's 8dp top padding and the 116dp the mascot takes
    // above the panel (the panel covers the 28dp bottom padding itself). Only taller forms, such as
    // registration, then need to scroll.
    val panelMin = (viewport - 8.dp - 116.dp).coerceAtLeast(0.dp)
    // Bleed past the screen's 20dp side and 28dp bottom padding so the panel reaches both edges and the bottom.
    Box(Modifier.fillMaxWidth().layout { measurable, constraints ->
        val side = 20.dp.roundToPx()
        val bottom = 28.dp.roundToPx()
        val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + 2 * side, maxWidth = constraints.maxWidth + 2 * side))
        layout(constraints.maxWidth, placeable.height - bottom) { placeable.place(-side, 0) }
    }) {
        Image(painterResource(R.drawable.near_mascot_head), null,
            Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(176.dp, 128.dp))
        Column(Modifier.padding(top = 116.dp).fillMaxWidth().heightIn(min = panelMin)
            .background(LoginPanel, LedgeShape).padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        Image(painterResource(R.drawable.near_mascot_paws), null,
            Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(176.dp, 128.dp))
    }
}

@Composable
private fun CompleteProfile(state: AccountState, actions: AccountActions) {
    PageHeading("Un gusto conocerte", "Completa tu perfil", "Elige el nombre que verá tu gente cuando conecten contigo.")
    Panel {
        SectionHeading(Icons.Default.PersonOutline, "¿Cómo te llamas?")
        ProfileNameForm(state, actions, completing = true)
        TextButton(onClick = actions::logout, enabled = !state.busy) { Text("Cerrar sesión") }
    }
}

@Composable
private fun ProfileNameForm(state: AccountState, actions: AccountActions, completing: Boolean = false, onSubmit: () -> Unit = {}) {
    val profile = state.profile ?: return
    var name by rememberSaveable(profile.id, profile.displayName) { mutableStateOf(if (profile.needsName) "" else profile.displayName) }
    var username by rememberSaveable(profile.id, profile.username) { mutableStateOf(profile.username.orEmpty()) }
    val normalized = Username.normalize(username)
    val trimmed = name.trim()
    val valid = trimmed.length in 1..80 && !trimmed.equals("Mi cuenta", ignoreCase = true) && Username.isValid(username)
    Field(name, { name = it.take(80) }, "Nombre visible", keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
    Field(username, { username = it.take(24) }, "Nombre de usuario", keyboard = KeyboardOptions(autoCorrectEnabled = false))
    Text("Para entrar: de 3 a 24 letras sin tildes, números o guion bajo. No distingue mayúsculas.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
    Button(onClick = { onSubmit(); actions.saveProfile(trimmed, normalized) }, modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy && valid && (completing || trimmed != profile.displayName || normalized != profile.username)) {
        Text(if (state.busy) "Guardando…" else if (completing) "Guardar y continuar" else "Guardar perfil")
    }
}

@Composable
private fun profileClock(zone: ZoneId): ZonedDateTime {
    var now by remember(zone) { mutableStateOf(ZonedDateTime.now(zone)) }
    LaunchedEffect(zone) { while (true) { now = ZonedDateTime.now(zone); delay(15_000) } }
    return now
}

@Composable
private fun contactStatus(person: AccountProfile, state: AccountState,
    schedules: List<ScheduleDto> = state.personSchedules, statuses: List<StatusOverrideDto> = state.personStatuses): UserCurrentStatus {
    val visibility = state.incomingVisibility[person.id]
        ?: return UserCurrentStatus.Error("Datos desactualizados. Actualiza para ver qué comparte.", person.displayName)
    if (!visibility.sharesSchedule && !visibility.sharesStatus) {
        return UserCurrentStatus.Error("Su horario y su estado son privados.", person.displayName)
    }
    if ((visibility.sharesSchedule && !state.peopleSchedulesFresh) ||
        (visibility.sharesStatus && !state.peopleStatusesFresh)) {
        return UserCurrentStatus.Error("Datos desactualizados. Actualiza para ver su actividad.", person.displayName)
    }
    val now = profileClock(person.zone())
    val status = StatusRepository.calculateStatus(person.displayName,
        schedules.takeIf { visibility.sharesSchedule }.orEmpty(),
        statuses.takeIf { visibility.sharesStatus }.orEmpty(), now.dayOfWeek.value, now)
    return if (!visibility.sharesSchedule && status is UserCurrentStatus.FreeTime) {
        UserCurrentStatus.Error("Horario privado. No hay un estado compartido en este momento.", person.displayName)
    } else status
}

@Composable
private fun MyDay(state: AccountState, actions: AccountActions, onOpenSchedule: () -> Unit) {
    var editStatus by rememberSaveable { mutableStateOf(false) }
    val profile = state.profile
    val now = profileClock(profile?.zone() ?: ZoneId.systemDefault())
    val status = profile?.let { StatusRepository.calculateStatus(it.displayName, state.schedules, state.statuses, now.dayOfWeek.value, now) }
    val today = state.schedules.occurrencesOn(now.toLocalDate(), now.zone)
    val next = today.firstOrNull { it.window.start.isAfter(now) }
    // Only your own scheduled activity can be ended; a shared status is changed with its own buttons.
    val inProgress = (status as? UserCurrentStatus.InClass)?.let { current ->
        today.firstOrNull { it.schedule.id == current.currentSchedule.id && !now.isBefore(it.window.start) && now.isBefore(it.window.end) }
    }?.takeIf { com.example.findme.domain.model.EndEarly.change(it, now) != null }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    PageHeading("", "Hoy", now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es")))
        .replaceFirstChar { it.titlecase() })
    status?.let { current ->
        val title = when (current) {
            is UserCurrentStatus.FreeTime -> "Estoy libre"
            is UserCurrentStatus.InClass -> current.currentSchedule.title
            is UserCurrentStatus.Override -> current.customLocation
            is UserCurrentStatus.Error -> "Estado no disponible"
            is UserCurrentStatus.Loading -> "Consultando actividad…"
        }
        // The span of what is shown now, for the bar under the title: the activity, or the shared status
        // from the moment it was shared until it ends.
        val span: Pair<ZonedDateTime, ZonedDateTime>? = when (current) {
            is UserCurrentStatus.InClass -> today.firstOrNull { it.schedule.id == current.currentSchedule.id &&
                !now.isBefore(it.window.start) && now.isBefore(it.window.end) }?.let { it.window.start to it.window.end }
            is UserCurrentStatus.Override -> state.statuses.firstOrNull { !it.isPlace && it.isActive && it.customLocation == current.customLocation }
                ?.let { dto ->
                    val start = dto.createdAt?.let(StatusRepository::parseIsoToInstant)?.atZone(now.zone)
                    val end = StatusRepository.parseIsoToInstant(current.expiresAt)?.atZone(now.zone)
                    if (start != null && end != null && start.isBefore(end)) start to end else null
                }
            else -> null
        }
        val remaining = span?.let { java.time.Duration.between(now, it.second).toMinutes().coerceAtLeast(0) }
        val detail = when (current) {
            is UserCurrentStatus.FreeTime -> next?.let { "Hasta las ${it.window.start.format(hourFormat)}" }
                ?: "Sin más actividades hoy"
            is UserCurrentStatus.InClass -> listOf(current.currentSchedule.location,
                "Termina en ${formatDurationMinutes(current.remainingMinutes)}").filter { it.isNotBlank() }.joinToString(" · ")
            is UserCurrentStatus.Override -> listOfNotNull(
                current.customMessage?.takeIf { it.isNotBlank() },
                StatusRepository.parseIsoToInstant(current.expiresAt)?.atZone(now.zone)?.let { "Hasta las ${it.format(hourFormat)}" },
                remaining?.let { "${compactDuration(it)} restantes" }
            ).joinToString(" · ")
            is UserCurrentStatus.Error -> current.message
            is UserCurrentStatus.Loading -> ""
        }.let { text -> listOfNotNull(current.place, text.takeIf { it.isNotBlank() }).joinToString(" · ") }
        HeroPanel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Eyebrow("Estado actual")
                    Text(title, style = MaterialTheme.typography.headlineMedium)
                    if (detail.isNotBlank()) Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                StatusOrb(statusIcon(current))
            }
            span?.let { (start, end) ->
                val total = java.time.Duration.between(start, end).toMillis().toFloat()
                val progress = (java.time.Duration.between(start, now).toMillis() / total).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = MaterialTheme.colorScheme.primary, trackColor = TextPrimary.copy(alpha = .12f), gapSize = 0.dp,
                        drawStopIndicator = {})
                    Row {
                        Text(start.format(hourFormat), Modifier.weight(1f), style = MonoLabel, color = TextSecondary)
                        Text(end.format(hourFormat), style = MonoLabel, color = TextSecondary)
                    }
                }
            }
            Button(onClick = { editStatus = true }, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
                Text(if (current is UserCurrentStatus.Override) "Cambiar mi estado" else "Compartir mi estado", fontWeight = FontWeight.Bold)
            }
            if (current is UserCurrentStatus.Override) TextButton(onClick = actions::clearStatus, enabled = !state.busy) {
                Text("Volver al horario", fontWeight = FontWeight.Bold)
            }
            inProgress?.let { occurrence ->
                OutlinedButton(onClick = { confirmEnd = true }, enabled = !state.busy) {
                    Icon(Icons.Default.StopCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Terminar ahora")
                }
                if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false }, shape = RoundedCornerShape(28.dp), containerColor = CardBg,
                    title = { Text("¿Terminó ${occurrence.schedule.title}?") },
                    text = { Text("Solo cambia la de hoy: termina a las ${now.format(hourFormat)} y tu gente te verá libre. " +
                        "Puedes restaurarla desde «Solo esta fecha».", color = TextSecondary) },
                    confirmButton = { Button(onClick = {
                        confirmEnd = false
                        actions.endActivityNow(occurrence.schedule.id, occurrence.originalDate.toString())
                    }) { Text("Terminar") } },
                    dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Cancelar") } })
            }
        }
    }
    val pause = state.pauses.covering(profile?.id, now.toLocalDate())
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Mi día", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        if (pause == null && today.isNotEmpty()) freeWindow(today.map { it.window.start to it.window.end }, now)?.let { label ->
            StateChip(label, FreeInk, FreeSurface, dot = false)
        }
    }
    if (today.isEmpty()) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(R.drawable.near_mascot_head), null, modifier = Modifier.size(74.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(pause?.title ?: "Un día a tu ritmo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (pause != null) "Tu horario está en pausa hasta el ${pause.endLabel()}" else "Nada más por hoy",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            TextButton(onClick = onOpenSchedule) { Text("Ver horario") }
        }
    } else {
        pause?.let { StatusPill("${it.title} · horario en pausa", BrandSky) }
        // «Ahora» goes before the first activity still to start, or after the last one.
        val nowIndex = today.indexOfFirst { it.window.start.isAfter(now) }.let { if (it < 0) today.size else it }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            today.forEachIndexed { index, occurrence ->
                if (index == nowIndex) NowMarker(now)
                ActivityTimelineCard(occurrence.schedule,
                    active = status is UserCurrentStatus.InClass && !now.isBefore(occurrence.window.start) &&
                        now.isBefore(occurrence.window.end) && status.currentSchedule.id == occurrence.schedule.id,
                    last = index == today.lastIndex, displayDay = now.dayOfWeek.value, compact = true)
            }
            if (nowIndex == today.size && now.toLocalDate() == today.last().window.end.toLocalDate()) NowMarker(now)
        }
        TextButton(onClick = onOpenSchedule) { Text("Ver horario") }
    }
    if (editStatus) StatusEditor(state, actions, status as? UserCurrentStatus.Override) { editStatus = false }
}

/** The lit card at the top of Hoy and of a contact: a violet gradient with its own border. */
@Composable
private fun HeroPanel(content: @Composable ColumnScope.() -> Unit) {
    NearCard(shape = RoundedCornerShape(24.dp), color = HeroGradient.last(), border = HeroBorder, padding = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(HeroGradient)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** The icon of the current status inside two rings. */
@Composable
private fun StatusOrb(icon: ImageVector) {
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.size(64.dp).border(1.5.dp, primary.copy(alpha = .45f), CircleShape).padding(7.dp)
        .background(primary.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(34.dp).background(primary.copy(alpha = .9f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun statusIcon(status: UserCurrentStatus): ImageVector = when (status) {
    is UserCurrentStatus.InClass -> ActivityCategory.fromKey(status.currentSchedule.category, status.currentSchedule.title).icon
    is UserCurrentStatus.Override -> status.customLocation.lowercase().let { text ->
        when {
            "casa" in text && "camino" !in text -> Icons.Default.Home
            "camino" in text || "calle" in text -> Icons.Default.DirectionsWalk
            "ocupad" in text -> Icons.Default.DoNotDisturbOn
            "disponible" in text || "libre" in text -> Icons.Default.WbSunny
            else -> Icons.Default.Place
        }
    }
    is UserCurrentStatus.FreeTime -> Icons.Default.WbSunny
    else -> Icons.Default.Info
}

/** «1h 45m», «35m». */
private fun compactDuration(minutes: Long): String =
    if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m".removeSuffix(" 0m") else "${minutes}m"

/**
 * The free stretch of the day that is going on now, or the next one: «Libre 10:35 – 16:10»,
 * «Libre hasta 08:00» before the first activity and «Libre desde 21:35» after the last.
 */
internal fun freeWindow(busy: List<Pair<ZonedDateTime, ZonedDateTime>>, now: ZonedDateTime): String? {
    val dayStart = now.toLocalDate().atStartOfDay(now.zone)
    val gaps = mutableListOf<Pair<ZonedDateTime, ZonedDateTime?>>()
    var cursor = dayStart
    busy.sortedBy { it.first }.forEach { (start, end) ->
        if (start.isAfter(cursor)) gaps += cursor to start
        if (end.isAfter(cursor)) cursor = end
    }
    if (cursor.toLocalDate() == now.toLocalDate()) gaps += cursor to null
    val (start, end) = gaps.firstOrNull { (_, end) -> end == null || end.isAfter(now) } ?: return null
    val format = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    return when {
        end == null -> "Libre desde ${start.format(format)}"
        start == dayStart -> "Libre hasta ${end.format(format)}"
        else -> "Libre ${start.format(format)} – ${end.format(format)}"
    }
}

/**
 * Someone's day at a glance, from 08:00 to 22:00 in two-hour blocks: rose while busy, green while free,
 * dimmed once past.
 */
@Composable
private fun DayStrip(schedules: List<ScheduleDto>, zone: ZoneId) {
    val now = profileClock(zone)
    val date = now.toLocalDate()
    val busy = remember(schedules, date, zone) { schedules.occurrencesOn(date, zone).map { it.window } }
    val free = BrandSky
    val description = remember(busy, now.hour) {
        val left = (8 until 22 step 2).count { hour -> date.atTime(hour + 2, 0).atZone(zone).isAfter(now) &&
            busy.none { it.start.isBefore(date.atTime(hour + 2, 0).atZone(zone)) && it.end.isAfter(date.atTime(hour, 0).atZone(zone)) } }
        "Hoy le quedan $left bloques libres de dos horas"
    }
    Row(Modifier.fillMaxWidth().semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        (8 until 22 step 2).forEach { hour ->
            val start = date.atTime(hour, 0).atZone(zone)
            val end = start.plusHours(2)
            val taken = busy.any { it.start.isBefore(end) && it.end.isAfter(start) }
            val color = when {
                !end.isAfter(now) -> CardBorder
                taken -> BusyInk
                else -> free
            }
            Box(Modifier.weight(1f).height(4.dp).background(color, CircleShape))
        }
    }
}

/** The line of the present moment across the day. */
@Composable
private fun NowMarker(now: ZonedDateTime) {
    val primary = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(now.format(hourFormat), Modifier.width(48.dp), style = MonoLabel, color = primary, textAlign = TextAlign.Center)
        Box(Modifier.size(7.dp).background(primary, CircleShape))
        Text("Ahora", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = primary)
        Box(Modifier.weight(1f).height(1.dp).background(primary.copy(alpha = .35f)))
    }
}

@Composable
private fun StatusEditor(state: AccountState, actions: AccountActions, current: UserCurrentStatus.Override?, dismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current?.customLocation.orEmpty()) }
    var minutes by rememberSaveable { mutableStateOf(60L) }
    // -1 means "until a chosen time"; the length is computed when sharing, from the current time.
    val zone = state.profile?.zone() ?: ZoneId.systemDefault()
    var until by rememberSaveable { mutableStateOf(StatusUntil.suggestion(ZonedDateTime.now(zone)).format(hourFormat)) }
    val untilMinutes = StatusRepository.parseLocalTimeOrNull(until)?.let { StatusUntil.minutes(it, ZonedDateTime.now(zone)) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(state.notice, state.busy) {
        if (submitted && !state.busy && state.notice in listOf("Estado actualizado.", "Se muestra tu horario de nuevo.")) dismiss()
    }
    Sheet(dismiss, "Tu estado, ahora", "Lo verán las personas conectadas contigo.") {
        Choices(listOf("En camino a casa", "En casa", "Ocupado", "Disponible"), text, { text = it }) { it }
        Field(text, { text = it.take(120) }, "¿Qué estás haciendo?", placeholder = "Escribe tu propio estado")
        Text("¿Durante cuánto tiempo?", style = MaterialTheme.typography.titleSmall)
        Choices(listOf(30L, 60L, 120L, 240L, -1L), minutes, { minutes = it }) { when { it < 0 -> "Hasta…"; it < 60 -> "$it min"; else -> "${it / 60} h" } }
        if (minutes < 0) {
            TimeField(until, { until = it }, "Hasta las")
            untilMinutes?.let {
                Text("Durará ${StatusUntil.lengthLabel(it)}" + if (it >= 1440) ", el máximo para un estado." else ".",
                    color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Al terminar se mostrará de nuevo tu horario. Puedes cambiarlo cuando quieras.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        if (submitted) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            submitted = true
            // Measured now, not when the time was picked, so it ends exactly at the chosen time.
            val length = if (minutes < 0) StatusRepository.parseLocalTimeOrNull(until)?.let { StatusUntil.minutes(it, ZonedDateTime.now(zone)) } else minutes
            if (length != null) actions.setStatus(text, length)
        }, enabled = !state.busy && text.isNotBlank() && (minutes > 0 || untilMinutes != null), modifier = Modifier.fillMaxWidth()) { Text("Compartir estado") }
        if (current != null) TextButton(onClick = { submitted = true; actions.clearStatus() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Quitar estado y volver al horario") }
    }
}

@Composable
private fun ScheduleManager(state: AccountState, actions: AccountActions) {
    var editing by remember { mutableStateOf<ScheduleDto?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ScheduleDto?>(null) }
    val zone = state.profile?.zone() ?: ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    var day by rememberSaveable { mutableStateOf(today.dayOfWeek.value) }
    var oneOff by remember { mutableStateOf<Pair<String, LocalDate>?>(null) }
    val dates = (0L..6L).map(monday::plusDays)
    val week = remember(state.schedules, monday, zone) {
        dates.map { state.schedules.occurrencesOn(it, zone) }
    }
    val date = dates[day - 1]
    PageHeading("", "Horario", "")
    WeeklyScheduleHeader(dates, day, onDaySelected = { day = it }, activities = week.map { it.size })
    val occurrences = week[day - 1]
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(date.longDate().replaceFirstChar { it.titlecase() }, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        val busyMinutes = occurrences.sumOf { occurrence ->
            java.time.Duration.between(maxOf(date.atStartOfDay(zone), occurrence.window.start),
                minOf(date.plusDays(1).atStartOfDay(zone), occurrence.window.end)).toMinutes().coerceAtLeast(0)
        }
        if (busyMinutes > 0) {
            val classes = occurrences.all { ActivityCategory.fromKey(it.schedule.category, it.schedule.title) == ActivityCategory.UNIVERSITY }
            Text("${compactDuration(busyMinutes)} de ${if (classes) "clase" else "actividad"}", style = MonoLabel, color = TextSecondary)
        }
    }
    val exceptions = state.schedules.mapNotNull { schedule ->
        schedule.occurrenceExceptions[date.toString()]?.let { schedule to it }
    }.filter { (_, exception) -> !exception.paused && (exception.cancelled || exception.date != date.toString()) }
    val pause = state.pauses.covering(state.profile?.id, date)
    var pausing by rememberSaveable { mutableStateOf(false) }
    var pendingPauseDelete by remember { mutableStateOf<SchedulePause?>(null) }
    pause?.let { paused ->
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentIcon(Icons.Default.BeachAccess, BrandSky)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(paused.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Horario en pausa · ${paused.rangeLabel()}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Tus actividades semanales no aparecen estos días. Las que reprogramaste para una fecha concreta, sí.",
                color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (occurrences.isEmpty() && exceptions.isEmpty() && pause == null) {
        EmptyPanel(Icons.Default.EventAvailable, "Espacio para tus planes", "Añade una clase, tu trabajo o una actividad personal.")
    }
    val dayStart = date.atStartOfDay(zone)
    val dayEnd = date.plusDays(1).atStartOfDay(zone)
    var occupiedUntil: ZonedDateTime? = null
    occurrences.forEach { occurrence ->
        val start = maxOf(dayStart, occurrence.window.start)
        val end = minOf(dayEnd, occurrence.window.end)
        occupiedUntil?.takeIf { it.isBefore(start) }?.let { gapStart ->
            ScheduleTimeRow(gapStart.format(hourFormat)) {
                Surface(color = FreeSurface, contentColor = FreeInk, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(7.dp).background(FreeInk, CircleShape))
                        Text("Tiempo libre · hasta ${start.format(hourFormat)}", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(compactDuration(java.time.Duration.between(gapStart, start).toMinutes()), style = MonoLabel)
                    }
                }
            }
        }
        occupiedUntil = maxOf(occupiedUntil ?: end, end)
        val schedule = occurrence.schedule
        val recurring = state.schedules.first { it.id == schedule.id }
        val category = ActivityCategory.fromKey(schedule.category)
        val reprogrammed = recurring.occurrenceExceptions.containsKey(occurrence.originalDate.toString())
        ScheduleTimeRow(if (occurrence.window.start.isBefore(dayStart)) "Desde ayer" else start.format(hourFormat)) {
            NearCard(padding = PaddingValues(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 14.dp), spacing = 4.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(category.icon, null, Modifier.size(18.dp), tint = category.accentColor)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(schedule.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            schedule.sourceOwnerId?.let { sourceOwner ->
                                val sourceName = state.profiles.firstOrNull { it.id == sourceOwner }?.displayName
                                Text(
                                    if (sourceName == null) "Horario sincronizado" else "Horario de $sourceName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                if (schedule.followedCustomized) {
                                    Text("Con ajustes personales", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                        }
                        var menuOpen by remember(occurrence.originalDate, schedule.id) { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }, enabled = !state.busy) {
                                Icon(Icons.Default.MoreVert, "Opciones de ${schedule.title}")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (!recurring.isOneOff) DropdownMenuItem(text = { Text("Solo esta fecha") }, onClick = {
                                    menuOpen = false
                                    oneOff = recurring.id to occurrence.originalDate
                                })
                                DropdownMenuItem(text = { Text(if (recurring.isOneOff) "Editar" else "Serie semanal") }, onClick = {
                                    menuOpen = false
                                    editing = recurring
                                    showEditor = true
                                })
                                DropdownMenuItem(text = { Text(when {
                                    recurring.sourceId != null -> "Dejar de seguir esta actividad"
                                    recurring.isOneOff -> "Eliminar"
                                    else -> "Eliminar serie"
                                }) },
                                    onClick = { menuOpen = false; pendingDelete = recurring })
                            }
                        }
                    }
                    Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(schedule.timeRangeLabel(day), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        val activityDetails = listOf(category.label, schedule.modality, schedule.location)
                            .filter { it.isNotBlank() }.joinToString(" · ")
                        Text(activityDetails, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        if (reprogrammed || schedule.isOneOff) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (reprogrammed) StateChip("Reprogramada", FreeInk, FreeSurface, dot = false)
                            if (schedule.isOneOff) StateChip("Solo el ${occurrence.originalDate.longDate()}", FreeInk, FreeSurface, dot = false)
                        }
                    }
            }
        }
    }
    exceptions.forEach { (schedule, exception) ->
        Panel {
            Text(schedule.title, fontWeight = FontWeight.Bold)
            val source = state.profiles.firstOrNull { it.id == schedule.sourceOwnerId }
            if (source != null) {
                Text("Horario de ${source.displayName}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            StatusPill(if (exception.cancelled) "Cancelada" else "Reprogramada",
                if (exception.cancelled) MaterialTheme.colorScheme.error else BrandSky)
            exception.date?.takeUnless { exception.cancelled }?.let {
                Text("Pasa al ${LocalDate.parse(it).longDate()}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            CardAction(Icons.Default.EventRepeat, "Solo esta fecha", enabled = !state.busy) { oneOff = schedule.id to date }
        }
    }
    Button(onClick = { editing = null; showEditor = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Añadir actividad")
    }
    SchedulePausesPanel(state, today, onAdd = { pausing = true }, onDelete = { pendingPauseDelete = it })
    CalendarImportPanel(state, actions, zone, today)
    oneOff?.let { (id, original) ->
        state.schedules.find { it.id == id }?.let { schedule ->
            ScheduleOccurrenceEditor(schedule, original, today, state, actions) { oneOff = null }
        }
    }
    if (pausing) PauseEditor(state, actions, today) { pausing = false }
    pendingPauseDelete?.let { paused -> Confirm("¿Quitar la pausa «${paused.title}»?", "Tu horario semanal volverá a aplicarse del ${paused.rangeLabel()}.",
        { pendingPauseDelete = null }, { actions.deletePause(paused.id); pendingPauseDelete = null }) }
    if (showEditor) ScheduleEditor(editing, day, date, today, state, actions) { showEditor = false }
    pendingDelete?.let { schedule ->
        if (schedule.sourceId != null) Confirm("¿Dejar de seguir ${schedule.title}?", "Se quitará de tu horario. Puedes volver a elegirla desde la ficha del contacto.",
            { pendingDelete = null }, { actions.deleteSchedule(schedule.id); pendingDelete = null })
        else if (schedule.isOneOff) Confirm("¿Eliminar ${schedule.title}?", "Solo ocurre una vez; desaparecerá de tu horario.",
            { pendingDelete = null }, { actions.deleteSchedule(schedule.id); pendingDelete = null })
        else Confirm("¿Eliminar la serie de ${schedule.title}?", "Se eliminarán todas las repeticiones y sus cambios puntuales.",
            { pendingDelete = null }, { actions.deleteSchedule(schedule.id); pendingDelete = null })
    }
}

@Composable
private fun ScheduleTimeRow(time: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(time, Modifier.width(50.dp).padding(top = 14.dp), color = TextSecondary, style = MonoLabel)
        Box(Modifier.weight(1f)) { content() }
    }
}

private fun LocalDate.longDate(): String =
    format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es")))

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))
private fun SchedulePause.endLabel(): String = range()?.second?.format(shortDate) ?: endDate
private fun SchedulePause.rangeLabel(): String = range()?.let { (start, end) ->
    if (start == end) start.format(shortDate) else "${start.format(shortDate)} al ${end.format(shortDate)}"
} ?: "$startDate al $endDate"

/** Opens a calendar file (.ics) and lets the person choose what to add before anything is saved. */
@Composable
private fun CalendarImportPanel(state: AccountState, actions: AccountActions, zone: ZoneId, today: LocalDate) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<com.example.findme.domain.model.IcsResult?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // Read at most one byte past the limit, so a huge file is rejected without loading it.
                        val limit = com.example.findme.domain.model.IcsImport.MAX_BYTES
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (out.size() <= limit) {
                            val read = input.read(buffer, 0, minOf(buffer.size, limit + 1 - out.size()))
                            if (read < 0) break
                            out.write(buffer, 0, read)
                        }
                        if (out.size() > limit) null else out.toString(Charsets.UTF_8.name())
                    }
                }.getOrNull()
            }
            if (text == null) { failed = "No se pudo leer el archivo. Elige un calendario .ics de menos de 1 MB."; return@launch }
            val parsed = com.example.findme.domain.model.IcsImport.parse(text, zone, today)
            if (parsed.candidates.isEmpty()) failed = "El archivo no tiene eventos con hora que Near pueda añadir."
            else { failed = null; result = parsed }
        }
    }
    Panel {
        SectionHeading(Icons.Default.UploadFile, "Importar un calendario",
            "Desde un archivo .ics de Google Calendar, Outlook o el portal de tu universidad.")
        failed?.let { FormProblem(it) }
        OutlinedButton(onClick = { open.launch(arrayOf("text/calendar", "text/x-vcalendar", "application/octet-stream", "text/plain")) },
            enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.UploadFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Elegir archivo .ics")
        }
    }
    result?.let { parsed -> CalendarImportSheet(parsed, state, actions) { result = null } }
}

@Composable
private fun CalendarImportSheet(result: com.example.findme.domain.model.IcsResult, state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    val draftId = rememberSaveable { UUID.randomUUID().toString() }
    var chosen by remember { mutableStateOf(result.candidates.map { it.uid }.toSet()) }
    var category by rememberSaveable { mutableStateOf(ActivityCategory.UNIVERSITY.key) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.busy, state.savedScheduleDraftId) {
        if (submitted && !state.busy && state.savedScheduleDraftId == draftId) dismiss()
    }
    Sheet(dismiss, "Importar ${result.candidates.size} eventos",
        if (result.skipped > 0) "${result.skipped} eventos no se pueden importar: de todo el día, ya pasados o con otras repeticiones." else null,
        gesturesEnabled = false) {
        ScheduleChoice("Tipo de actividad", ActivityCategory.entries, ActivityCategory.fromKey(category), { category = it.key },
            leading = ActivityCategory.fromKey(category).icon) { it.label }
        result.candidates.forEach { event ->
            val checked = event.uid in chosen
            Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox) { chosen = if (it) chosen + event.uid else chosen - event.uid },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Checkbox(checked, onCheckedChange = null)
                Column(Modifier.weight(1f)) {
                    Text(event.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val whenText = if (event.weekly) event.days.joinToString(", ") { days[it - 1].take(3) } + " · cada semana"
                        else event.date!!.longDate()
                    Text("$whenText · ${formatScheduleTime(event.start)}–${formatScheduleTime(event.end)}",
                        color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Las que coincidan con tu horario se omitirán. Si importas el mismo archivo otra vez, se actualizan en lugar de duplicarse.",
            color = TextMuted, style = MaterialTheme.typography.bodySmall)
        state.error?.takeIf { submitted }?.let { FormProblem(it) }
        val selected = result.candidates.filter { it.uid in chosen }
        Button(onClick = { submitted = true; actions.importSchedules(draftId, selected.flatMap { it.toSchedules(category) }) },
            enabled = !state.busy && selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Importando…" else "Importar ${selected.size} ${if (selected.size == 1) "evento" else "eventos"}")
        }
    }
}

/** Upcoming pauses, with the way to add one. Past pauses are not shown. */
@Composable
private fun SchedulePausesPanel(state: AccountState, today: LocalDate, onAdd: () -> Unit, onDelete: (SchedulePause) -> Unit) {
    val upcoming = state.pauses.filter { it.userId == state.profile?.id && it.range()?.second?.isBefore(today) == false }
        .sortedBy { it.startDate }
    NearCard(padding = PaddingValues(0.dp), spacing = 0.dp) {
        Row(Modifier.fillMaxWidth().clickable(enabled = !state.busy, onClickLabel = "Pausar mi horario", onClick = onAdd)
            .padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBubble(Icons.Default.BeachAccess)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Pausas del horario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Vacaciones, feriados o semanas sin clases.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Pausar mi horario", tint = TextSecondary)
        }
        if (upcoming.isNotEmpty()) Column(Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp)) {
          HorizontalDivider(color = CardBorder, modifier = Modifier.padding(end = 8.dp, bottom = 4.dp))
          upcoming.forEach { pause ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(pause.title, fontWeight = FontWeight.SemiBold)
                    Text(pause.rangeLabel(), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { onDelete(pause) }, enabled = !state.busy) {
                    Icon(Icons.Default.DeleteOutline, "Quitar la pausa ${pause.title}", tint = TextMuted)
                }
            }
          }
        }
    }
}

@Composable
private fun PauseEditor(state: AccountState, actions: AccountActions, today: LocalDate, dismiss: () -> Unit) {
    val draftId = rememberSaveable { UUID.randomUUID().toString() }
    var title by rememberSaveable { mutableStateOf("Vacaciones") }
    var startText by rememberSaveable { mutableStateOf<String?>(null) }
    var endText by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val start = startText?.let(LocalDate::parse)
    val end = endText?.let(LocalDate::parse)
    val problem = validatePause(title, start, end, today)
    LaunchedEffect(state.busy, state.savedScheduleDraftId) {
        if (submitted && !state.busy && state.savedScheduleDraftId == draftId) dismiss()
    }
    Sheet(dismiss, "Pausar mi horario", "Tus actividades semanales no aparecerán esos días, ni para ti ni para tu gente.", gesturesEnabled = false) {
        Choices(listOf("Vacaciones", "Feriado", "Exámenes", "Viaje"), title, { title = it }) { it }
        Field(title, { title = it.take(60) }, "Nombre de la pausa")
        SelectField("Fechas", if (start != null && end != null)
            SchedulePause(title = title, startDate = start.toString(), endDate = end.toString()).rangeLabel() else "Elige el inicio y el fin",
            onClick = { picking = true }, expanded = picking, trailing = Icons.Default.DateRange)
        (problem?.takeIf { start != null || submitted } ?: state.error?.takeIf { submitted })?.let { FormProblem(it) }
        Button(onClick = { submitted = true; actions.addPause(draftId, title, startText.orEmpty(), endText.orEmpty()) },
            enabled = !state.busy && problem == null, modifier = Modifier.fillMaxWidth()) { Text("Pausar horario") }
    }
    if (picking) PauseRangePicker(today, start, end, dismiss = { picking = false }) { from, to ->
        startText = from.toString(); endText = to.toString(); picking = false
    }
}

@Composable
private fun SingleDatePicker(selected: LocalDate, first: LocalDate, last: LocalDate, dismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    fun LocalDate.millis() = atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.date() = Instant.ofEpochMilli(this).atZone(java.time.ZoneOffset.UTC).toLocalDate()
    val picker = rememberDatePickerState(initialSelectedDateMillis = selected.millis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.date().let { !it.isBefore(first) && !it.isAfter(last) }
            override fun isSelectableYear(year: Int) = year in first.year..last.year
        })
    DatePickerDialog(onDismissRequest = dismiss, confirmButton = {
        TextButton(onClick = { picker.selectedDateMillis?.date()?.let(onPicked) }, enabled = picker.selectedDateMillis != null) { Text("Listo") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } }) {
        DatePicker(picker, showModeToggle = false)
    }
}

@Composable
private fun PauseRangePicker(today: LocalDate, start: LocalDate?, end: LocalDate?, dismiss: () -> Unit,
    onPicked: (LocalDate, LocalDate) -> Unit) {
    fun LocalDate.millis() = atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.date() = Instant.ofEpochMilli(this).atZone(java.time.ZoneOffset.UTC).toLocalDate()
    val last = today.plusDays(366)
    val picker = rememberDateRangePickerState(initialSelectedStartDateMillis = start?.millis(),
        initialSelectedEndDateMillis = end?.millis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.date().let { !it.isBefore(today) && !it.isAfter(last) }
            override fun isSelectableYear(year: Int) = year in today.year..last.year
        })
    DatePickerDialog(onDismissRequest = dismiss, confirmButton = {
        TextButton(onClick = {
            val from = picker.selectedStartDateMillis?.date()
            if (from != null) onPicked(from, picker.selectedEndDateMillis?.date() ?: from)
        }, enabled = picker.selectedStartDateMillis != null) { Text("Listo") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } }) {
        DateRangePicker(picker, Modifier.heightIn(max = 520.dp), title = {
            Text("Elige la primera y la última fecha", Modifier.padding(start = 24.dp, top = 16.dp))
        }, showModeToggle = false)
    }
}

@Composable
private fun ScheduleOccurrenceEditor(
    schedule: ScheduleDto, preferredDate: LocalDate, today: LocalDate,
    state: AccountState, actions: AccountActions, dismiss: () -> Unit
) {
    val next = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(schedule.dayOfWeek)))
    val originals = (0L..3L).map(next::plusWeeks).let { dates ->
        if (preferredDate >= today || schedule.occurrenceExceptions[preferredDate.toString()]?.date != null)
            (dates + preferredDate).distinct().sorted() else dates
    }
    var originalText by rememberSaveable { mutableStateOf(originals.firstOrNull { it == preferredDate }?.toString() ?: next.toString()) }
    val original = LocalDate.parse(originalText)
    // A date inside a pause looks cancelled on the device, but has no change of its own on the server.
    val existing = schedule.occurrenceExceptions[originalText]?.takeUnless { it.paused }
    val draftId = rememberSaveable { UUID.randomUUID().toString() }
    var submitted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.busy, state.savedScheduleDraftId) {
        if (submitted && !state.busy && state.savedScheduleDraftId == draftId) dismiss()
    }
    Sheet(dismiss, "Solo esta fecha", gesturesEnabled = false) {
        Text(schedule.title, fontWeight = FontWeight.Bold)
        ScheduleChoice("Fecha original", originals, original, { originalText = it.toString() }, leading = Icons.Default.Event) { it.longDate() }
        key(originalText) {
            var moving by rememberSaveable { mutableStateOf(existing?.date != null) }
            val moveDates = (0L..13L).map { maxOf(today, original.minusDays(6)).plusDays(it) }.let { dates ->
                (dates + listOfNotNull(existing?.date?.let(LocalDate::parse))).distinct().sorted()
            }
            var dateText by rememberSaveable { mutableStateOf(existing?.date ?: maxOf(original, today).toString()) }
            var start by rememberSaveable { mutableStateOf(existing?.startTime ?: schedule.startTime) }
            var end by rememberSaveable { mutableStateOf(existing?.endTime ?: schedule.endTime) }
            var overnight by rememberSaveable { mutableStateOf(existing?.endsNextDay ?: schedule.endsNextDay) }
            Choices(listOf(false, true), moving, { moving = it }) { if (it) "Reprogramar" else "Cancelar actividad" }
            if (moving) {
                ScheduleChoice("Nueva fecha", moveDates, LocalDate.parse(dateText), { dateText = it.toString() }, leading = Icons.Default.EventRepeat) { it.longDate() }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { TimeField(start, { start = it }, "Inicio") }
                    Box(Modifier.weight(1f)) { TimeField(end, { end = it }, "Fin") }
                }
                Row(Modifier.fillMaxWidth().toggleable(overnight, role = Role.Switch, onValueChange = { overnight = it }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Termina al día siguiente", modifier = Modifier.weight(1f))
                    Switch(checked = overnight, onCheckedChange = null, colors = nearSwitchColors())
                }
            }
            val validation = if (moving) validateSchedule(schedule.dayOfWeek, start, end, schedule.title, overnight) else null
            (validation ?: state.error?.takeIf { submitted })?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = {
                submitted = true
                val change = if (moving) ScheduleException(date = dateText, startTime = start, endTime = end, endsNextDay = overnight)
                    else ScheduleException(cancelled = true)
                actions.changeScheduleOccurrence(draftId, schedule.id, originalText, change)
            }, enabled = !state.busy && validation == null, modifier = Modifier.fillMaxWidth()) {
                Text(if (moving) "Guardar reprogramación" else "Cancelar solo esta fecha")
            }
            if (existing != null) TextButton(onClick = {
                submitted = true
                actions.changeScheduleOccurrence(draftId, schedule.id, originalText, null)
            }, enabled = !state.busy) { Text("Restaurar horario habitual") }
        }
    }
}

@Composable
private fun ScheduleEditor(existing: ScheduleDto?, initialDay: Int, initialDate: LocalDate, today: LocalDate,
    state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    val draftId = rememberSaveable { UUID.randomUUID().toString() }
    val editingId by rememberSaveable { mutableStateOf(existing?.id) }
    // A weekly series and a one-off activity cannot be turned into each other; only new activities choose.
    var once by rememberSaveable { mutableStateOf(existing?.isOneOff ?: false) }
    var onceText by rememberSaveable { mutableStateOf(existing?.oneOffDate ?: maxOf(initialDate, today).toString()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val onceDate = LocalDate.parse(onceText)
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var location by rememberSaveable { mutableStateOf(existing?.location.orEmpty()) }
    var start by rememberSaveable { mutableStateOf(existing?.startTime ?: "09:00") }
    var end by rememberSaveable { mutableStateOf(existing?.endTime ?: "10:00") }
    var endsNextDay by rememberSaveable { mutableStateOf(existing?.endsNextDay ?: false) }
    var selectedDays by rememberSaveable { mutableStateOf(listOf(existing?.dayOfWeek ?: initialDay)) }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "university") }
    var modality by rememberSaveable { mutableStateOf(existing?.modality ?: "presencial") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val activities = if (once) listOf(ScheduleDto(
        id = editingId ?: UUID.nameUUIDFromBytes("$draftId:once".toByteArray(Charsets.UTF_8)).toString(),
        dayOfWeek = onceDate.dayOfWeek.value, startTime = start.trim(), endTime = end.trim(),
        title = title.trim(), location = location.trim(), category = category, modality = modality,
        endsNextDay = endsNextDay, oneOffDate = onceText))
    else selectedDays.map { day ->
        val id = editingId ?: UUID.nameUUIDFromBytes("$draftId:$day".toByteArray(Charsets.UTF_8)).toString()
        ScheduleDto(id = id, dayOfWeek = day, startTime = start.trim(), endTime = end.trim(),
            title = title.trim(), location = location.trim(), category = category, modality = modality,
            endsNextDay = endsNextDay)
    }
    // Problems are only reported after a save attempt, so editing never flashes errors mid-way.
    val problem = (if (once) validateOneOffDate(onceDate, today) else if (selectedDays.isEmpty()) "Elige al menos un día." else null)
        ?: validateSchedule(activities.first().dayOfWeek, start, end, title, endsNextDay)
        ?: activities.firstNotNullOfOrNull { findScheduleConflict(state.schedules, it) }?.conflictMessage()
    var showProblem by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(title, start, end, endsNextDay, selectedDays, category, modality, once, onceText) { showProblem = false }
    LaunchedEffect(state.busy, state.savedScheduleDraftId) {
        if (submitted && !state.busy && state.savedScheduleDraftId == draftId) dismiss()
    }
    Sheet(dismiss, when { editingId == null -> "Nueva actividad"; once -> "Editar actividad"; else -> "Editar serie semanal" }, gesturesEnabled = false) {
        Field(title, { title = it.take(120) }, "Título", placeholder = if (once) "Ej. Cita médica" else "Ej. Clase de inglés")
        if (editingId == null) Choices(listOf(false, true), once, { once = it }) { if (it) "Una sola fecha" else "Cada semana" }
        when {
            once && existing?.sourceId != null -> Text(onceDate.longDate(), style = MaterialTheme.typography.bodyMedium)
            once -> SelectField("Fecha", onceDate.longDate().replaceFirstChar(Char::titlecase), onClick = { pickingDate = true },
                expanded = pickingDate, trailing = Icons.Default.Event)
            editingId == null -> ScheduleDays(selectedDays, { selectedDays = it })
            else -> ScheduleChoice("Día", (1..7).toList(), selectedDays.single(), { selectedDays = listOf(it) }) { days[it - 1] }
        }
        if (pickingDate) SingleDatePicker(onceDate, today, today.plusDays(366), dismiss = { pickingDate = false }) {
            onceText = it.toString(); pickingDate = false
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScheduleChoice("Tipo", ActivityCategory.entries, ActivityCategory.fromKey(category),
                { category = it.key }, Modifier.weight(1f), leading = ActivityCategory.fromKey(category).icon) { it.label }
            ScheduleChoice("Modalidad", listOf("presencial", "virtual"), modality, { modality = it }, Modifier.weight(1f),
                leading = if (modality == "virtual") Icons.Default.Videocam else Icons.Default.LocationOn) {
                it.replaceFirstChar(Char::titlecase)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { TimeField(start, { newStart ->
                // Keep the activity's length so changing the start rarely needs a second edit.
                shiftScheduleEnd(start, end, endsNextDay, newStart)?.let { (newEnd, nextDay) ->
                    end = newEnd; endsNextDay = nextDay
                }
                start = newStart
            }, "Inicio") }
            Box(Modifier.weight(1f)) { TimeField(end, { end = it }, "Fin") }
        }
        Row(
            Modifier.fillMaxWidth().toggleable(value = endsNextDay, role = Role.Switch, enabled = !state.busy) {
                endsNextDay = it
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Termina al día siguiente", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = endsNextDay, onCheckedChange = null, colors = nearSwitchColors())
        }
        Field(location, { location = it.take(160) }, "Lugar o aula (opcional)")
        val feedback = problem?.takeIf { showProblem } ?: state.error?.takeIf { submitted }
        feedback?.let { FormProblem(it) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = dismiss) { Text("Cancelar") }
            Button(onClick = {
                if (problem != null) {
                    showProblem = true
                } else {
                    submitted = true
                    actions.saveSchedules(draftId, activities)
                }
            }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Guardar actividad") }
        }
    }
}

/** One tap per day: all seven days stay visible instead of hiding behind a menu. */
@Composable
private fun ScheduleDays(chosen: List<Int>, onSelect: (List<Int>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Días", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEachIndexed { index, name ->
                val day = index + 1
                val checked = day in chosen
                val primary = MaterialTheme.colorScheme.primary
                Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (checked) primary else Color.Transparent)
                    .border(1.dp, if (checked) primary else CardBorder, RoundedCornerShape(12.dp))
                    .selectable(checked, role = Role.Checkbox) {
                        onSelect(if (checked) chosen - day else (chosen + day).sorted())
                    }
                    .semantics { contentDescription = name },
                    contentAlignment = Alignment.Center) {
                    Text(dayLetters[index], fontWeight = FontWeight.SemiBold,
                        color = if (checked) MaterialTheme.colorScheme.onPrimary else TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun <T> ScheduleChoice(
    label: String,
    choices: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    name: (T) -> String
) {
    SelectMenuField(label, name(selected), modifier, leading = leading) { close ->
        choices.forEach { choice ->
            SelectMenuItem(name(choice), choice == selected, onClick = { onSelect(choice); close() })
        }
    }
}

/** A single, calm notice shown next to the save action instead of live red text. */
@Composable
private fun FormProblem(message: String) {
    val error = MaterialTheme.colorScheme.error
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(error.copy(alpha = .08f))
        .semantics { liveRegion = LiveRegionMode.Polite }
        .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = error)
        Text(message, color = error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TimeField(value: String, change: (String) -> Unit, label: String) {
    var picking by rememberSaveable { mutableStateOf(false) }
    SelectField(label, formatScheduleTime(value), onClick = { picking = true }, expanded = picking,
        trailing = Icons.Default.Schedule)
    if (picking) {
        val initial = StatusRepository.parseLocalTimeOrNull(value) ?: LocalTime.of(9, 0)
        ScheduleTimePicker("Hora de ${label.lowercase()}", initial, dismiss = { picking = false }) { time ->
            change(time.format(hourFormat))
            picking = false
        }
    }
}

@Composable
private fun CommonAvailabilitySheet(person: AccountProfile, actions: AccountActions, onPropose: (FreeSlot) -> Unit, dismiss: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var schedules by remember { mutableStateOf<SharedSchedules?>(null) }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    var allHours by rememberSaveable { mutableStateOf(false) }
    var asOf by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(lifecycle, person.id, retry) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                loading = true
                schedules = null
                error = false
                try {
                    schedules = actions.sharedSchedulesForAvailability(person.id)
                    asOf = Instant.now()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = true
                } finally {
                    loading = false
                }
                delay(60_000)
            }
        }
    }

    Sheet(dismiss, "Horas libres con ${person.displayName}") {
        val pair = schedules
        when {
            loading -> Text("Consultando ambos horarios…", color = TextSecondary)
            error -> {
                Text("No pudimos consultar la disponibilidad.", color = TextSecondary)
                TextButton(onClick = { retry++ }) { Text("Reintentar") }
            }
            pair == null -> Text("Ambas personas deben compartir su horario en esta conexión.", color = TextSecondary)
            else -> {
                Text("Próximos 7 días. Huecos de 30 minutos o más, en tu zona horaria. Se calculan a partir de sus actividades programadas. " +
                    "Toca un hueco para proponer un plan.",
                    color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Choices(listOf(false, true), allHours, { allHours = it }) { if (it) "Todo el día" else "8:00–22:00" }
                val freeDays = remember(pair, allHours, asOf) { commonFreeTime(pair, asOf, allHours) }
                freeDays.forEach { day ->
                    Surface(shape = RoundedCornerShape(18.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(day.date.longDate().replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (day.slots.isNotEmpty()) Text("${day.slots.size} ${if (day.slots.size == 1) "hueco" else "huecos"}",
                                    color = TextMuted, style = MaterialTheme.typography.labelMedium)
                            }
                            if (day.slots.isEmpty()) Text("Sin huecos de 30 min", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                day.slots.forEach { slot ->
                                    val end = if (slot.end.toLocalDate() > day.date) "24:00" else slot.end.format(hourFormat)
                                    SuggestionChip(onClick = { onPropose(slot) }, label = { Text("${slot.start.format(hourFormat)} – $end") },
                                        icon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(labelColor = BrandSky, iconContentColor = BrandSky),
                                        border = BorderStroke(1.dp, BrandSky.copy(alpha = .45f)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun People(state: AccountState, actions: AccountActions) {
    var connect by rememberSaveable { mutableStateOf(false) }
    var manageMessages by rememberSaveable { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf<PersonConnection?>(null) }
    var manageConnection by remember { mutableStateOf<PersonConnection?>(null) }
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var groupEditor by rememberSaveable { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<PersonGroup?>(null) }
    if (selectedGroup != null && state.groups.none { it.id == selectedGroup }) selectedGroup = null
    val controls = state.controls
    PageHeading("", "Personas", "", trailing = state.lastSync?.let { updated ->
        "Actualizado ${updated.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}"
    })
    val people = state.connections.mapNotNull { connection ->
        state.profiles.find { it.id == connection.otherPerson(state.profile?.id.orEmpty()) }
    }.sortedBy { controls.nameOf(it).lowercase() }
    val pendingFollowers = state.scheduleFollowRequests.orEmpty()
        .filter { it.leaderId == state.profile?.id && !it.approved }
        .map { it.followerId }.toSet()
    people.filter { it.id in pendingFollowers }.forEach { requester ->
        OutlinedButton(
            onClick = { selectedGroup = null; actions.selectPerson(requester) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Solicitud de horario de ${controls.nameOf(requester)}") }
    }
    if (state.connections.isEmpty()) {
        EmptyPanel(Icons.Default.PeopleOutline, "Lo cotidiano se comparte", "Conecta con una persona para ver sus estados y horarios. Tú decides con quién.")
    } else {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Todos", selectedGroup == null, onClick = { selectedGroup = null }, enabled = !state.busy)
            state.groups.sortedBy { it.name.lowercase() }.forEach { group ->
                ChoiceChip(group.name, selectedGroup == group.id, onClick = { selectedGroup = group.id }, enabled = !state.busy)
            }
            ChoiceChip("Grupo", false, onClick = { editingGroup = null; groupEditor = true }, enabled = !state.busy,
                leading = { Icon(Icons.Default.GroupAdd, null, Modifier.size(18.dp)) })
        }
        if (selectedGroup == null) NearCard(padding = PaddingValues(0.dp), spacing = 0.dp) {
            people.forEachIndexed { index, person ->
                key(person.id) {
                    val schedules = state.othersSchedules.filter { it.userId == person.id }
                    val status = if (state.selectedPerson?.id == person.id) contactStatus(person, state)
                    else contactStatus(person, state, schedules, state.othersStatuses.filter { it.userId == person.id })
                    val (shown, ink, background) = when (status) {
                        is UserCurrentStatus.FreeTime -> Triple("Libre ahora", FreeInk, FreeSurface)
                        is UserCurrentStatus.InClass -> Triple("Ocupado · ${ActivityCategory.fromKey(status.currentSchedule.category,
                            status.currentSchedule.title).label}", BusyInk, BusySurface)
                        is UserCurrentStatus.Override -> Triple(status.customLocation, MaterialTheme.colorScheme.primary, AccentContainer)
                        is UserCurrentStatus.Error -> Triple(status.message, TextSecondary, InnerBg)
                        is UserCurrentStatus.Loading -> Triple("Consultando…", TextSecondary, InnerBg)
                    }
                    val label = listOfNotNull(shown, status.place).joinToString(" · ")
                    if (index > 0) HorizontalDivider(color = CardBorder)
                    Row(Modifier.fillMaxWidth().selectable(selected = state.selectedPerson?.id == person.id,
                        enabled = !state.busy, role = Role.RadioButton, onClick = { actions.selectPerson(person) })
                        .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileAvatar(controls.nameOf(person), Modifier.size(44.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(controls.nameOf(person), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.weight(1f))
                                StateChip(label, ink, background, Modifier.widthIn(max = 180.dp),
                                    dot = status is UserCurrentStatus.FreeTime || status is UserCurrentStatus.InClass)
                            }
                            if (state.incomingVisibility[person.id]?.sharesSchedule == true && state.peopleSchedulesFresh) {
                                DayStrip(schedules, person.zone())
                            }
                        }
                        if (controls.isMuted(person.id)) Icon(Icons.Default.NotificationsOff, "Avisos silenciados",
                            Modifier.size(18.dp), tint = TextMuted)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextSecondary)
                    }
                }
            }
        }
    }
    OutlinedButton(onClick = { connect = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.PersonAddAlt, null, Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text("Conectar con alguien")
    }
    if (state.signals.isNotEmpty()) Panel {
        SectionHeading(Icons.Default.MarkChatUnread, "Te han escrito", "Mensajes recientes · Últimas 24 horas")
        state.signals.forEach { signal ->
            ReceivedSignalItem(signal, state.profiles.find { it.id == signal.senderId }?.let(controls::nameOf) ?: "Persona conectada",
                state.busy, actions)
        }
    }
    PlansPanel(state, actions)
    state.groups.find { it.id == selectedGroup }?.let { group ->
        GroupPanel(group, state, actions, onEdit = { editingGroup = group; groupEditor = true })
    }
    if (groupEditor) GroupEditor(editingGroup, state, actions) { groupEditor = false }
    if (selectedGroup == null) state.selectedPerson?.let { selected ->
        val person = selected.copy(displayName = controls.nameOf(selected))
        val visibility = state.incomingVisibility[person.id]
        key(person.id) {
            var showAvailability by rememberSaveable { mutableStateOf(false) }
            var sendMessage by rememberSaveable { mutableStateOf(false) }
            val current = contactStatus(person, state)
            val busy = current is UserCurrentStatus.InClass ||
                (current is UserCurrentStatus.Override && !current.customLocation.trim().equals("Disponible", ignoreCase = true))
            val watching = state.freeAlerts.any { it.targetId == person.id }
            Surface(shape = CardShape, color = FreeSurface, contentColor = FreeInk, border = BorderStroke(1.dp, FreeInk.copy(alpha = .18f))) {
                Row(Modifier.fillMaxWidth().clickable { showAvailability = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(38.dp).background(FreeInk.copy(alpha = .12f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center) { Icon(Icons.Default.EventAvailable, null, Modifier.size(19.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text("Horas libres en común", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Ver disponibilidad con ${person.displayName}", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
            val personNow = profileClock(person.zone())
            val upcoming = if (visibility?.sharesSchedule == true && state.peopleSchedulesFresh)
                state.personSchedules.occurrencesOn(personNow.toLocalDate(), personNow.zone).firstOrNull { it.window.start.isAfter(personNow) }
                else null
            HeroStatusCard(current, footer = upcoming?.let { "Próxima actividad: ${it.window.start.format(hourFormat)} · ${it.schedule.title}" }) {
                Button(onClick = { sendMessage = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enviar mensaje")
                }
                // Only useful while the person is busy; the server sends one push when that ends.
                if (visibility?.sharesSchedule != false && watching) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Te avisaremos cuando quede libre", Modifier.weight(1f), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { actions.cancelFreeAlert(person.id) }, enabled = !state.busy) { Text("Cancelar") }
                } else if (visibility?.sharesSchedule != false && busy) OutlinedButton(onClick = { actions.notifyWhenFree(person.id) },
                    enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.NotificationsActive, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Avísame cuando esté libre")
                }
                TextButton(onClick = { manageMessages = true }) { Text("Mis mensajes") }
            }
            var proposing by remember { mutableStateOf<FreeSlot?>(null) }
            if (showAvailability) CommonAvailabilitySheet(person, actions,
                onPropose = { proposing = it; showAvailability = false }) { showAvailability = false }
            proposing?.let { slot -> PlanSheet(person, slot, state, actions) { proposing = null } }
            if (sendMessage) Sheet({ sendMessage = false }, "Mensaje para ${person.displayName}", "Los mensajes caducan después de 24 horas.") {
                if (state.messages.isEmpty()) Text("Crea un mensaje rápido para enviarlo desde aquí.", color = TextSecondary)
                state.messages.forEach { quick ->
                    OutlinedButton(onClick = { actions.send(quick); sendMessage = false }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorder), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                        Text(quick.label, Modifier.weight(1f), textAlign = TextAlign.Start, color = TextPrimary,
                            style = MaterialTheme.typography.bodyLarge)
                        Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                TextButton(onClick = { sendMessage = false; manageMessages = true }) { Text("Editar mensajes rápidos") }
            }
        }
        ScheduleFollowPanel(person, state, actions, visibility?.sharesSchedule == true)
        SectionHeading(Icons.Default.CalendarMonth, "Su semana")
        when {
            visibility?.sharesSchedule == false -> Panel {
                Text("Horario privado", fontWeight = FontWeight.Bold)
                Text("Esta persona no comparte su horario contigo.", color = TextSecondary)
            }
            visibility == null || !state.peopleSchedulesFresh -> Panel {
                Text("Datos desactualizados", fontWeight = FontWeight.Bold)
                TextButton(onClick = actions::refresh, enabled = !state.busy) { Text("Actualizar horario") }
            }
            else -> {
                var personDay by rememberSaveable(person.id) { mutableStateOf(ZonedDateTime.now(person.zone()).dayOfWeek.value) }
                SegmentedChoice((1..7).toList(), personDay, { personDay = it }) { days[it - 1].take(3) }
                val personDate = LocalDate.now(person.zone()).with(TemporalAdjusters.nextOrSame(DayOfWeek.of(personDay)))
                Text(personDate.longDate(), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                val schedules = state.personSchedules.occurrencesOn(personDate, person.zone()).map { it.schedule }
                state.pauses.covering(person.id, personDate)?.let { pause ->
                    StatusPill("${pause.title} · horario en pausa hasta el ${pause.endLabel()}", BrandSky)
                }
                if (schedules.isEmpty()) Text("Sin actividades programadas", color = TextSecondary)
                schedules.forEachIndexed { index, schedule -> ActivityTimelineCard(schedule, false,
                    last = index == schedules.lastIndex, displayDay = personDay) }
            }
        }
        state.connections.find { it.otherPerson(state.profile?.id.orEmpty()) == person.id }?.let { connection ->
            TextButton(onClick = { manageConnection = connection }, enabled = !state.busy) { Text("Administrar conexión con ${person.displayName}", color = TextMuted) }
        }
    }
    if (connect) ConnectionSheet(state, actions) { connect = false }
    if (manageMessages) MessageSheet(state, actions) { manageMessages = false }
    manageConnection?.let { connection ->
        val person = state.profiles.find { it.id == connection.otherPerson(state.profile?.id.orEmpty()) }
        val preference = person?.let { controls.forPerson(it.id) }
        if (person != null && preference != null) ConnectionSettingsSheet(person, preference, state, actions,
            onDisconnect = { manageConnection = null; disconnect = connection }) { manageConnection = null }
    }
    disconnect?.let { connection -> Confirm("¿Desvincular persona?", "Ambos dejarán de ver el horario y estado del otro.",
        { disconnect = null }, { actions.disconnect(connection.id); disconnect = null }) }
}

/** What everyone in a group is doing now, and a message for all of them. */
@Composable
private fun ScheduleFollowPanel(person: AccountProfile, state: AccountState, actions: AccountActions, sharesSchedule: Boolean) {
    val requests = state.scheduleFollowRequests ?: return
    val owner = state.profile?.id ?: return
    val outgoing = requests.firstOrNull { it.followerId == owner }
    val incoming = requests.firstOrNull { it.followerId == person.id && it.leaderId == owner }
    var showActivities by rememberSaveable(person.id) { mutableStateOf(false) }
    var showDetach by rememberSaveable(person.id) { mutableStateOf(false) }

    if (incoming != null) Panel {
        if (incoming.approved) {
            Text("${person.displayName} sigue tu horario")
            TextButton(onClick = { actions.endScheduleFollow(incoming.id, null) }, enabled = !state.busy) {
                Text("Detener sincronización")
            }
        } else {
            Text("${person.displayName} quiere seguir tu horario")
            Button(onClick = { actions.approveScheduleFollow(incoming.id) }, enabled = !state.busy) {
                Text("Aceptar solicitud")
            }
            TextButton(onClick = { actions.endScheduleFollow(incoming.id, null) }, enabled = !state.busy) {
                Text("Rechazar")
            }
        }
    }

    when {
        outgoing?.leaderId == person.id && outgoing.approved -> Panel {
            Text("Horario de ${person.displayName}")
            Button(onClick = { showActivities = true }, enabled = !state.busy && sharesSchedule) {
                Text("Elegir actividades")
            }
            TextButton(onClick = { showDetach = true }, enabled = !state.busy) {
                Text("Desincronizar horario")
            }
        }
        outgoing?.leaderId == person.id -> Panel {
            Text("Solicitud pendiente de aceptación")
            TextButton(onClick = { actions.endScheduleFollow(outgoing.id, null) }, enabled = !state.busy) {
                Text("Cancelar solicitud")
            }
        }
        outgoing == null && sharesSchedule -> OutlinedButton(
            onClick = { actions.requestScheduleFollow(person.id) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Solicitar sincronización de horario") }
    }

    if (showActivities && outgoing?.approved == true && outgoing.leaderId == person.id && sharesSchedule) {
        Sheet({ showActivities = false }, "Actividades de ${person.displayName}", "Elige cuáles quieres ver en tu horario.") {
            val chosen = state.schedules.mapNotNull { it.sourceId }.toSet()
            val choices = state.personSchedules.filter { it.sourceId == null }
                .sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))
            if (choices.isEmpty()) Text("No hay actividades para sincronizar.")
            choices.forEach { activity ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = activity.id in chosen,
                        onCheckedChange = { actions.selectFollowedActivity(outgoing.id, activity.id, it) },
                        enabled = !state.busy
                    )
                    Column {
                        Text(activity.title)
                        Text(
                            "${activity.oneOffDate ?: days[activity.dayOfWeek - 1]} · ${activity.startTime.take(5)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showDetach && outgoing?.approved == true && outgoing.leaderId == person.id) {
        val description = if (sharesSchedule) "Tus actividades propias y las que personalizaste se conservarán."
            else "El horario de origen ya no está disponible para guardar una copia. Tus actividades propias se conservarán."
        Sheet({ showDetach = false }, "Desincronizar horario", description) {
            if (sharesSchedule) {
                Button(onClick = { actions.endScheduleFollow(outgoing.id, true) }, enabled = !state.busy) {
                    Text("Conservar copia de las actividades")
                }
                OutlinedButton(onClick = { actions.endScheduleFollow(outgoing.id, false) }, enabled = !state.busy) {
                    Text("Quitar las actividades sin editar")
                }
            } else {
                OutlinedButton(onClick = { actions.endScheduleFollow(outgoing.id, null) }, enabled = !state.busy) {
                    Text("Desincronizar sin guardar copia")
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun GroupPanel(group: PersonGroup, state: AccountState, actions: AccountActions, onEdit: () -> Unit) {
    var sending by rememberSaveable(group.id) { mutableStateOf(false) }
    val members = state.membersOf(group.id).mapNotNull { id -> state.profiles.find { it.id == id } }
        .sortedBy { state.controls.nameOf(it).lowercase() }
    Panel {
        SectionHeading(Icons.Default.Groups, group.name, "${members.size} ${if (members.size == 1) "persona" else "personas"} · solo tú ves este grupo")
        if (members.isEmpty()) Text("Nadie de este grupo sigue conectado contigo.", color = TextSecondary)
        members.forEach { person ->
            val status = contactStatus(person.copy(displayName = state.controls.nameOf(person)), state,
                state.othersSchedules.filter { it.userId == person.id }, state.othersStatuses.filter { it.userId == person.id })
            val (line, color) = when (status) {
                is UserCurrentStatus.Override -> status.customLocation to BrandSky
                is UserCurrentStatus.InClass -> status.currentSchedule.title to ActivityCategory.fromKey(status.currentSchedule.category).accentColor
                is UserCurrentStatus.Error -> status.message to TextMuted
                else -> "Libre" to TextMuted
            }.let { (text, ink) -> listOfNotNull(text, status.place).joinToString(" · ") to ink }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { actions.selectPerson(person) }
                .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileAvatar(state.controls.nameOf(person), Modifier.size(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.controls.nameOf(person), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(line, color = color, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { sending = true }, enabled = !state.busy && members.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Mensaje al grupo")
            }
            OutlinedButton(onClick = onEdit, enabled = !state.busy) { Text("Editar") }
        }
    }
    if (sending) Sheet({ sending = false }, "Mensaje para «${group.name}»", "Le llegará a cada persona del grupo. Caduca después de 24 horas.") {
        if (state.messages.isEmpty()) Text("Crea un mensaje rápido en «Mis mensajes» para enviarlo desde aquí.", color = TextSecondary)
        state.messages.forEach { quick ->
            OutlinedButton(onClick = { actions.sendToGroup(group.id, quick); sending = false }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder)) {
                Text(quick.label, Modifier.weight(1f), textAlign = TextAlign.Start, color = TextPrimary)
                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun GroupEditor(existing: PersonGroup?, state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    val draftId = rememberSaveable { UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "Familia") }
    var members by remember { mutableStateOf(existing?.let { state.membersOf(it.id).toSet() } ?: emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.busy, state.savedScheduleDraftId) {
        if (submitted && !state.busy && state.savedScheduleDraftId == draftId) dismiss()
    }
    val people = state.connections.mapNotNull { connection ->
        state.profiles.find { it.id == connection.otherPerson(state.profile?.id.orEmpty()) }
    }.sortedBy { state.controls.nameOf(it).lowercase() }
    val problem = when {
        name.isBlank() || name.trim().length > 40 -> "El nombre debe tener entre 1 y 40 caracteres."
        members.isEmpty() -> "Elige al menos una persona."
        members.size > 20 -> "Un grupo puede tener hasta 20 personas."
        else -> null
    }
    Sheet(dismiss, if (existing == null) "Nuevo grupo" else "Editar grupo", "Solo tú lo ves; las personas no reciben ningún aviso.", gesturesEnabled = false) {
        if (existing == null) Choices(listOf("Familia", "Amigos", "Trabajo", "Universidad"), name, { name = it }) { it }
        Field(name, { name = it.take(40) }, "Nombre del grupo")
        people.forEach { person ->
            val checked = person.id in members
            Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox) { members = if (it) members + person.id else members - person.id },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Checkbox(checked, onCheckedChange = null)
                Text(state.controls.nameOf(person), Modifier.weight(1f))
            }
        }
        (problem?.takeIf { submitted } ?: state.error?.takeIf { submitted })?.let { FormProblem(it) }
        Button(onClick = { submitted = true; if (problem == null) actions.saveGroup(draftId, existing?.id, name, members) },
            enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar grupo") }
        if (existing != null) TextButton(onClick = { confirmDelete = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Eliminar grupo", color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmDelete && existing != null) Confirm("¿Eliminar «${existing.name}»?", "Las personas siguen conectadas contigo; solo desaparece el grupo.",
        { confirmDelete = false }, { actions.deleteGroup(existing.id); confirmDelete = false; dismiss() })
}

/** Upcoming plans with connected people: answer the ones received, follow or cancel the ones sent. */
@Composable
private fun PlansPanel(state: AccountState, actions: AccountActions) {
    val own = state.profile?.id ?: return
    val now = Instant.now()
    val plans = Plans.upcoming(state.plans, now).filter { it.status != "cancelled" }
    if (plans.isEmpty()) return
    val zone = ZoneId.systemDefault()
    fun name(id: String) = state.profiles.find { it.id == id }?.let(state.controls::nameOf) ?: "Tu gente"
    Panel {
        SectionHeading(Icons.Default.Event, "Planes", "Propuestas desde las horas libres en común")
        plans.forEach { plan ->
            val received = plan.recipientId == own
            val other = name(if (received) plan.senderId else plan.recipientId)
            Surface(shape = RoundedCornerShape(16.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(plan.title, fontWeight = FontWeight.Bold)
                    Text(Plans.whenText(plan.startsAt, plan.endsAt, zone).replaceFirstChar(Char::titlecase),
                        color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    if (plan.location.isNotBlank()) Text(plan.location, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    when {
                        plan.status == "pending" && received -> {
                            Text("$other te lo propone", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { actions.answerPlan(plan.id, true) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Aceptar") }
                                OutlinedButton(onClick = { actions.answerPlan(plan.id, false) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("No puedo") }
                            }
                        }
                        plan.status == "pending" -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Esperando respuesta de $other", Modifier.weight(1f), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { actions.cancelPlan(plan.id) }, enabled = !state.busy) { Text("Cancelar") }
                        }
                        plan.status == "accepted" -> StatusPill(if (received) "Aceptado · en tu horario" else "$other aceptó · en tu horario", BrandSky)
                        else -> StatusPill(if (received) "Lo rechazaste" else "$other no puede", TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanSheet(person: AccountProfile, slot: FreeSlot, state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    val draftId = rememberSaveable { UUID.randomUUID().toString() }
    var title by rememberSaveable { mutableStateOf(Plans.TITLES.first()) }
    var location by rememberSaveable { mutableStateOf("") }
    // Starts every 30 minutes inside the free slot, leaving at least 30 minutes before it ends.
    val firstStart = slot.start.truncatedTo(java.time.temporal.ChronoUnit.MINUTES).let { start ->
        val extra = (30 - start.minute % 30) % 30
        start.plusMinutes(extra.toLong()).takeIf { !it.plusMinutes(30).isAfter(slot.end) } ?: start
    }
    val starts = generateSequence(firstStart) { it.plusMinutes(30) }.takeWhile { !it.plusMinutes(30).isAfter(slot.end) }.take(48).toList()
        .ifEmpty { listOf(slot.start) }
    var startIndex by rememberSaveable { mutableIntStateOf(0) }
    val start = starts[startIndex.coerceIn(starts.indices)]
    val durations = Plans.DURATIONS.filter { !start.plusMinutes(it).isAfter(slot.end) }.ifEmpty { listOf(30L) }
    var minutes by rememberSaveable { mutableLongStateOf(60L) }
    val duration = if (minutes in durations) minutes else durations.last()
    val end = start.plusMinutes(duration)
    var submitted by rememberSaveable { mutableStateOf(false) }
    val problem = Plans.validate(title, start.toInstant(), end.toInstant(), Instant.now())
    LaunchedEffect(state.busy, state.savedScheduleDraftId) {
        if (submitted && !state.busy && state.savedScheduleDraftId == draftId) dismiss()
    }
    Sheet(dismiss, "Proponer un plan a ${person.displayName}", "Si acepta, se añadirá al horario de ambos.") {
        Choices(Plans.TITLES, title, { title = it }) { it }
        Field(title, { title = it.take(120) }, "¿Qué quieres proponer?")
        Text(start.toLocalDate().longDate().replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScheduleChoice("Empieza", starts.indices.toList(), startIndex.coerceIn(starts.indices), { startIndex = it },
                Modifier.weight(1f), leading = Icons.Default.Schedule) { starts[it].format(hourFormat) }
            ScheduleChoice("Dura", durations, duration, { minutes = it }, Modifier.weight(1f)) {
                if (it < 60) "$it min" else if (it % 60 == 0L) "${it / 60} h" else "${it / 60} h ${it % 60} min"
            }
        }
        Field(location, { location = it.take(160) }, "Lugar (opcional)")
        (problem ?: state.error?.takeIf { submitted })?.let { FormProblem(it) }
        Button(onClick = { submitted = true; actions.proposePlan(draftId, person.id, title, location, start.toInstant(), end.toInstant()) },
            enabled = !state.busy && problem == null, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Enviar propuesta")
        }
    }
}

@Composable
private fun ConnectionSheet(state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf("Compartir código") }
    var code by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(state.notice, state.busy) { if (submitted && !state.busy && state.notice == "Conexión establecida.") dismiss() }
    Sheet(dismiss, "Conecta con tu gente", "Al conectar, ambos podrán ver sus estados y horarios.") {
        Choices(listOf("Compartir código", "Tengo un código"), mode, { mode = it }) { it }
        if (mode == "Compartir código") {
            state.invitation?.let { invitation ->
                val display = InvitationCode.display(invitation.code)
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
                        Text(display, color = TextPrimary, fontSize = 27.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                        Text("Válido 24 horas · Un solo uso", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Código Near", display))) } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Copiar")
                    }
                    Button(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Conecta conmigo en Near. Mi código es $display. Es válido durante 24 horas.") }, "Compartir invitación"))
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Compartir")
                    }
                }
            } ?: EmptyPanel(Icons.Default.Key, "Invita a una persona", "Envíale tu código y pídele que lo introduzca en su app.")
            if (state.invitation == null) Button(onClick = actions::invite, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Key, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Crear mi código")
            } else TextButton(onClick = actions::invite, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Generar otro código")
            }
        } else {
            Field(code, { code = it.take(40) }, "Código de invitación", placeholder = "ABCD-EFGH", keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            Text("Introduce el código que esa persona compartió contigo.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { submitted = true; actions.accept(code) }, enabled = !state.busy && InvitationCode.isValid(code), modifier = Modifier.fillMaxWidth()) { Text("Conectar y compartir") }
        }
        if (submitted || state.invitation == null) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun MessageSheet(state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    var message by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.notice) { if (state.notice == "Mensaje rápido creado.") message = "" }
    Sheet(dismiss, "Tus mensajes rápidos", "Guarda un mensaje que quieras enviar con un toque.") {
        Text("Por ejemplo, una pregunta o un saludo. Se envía a la persona que tengas seleccionada; no cambia tu estado.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Field(message, { message = it.take(240) }, "¿Qué quieres decir?", placeholder = "Escribe tu mensaje")
        Button(onClick = { actions.saveMessage(message.trim().take(30), message) }, enabled = !state.busy && message.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Guardar mensaje") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.messages.isNotEmpty()) Text("Guardados", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        state.messages.forEach { quick ->
            Surface(shape = RoundedCornerShape(16.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(quick.message, Modifier.weight(1f).padding(vertical = 10.dp), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { actions.deleteMessage(quick.id) }, enabled = !state.busy) {
                        Icon(Icons.Default.DeleteOutline, "Eliminar mensaje", tint = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountProfilePanel(state: AccountState, actions: AccountActions, updates: UpdateState,
    onCheckUpdates: () -> Unit, onInstallUpdate: () -> Unit) {
    val context = LocalContext.current
    val notificationManager = remember(context) { context.getSystemService(NotificationManager::class.java) }
    fun blockedChannels() = com.example.findme.util.NotificationHelper.Channel.entries.filter {
        notificationManager?.getNotificationChannel(it.id)?.importance == NotificationManager.IMPORTANCE_NONE
    }
    var notificationsAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var silencedChannels by remember { mutableStateOf(blockedChannels()) }
    var exact by remember { mutableStateOf(com.example.findme.reminders.ActivityReminders.exactAllowed(context)) }
    // Android 14+ asks the person before an app may open a full screen over the lock screen.
    fun fullScreenAllowed() = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        notificationManager?.canUseFullScreenIntent() != false
    var fullScreen by remember { mutableStateOf(fullScreenAllowed()) }
    androidx.lifecycle.compose.LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        silencedChannels = blockedChannels()
        exact = com.example.findme.reminders.ActivityReminders.exactAllowed(context)
        fullScreen = fullScreenAllowed()
    }
    val nearAlerts = with(state.settings) { activities || statuses || messages || reminders }
    var accountSheet by remember { mutableStateOf<AccountSheet?>(null) }
    PageHeading("", "Perfil y ajustes", "")
    state.profile?.let { profile ->
        ProfileHeader(profile, enabled = !state.busy) { accountSheet = AccountSheet.Profile }
    }
    SettingsGroupLabel("Preferencias")
    Panel {
        SectionHeading(Icons.Default.NotificationsNone, "Notificaciones")
        Surface(shape = RoundedCornerShape(16.dp), color = AppBackground.copy(alpha = .35f), border = BorderStroke(1.dp, CardBorder)) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusLine(if (nearAlerts) "Near · Hay avisos activados" else "Near · Todos los avisos desactivados", nearAlerts)
                StatusLine(if (notificationsAllowed) "Android · Permite las notificaciones" else "Android · Bloquea las notificaciones",
                    notificationsAllowed, problem = !notificationsAllowed)
                if (notificationsAllowed) silencedChannels.forEach { channel ->
                    SettingsRow(Icons.Default.NotificationsOff, "Android silenció: ${channel.title}", tint = MaterialTheme.colorScheme.error) {
                        context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, channel.id))
                    }
                }
                SettingsRow(Icons.AutoMirrored.Filled.OpenInNew, "Abrir ajustes de Android") {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
        }
        HorizontalDivider(color = CardBorder)
        AccountSettingsSwitch("Inicio de actividades", "Cuando empieza una actividad de tu gente.", state.settings.activities) { actions.setAlert(AccountAlert.Activities, it) }
        HorizontalDivider(color = CardBorder)
        AccountSettingsSwitch("Cambios de estado", "Cuando tu gente cambia de planes.", state.settings.statuses) { actions.setAlert(AccountAlert.Statuses, it) }
        HorizontalDivider(color = CardBorder)
        AccountSettingsSwitch("Mensajes recibidos", "", state.settings.messages) { actions.setAlert(AccountAlert.Messages, it) }
        HorizontalDivider(color = CardBorder)
        AccountSettingsSwitch("Antes de tus actividades", "", state.settings.reminders) { actions.setReminders(it) }
        if (state.settings.reminders) {
            Choices(com.example.findme.data.local.REMINDER_CHOICES, state.settings.reminderMinutes, actions::setReminderMinutes) { minutes ->
                if (minutes >= 60) "${minutes / 60} h antes" else "$minutes min antes"
            }
            if (!exact && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                Text(if (state.settings.reminderAlarm)
                    "Sin alarmas exactas, Android puede retrasar la alarma o dejarla solo como notificación. Permítelas para que suene a tiempo."
                    else "Android puede retrasar el recordatorio unos minutos. Permite las alarmas exactas para que llegue a tiempo.",
                    color = TextMuted, style = MaterialTheme.typography.bodySmall)
                SettingsRow(Icons.Default.Alarm, "Permitir alarmas exactas") {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}")))
                }
            }
            HorizontalDivider(color = CardBorder)
            AccountSettingsSwitch("Sonar como alarma", "Suena con un sonido de alarma y ocupa la pantalla hasta que la detengas o la pospongas.",
                state.settings.reminderAlarm) { actions.setReminderAlarm(it) }
            if (state.settings.reminderAlarm) ReminderAlarmSettings(state, actions, fullScreen)
        }
    }
    AutoPlacesPanel(state, actions)
    AttendancePanel(state, actions)
    Panel {
        SectionHeading(Icons.Default.Palette, "Apariencia")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTheme.entries.forEach { theme ->
                ThemeOption(theme, state.settings.theme == theme, Modifier.weight(1f)) { actions.setTheme(theme) }
            }
        }
    }
    val scope = rememberCoroutineScope()
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val data = actions.exportData()
        if (uri == null || data == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) } != null }.getOrDefault(false)
            }
            actions.exportFinished(saved)
        }
    }
    SettingsGroupLabel("Cuenta")
    Panel {
        SectionHeading(Icons.Default.PrivacyTip, "Privacidad y seguridad",
            "Tu horario y tus estados solo los ven las personas que conectes.")
        Column {
            SettingsRow(Icons.Default.Lock, "Cambiar contraseña", enabled = !state.busy) { accountSheet = AccountSheet.Password }
            HorizontalDivider(color = CardBorder)
            SettingsRow(Icons.Default.Download, "Exportar mis datos", enabled = !state.busy && state.profile != null) {
                exportLauncher.launch("near-${state.profile?.username ?: "datos"}.json")
            }
        }
    }
    Panel {
        SectionHeading(Icons.Default.Info, "Acerca de Near", "Versión ${BuildConfig.VERSION_NAME}")
        Text(updates.message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onCheckUpdates, enabled = !updates.checking && !updates.downloading, modifier = Modifier.fillMaxWidth()) {
            if (updates.checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (updates.checking) "Comprobando…" else "Buscar actualizaciones", maxLines = 1)
        }
        if (updates.release != null && !updates.downloading) Button(onClick = onInstallUpdate, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.SystemUpdate, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Actualizar ahora")
        }
    }
    Panel {
        Column {
            SettingsRow(Icons.AutoMirrored.Filled.Logout, "Cerrar sesión", enabled = !state.busy, chevron = false, onClick = actions::logout)
            HorizontalDivider(color = CardBorder)
            SettingsRow(Icons.Default.DeleteForever, "Eliminar mi cuenta", enabled = !state.busy,
                tint = MaterialTheme.colorScheme.error) { accountSheet = AccountSheet.Delete }
        }
    }
    when (accountSheet) {
        AccountSheet.Profile -> EditProfileSheet(state, actions) { accountSheet = null }
        AccountSheet.Password -> ChangePasswordSheet(state, actions) { accountSheet = null }
        AccountSheet.Delete -> DeleteAccountSheet(state, actions) { accountSheet = null }
        null -> Unit
    }
}

private enum class AccountSheet { Profile, Password, Delete }

/** Sound, vibration and snooze of the alarm mode. The sounds are the phone's own, chosen with Android's picker. */
@Composable
private fun ReminderAlarmSettings(state: AccountState, actions: AccountActions, fullScreen: Boolean) {
    val context = LocalContext.current
    val sound = state.settings.alarmSound
    val soundTitle = remember(sound) { com.example.findme.reminders.ReminderAlarm.soundTitle(context, sound) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked = result.data?.let { androidx.core.content.IntentCompat.getParcelableExtra(it,
            android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java) }
        // "Predeterminado" follows whatever alarm sound the phone uses, also if it changes later.
        actions.setAlarmSound(picked?.takeIf { it != Settings.System.DEFAULT_ALARM_ALERT_URI }?.toString())
    }
    SettingsRow(Icons.Default.MusicNote, "Sonido: $soundTitle") {
        val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALARM)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonido de la alarma")
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                sound?.let(android.net.Uri::parse) ?: Settings.System.DEFAULT_ALARM_ALERT_URI)
        try { picker.launch(intent) } catch (_: android.content.ActivityNotFoundException) { }
    }
    AccountSettingsSwitch("Vibrar", "", state.settings.alarmVibrate) { actions.setAlarmVibrate(it) }
    Text("Posponer", style = MaterialTheme.typography.titleSmall)
    Choices(com.example.findme.data.local.ALARM_SNOOZE_CHOICES, state.settings.alarmSnoozeMinutes, actions::setAlarmSnoozeMinutes) { "$it min" }
    if (!fullScreen && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Text("Android no deja que la alarma se muestre sobre la pantalla bloqueada; sonará, pero solo verás la notificación.",
            color = TextMuted, style = MaterialTheme.typography.bodySmall)
        SettingsRow(Icons.Default.Fullscreen, "Permitir pantalla completa") {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    android.net.Uri.parse("package:${context.packageName}")))
            } catch (_: android.content.ActivityNotFoundException) { }
        }
    }
    SettingsRow(Icons.Default.PlayArrow, "Probar la alarma", chevron = false) { actions.testAlarm() }
}

/**
 * Automatic status by place. The places are saved only on this phone; the only thing shared is the label,
 * as a normal status. Location permission is asked for when the first place is saved, not before.
 */
@Composable
private fun AutoPlacesPanel(state: AccountState, actions: AccountActions) {
    val context = LocalContext.current
    var location by remember { mutableStateOf(com.example.findme.places.AutoPlaces.hasLocation(context)) }
    var background by remember { mutableStateOf(com.example.findme.places.AutoPlaces.hasBackground(context)) }
    var adding by rememberSaveable { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val nowLocation = com.example.findme.places.AutoPlaces.hasLocation(context)
        val nowBackground = com.example.findme.places.AutoPlaces.hasBackground(context)
        if (nowLocation != location || nowBackground != background) actions.refreshAutoPlaces()
        location = nowLocation; background = nowBackground
    }
    val askLocation = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        location = granted[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (location) adding = true
    }
    val askBackground = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        background = com.example.findme.places.AutoPlaces.hasBackground(context)
        actions.refreshAutoPlaces()
    }
    Panel {
        SectionHeading(Icons.Default.Place, "Estado automático por lugar",
            "Al llegar a un lugar guardado compartimos su nombre, por ejemplo «En casa», y lo quitamos al salir. " +
                "Tu ubicación nunca sale del teléfono.")
        state.autoPlaces.forEach { place ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Place, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(place.label, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(place.activityCategory?.let { com.example.findme.data.model.ActivityCategory.fromKey(it).label },
                        place.address, "${place.radius.toInt()} m").joinToString(" · "),
                        color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { actions.removeAutoPlace(place.id) }, enabled = !state.busy) {
                    Icon(Icons.Default.DeleteOutline, "Quitar ${place.label}", tint = TextMuted)
                }
            }
        }
        if (state.autoPlaces.isNotEmpty() && location && !background) {
            Text("Para que funcione con Near cerrado, permite la ubicación «Todo el tiempo».",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            SettingsRow(Icons.Default.LocationOn, "Permitir ubicación todo el tiempo") {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                    askBackground.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
        if (state.autoPlaces.isNotEmpty() && !location) Text("Near ya no tiene permiso de ubicación; los lugares están en pausa.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Text("Tu lugar se ve junto a tu actividad o tu estado, sin reemplazarlos.", color = TextMuted,
            style = MaterialTheme.typography.bodySmall)
        if (state.autoPlaces.isNotEmpty()) {
            HorizontalDivider(color = CardBorder)
            AccountSettingsSwitch("«${com.example.findme.places.AutoPlaces.STREET}» fuera de tus lugares",
                "Al salir de todos tus lugares lo mostramos junto a tu actividad. Tu gente sabrá cuándo estás fuera.", state.settings.streetStatus) { actions.setStreetStatus(it) }
        }
        if (state.autoPlaces.size < com.example.findme.places.AutoPlaces.MAX) OutlinedButton(onClick = {
            if (location) adding = true
            else askLocation.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
        }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AddLocationAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Añadir lugar")
        }
    }
    if (adding) AutoPlaceSheet(state, actions) { adding = false }
}

private enum class PlaceSource(val label: String) { Here("Dónde estoy ahora"), Address("Buscar dirección"), Map("En el mapa") }
private const val MAP_POINT = "Punto elegido en el mapa"

@Composable
private fun AutoPlaceSheet(state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    var label by rememberSaveable { mutableStateOf(com.example.findme.places.AutoPlaces.LABELS.first()) }
    var submitted by remember { mutableStateOf(false) }
    val count = state.autoPlaces.size
    LaunchedEffect(state.autoPlaces.size) { if (submitted && state.autoPlaces.size > count) dismiss() }
    var source by rememberSaveable { mutableStateOf(PlaceSource.Here) }
    val byAddress = source != PlaceSource.Here
    // Follows the chosen label («En el trabajo» → Trabajo) until the person picks one themselves.
    var categoryTouched by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf(com.example.findme.places.AutoPlaces.suggestedCategory(label)) }
    LaunchedEffect(label) { if (!categoryTouched) category = com.example.findme.places.AutoPlaces.suggestedCategory(label) }
    var radius by rememberSaveable { mutableStateOf(com.example.findme.places.AutoPlaces.RADIUS) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.example.findme.places.AutoPlaces.Found>>(emptyList()) }
    var chosen by remember { mutableStateOf<com.example.findme.places.AutoPlaces.Found?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // With a map on screen, dragging should move the map, not close the sheet.
    Sheet(dismiss, "Añadir lugar", "Al llegar a esta zona compartiremos el estado que elijas.",
        gesturesEnabled = !(com.example.findme.BuildConfig.MAPS_ENABLED && source != PlaceSource.Here)) {
        Choices(com.example.findme.places.AutoPlaces.LABELS, label, { label = it }) { it }
        Field(label, { label = it.take(60) }, "Estado al llegar")
        Choices(PlaceSource.entries.filter { it != PlaceSource.Map || com.example.findme.BuildConfig.MAPS_ENABLED }, source,
            { source = it; chosen = null; results = emptyList(); searched = false }) { it.label }
        if (source == PlaceSource.Address) {
            Field(query, { query = it.take(200); chosen = null; searched = false }, "Dirección o nombre del lugar")
            OutlinedButton(onClick = {
                searching = true
                scope.launch {
                    results = actions.searchAddress(query)
                    chosen = results.singleOrNull()
                    searching = false; searched = true
                }
            }, enabled = !searching && query.trim().length >= 3, modifier = Modifier.fillMaxWidth()) {
                if (searching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(if (searching) "Buscando…" else "Buscar")
            }
            if (searched && results.isEmpty()) FormProblem("No encontramos esa dirección. Prueba con la calle, el número y la ciudad.")
            results.forEach { found ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { chosen = found }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RadioButton(selected = chosen == found, onClick = { chosen = found })
                    Text(found.address, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        val point = chosen?.let { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) }
        if (source == PlaceSource.Address && chosen != null && com.example.findme.BuildConfig.MAPS_ENABLED) {
            Text("Toca el mapa para ajustar el punto.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            ZonePicker(point, radius, onPick = { chosen = chosen?.copy(latitude = it.latitude, longitude = it.longitude) })
        }
        if (source == PlaceSource.Map) {
            val context = LocalContext.current
            // Opens where you are, without choosing it: the point is only set by tapping.
            var here by remember { mutableStateOf<com.google.android.gms.maps.model.LatLng?>(null) }
            LaunchedEffect(Unit) {
                com.example.findme.places.AutoPlaces.currentLocation(context)?.let { here = com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) }
            }
            Text(if (chosen == null) "Toca el mapa donde está el lugar." else "Toca otra parte del mapa para moverlo.",
                color = TextMuted, style = MaterialTheme.typography.bodySmall)
            ZonePicker(point, radius, center = here,
                onPick = { chosen = com.example.findme.places.AutoPlaces.Found(MAP_POINT, it.latitude, it.longitude) })
        }
        Text("Tamaño de la zona", style = MaterialTheme.typography.titleSmall)
        Choices(com.example.findme.places.AutoPlaces.RADIUS_CHOICES, radius, { radius = it }) { "${it.toInt()} m" }
        (com.example.findme.places.AutoPlaces.validateLabel(label) ?: state.error?.takeIf { submitted })?.let { FormProblem(it) }
        val ready = com.example.findme.places.AutoPlaces.validateLabel(label) == null && (!byAddress || chosen != null)
        Text("Actividades en este lugar", style = MaterialTheme.typography.titleSmall)
        Choices(listOf<String?>(null) + com.example.findme.places.AutoPlaces.CATEGORIES, category, { category = it; categoryTouched = true }) { key ->
            key?.let { com.example.findme.data.model.ActivityCategory.fromKey(it).label } ?: "Ninguna"
        }
        Text("Tus actividades presenciales de esa categoría cuentan como asistidas cuando estás aquí.",
            color = TextMuted, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { submitted = true; actions.addAutoPlace(label, radius, if (byAddress) chosen else null, category) },
            enabled = !state.busy && ready, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) (if (byAddress) "Guardando…" else "Buscando tu ubicación…") else "Guardar lugar")
        }
    }
}

/**
 * Attendance to your university and work activities. In-person ones with a saved place of their category
 * count by themselves; any date can also be marked by hand. Only you can see it.
 */
@Composable
private fun AttendancePanel(state: AccountState, actions: AccountActions) {
    val enabled = state.settings.attendance
    LaunchedEffect(enabled) { if (enabled) actions.refreshAttendance() }
    Panel {
        SectionHeading(Icons.Default.FactCheck, "Asistencia",
            "Cuenta a qué clases y turnos fuiste. Solo tú la ves.")
        AccountSettingsSwitch("Registrar mi asistencia",
            "Las actividades presenciales cuentan como asistidas si estás en un lugar de su categoría desde 15 min antes. " +
                "Al salir antes de tiempo te preguntamos si terminó.", enabled) { actions.setAttendance(it) }
        if (!enabled) return@Panel
        if (state.autoPlaces.none { it.activityCategory != null }) Text(
            "Ningún lugar tiene actividades: indica en «Estado automático por lugar» cuál es tu universidad o tu trabajo, " +
                "o marca la asistencia a mano.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        val zone = state.profile?.zone() ?: ZoneId.systemDefault()
        val now = profileClock(zone)
        val since = state.settings.attendanceSince?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: now.toLocalDate()
        val summaries = remember(state.schedules, state.autoPlaces, state.attendance, since, now.toLocalDate(), now.hour) {
            com.example.findme.attendance.Attendance.summaries(state.schedules, state.autoPlaces, state.attendance, since, now)
        }
        if (summaries.isEmpty()) Text("Aún no terminó ninguna clase o turno desde el ${since.longDate()}.",
            color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        summaries.forEach { summary -> AttendanceRow(summary, state, actions) }
    }
}

@Composable
private fun AttendanceRow(summary: com.example.findme.attendance.Attendance.Summary, state: AccountState, actions: AccountActions) {
    var open by rememberSaveable(summary.schedule.id) { mutableStateOf(false) }
    HorizontalDivider(color = CardBorder)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(summary.schedule.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(
                "${summary.attended} asistidas", "${summary.missed} faltas",
                summary.days.count { it.mark == com.example.findme.attendance.Attendance.Mark.Unmarked }.takeIf { it > 0 }?.let { "$it sin marcar" },
                if (summary.automatic) null else "a mano"
            ).joinToString(" · "), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Text(summary.rate?.let { "$it %" } ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = when { summary.rate == null -> TextMuted; summary.rate!! >= 80 -> StatusGreenText; else -> MaterialTheme.colorScheme.error })
        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = TextMuted)
    }
    if (open) summary.days.take(10).forEach { day ->
        val date = day.occurrence.originalDate.toString()
        Column(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(day.occurrence.window.start.toLocalDate().longDate() +
                if (day.manual) " · marcada a mano" else "", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Asistí", day.mark == com.example.findme.attendance.Attendance.Mark.Attended, onClick = {
                    if (!state.busy) actions.markAttendance(summary.schedule.id, date, true)
                })
                ChoiceChip("Falté", day.mark == com.example.findme.attendance.Attendance.Mark.Missed, onClick = {
                    if (!state.busy) actions.markAttendance(summary.schedule.id, date, false)
                })
                if (day.manual) TextButton(onClick = { actions.markAttendance(summary.schedule.id, date, null) }, enabled = !state.busy) {
                    Text("Quitar")
                }
            }
        }
    }
}

/** Avatar, name and handle at the top of the profile, with a single entry point to edit them. */
@Composable
private fun ProfileHeader(profile: AccountProfile, enabled: Boolean, onEdit: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileAvatar(profile.displayName, Modifier.size(58.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                profile.username?.let { Text("@$it", color = TextSecondary, style = MonoLabel) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Public, null, Modifier.size(14.dp), tint = TextMuted)
                    Text(profile.timezone, color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        OutlinedButton(onClick = onEdit, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Editar perfil")
        }
    }
}

/** Small uppercase label that groups the settings cards below it. */
@Composable
private fun SettingsGroupLabel(text: String) {
    Text(text.uppercase(), Modifier.padding(start = 4.dp, top = 6.dp), color = TextMuted,
        style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp)
}

@Composable
private fun EditProfileSheet(state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    var submitted by remember { mutableStateOf(false) }
    CloseWhenDone(state, submitted, dismiss) { submitted = false }
    Sheet(dismiss, "Editar perfil", "Tu gente te verá con este nombre.") {
        ProfileNameForm(state, actions, onSubmit = { submitted = true })
        if (submitted) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** Closes a sheet once its own request finishes without an error. */
@Composable
private fun CloseWhenDone(state: AccountState, submitted: Boolean, onDone: () -> Unit, onFailed: () -> Unit) {
    LaunchedEffect(state.busy, submitted) {
        if (submitted && !state.busy) { if (state.error == null) onDone() else onFailed() }
    }
}

@Composable
private fun ChangePasswordSheet(state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    CloseWhenDone(state, submitted, dismiss) { submitted = false }
    Sheet(dismiss, "Cambiar contraseña", "Confirma tu contraseña actual y elige una nueva de 8 a 128 caracteres.") {
        PasswordField(current, { current = it.take(128) }, "Contraseña actual", !state.busy)
        PasswordField(new, { new = it.take(128) }, "Nueva contraseña", !state.busy)
        PasswordField(confirmation, { confirmation = it.take(128) }, "Confirmar nueva contraseña", !state.busy,
            isError = confirmation.isNotEmpty() && confirmation != new)
        if (submitted || state.error != null) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { submitted = true; actions.changePassword(current, new, confirmation) },
            enabled = !state.busy && current.isNotEmpty() && new.length in 8..128 && new == confirmation,
            modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Guardando…" else "Guardar contraseña") }
    }
}

@Composable
private fun DeleteAccountSheet(state: AccountState, actions: AccountActions, dismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    CloseWhenDone(state, submitted, dismiss) { submitted = false }
    Sheet(dismiss, "¿Eliminar tu cuenta?", "Se borran tu perfil, tu horario, tus estados, tus mensajes rápidos y tus conexiones. " +
        "Tu gente dejará de verte. No se puede deshacer. Si quieres conservar una copia, usa antes «Exportar mis datos».") {
        PasswordField(password, { password = it.take(128) }, "Tu contraseña", !state.busy)
        if (submitted || state.error != null) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { submitted = true; actions.deleteAccount(password) }, enabled = !state.busy && password.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Eliminando…" else "Eliminar definitivamente") }
        TextButton(onClick = dismiss, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}

@Composable
private fun PasswordField(value: String, change: (String) -> Unit, label: String, enabled: Boolean, isError: Boolean = false) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, enabled = enabled, isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "Ocultar contraseña" else "Mostrar contraseña")
            }
        }, shape = FieldShape, modifier = Modifier.fillMaxWidth(), colors = nearTextFieldColors())
}

/** A tappable settings row: icon, label and an optional chevron, with a 52dp touch target. */
@Composable
private fun SettingsRow(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = TextPrimary,
    chevron: Boolean = true, onClick: () -> Unit) {
    val color = if (enabled) tint else TextMuted
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (tint == TextPrimary && enabled) TextSecondary else color)
        Text(label, Modifier.weight(1f), color = color, style = MaterialTheme.typography.bodyLarge)
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = TextMuted)
    }
}

/** A theme card with a small preview drawn in that theme's own colours. */
@Composable
private fun ThemeOption(theme: AppTheme, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val preview = if (theme == AppTheme.Light) LightPalette else DarkPalette
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).border(if (selected) 2.dp else 1.dp, if (selected) primary else CardBorder, shape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(10.dp)).background(preview.background)
            .padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).background(preview.card)
                .border(1.dp, preview.cardBorder, RoundedCornerShape(6.dp)).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(preview.accent, CircleShape))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(width = 44.dp, height = 5.dp).background(preview.textPrimary, CircleShape))
                    Box(Modifier.size(width = 28.dp, height = 4.dp).background(preview.textMuted, CircleShape))
                }
            }
            Box(Modifier.size(width = 36.dp, height = 8.dp).background(preview.accent, CircleShape))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (theme == AppTheme.Light) Icons.Default.LightMode else Icons.Default.DarkMode, null,
                Modifier.size(18.dp), tint = if (selected) primary else TextSecondary)
            Text(theme.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                color = if (selected) primary else TextPrimary)
            if (selected) Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = primary)
        }
    }
}

/** A status line with a coloured dot: green when everything is fine, red when something needs attention. */
@Composable
private fun StatusLine(text: String, on: Boolean, problem: Boolean = false) {
    val color = when { problem -> MaterialTheme.colorScheme.error; on -> StatusGreenText; else -> TextMuted }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, color = if (problem) color else TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AccountSettingsSwitch(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (description.isNotBlank()) Text(description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null, colors = nearSwitchColors())
    }
}

/** Snackbars on a raised card with the accent for actions, instead of the cream inverse surface. */
@Composable
private fun NearSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        Snackbar(data, Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp),
            containerColor = InnerBg, contentColor = TextPrimary,
            actionColor = MaterialTheme.colorScheme.primary)
    }
}

/** Compact secondary action used inside cards, with an icon so it reads as a button and not as loose text. */
@Composable
private fun CardAction(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 40.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f),
            contentColor = MaterialTheme.colorScheme.primary)) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun EmptyPanel(icon: ImageVector, title: String, description: String) {
    Panel {
        AccentIcon(icon, BrandSky)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Sheet(
    dismiss: () -> Unit,
    title: String,
    description: String? = null,
    gesturesEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = dismiss,
        containerColor = AppSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = gesturesEnabled,
        dragHandle = if (gesturesEnabled) { { BottomSheetDefaults.DragHandle(color = TextMuted.copy(alpha = .6f)) } } else null
    ) {
        val scroll = rememberScrollState()
        val scrollModifier = if (gesturesEnabled) Modifier.verticalScroll(scroll).imePadding()
            else Modifier.imePadding().verticalScroll(scroll, overscrollEffect = null)
        Column(Modifier.fillMaxWidth().then(scrollModifier).padding(horizontal = 22.dp)
            .padding(top = if (gesturesEnabled) 0.dp else 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    description?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodyMedium) }
                }
                // Sheets without drag gestures need a visible way out besides the back gesture.
                if (!gesturesEnabled) IconButton(onClick = dismiss,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = InnerBg, contentColor = TextSecondary)) {
                    Icon(Icons.Default.Close, "Cerrar")
                }
            }
            content()
        }
    }
}

@Composable
private fun Field(value: String, change: (String) -> Unit, label: String, placeholder: String? = null, keyboard: KeyboardOptions = KeyboardOptions.Default,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null, trailing: (@Composable () -> Unit)? = null,
    hidden: Boolean = false, enabled: Boolean = true, isError: Boolean = false) {
    OutlinedTextField(value, change, label = { Text(label) }, placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leading?.let { icon -> { Icon(icon, null, Modifier.size(20.dp)) } }, trailingIcon = trailing,
        visualTransformation = if (hidden) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = keyboard, singleLine = true, enabled = enabled, isError = isError,
        shape = FieldShape, modifier = Modifier.fillMaxWidth(), colors = nearTextFieldColors())
}

@Composable
private fun <T> Choices(values: List<T>, selected: T, change: (T) -> Unit, label: (T) -> String) {
    if (values.size <= 2) {
        SegmentedChoice(values, selected, change, label = label)
    } else {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value -> ChoiceChip(label(value), selected == value, onClick = { change(value) }) }
        }
    }
}

@Composable
private fun Confirm(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    // Every confirmation in the app removes something, so the confirm action uses the error colour.
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp), containerColor = CardBg,
        icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = { Text(message, color = TextSecondary, textAlign = TextAlign.Center) },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
