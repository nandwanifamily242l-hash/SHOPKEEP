package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.example.ui.components.ShareUtils
import com.example.ui.habits.HabitsScreen
import com.example.ui.home.HomeDashboardScreen
import com.example.ui.photos.PhotosScreen
import com.example.ui.tasks.EisenhowerMatrixScreen
import com.example.ui.tasks.TasksScreen
import com.example.ui.theme.ShopKeepTheme
import com.example.util.configureAppCheck
import com.example.viewmodel.TaskViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

enum class ShopDestination(val title: String) {
    HOME("Shop"),
    CHECKLIST("Checklist"),
    TASKS("Tasks"),
    PHOTOS("Photos"),
    ROUTINES("Routines"),
    MATRIX("Priority Grid")
}

private object CrashRecorder {
    private const val PREFS = "shopkeep_diag_prefs"
    private const val KEY_TRACE = "last_crash_trace"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            installed = true
            val appContext = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    val trace = Log.getStackTraceString(throwable)
                    appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_TRACE, trace.take(4000))
                        .commit()
                }
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    fun consumeLastCrash(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trace = prefs.getString(KEY_TRACE, null)
        if (!trace.isNullOrBlank()) {
            prefs.edit().remove(KEY_TRACE).apply()
            return trace
        }
        return null
    }
}

class MainActivity : ComponentActivity() {

    private var deepLinkParam by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        CrashRecorder.install(this)

        // Step 1: Configure App Check before UI or any Firebase calls
        configureAppCheck(this, intent)

        deepLinkParam = extractShareParam(intent)
        val previousCrash = CrashRecorder.consumeLastCrash(this)

        setContent {
            ShopKeepTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShopKeepAppRoot(
                        incomingShareCode = deepLinkParam,
                        previousCrashTrace = previousCrash,
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
        val queryCode = data.getQueryParameter("checklist") ?: data.getQueryParameter("board")
        if (!queryCode.isNullOrBlank()) return queryCode.trim()
        val path = data.path ?: ""
        if (path.contains("/checklist/")) {
            return path.substringAfter("/checklist/").substringBefore("/").ifBlank { null }
        }
        if ((data.scheme == "shopkeep" || data.scheme == "homekeep") && data.host == "checklist") {
            return data.lastPathSegment?.takeIf { it.isNotBlank() }
        }
        if (data.scheme == "https" || data.scheme == "http") {
            return "PUBLIC_DEFAULT"
        }
        return data.lastPathSegment
    }
}

@Composable
fun ShopKeepAppRoot(
    incomingShareCode: String?,
    previousCrashTrace: String? = null,
    onConsumedDeepLink: () -> Unit
) {
    val context = LocalContext.current
    var crashTraceToShow by remember(previousCrashTrace) { mutableStateOf(previousCrashTrace) }

    val initResult = remember {
        runCatching {
            val auth = FirebaseAuth.getInstance()
            val repo = TaskRepository.create(context)
            auth to repo
        }
    }

    val pair = initResult.getOrNull()
    if (pair == null) {
        val error = initResult.exceptionOrNull()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ShopKeep Initialization Error",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = error?.localizedMessage ?: "Failed to initialize cloud services.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    val (auth, repository) = pair
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val viewModel: TaskViewModel = viewModel(
        factory = TaskViewModel.Factory(repository, context)
    )

    LaunchedEffect(incomingShareCode) {
        if (!incomingShareCode.isNullOrBlank()) {
            viewModel.setPendingDeepLinkCode(incomingShareCode)
            onConsumedDeepLink()
        }
    }

    val activeUser = currentUser
    if (activeUser == null) {
        LaunchedEffect(Unit) {
            viewModel.stopSync()
        }
        AuthScreen(
            onSignedIn = {
                currentUser = auth.currentUser
            }
        )
    } else {
        key(activeUser.uid) {
            AuthenticatedShopKeepContent(
                user = activeUser,
                viewModel = viewModel,
                onSignedOut = {
                    currentUser = null
                }
            )
        }
    }

    if (!crashTraceToShow.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { crashTraceToShow = null },
            title = { Text("Previous Launch Error Report", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = crashTraceToShow ?: "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    ShareUtils.copyToClipboard(context, "Crash Log", crashTraceToShow ?: "")
                    crashTraceToShow = null
                }) {
                    Text("Copy Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { crashTraceToShow = null }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShopKeepContent(
    user: FirebaseUser,
    viewModel: TaskViewModel,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(user.uid) {
        viewModel.startSyncForCurrentUser()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = {}
        )
        LaunchedEffect(user.uid) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runCatching {
                    permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentDestination by remember { mutableStateOf(ShopDestination.HOME) }
    var showAiDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.navigateToSharedTabEvent) {
        if (uiState.navigateToSharedTabEvent > 0) {
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
        contentWindowInsets = WindowInsets.safeDrawing,
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
                                onSignedOut()
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
                    userDisplayName = user.displayName ?: "Shop Owner",
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
