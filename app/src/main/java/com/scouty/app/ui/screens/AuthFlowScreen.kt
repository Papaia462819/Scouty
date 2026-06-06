@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.scouty.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Info as InfoIcon
import com.composables.icons.lucide.LogIn
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Map
import com.composables.icons.lucide.Mountain
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.UserPlus
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scouty.app.R
import com.scouty.app.profile.AssessmentResult
import com.scouty.app.profile.OnboardingDraft
import com.scouty.app.profile.ProfileAssessmentEngine
import com.scouty.app.profile.ProfileQuestion
import com.scouty.app.profile.ScoutyLevel
import com.scouty.app.ui.components.StatusChip
import com.scouty.app.ui.components.ScoutyCard
import com.scouty.app.ui.theme.CardDarkAlt
import com.scouty.app.ui.theme.PrimaryGreen
import com.scouty.app.ui.theme.AccentGreen
import com.scouty.app.ui.theme.AccentGreenBg
import com.scouty.app.ui.theme.AccentGreenOnSurface
import com.scouty.app.ui.theme.BgPrimary
import com.scouty.app.ui.theme.BgSurfaceRaised
import com.scouty.app.ui.theme.BorderDefault
import com.scouty.app.ui.theme.Danger
import com.scouty.app.ui.theme.Info
import com.scouty.app.ui.theme.StatusAmber
import com.scouty.app.ui.theme.StatusBlue
import com.scouty.app.ui.theme.StatusOrange
import com.scouty.app.ui.theme.StatusRedSoft
import com.scouty.app.ui.theme.TextMuted
import com.scouty.app.ui.theme.TextPrimary
import com.scouty.app.ui.theme.TextSecondary
import com.scouty.app.ui.theme.TextTertiary
import com.scouty.app.ui.theme.Water
import com.scouty.app.ui.theme.Warning
import java.util.Locale

private enum class AuthMode {
    LOGIN,
    REGISTER
}

enum class ProfileFlowMode {
    CREATE,
    EDIT
}

private data class AvatarOption(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val tint: Color
)

private val profileAvatars = listOf(
    AvatarOption("summit", "Varf", Lucide.Mountain, AccentGreen),
    AvatarOption("compass", "Busola", Lucide.Compass, Info),
    AvatarOption("route", "Traseu", Lucide.Route, Warning),
    AvatarOption("atlas", "Atlas", Lucide.Map, Water),
    AvatarOption("spark", "Scanteie", Lucide.Sparkles, AccentGreen),
    AvatarOption("star", "Stea", Lucide.Star, Danger)
)

private val homeRegionOptions = listOf(
    "Bucegi",
    "Brasov",
    "Piatra Craiului",
    "Fagaras",
    "Retezat",
    "Apuseni",
    "Parang",
    "Ceahlau",
    "Ciucas",
    "Baiului",
    "Postavaru",
    "Rodnei",
    "Maramures",
    "Calimani",
    "Siriu",
    "Leaota",
    "Cozia",
    "Macin",
    "Carpatii Orientali",
    "Carpatii Meridionali",
    "Carpatii Occidentali"
)

@Composable
fun AuthScreen(
    accountExists: Boolean,
    isLoading: Boolean,
    authMessage: String?,
    onClearMessage: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onGoogleSignIn: () -> Unit
) {
    var mode by rememberSaveable(accountExists) {
        mutableStateOf(if (accountExists) AuthMode.LOGIN else AuthMode.REGISTER)
    }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var localMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(mode) {
        localMessage = null
        onClearMessage()
    }

    val accent by animateColorAsState(
        targetValue = if (mode == AuthMode.LOGIN) AccentGreen else Warning,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "authAccent"
    )
    val onAccent = if (mode == AuthMode.LOGIN) AccentGreenOnSurface else Color(0xFF2A1A05)

    ScoutyBackdrop(mode = mode) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            AuthHero(mode = mode, accent = accent)
            Spacer(modifier = Modifier.height(22.dp))

            ScoutyCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    AuthModeTabs(
                        mode = mode,
                        onModeChange = { mode = it }
                    )
                    Spacer(Modifier.height(16.dp))

                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = {
                            val direction = if (targetState == AuthMode.REGISTER) {
                                AnimatedContentTransitionScope.SlideDirection.Left
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.Right
                            }
                            (slideIntoContainer(direction, animationSpec = tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(200)))
                                .togetherWith(slideOutOfContainer(direction, animationSpec = tween(320, easing = FastOutSlowInEasing)) + fadeOut(tween(180)))
                        },
                        label = "authForm"
                    ) { formMode ->
                        AuthFormContent(
                            mode = formMode,
                            accountExists = accountExists,
                            email = email,
                            password = password,
                            confirmPassword = confirmPassword,
                            message = if (isLoading) null else localMessage ?: authMessage,
                            isLoading = isLoading,
                            accent = if (formMode == AuthMode.LOGIN) AccentGreen else Warning,
                            onAccent = if (formMode == AuthMode.LOGIN) AccentGreenOnSurface else Color(0xFF2A1A05),
                            onEmailChange = {
                                email = it
                                localMessage = null
                                onClearMessage()
                            },
                            onPasswordChange = {
                                password = it
                                localMessage = null
                                onClearMessage()
                            },
                            onConfirmPasswordChange = {
                                confirmPassword = it
                                localMessage = null
                            },
                            onSubmit = {
                                if (isLoading) return@AuthFormContent
                                localMessage = when {
                                    email.isBlank() || password.isBlank() -> "Completează emailul și parola."
                                    formMode == AuthMode.REGISTER && password != confirmPassword ->
                                        "Parolele trebuie să coincidă înainte de configurarea profilului."
                                    else -> null
                                }
                                if (localMessage == null) {
                                    if (formMode == AuthMode.LOGIN) {
                                        onLogin(email, password)
                                    } else {
                                        onRegister(email, password)
                                    }
                                }
                            },
                            onGoogleSignIn = {
                                if (!isLoading) {
                                    onGoogleSignIn()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "v1.0.0",
                fontSize = 10.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }
    }
}

@Composable
private fun AuthHero(mode: AuthMode, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.12f))
                .border(0.5.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Lucide.Mountain, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Crossfade(targetState = mode, animationSpec = tween(200), label = "authTitle") { currentMode ->
            Text(
                text = if (currentMode == AuthMode.LOGIN) "Bine ai revenit în Scouty" else "Începe aventura",
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.5).sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(6.dp))
        Crossfade(targetState = mode, animationSpec = tween(200), label = "authSubtitle") { currentMode ->
            Text(
                text = if (currentMode == AuthMode.LOGIN) {
                    "Intră în profilul tău Scouty înainte să pornești pe traseu."
                } else {
                    "Creează contul și profilul de drumeț în câțiva pași."
                },
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp)
            )
        }
    }
}

