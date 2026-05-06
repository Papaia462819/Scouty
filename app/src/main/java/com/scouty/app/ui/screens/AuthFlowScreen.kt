@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.scouty.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Map
import com.composables.icons.lucide.Mountain
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Star
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun AuthScreen(
    accountExists: Boolean,
    authMessage: String?,
    onClearMessage: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit
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

    ScoutyBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scouty",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Build a real hiker profile before you hit the trail. Local account, smart starter tier, cleaner profile data.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp
            )

            Surface(
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
                            .padding(4.dp)
                    ) {
                        AuthMode.entries.forEach { authMode ->
                            val selected = mode == authMode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { mode = authMode },
                                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
                            ) {
                                Text(
                                    text = if (authMode == AuthMode.LOGIN) "Login" else "Register",
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = when {
                            mode == AuthMode.REGISTER && accountExists ->
                                "Registering here replaces the single local account stored on this device."
                            mode == AuthMode.REGISTER ->
                                "Register creates the local account and launches the profile builder right away."
                            else ->
                                "Login opens the app with the profile already stored on this device."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )

                    AuthTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            localMessage = null
                            onClearMessage()
                        },
                        label = "Email",
                        keyboardType = KeyboardType.Email
                    )

                    AuthTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            localMessage = null
                            onClearMessage()
                        },
                        label = "Password",
                        keyboardType = KeyboardType.Password,
                        password = true
                    )

                    if (mode == AuthMode.REGISTER) {
                        AuthTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                localMessage = null
                            },
                            label = "Confirm password",
                            keyboardType = KeyboardType.Password,
                            password = true
                        )
                    }

                    val message = localMessage ?: authMessage
                    if (message != null) {
                        MessageBanner(message = message)
                    }

                    Button(
                        onClick = {
                            localMessage = when {
                                email.isBlank() || password.isBlank() -> "Fill in both email and password."
                                mode == AuthMode.REGISTER && password != confirmPassword ->
                                    "Passwords need to match before profile setup starts."
                                else -> null
                            }
                            if (localMessage == null) {
                                if (mode == AuthMode.LOGIN) {
                                    onLogin(email, password)
                                } else {
                                    onRegister(email, password)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = if (mode == AuthMode.LOGIN) "Enter Scouty" else "Build my profile",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
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
                text = email.ifBlank { "email local" },
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

        LabeledOnboardingTextField(
            label = "REGIUNE DE ACASA",
            value = homeRegion,
            onValueChange = onHomeRegionChange,
            placeholder = "Alege sau scrie regiunea...",
            helper = "Exemplu: Carpati, Bucegi, Brasov",
            isError = homeRegion.isNotBlank() && homeRegion.trim().length < 2,
            trailing = {
                Icon(
                    imageVector = Lucide.ChevronDown,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
            },
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
                        text = "${email.ifBlank { "email local" }} · ${homeRegion.ifBlank { "regiune nealeasa" }}",
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
                        text = "SCOR FITNESS",
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
    ("gear_setup" to "checklist") to LocalizedOptionText("Am checklist", "Urmaresti o structura repetabila."),
    ("gear_setup" to "route_tuned") to LocalizedOptionText("Adaptez la traseu", "Ajustezi dupa distanta, teren si prognoza."),
    ("gear_setup" to "locked_in") to LocalizedOptionText("Kitul e stabil", "Ai deja un sistem rafinat."),

    ("hike_style" to "scenic") to LocalizedOptionText("Scurt si scenic", "Recompensa rapida si stres mic."),
    ("hike_style" to "classic_day") to LocalizedOptionText("Ture clasice de o zi", "Plan montan echilibrat, pe o zi intreaga."),
    ("hike_style" to "long_effort") to LocalizedOptionText("Zile lungi de efort", "Iti plac distanta si ritmul sustinut."),
    ("hike_style" to "peaks") to LocalizedOptionText("Varfuri si creste", "Summiturile si terenul ascutit te atrag."),
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
        ScoutyLevel.LEVEL_1 -> "Junior"
        ScoutyLevel.LEVEL_2 -> "Campist incepator"
        ScoutyLevel.LEVEL_3 -> "Explorator de poteci"
        ScoutyLevel.LEVEL_4 -> "Urcator de dealuri"
        ScoutyLevel.LEVEL_5 -> "Navigator de traseu"
        ScoutyLevel.LEVEL_6 -> "Cautator de varfuri"
        ScoutyLevel.LEVEL_7 -> "Cuceritor de creste"
        ScoutyLevel.LEVEL_8 -> "Expert de drumetie"
        ScoutyLevel.LEVEL_9 -> "As de summit"
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
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun ScoutyBackdrop(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF081109),
                        Color(0xFF0B160D),
                        Color(0xFF071008)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 42.dp, end = 22.dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 140.dp, start = 8.dp)
                .size(120.dp)
                .clip(CircleShape)
                .background(StatusBlue.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 54.dp)
                .fillMaxWidth(0.86f)
                .height(140.dp)
                .clip(RoundedCornerShape(100.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f), RoundedCornerShape(100.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            PrimaryGreen.copy(alpha = 0.05f),
                            StatusOrange.copy(alpha = 0.03f),
                            CardDarkAlt.copy(alpha = 0.02f)
                        )
                    )
                )
        )
        content()
    }
}
