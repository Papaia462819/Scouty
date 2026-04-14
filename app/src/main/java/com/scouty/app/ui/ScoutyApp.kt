package com.scouty.app.ui

import androidx.annotation.StringRes
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scouty.app.profile.OnboardingDraft
import com.scouty.app.profile.ProfileViewModel
import com.scouty.app.profile.SessionStage
import com.scouty.app.profile.toOnboardingDraft
import com.scouty.app.assistant.ui.AssistantViewModel
import com.scouty.app.R
import com.scouty.app.ui.screens.AuthScreen
import com.scouty.app.ui.screens.ChatScreen
import com.scouty.app.ui.screens.GearScreen
import com.scouty.app.ui.screens.HomeScreen
import com.scouty.app.ui.screens.MapScreen
import com.scouty.app.ui.screens.ProfileFlowMode
import com.scouty.app.ui.screens.ProfileOnboardingScreen
import com.scouty.app.ui.screens.ProfileScreen
import com.scouty.app.ui.screens.SosScreen

private enum class TopDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(R.string.nav_home, Icons.Filled.Home),
    MAP(R.string.nav_map, Icons.Filled.Map),
    CHAT(R.string.nav_chat, Icons.AutoMirrored.Filled.Chat),
    SOS(R.string.nav_sos, Icons.Filled.Warning),
    GEAR(R.string.nav_gear, Icons.Filled.Checklist),
    PROFILE(R.string.nav_profile, Icons.Filled.Person),
}

@Composable
fun ScoutyApp(mainViewModel: MainViewModel = viewModel()) {
    val profileViewModel: ProfileViewModel = viewModel()
    val profileUiState by profileViewModel.uiState.collectAsState()
    var editingProfile by rememberSaveable { mutableStateOf(false) }

    when (profileUiState.stage) {
        SessionStage.AUTH -> {
            AuthScreen(
                accountExists = profileUiState.accountExists,
                authMessage = profileUiState.authMessage,
                onClearMessage = profileViewModel::clearAuthMessage,
                onLogin = profileViewModel::login,
                onRegister = profileViewModel::startRegistration
            )
            return
        }

        SessionStage.ONBOARDING -> {
            ProfileOnboardingScreen(
                mode = ProfileFlowMode.CREATE,
                email = profileUiState.pendingRegistrationEmail.orEmpty(),
                initialDraft = profileUiState.profile?.toOnboardingDraft() ?: OnboardingDraft(),
                onBack = profileViewModel::cancelRegistration,
                onComplete = profileViewModel::completeRegistration
            )
            return
        }

        SessionStage.APP -> Unit
    }

    val currentProfile = profileUiState.profile ?: return
    if (editingProfile) {
        ProfileOnboardingScreen(
            mode = ProfileFlowMode.EDIT,
            email = currentProfile.email,
            initialDraft = currentProfile.toOnboardingDraft(),
            onBack = { editingProfile = false },
            onComplete = {
                profileViewModel.updateProfile(it)
                editingProfile = false
            }
        )
        return
    }

    val application = LocalContext.current.applicationContext as Application
    val assistantViewModel: AssistantViewModel = viewModel(
        factory = remember(mainViewModel, application) {
            AssistantViewModel.Factory(
                application = application,
                deviceContextProvider = mainViewModel,
                chatActionHandler = mainViewModel
            )
        }
    )
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val uiState by mainViewModel.uiState.collectAsState()
    val mapSessionState by mainViewModel.mapSessionState.collectAsState()
    val assistantUiState by assistantViewModel.uiState.collectAsState()
    val destination = TopDestination.entries[selectedIndex]

    LaunchedEffect(mapSessionState.lastCompletedTrail?.id) {
        mapSessionState.lastCompletedTrail?.let { completedTrail ->
            profileViewModel.recordTrailCompletion(completedTrail)
            mainViewModel.consumeLastCompletedTrail()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(color = Color.Transparent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                        ),
                        shadowElevation = 18.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TopDestination.entries.forEachIndexed { index, item ->
                                BottomBarItem(
                                    modifier = Modifier.weight(1f),
                                    selected = selectedIndex == index,
                                    icon = item.icon,
                                    label = stringResource(id = item.labelRes),
                                    onClick = { selectedIndex = index }
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Surface(modifier = Modifier.fillMaxSize()) {
            when (destination) {
                TopDestination.HOME -> HomeScreen(
                    status = uiState,
                    contentPadding = innerPadding,
                    onActiveTrailClick = {
                        if (uiState.activeTrail != null) {
                            mainViewModel.focusActiveTrailOnMap()
                            selectedIndex = TopDestination.MAP.ordinal
                        }
                    }
                )
                TopDestination.MAP -> MapScreen(
                    status = uiState,
                    contentPadding = innerPadding,
                    viewModel = mainViewModel
                )
                TopDestination.CHAT -> ChatScreen(
                    uiState = assistantUiState,
                    contentPadding = innerPadding,
                    onInputChange = assistantViewModel::updateDraft,
                    onSend = assistantViewModel::sendCurrentDraft,
                    onPromptSelected = assistantViewModel::sendPrompt
                )
                TopDestination.SOS -> SosScreen(contentPadding = innerPadding)
                TopDestination.GEAR -> GearScreen(
                    status = uiState, 
                    onToggleItem = { mainViewModel.toggleGearItem(it) }, 
                    contentPadding = innerPadding
                )
                TopDestination.PROFILE -> ProfileScreen(
                    contentPadding = innerPadding,
                    profile = currentProfile,
                    status = uiState,
                    onEditProfile = { editingProfile = true },
                    onSignOut = {
                        editingProfile = false
                        profileViewModel.signOut()
                    }
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = if (selected) activeColor else inactiveColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (selected) activeColor else inactiveColor,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (selected) activeColor else Color.Transparent)
        )
    }
}