@Composable
private fun AuthModeTabs(mode: AuthMode, onModeChange: (AuthMode) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(3.dp)
    ) {
        val tabWidth = (maxWidth - 6.dp) / 2
        val indicatorOffset by animateDpAsState(
            targetValue = if (mode == AuthMode.LOGIN) 0.dp else tabWidth,
            animationSpec = tween(280, easing = FastOutSlowInEasing),
            label = "authTabOffset"
        )
        val indicatorColor by animateColorAsState(
            targetValue = if (mode == AuthMode.LOGIN) AccentGreen.copy(alpha = 0.18f) else Warning.copy(alpha = 0.18f),
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "authTabColor"
        )
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(indicatorColor)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            AuthTab(
                selected = mode == AuthMode.LOGIN,
                icon = Lucide.LogIn,
                label = "Autentificare",
                activeColor = AccentGreen,
                modifier = Modifier.weight(1f),
                onClick = { onModeChange(AuthMode.LOGIN) }
            )
            AuthTab(
                selected = mode == AuthMode.REGISTER,
                icon = Lucide.UserPlus,
                label = "Înregistrare",
                activeColor = Warning,
                modifier = Modifier.weight(1f),
                onClick = { onModeChange(AuthMode.REGISTER) }
            )
        }
    }
}

@Composable
private fun AuthTab(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val contentColor = if (selected) activeColor else TextSecondary
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = contentColor)
    }
}

@Composable
private fun AuthFormContent(
    mode: AuthMode,
    accountExists: Boolean,
    email: String,
    password: String,
    confirmPassword: String,
    message: String?,
    isLoading: Boolean,
    accent: Color,
    onAccent: Color,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignIn: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AuthHelperRow(mode = mode, accountExists = accountExists, accent = accent)
        Spacer(Modifier.height(14.dp))
        AuthTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "E-MAIL",
            placeholder = "nume@email.com",
            icon = Lucide.Mail,
            accent = accent,
            keyboardType = KeyboardType.Email
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "PAROLĂ",
            placeholder = "Parola ta",
            icon = Lucide.Lock,
            accent = accent,
            keyboardType = KeyboardType.Password,
            password = true
        )
        if (mode == AuthMode.REGISTER) {
            PasswordStrengthMeter(password = password)
            Spacer(Modifier.height(12.dp))
            AuthTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = "CONFIRMĂ PAROLA",
                placeholder = "Repetă parola",
                icon = Lucide.Lock,
                accent = accent,
                keyboardType = KeyboardType.Password,
                password = true
            )
        }
        message?.let {
            Spacer(Modifier.height(12.dp))
            MessageBanner(message = it)
        }
        Spacer(Modifier.height(16.dp))
        AuthPrimaryButton(
            mode = mode,
            accent = accent,
            onAccent = onAccent,
            enabled = !isLoading,
            onClick = onSubmit
        )
        Spacer(Modifier.height(10.dp))
        GoogleAuthButton(enabled = !isLoading, onClick = onGoogleSignIn)
        Spacer(Modifier.height(10.dp))
        if (mode == AuthMode.LOGIN) {
            Text(
                text = "Ai uitat parola?",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = AccentGreen,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = buildAnnotatedString {
                    append("Continuând, ești de acord cu ")
                    withStyle(SpanStyle(color = Warning, fontWeight = FontWeight.Medium)) { append("termenii") }
                },
                fontSize = 10.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AuthHelperRow(mode: AuthMode, accountExists: Boolean, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.05f))
            .border(0.5.dp, accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Lucide.InfoIcon, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
        Text(
            text = when {
                mode == AuthMode.REGISTER && accountExists ->
                    "Înregistrarea creează un cont nou pentru acest dispozitiv."
                mode == AuthMode.REGISTER ->
                    "După creare, configurăm profilul și îl sincronizăm în siguranță."
                else ->
                    "Bine ai revenit. Încărcăm profilul tău."
            },
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = TextPrimary.copy(alpha = 0.75f),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AuthPrimaryButton(
    mode: AuthMode,
    accent: Color,
    onAccent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) accent else accent.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (mode == AuthMode.LOGIN) "Intră în Scouty" else "Creează profilul",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = onAccent
        )
        Spacer(Modifier.width(8.dp))
        Icon(Lucide.ChevronRight, contentDescription = null, tint = onAccent, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun GoogleAuthButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.08f else 0.04f))
            .border(0.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(alpha = if (enabled) 1f else 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .alpha(if (enabled) 1f else 0.5f)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Continuă cu Google",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) TextPrimary else TextTertiary
        )
    }
}

