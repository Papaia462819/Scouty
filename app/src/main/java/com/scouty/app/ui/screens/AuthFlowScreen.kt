@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.scouty.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scouty.app.profile.AssessmentResult
import com.scouty.app.profile.OnboardingDraft
import com.scouty.app.profile.ProfileAssessmentEngine
import com.scouty.app.profile.ProfileQuestion
import com.scouty.app.profile.ScoutyLevel
import com.scouty.app.ui.components.StatusChip
import com.scouty.app.ui.theme.CardDarkAlt
import com.scouty.app.ui.theme.PrimaryGreen
import com.scouty.app.ui.theme.StatusAmber
import com.scouty.app.ui.theme.StatusBlue
import com.scouty.app.ui.theme.StatusOrange
import com.scouty.app.ui.theme.StatusRedSoft

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
    AvatarOption("summit", "Summit", Icons.Default.Landscape, PrimaryGreen),
    AvatarOption("compass", "Compass", Icons.Default.Explore, StatusBlue),
    AvatarOption("route", "Route", Icons.Default.Route, StatusOrange),
    AvatarOption("atlas", "Atlas", Icons.Default.Map, Color(0xFF7AD7C5)),
    AvatarOption("spark", "Spark", Icons.Default.AutoAwesome, Color(0xFFFFC75A)),
    AvatarOption("star", "Star", Icons.Default.Star, Color(0xFFFF8A65))
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
    val lastInteractiveStep = questions.size

    var currentStep by rememberSaveable(mode, email) { mutableIntStateOf(0) }
    var displayName by rememberSaveable(mode, initialDraft.displayName) { mutableStateOf(initialDraft.displayName) }
    var homeRegion by rememberSaveable(mode, initialDraft.homeRegion) { mutableStateOf(initialDraft.homeRegion) }
    var avatarId by rememberSaveable(mode, initialDraft.avatarId) { mutableStateOf(initialDraft.avatarId) }
    var answers by rememberSaveable(mode, initialDraft.answers.toString()) { mutableStateOf(initialDraft.answers) }

    val canContinue = when {
        currentStep == 0 -> displayName.trim().length >= 2 && homeRegion.trim().length >= 2
        currentStep in 1..lastInteractiveStep -> answers[questions[currentStep - 1].id] != null
        else -> true
    }
    val result = remember(answers) { ProfileAssessmentEngine.evaluate(answers) }
    val stepProgress = ((currentStep.coerceAtMost(lastInteractiveStep)).toFloat() / lastInteractiveStep.toFloat())
        .coerceIn(0f, 1f)

    ScoutyBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (currentStep == 0) {
                        onBack()
                    } else {
                        currentStep -= 1
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentStep <= lastInteractiveStep) {
                            "Step ${currentStep + 1} / ${lastInteractiveStep + 1}"
                        } else {
                            "Starter tier ready"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (mode == ProfileFlowMode.CREATE) {
                            "Build your Scouty identity"
                        } else {
                            "Tune your Scouty profile"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { stepProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        question = questions[currentStep - 1],
                        selectedOptionId = answers[questions[currentStep - 1].id],
                        onSelect = { optionId ->
                            answers = answers + (questions[currentStep - 1].id to optionId)
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

            Spacer(modifier = Modifier.height(12.dp))

            if (currentStep <= lastInteractiveStep) {
                Button(
                    onClick = {
                        if (!canContinue) return@Button
                        currentStep += 1
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canContinue,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(vertical = 15.dp)
                ) {
                    Text(
                        text = if (currentStep == lastInteractiveStep) "See my starter tier" else "Continue",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = {
                        onComplete(
                            OnboardingDraft(
                                displayName = displayName.trim(),
                                avatarId = avatarId,
                                homeRegion = homeRegion.trim(),
                                answers = answers
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(vertical = 15.dp)
                ) {
                    Text(
                        text = if (mode == ProfileFlowMode.CREATE) "Create profile" else "Save profile",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            TextButton(
                onClick = {
                    if (currentStep == 0) {
                        onBack()
                    } else if (currentStep > 0) {
                        currentStep -= 1
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (currentStep == 0) "Cancel" else "Back",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Profile configuration",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Set the identity that Scouty shows on the profile tab. Keep it clean and easy to recognize.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
            StatusChip(text = email)
            AuthTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = "Display name"
            )
            AuthTextField(
                value = homeRegion,
                onValueChange = onHomeRegionChange,
                label = "Home region"
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose your badge",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    profileAvatars.forEach { avatar ->
                        val selected = avatar.id == avatarId
                        Surface(
                            modifier = Modifier
                                .width(104.dp)
                                .clickable { onAvatarSelected(avatar.id) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) avatar.tint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) avatar.tint else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape,
                                    color = avatar.tint.copy(alpha = 0.18f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = avatar.icon,
                                            contentDescription = avatar.title,
                                            tint = avatar.tint
                                        )
                                    }
                                }
                                Text(
                                    text = avatar.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionStep(
    question: ProfileQuestion,
    selectedOptionId: String?,
    onSelect: (String) -> Unit
) {
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
            Text(
                text = question.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                lineHeight = 36.sp
            )
            Text(
                text = question.helper,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                question.options.forEach { option ->
                    val selected = option.id == selectedOptionId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.id) },
                        shape = RoundedCornerShape(22.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
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
        ProfileAssessmentEngine.answerLabel("navigation", answers["navigation"]),
        ProfileAssessmentEngine.answerLabel("terrain", answers["terrain"]),
        ProfileAssessmentEngine.answerLabel("first_aid", answers["first_aid"])
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Starter tier assigned",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Scouty only grants starter tiers up to ${topStarterLevel.title}. The rest of the ladder unlocks later from real use, not from self-report.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = CardDarkAlt,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarBadge(avatarId = avatarId, size = 72.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        StatusChip(text = "Level ${currentLevel.number} · ${currentLevel.title}")
                        Text(
                            text = "$email · $homeRegion",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Assessment",
                    value = "${result.score}",
                    accent = MaterialTheme.colorScheme.primary
                )
                ResultStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Starter cap",
                    value = "3 / 10",
                    accent = StatusAmber
                )
            }

            if (summaryTags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Key signals",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        summaryTags.forEach { tag ->
                            StatusChip(
                                text = tag,
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Full ladder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ScoutyLevel.entries.forEach { level ->
                        val unlocked = level.number <= currentLevel.number
                        StatusChip(
                            text = level.title,
                            containerColor = if (unlocked) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (unlocked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
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
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size / 3),
        color = avatar.tint.copy(alpha = 0.18f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = avatar.icon,
                contentDescription = avatar.title,
                tint = avatar.tint,
                modifier = Modifier.size(size * 0.54f)
            )
        }
    }
}

@Composable
private fun ResultStatCard(
    modifier: Modifier,
    title: String,
    value: String,
    accent: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
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
