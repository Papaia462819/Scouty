package com.scouty.app.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.House
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Map
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.User
import com.scouty.app.R
import com.scouty.app.assistant.ui.AssistantViewModel
import com.scouty.app.profile.OnboardingDraft
import com.scouty.app.profile.ProfileViewModel
import com.scouty.app.profile.SessionStage
import com.scouty.app.profile.toOnboardingDraft
import com.scouty.app.ui.components.ScoutyBottomBar
import com.scouty.app.ui.components.ScoutyNavItem
import com.scouty.app.ui.models.NearbyGuideType
import com.scouty.app.ui.screens.AuthScreen
import com.scouty.app.ui.screens.ChatScreen
import com.scouty.app.ui.screens.GearScreen
import com.scouty.app.ui.screens.HomeScreen
import com.scouty.app.ui.screens.MapScreen
import com.scouty.app.ui.screens.ProfileFlowMode
import com.scouty.app.ui.screens.ProfileOnboardingScreen
import com.scouty.app.ui.screens.ProfileScreen
import com.scouty.app.ui.screens.SosScreen
import com.scouty.app.tracks.ui.TrackCameraScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_MAP = "map"
private const val ROUTE_CHAT = "chat"
private const val ROUTE_SOS = "sos"
private const val ROUTE_GEAR = "gear"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_TRACKS = "tracks"

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
    var selectedRoute by rememberSaveable { mutableStateOf(ROUTE_HOME) }
    val uiState by mainViewModel.uiState.collectAsState()
    val mapSessionState by mainViewModel.mapSessionState.collectAsState()
    val assistantUiState by assistantViewModel.uiState.collectAsState()

    LaunchedEffect(mapSessionState.lastCompletedTrail?.id) {
        mapSessionState.lastCompletedTrail?.let { completedTrail ->
            profileViewModel.recordTrailCompletion(completedTrail)
            mainViewModel.consumeLastCompletedTrail()
        }
    }

    val navItems = listOf(
        ScoutyNavItem(ROUTE_HOME, stringResource(R.string.nav_home), Lucide.House),
        ScoutyNavItem(ROUTE_MAP, stringResource(R.string.nav_map), Lucide.Map),
        ScoutyNavItem(ROUTE_CHAT, stringResource(R.string.nav_chat), Lucide.MessageSquare),
        ScoutyNavItem(ROUTE_SOS, stringResource(R.string.nav_sos), Lucide.TriangleAlert, isDanger = true),
        ScoutyNavItem(ROUTE_GEAR, stringResource(R.string.nav_gear), Lucide.ListChecks),
        ScoutyNavItem(ROUTE_PROFILE, stringResource(R.string.nav_profile), Lucide.User),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            ScoutyBottomBar(
                items = navItems,
                selectedKey = selectedRoute,
                onSelect = { selectedRoute = it },
            )
        },
    ) { innerPadding ->
        Surface(modifier = Modifier.fillMaxSize()) {
            when (selectedRoute) {
                ROUTE_HOME -> HomeScreen(
                    status = uiState,
                    contentPadding = innerPadding,
                    onActiveTrailClick = {
                        if (uiState.activeTrail != null) {
                            mainViewModel.focusActiveTrailOnMap()
                            selectedRoute = ROUTE_MAP
                        }
                    },
                    onShelterClick = {
                        mainViewModel.requestNearbyGuide(NearbyGuideType.SHELTER)
                        selectedRoute = ROUTE_MAP
                    },
                    onWaterClick = {
                        mainViewModel.requestNearbyGuide(NearbyGuideType.WATER)
                        selectedRoute = ROUTE_MAP
                    },
                    onTrackClick = { selectedRoute = ROUTE_TRACKS }
                )
                ROUTE_MAP -> MapScreen(
                    status = uiState,
                    contentPadding = innerPadding,
                    viewModel = mainViewModel
                )
                ROUTE_CHAT -> ChatScreen(
                    uiState = assistantUiState,
                    contentPadding = innerPadding,
                    onInputChange = assistantViewModel::updateDraft,
                    onSend = assistantViewModel::sendCurrentDraft,
                    onPromptSelected = assistantViewModel::sendPrompt
                )
                ROUTE_SOS -> SosScreen(contentPadding = innerPadding)
                ROUTE_GEAR -> GearScreen(
                    status = uiState,
                    onToggleItem = { mainViewModel.toggleGearItem(it) },
                    contentPadding = innerPadding
                )
                ROUTE_PROFILE -> ProfileScreen(
                    contentPadding = innerPadding,
                    profile = currentProfile,
                    status = uiState,
                    onEditProfile = { editingProfile = true },
                    onSignOut = {
                        editingProfile = false
                        profileViewModel.signOut()
                    }
                )
                ROUTE_TRACKS -> TrackCameraScreen(
                    contentPadding = innerPadding,
                    onBack = { selectedRoute = ROUTE_HOME },
                )
            }
        }
    }
}