@Composable
fun ProfileOnboardingScreen(
    mode: ProfileFlowMode,
    email: String,
    initialDraft: OnboardingDraft,
    onBack: () -> Unit,
    onComplete: (OnboardingDraft) -> Unit
) {
    val questions = remember { ProfileAssessmentEngine.questions }
    val questionGroups = remember(questions) {
        if (questions.size <= 9) {
            questions.map { listOf(it) }
        } else {
            questions.take(8).map { listOf(it) } + listOf(questions.drop(8))
        }
    }
    val lastInteractiveStep = questionGroups.size

    var currentStep by rememberSaveable(mode, email) { mutableIntStateOf(0) }
    var displayName by rememberSaveable(mode, initialDraft.displayName) { mutableStateOf(initialDraft.displayName) }
    var homeRegion by rememberSaveable(mode, initialDraft.homeRegion) { mutableStateOf(initialDraft.homeRegion) }
    var avatarId by rememberSaveable(mode, initialDraft.avatarId) { mutableStateOf(initialDraft.avatarId) }
    var answers by rememberSaveable(mode, initialDraft.answers.toString()) { mutableStateOf(initialDraft.answers) }

    val canContinue = when {
        currentStep == 0 -> displayName.trim().length >= 2 && homeRegion.trim().length >= 2
        currentStep in 1..lastInteractiveStep -> questionGroups[currentStep - 1].all { question ->
            answers[question.id] != null
        }
        else -> true
    }
    val result = remember(answers) { ProfileAssessmentEngine.evaluate(answers) }
    val totalSteps = lastInteractiveStep + 2
    val stepNumber = (currentStep + 1).coerceAtMost(totalSteps)
    val isSummary = currentStep > lastInteractiveStep
    val stepTitle = when {
        currentStep == 0 -> "Configurare profil"
        currentStep in 1..lastInteractiveStep -> localizedStepTitle(questionGroups[currentStep - 1])
        else -> "Nivel de start pregatit"
    }
    val flowSubtitle = when {
        isSummary -> "Aproape gata"
        mode == ProfileFlowMode.CREATE -> "Configureaza profilul Scouty"
        else -> "Ajusteaza profilul Scouty"
    }

    OnboardingStepScaffold(
        stepNumber = stepNumber,
        totalSteps = totalSteps,
        flowSubtitle = flowSubtitle,
        stepTitle = stepTitle,
        estimatedTimeRemaining = if (currentStep == 0) "~5 min" else null,
        onBack = {
            if (currentStep == 0) {
                onBack()
            } else {
                currentStep -= 1
            }
        },
        onContinue = {
            if (!canContinue) return@OnboardingStepScaffold
            if (isSummary) {
                onComplete(
                    OnboardingDraft(
                        displayName = displayName.trim(),
                        avatarId = avatarId,
                        homeRegion = homeRegion.trim(),
                        answers = answers
                    )
                )
            } else {
                currentStep += 1
            }
        },
        isContinueEnabled = canContinue,
        continueLabel = when {
            isSummary -> "Salveaza profilul"
            currentStep == lastInteractiveStep -> "Vezi nivelul de start"
            else -> "Continua"
        },
        backLabel = if (currentStep == 0) "Anuleaza" else "Inapoi",
        showBack = true,
    ) {
        when {
            currentStep == 0 -> ProfileConfigStep(
                email = email,
                displayName = displayName,
                onDisplayNameChange = { displayName = it },
                homeRegion = homeRegion,
                onHomeRegionChange = { homeRegion = it },
                avatarId = avatarId,
                onAvatarSelected = { avatarId = it }
            )

            currentStep in 1..lastInteractiveStep -> QuestionStep(
                questions = questionGroups[currentStep - 1],
                answers = answers,
                onSelect = { questionId, optionId ->
                    answers = answers + (questionId to optionId)
                }
            )

            else -> ResultStep(
                email = email,
                displayName = displayName,
                homeRegion = homeRegion,
                avatarId = avatarId,
                result = result,
                answers = answers
            )
        }
    }
}

