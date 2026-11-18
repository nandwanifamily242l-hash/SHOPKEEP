package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.TaskRepository
import com.example.ui.ai.ShopAiAssistantDialog
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.checklist.ChecklistScreen
import com.example.ui.habits.HabitsScreen
import com.example.ui.home.HomeDashboardScreen
import com.example.ui.photos.PhotosScreen
import com.example.ui.tasks.EisenhowerMatrixScreen
import com.example.ui.tasks.TasksScreen
import com.example.ui.theme.ShopKeepTheme
import com.example.util.configureAppCheck
import com.example.viewmodel.TaskViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

enum class ShopDestination(val title: String) {
    HOME("Shop"),
    CHECKLIST("Checklist"),
    TASKS("Tasks"),
    PHOTOS("Photos"),
    ROUTINES("Routines"),
    MATRIX("Priority Grid")
}

class MainActivity : ComponentActivity() {

    private var deepLinkParam by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Configure App Check before UI or any Firebase calls
        configureAppCheck(this, intent)

        deepLinkParam = extractShareParam(intent)

        setContent {
            ShopKeepTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShopKeepAppRoot(
                        incomingShareCode = deepLinkParam,
                        onConsumedDeepLink = { deepLinkParam = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkParam = extractShareParam(intent)
    }

    private fun extractShareParam(intent: Intent?): String? {
        val data = intent?.data ?: return null
        val queryCode = data.getQueryParameter("checklist")
        if (!queryCode.isNullOrBlank()) return queryCode
        val path = data.path ?: return null
        if (path.contains("/checklist/")) {
            return path.substringAfter("/checklist/").substringBefore("/").ifBlank { null }
        }
        return data.lastPathSegment
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopKeepAppRoot(
    incomingShareCode: String?,
    onConsumedDeepLink: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val repository = remember { TaskRepository.create(context) }
    val viewModel: TaskViewModel = viewModel(
        factory = TaskViewModel.Factory(repository, context)
    )

    LaunchedEffect(incomingShareCode) {
        if (!incomingShareCode.isNullOrBlank()) {
            viewModel.setPendingDeepLinkCode(incomingShareCode)
            onConsumedDeepLink()
        }
    }

    if (currentUser == null) {
        LaunchedEffect(Unit) {
            viewModel.stopSync()
        }
        AuthScreen(
            onSignedIn = {
                currentUser = auth.currentUser
            }
        )
        return
    }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) {
            viewModel.startSyncForCurrentUser()
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = {}
        )
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentDestination by remember { mutableStateOf(ShopDestination.HOME) }
    var showAiDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.activeSharedCode) {
        if (uiState.activeSharedCode != null && incomingShareCode != null) {
            currentDestination = ShopDestination.CHECKLIST
        }
    }

    LaunchedEffect(uiState.errorMessage, uiState.statusBanner) {
        val msg = uiState.errorMessage ?: uiState.statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    if (currentDestination != ShopDestination.HOME) {
        BackHandler {
            currentDestination = ShopDestination.HOME
        }
    }

    val bottomTabs = listOf(
        Triple(ShopDestination.HOME, "Shop", Icons.Rounded.Home),
        Triple(ShopDestination.CHECKLIST, "Checklist", Icons.Rounded.CheckBox),
        Triple(ShopDestination.TASKS, "Tasks", Icons.Rounded.TaskAlt),
        Triple(ShopDestination.PHOTOS, "Photos", Icons.Rounded.PhotoLibrary),
        Triple(ShopDestination.ROUTINES, "Routines", Icons.Rounded.Repeat)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (currentDestination == ShopDestination.HOME) {
                                "ShopKeep"
                            } else {
                                currentDestination.title
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    if (currentDestination == ShopDestination.MATRIX) {
                        IconButton(onClick = { currentDestination = ShopDestination.HOME }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { currentDestination = ShopDestination.MATRIX },
                        modifier = Modifier.testTag("topbar_matrix_button")
                    ) {
                        Icon(Icons.Rounded.GridView, contentDescription = "Priority Matrix")
                    }
                    IconButton(
                        onClick = { showAiDialog = true },
                        modifier = Modifier.testTag("topbar_ai_button")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "ShopKeep AI")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                viewModel.stopSync()
                                signOutUser(context)
                                currentUser = null
                            }
                        },
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Sign out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                bottomTabs.forEach { (dest, label, icon) ->
                    NavigationBarItem(
                        selected = currentDestination == dest,
                        onClick = { currentDestination = dest },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("nav_${dest.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentDestination) {
                ShopDestination.HOME -> HomeDashboardScreen(
                    uiState = uiState,
                    userDisplayName = currentUser?.displayName ?: "Shop Owner",
                    onOpenTasks = { currentDestination = ShopDestination.TASKS },
                    onOpenChecklist = { currentDestination = ShopDestination.CHECKLIST },
                    onOpenPhotos = { currentDestination = ShopDestination.PHOTOS },
                    onOpenMatrix = { currentDestination = ShopDestination.MATRIX },
                    onOpenHabits = { currentDestination = ShopDestination.ROUTINES },
                    onOpenAi = { showAiDialog = true },
                    onToggleTask = { viewModel.toggleTaskCompletion(it) },
                    onSaveZone = { id, name, desc -> viewModel.saveArea(id, name, desc) },
                    onDeleteZone = { viewModel.deleteArea(it) }
                )
                ShopDestination.CHECKLIST -> ChecklistScreen(
                    uiState = uiState,
                    viewModel = viewModel
                )
                ShopDestination.TASKS -> TasksScreen(
                    uiState = uiState,
                    viewModel = viewModel
                )
                ShopDestination.PHOTOS -> PhotosScreen(
                    uiState = uiState,
                    viewModel = viewModel
                )
                ShopDestination.ROUTINES -> HabitsScreen(
                    uiState = uiState,
                    viewModel = viewModel
                )
                ShopDestination.MATRIX -> EisenhowerMatrixScreen(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }

    if (showAiDialog) {
        val pendingTasks = uiState.tasks.count { !it.isDeleted && !it.isCompleted }
        val pendingStock = uiState.checklistItems.count { !it.isDeleted && !it.isChecked }
        ShopAiAssistantDialog(
            contextSummary = "$pendingTasks pending shop tasks, $pendingStock pending checklist items, ${uiState.areas.size} shop zones.",
            onDismiss = { showAiDialog = false },
            onAddChecklistSuggestions = { lines ->
                lines.forEach { item ->
                    viewModel.saveChecklistItem(
                        title = item,
                        quantity = "",
                        category = "General Stock"
                    )
                }
            }
        )
    }
}