@Composable
private fun ProfileConfigStep(
    email: String,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    homeRegion: String,
    onHomeRegionChange: (String) -> Unit,
    avatarId: String,
    onAvatarSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "Seteaza identitatea pe care Scouty o afiseaza in profil. Alege un nume clar si usor de recunoscut.",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BgSurfaceRaised)
                .border(0.5.dp, BorderDefault, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.CircleCheck,
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = email.ifBlank { "email cont" },
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        LabeledOnboardingTextField(
            label = "NUME AFISAT",
            value = displayName,
            onValueChange = onDisplayNameChange,
            placeholder = "Numele tau",
            helper = "3-30 caractere",
            isError = displayName.isNotBlank() && displayName.trim().length < 2,
        )

        HomeRegionDropdownField(
            value = homeRegion,
            onValueChange = onHomeRegionChange,
            isError = homeRegion.isNotBlank() && homeRegion.trim().length < 2,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel("ALEGE INSIGNA")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profileAvatars.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { avatar ->
                            BadgeOptionCard(
                                avatar = avatar,
                                selected = avatar.id == avatarId,
                                onClick = { onAvatarSelected(avatar.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRegionDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val query = value.trim()
    val options = remember(query) {
        val normalizedQuery = normalizeRegionQuery(query)
        val matches = if (normalizedQuery.isBlank()) {
            homeRegionOptions
        } else {
            homeRegionOptions.filter { normalizeRegionQuery(it).contains(normalizedQuery) }
        }
        matches.take(7)
    }

    Column {
        FieldLabel("REGIUNE DE ACASA")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .shadow(if (value.isNotBlank()) 3.dp else 0.dp, RoundedCornerShape(12.dp))
                .background(BgSurfaceRaised, RoundedCornerShape(12.dp))
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        expanded = true
                    }
                },
            singleLine = true,
            placeholder = {
                Text(
                    text = "Cauta sau alege regiunea...",
                    color = TextTertiary,
                    fontSize = 14.sp,
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
                    contentDescription = if (expanded) "Inchide lista" else "Deschide lista",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { expanded = !expanded },
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = TextPrimary,
                fontSize = 14.sp,
            ),
            shape = RoundedCornerShape(12.dp),
            isError = isError,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = BgSurfaceRaised,
                unfocusedContainerColor = BgSurfaceRaised,
                disabledContainerColor = BgSurfaceRaised,
                errorContainerColor = BgSurfaceRaised,
                focusedIndicatorColor = AccentGreen,
                unfocusedIndicatorColor = BorderDefault,
                errorIndicatorColor = Danger,
                cursorColor = AccentGreen,
            )
        )
        if (expanded && options.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgSurfaceRaised)
                    .border(0.5.dp, BorderDefault, RoundedCornerShape(12.dp))
                    .padding(vertical = 4.dp)
            ) {
                options.forEach { option ->
                    RegionDropdownOption(
                        label = option,
                        selected = option.equals(value.trim(), ignoreCase = true),
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isError) "Alege sau scrie cel putin 2 caractere." else "Poti selecta din lista sau cauta prin tastare.",
            color = if (isError) Danger else TextTertiary,
            fontSize = 10.sp,
            lineHeight = 13.sp,
        )
    }
}

@Composable
private fun RegionDropdownOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) AccentGreen.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = if (selected) AccentGreen else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
        if (selected) {
            Icon(
                imageVector = Lucide.Check,
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

private fun normalizeRegionQuery(value: String): String =
    value
        .lowercase(Locale.ROOT)
        .replace("ă", "a")
        .replace("â", "a")
        .replace("î", "i")
        .replace("ș", "s")
        .replace("ş", "s")
        .replace("ț", "t")
        .replace("ţ", "t")

@Composable
private fun LabeledOnboardingTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    helper: String,
    isError: Boolean,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(if (value.isNotBlank()) 3.dp else 0.dp, RoundedCornerShape(12.dp))
                .background(BgSurfaceRaised, RoundedCornerShape(12.dp)),
            singleLine = true,
            placeholder = {
                Text(
                    text = placeholder,
                    color = TextTertiary,
                    fontSize = 14.sp,
                )
            },
            trailingIcon = trailing,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = TextPrimary,
                fontSize = 14.sp,
            ),
            shape = RoundedCornerShape(12.dp),
            isError = isError,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = BgSurfaceRaised,
                unfocusedContainerColor = BgSurfaceRaised,
                disabledContainerColor = BgSurfaceRaised,
                errorContainerColor = BgSurfaceRaised,
                focusedIndicatorColor = AccentGreen,
                unfocusedIndicatorColor = BorderDefault,
                errorIndicatorColor = Danger,
                cursorColor = AccentGreen,
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = helper,
            color = if (isError) Danger else TextTertiary,
            fontSize = 10.sp,
            lineHeight = 13.sp,
        )
    }
}

@Composable
private fun BadgeOptionCard(
    avatar: AvatarOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        targetValue = avatar.tint.copy(alpha = if (selected) 0.18f else 0.06f),
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "badgeBg",
    )
    val borderColor by animateColorAsState(
        targetValue = avatar.tint.copy(alpha = if (selected) 1f else 0.15f),
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "badgeBorder",
    )
    Box(
        modifier = modifier
            .aspectRatio(1.34f)
            .then(
                if (selected) {
                    Modifier.border(4.dp, avatar.tint.copy(alpha = 0.08f), RoundedCornerShape(15.dp))
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(if (selected) 1.5.dp else 0.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(avatar.tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Lucide.Check,
                    contentDescription = null,
                    tint = AccentGreenOnSurface,
                    modifier = Modifier.size(9.dp),
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(avatar.tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = avatar.icon,
                    contentDescription = avatar.title,
                    tint = avatar.tint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = avatar.title,
                color = if (selected) avatar.tint else TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuestionStep(
    questions: List<ProfileQuestion>,
    answers: Map<String, String>,
    onSelect: (String, String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        questions.forEach { question ->
            QuestionBlock(
                question = question,
                selectedOptionId = answers[question.id],
                onSelect = { optionId -> onSelect(question.id, optionId) },
            )
        }
    }
}

@Composable
private fun QuestionBlock(
    question: ProfileQuestion,
    selectedOptionId: String?,
    onSelect: (String) -> Unit,
) {
    val localized = remember(question.id) { localizedQuestion(question) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = localized.title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.3).sp,
            lineHeight = 24.sp,
        )
        Text(
            text = localized.helper,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            question.options.forEach { option ->
                val selected = option.id == selectedOptionId
                val optionText = localizedOption(question.id, option.id, option.label, option.description)
                SingleSelectOptionCard(
                    title = optionText.label,
                    helper = optionText.description,
                    selected = selected,
                    onClick = { onSelect(option.id) },
                )
            }
        }
    }
}

@Composable
private fun SingleSelectOptionCard(
    title: String,
    helper: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) AccentGreen.copy(alpha = 0.08f) else BgSurfaceRaised,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "optionBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) AccentGreen else BorderDefault,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "optionBorder",
    )
    val titleColor by animateColorAsState(
        targetValue = if (selected) AccentGreen else TextPrimary,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "optionTitle",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(4.dp, AccentGreen.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(if (selected) 1.5.dp else 0.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 13.dp else 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(if (selected) 2.dp else 1.5.dp, if (selected) AccentGreen else TextTertiary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AccentGreen),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = helper,
                color = if (selected) TextSecondary else TextTertiary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun ResultStep(
    email: String,
    displayName: String,
    homeRegion: String,
    avatarId: String,
    result: AssessmentResult,
    answers: Map<String, String>
) {
    val currentLevel = result.starterLevel
    val topStarterLevel = ScoutyLevel.LEVEL_3
    val summaryTags = listOfNotNull(
        localizedAnswerLabel("navigation", answers["navigation"]),
        localizedAnswerLabel("terrain", answers["terrain"]),
        localizedAnswerLabel("first_aid", answers["first_aid"])
    )
    val avatar = remember(avatarId) { profileAvatars.firstOrNull { it.id == avatarId } ?: profileAvatars.first() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            AccentGreenBg,
                            AccentGreen.copy(alpha = 0.03f),
                            Color.Transparent,
                        )
                    )
                )
                .border(0.5.dp, AccentGreen.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(avatar.tint.copy(alpha = 0.12f))
                        .border(0.5.dp, avatar.tint.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = avatar.icon,
                        contentDescription = null,
                        tint = avatar.tint,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName.ifBlank { "Profil Scouty" },
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.2).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    TierPill(level = currentLevel)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${email.ifBlank { "email cont" }} · ${homeRegion.ifBlank { "regiune nealeasa" }}",
                        color = TextTertiary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                text = buildAnnotatedString {
                    append("Scouty acorda niveluri de start pana la ")
                    withStyle(SpanStyle(color = AccentGreen, fontWeight = FontWeight.Medium)) {
                        append(levelTitleRo(topStarterLevel))
                    }
                    append(". Restul scarii se deblocheaza mai tarziu prin ture reale, nu prin auto-raportare.")
                },
                color = TextPrimary.copy(alpha = 0.7f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoutyCard(
                    modifier = Modifier.weight(1f),
                    semantic = AccentGreen,
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Text(
                        text = "${result.score}",
                        color = AccentGreen,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = "SCOR CONDIȚIE",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                ScoutyCard(
                    modifier = Modifier.weight(1f),
                    semantic = Warning,
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${currentLevel.number + 1}",
                            color = Warning,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.5).sp,
                        )
                        Text(
                            text = " / 10",
                            color = TextTertiary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    Text(
                        text = "PROGRES NIVEL",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        if (summaryTags.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("SEMNALE CHEIE")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    summaryTags.forEachIndexed { index, tag ->
                        val semantic = when (index) {
                            0 -> Info
                            1 -> AccentGreen
                            else -> Warning
                        }
                        SignalPill(text = tag, semantic = semantic)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FieldLabel("SCARA COMPLETA")
                Text(
                    text = "${currentLevel.number + 1} din 10 deblocate",
                    color = TextTertiary,
                    fontSize = 10.sp,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ScoutyLevel.entries.forEach { level ->
                    LadderPill(level = level, currentLevel = currentLevel)
                }
            }
        }
    }
}

@Composable
fun AvatarBadge(
    avatarId: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 58.dp
) {
    val avatar = remember(avatarId) { profileAvatars.firstOrNull { it.id == avatarId } ?: profileAvatars.first() }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(avatar.tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = avatar.icon,
            contentDescription = avatar.title,
            tint = avatar.tint,
            modifier = Modifier.size(size * 0.54f)
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        lineHeight = 12.sp,
    )
}

@Composable
private fun TierPill(level: ScoutyLevel) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AccentGreenBg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Lucide.Star,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(10.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Nivel ${level.number} · ${levelTitleRo(level)}",
            color = AccentGreen,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SignalPill(text: String, semantic: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(semantic.copy(alpha = 0.1f))
            .border(0.5.dp, semantic.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            color = semantic,
            fontSize = 11.sp,
            lineHeight = 13.sp,
        )
    }
}

@Composable
private fun LadderPill(level: ScoutyLevel, currentLevel: ScoutyLevel) {
    val unlocked = level.number < currentLevel.number
    val current = level == currentLevel
    val reachable = level.number == currentLevel.number + 1 && level.number <= ScoutyLevel.LEVEL_3.number
    val locked = !unlocked && !current && !reachable
    val shape = RoundedCornerShape(20.dp)
    val baseModifier = Modifier
        .clip(shape)
        .then(
            when {
                current -> Modifier.border(3.dp, AccentGreen.copy(alpha = 0.1f), shape)
                reachable -> Modifier.dashedBorder(AccentGreen.copy(alpha = 0.3f), shape)
                else -> Modifier
            }
        )
        .background(
            when {
                current -> AccentGreen.copy(alpha = 0.18f)
                unlocked -> AccentGreen.copy(alpha = 0.12f)
                reachable -> AccentGreen.copy(alpha = 0.06f)
                else -> Color(0xFFFFFFFF).copy(alpha = 0.03f)
            },
            shape,
        )
        .border(
            width = if (current) 1.dp else 0.5.dp,
            color = when {
                current -> AccentGreen
                unlocked -> AccentGreen.copy(alpha = 0.3f)
                locked -> BorderDefault
                else -> Color.Transparent
            },
            shape = shape,
        )
        .padding(horizontal = 10.dp, vertical = 6.dp)

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when {
            unlocked -> Icon(
                imageVector = Lucide.Check,
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(9.dp),
            )
            locked -> Icon(
                imageVector = Lucide.Lock,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(9.dp),
            )
        }
        Text(
            text = levelTitleRo(level),
            color = when {
                locked -> TextTertiary
                reachable -> AccentGreen.copy(alpha = 0.7f)
                else -> AccentGreen
            },
            fontSize = 11.sp,
            fontWeight = if (current || unlocked) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

private fun Modifier.dashedBorder(color: Color, shape: RoundedCornerShape): Modifier =
    drawBehind {
        val strokeWidth = 1.dp.toPx()
        drawRoundRect(
            color = color,
            style = Stroke(
                width = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f),
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx(), 20.dp.toPx()),
        )
    }

private data class LocalizedQuestionText(
    val title: String,
    val helper: String,
)

private data class LocalizedOptionText(
    val label: String,
    val description: String,
)

private fun localizedStepTitle(questions: List<ProfileQuestion>): String =
    if (questions.size == 1) {
        localizedStepTitle(questions.first())
    } else {
        "Context si siguranta"
    }

private fun localizedStepTitle(question: ProfileQuestion): String =
    when (question.id) {
        "hike_frequency" -> "Frecventa drumetiilor"
        "max_distance" -> "Cea mai lunga tura"
        "physical_condition" -> "Conditie fizica"
        "navigation" -> "Orientare"
        "terrain" -> "Teren"
        "conditions" -> "Conditii meteo"
        "gear_setup" -> "Echipament"
        "hike_style" -> "Stil de drumetie"
        ProfileAssessmentEngine.AgeQuestionId -> "Varsta"
        "first_aid" -> "Prim ajutor"
        else -> question.title
    }

private fun localizedQuestion(question: ProfileQuestion): LocalizedQuestionText =
    when (question.id) {
        "hike_frequency" -> LocalizedQuestionText(
            "Cat de des mergi in drumetii?",
            "Asta ii arata lui Scouty cat de activ esti deja pe trasee.",
        )
        "max_distance" -> LocalizedQuestionText(
            "Care este cea mai lunga tura de o zi pe care ai terminat-o?",
            "Alege distanta unei singure zile, nu totalul unei ture de mai multe zile.",
        )
        "physical_condition" -> LocalizedQuestionText(
            "Cum este conditia ta fizica acum?",
            "Conteaza forma actuala, nu cel mai bun an al tau.",
        )
        "navigation" -> LocalizedQuestionText(
            "Cat de sigur esti pe orientare?",
            "Gandeste-te la harti, decizii de traseu, rerutare si calm cand poteca devine neclara.",
        )
        "terrain" -> LocalizedQuestionText(
            "Ce teren poti gestiona controlat?",
            "Alege varianta care inca se simte stabila, nu haotica.",
        )
        "conditions" -> LocalizedQuestionText(
            "In ce conditii iesi totusi pe traseu?",
            "Ajuta la estimarea rezistentei si a nivelului de prudenta.",
        )
        "gear_setup" -> LocalizedQuestionText(
            "Cat de bine este pus la punct echipamentul tau?",
            "Scouty foloseste asta pentru recomandari mai bune mai tarziu.",
        )
        "hike_style" -> LocalizedQuestionText(
            "Ce fel de ture te atrag?",
            "Preferinta conteaza, dar nu trebuie sa domine scorul de abilitate.",
        )
        ProfileAssessmentEngine.AgeQuestionId -> LocalizedQuestionText(
            "Care este intervalul tau de varsta?",
            "Este folosit pentru context de profil si recomandari, nu ca sa iti scada nivelul de start.",
        )
        "first_aid" -> LocalizedQuestionText(
            "Cunosti primul ajutor de baza pe traseu?",
            "Asta creste autonomia pe care Scouty o presupune pentru tine in teren.",
        )
        else -> LocalizedQuestionText(question.title, question.helper)
    }

private fun localizedOption(
    questionId: String,
    optionId: String,
    fallbackLabel: String,
    fallbackDescription: String,
): LocalizedOptionText =
    localizedOptions[questionId to optionId] ?: LocalizedOptionText(fallbackLabel, fallbackDescription)

private fun localizedAnswerLabel(questionId: String, optionId: String?): String? =
    optionId?.let { localizedOptions[questionId to it]?.label }
        ?: ProfileAssessmentEngine.answerLabel(questionId, optionId)

private val localizedOptions = mapOf(
    ("hike_frequency" to "rarely") to LocalizedOptionText("Rar", "De cateva ori pe an sau mai putin."),
    ("hike_frequency" to "seasonal") to LocalizedOptionText("La cateva luni", "Iesi cand apare un weekend bun."),
    ("hike_frequency" to "monthly") to LocalizedOptionText("1-2 ori pe luna", "Drumetia face deja parte din rutina."),
    ("hike_frequency" to "weekly") to LocalizedOptionText("Aproape saptamanal", "Ajungi pe traseu in majoritatea saptamanilor."),
    ("hike_frequency" to "constant") to LocalizedOptionText("De mai multe ori pe saptamana", "Traseele sunt ritmul tau normal."),

    ("max_distance" to "under_5") to LocalizedOptionText("Sub 5 km", "Plimbari scurte sau bucle usoare."),
    ("max_distance" to "5_10") to LocalizedOptionText("5-10 km", "Ture scurte spre medii, de jumatate de zi."),
    ("max_distance" to "10_15") to LocalizedOptionText("10-15 km", "Zona clasica de tura de o zi."),
    ("max_distance" to "15_20") to LocalizedOptionText("15-20 km", "Efort mai lung, cu ritm real."),
    ("max_distance" to "20_plus") to LocalizedOptionText("20+ km", "Zilele mari sunt deja pe masa."),

    ("physical_condition" to "restart") to LocalizedOptionText("Reincep usor", "Vrei sa revii treptat si cu grija."),
    ("physical_condition" to "short") to LocalizedOptionText("Bun pentru ture scurte", "Poti tine un ritm stabil in zile mai usoare."),
    ("physical_condition" to "solid") to LocalizedOptionText("Solid", "O zi intreaga de drumetie pare realista."),
    ("physical_condition" to "strong") to LocalizedOptionText("Foarte bun", "Te refaci bine dupa efort lung."),
    ("physical_condition" to "endurance") to LocalizedOptionText("Pregatit pentru anduranta", "Efortul sustinut face parte din plan."),

    ("navigation" to "marked_only") to LocalizedOptionText("Doar trasee marcate", "Te bazezi pe marcaje evidente."),
    ("navigation" to "basic_map") to LocalizedOptionText("Traseu + harta simpla", "Poti urma ajutoare de baza pentru ruta."),
    ("navigation" to "gps_ok") to LocalizedOptionText("Folosesc GPS bine", "Navigatia pe telefon iti este deja utila."),
    ("navigation" to "map_gps") to LocalizedOptionText("Harta + GPS + rerutare", "Poti repara mici greseli de traseu."),
    ("navigation" to "independent") to LocalizedOptionText("Planific si navighez singur", "Poti construi si urma o ruta pe cont propriu."),

    ("terrain" to "forest_road") to LocalizedOptionText("Drumuri forestiere", "Poteci late si teren foarte bland."),
    ("terrain" to "standard") to LocalizedOptionText("Trasee standard", "Traseele marcate normale sunt in regula."),
    ("terrain" to "steep") to LocalizedOptionText("Urcari abrupte", "Esti ok cu urcare sustinuta."),
    ("terrain" to "technical_light") to LocalizedOptionText("Piatra instabila / pasaje tehnice", "Ramanai calm pe teren mai dificil."),
    ("terrain" to "ridge") to LocalizedOptionText("Creste / zone expuse", "Liniile inguste si aeriene sunt gestionabile."),

    ("conditions" to "perfect") to LocalizedOptionText("Doar vreme perfecta", "Uscat, stabil si usor de citit."),
    ("conditions" to "cool") to LocalizedOptionText("Racoare sau vant usor", "Un pic de disconfort este acceptabil."),
    ("conditions" to "mixed") to LocalizedOptionText("Ploaie sau vant mai tare", "Poti continua cand vremea se strica."),
    ("conditions" to "three_season") to LocalizedOptionText("Zile mixte trei sezoane", "Vremea schimbatoare de munte intra in calcul."),
    ("conditions" to "winter") to LocalizedOptionText("Si iarna sau zapada", "Turele de sezon rece sunt deja in joc."),

    ("gear_setup" to "improvise") to LocalizedOptionText("Improvizez", "Impachetezi mai mult dupa instinct."),
    ("gear_setup" to "basics") to LocalizedOptionText("Stiu baza", "De obicei iei lucrurile esentiale."),
    ("gear_setup" to "checklist") to LocalizedOptionText("Am listă", "Urmaresti o structura repetabila."),
    ("gear_setup" to "route_tuned") to LocalizedOptionText("Adaptez la traseu", "Ajustezi dupa distanta, teren si prognoza."),
    ("gear_setup" to "locked_in") to LocalizedOptionText("Kitul e stabil", "Ai deja un sistem rafinat."),

    ("hike_style" to "scenic") to LocalizedOptionText("Scurt si scenic", "Recompensa rapida si stres mic."),
    ("hike_style" to "classic_day") to LocalizedOptionText("Ture clasice de o zi", "Plan montan echilibrat, pe o zi intreaga."),
    ("hike_style" to "long_effort") to LocalizedOptionText("Zile lungi de efort", "Iti plac distanta si ritmul sustinut."),
    ("hike_style" to "peaks") to LocalizedOptionText("Vârfuri și creste", "Vârfurile și terenul ascuțit te atrag."),
    ("hike_style" to "adventure") to LocalizedOptionText("Zile solicitante de aventura", "Obiectivele mari fac parte din placere."),

    (ProfileAssessmentEngine.AgeQuestionId to "under_18") to LocalizedOptionText("Sub 18", "Energie tanara, motor in crestere."),
    (ProfileAssessmentEngine.AgeQuestionId to "18_24") to LocalizedOptionText("18-24", "Ani cu recuperare rapida."),
    (ProfileAssessmentEngine.AgeQuestionId to "25_34") to LocalizedOptionText("25-34", "Fereastra puternica pentru drumetii."),
    (ProfileAssessmentEngine.AgeQuestionId to "35_44") to LocalizedOptionText("35-44", "Baza solida si experienta in amestec."),
    (ProfileAssessmentEngine.AgeQuestionId to "45_54") to LocalizedOptionText("45-54", "Eficienta incepe sa conteze mai mult."),
    (ProfileAssessmentEngine.AgeQuestionId to "55_plus") to LocalizedOptionText("55+", "Tehnica si ritmul conduc ziua."),

    ("first_aid" to "none") to LocalizedOptionText("Nu prea", "Preferi sa eviti miscari gresite."),
    ("first_aid" to "few_basics") to LocalizedOptionText("Cateva baze", "Stii ideile generale."),
    ("first_aid" to "common_issues") to LocalizedOptionText("Da, probleme comune", "Poti gestiona situatii simple pe traseu."),
    ("first_aid" to "confident") to LocalizedOptionText("Da, cu incredere", "Poti actiona calm in scenarii comune."),
)

private fun levelTitleRo(level: ScoutyLevel): String =
    when (level) {
        ScoutyLevel.LEVEL_1 -> "Începător"
        ScoutyLevel.LEVEL_2 -> "Drumeț în formare"
        ScoutyLevel.LEVEL_3 -> "Explorator de poteci"
        ScoutyLevel.LEVEL_4 -> "Călător pe dealuri"
        ScoutyLevel.LEVEL_5 -> "Navigator de traseu"
        ScoutyLevel.LEVEL_6 -> "Căutător de vârfuri"
        ScoutyLevel.LEVEL_7 -> "Cuceritor de creste"
        ScoutyLevel.LEVEL_8 -> "Expert în drumeții"
        ScoutyLevel.LEVEL_9 -> "Maestru al vârfurilor"
        ScoutyLevel.LEVEL_10 -> "Maestru montan"
    }

@Composable
private fun MessageBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = StatusRedSoft,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5A2A27))
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFFFC2BA)
        )
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    accent: Color,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    var revealPassword by rememberSaveable { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) accent else BorderDefault,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "authFieldBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = if (focused) accent.copy(alpha = 0.04f) else BgSurfaceRaised,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "authFieldBg"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (focused) {
                        Modifier.border(4.dp, accent.copy(alpha = 0.08f), RoundedCornerShape(15.dp))
                    } else {
                        Modifier
                    }
                )
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(if (focused) 1.dp else 0.5.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (focused) accent else TextTertiary,
                modifier = Modifier.size(13.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                if (value.isBlank()) {
                    Text(text = placeholder, fontSize = 13.sp, color = TextTertiary, maxLines = 1)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    ),
                    visualTransformation = if (password && !revealPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = ImeAction.Next
                    ),
                    cursorBrush = SolidColor(accent)
                )
            }
            if (password) {
                Icon(
                    imageVector = if (revealPassword) Lucide.EyeOff else Lucide.Eye,
                    contentDescription = if (revealPassword) "Ascunde parola" else "Arată parola",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(15.dp)
                        .clickable { revealPassword = !revealPassword }
                )
            }
        }
    }
}

@Composable
private fun PasswordStrengthMeter(password: String) {
    val score = passwordStrengthScore(password)
    if (password.isBlank()) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFFFFFFF).copy(alpha = 0.08f))
                )
            }
        }
        return
    }
    val color = when (score) {
        1 -> Danger
        2, 3 -> Warning
        else -> AccentGreen
    }
    val label = when (score) {
        1 -> "Prea slabă"
        2 -> "Acceptabilă"
        3 -> "Bună"
        else -> "Puternică"
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < score) color else Color(0xFFFFFFFF).copy(alpha = 0.08f))
            )
        }
    }
    Spacer(Modifier.height(5.dp))
    Text(text = label, fontSize = 10.sp, color = TextTertiary)
}

private fun passwordStrengthScore(password: String): Int {
    if (password.isBlank()) return 0
    var score = 1
    if (password.length >= 8) score += 1
    if (password.any(Char::isDigit) && password.any(Char::isLetter)) score += 1
    if (password.any { !it.isLetterOrDigit() }) score += 1
    return score.coerceIn(1, 4)
}

@Composable
private fun ScoutyBackdrop(mode: AuthMode, content: @Composable () -> Unit) {
    val glowColor by animateColorAsState(
        targetValue = if (mode == AuthMode.LOGIN) AccentGreen.copy(alpha = 0.06f) else Warning.copy(alpha = 0.07f),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "authGlow"
    )
    val glowOffset by animateFloatAsState(
        targetValue = if (mode == AuthMode.LOGIN) 0f else 1f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "authGlowOffset"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-50).dp + (170.dp * glowOffset), y = (-100).dp)
                .size(220.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        radius = 220f
                    )
                )
        )
        PineTreeline(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.BottomCenter)
        )
        content()
    }
}

@Composable
private fun PineTreeline(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        fun addTree(path: Path, centerX: Float, baseY: Float, baseWidth: Float, treeHeight: Float) {
            path.moveTo(centerX, baseY - treeHeight)
            path.lineTo(centerX - baseWidth / 2f, baseY)
            path.lineTo(centerX + baseWidth / 2f, baseY)
            path.close()
        }
        val back = Path()
        repeat(7) { index ->
            val center = width * ((index + 0.35f) / 7f)
            addTree(back, center, height, 34.dp.toPx(), (52 + (index % 3) * 7).dp.toPx())
        }
        val front = Path()
        repeat(13) { index ->
            val center = width * ((index + 0.2f) / 13f)
            addTree(front, center, height, (20 + (index % 4) * 2).dp.toPx(), (82 + (index % 5) * 12).dp.toPx())
        }
        drawPath(back, color = Color(0xFF0F2614).copy(alpha = 0.28f))
        drawPath(front, color = Color(0xFF1A3A1F).copy(alpha = 0.5f))
        drawRect(
            brush = Brush.verticalGradient(
                0f to BgPrimary.copy(alpha = 0.0f),
                0.45f to BgPrimary.copy(alpha = 0.25f),
                1f to BgPrimary.copy(alpha = 0.0f)
            )
        )
    }
}
