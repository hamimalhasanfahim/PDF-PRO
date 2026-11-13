package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.domain.model.PdfToolId
import com.example.ui.localization.LocalAppStrings
import com.example.ui.localization.stringsFor
import com.example.ui.navigation.CompressorRoute
import com.example.ui.navigation.ConverterRoute
import com.example.ui.navigation.CreatePdfRoute
import com.example.ui.navigation.DocumentsRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.OcrRoute
import com.example.ui.navigation.OnboardingRoute
import com.example.ui.navigation.OrganizerRoute
import com.example.ui.navigation.PdfViewerEditorRoute
import com.example.ui.navigation.ScannerRoute
import com.example.ui.navigation.SecurityRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.ToolsHubRoute
import com.example.ui.navigation.UtilitiesRoute
import com.example.ui.screens.compressor.CompressorScreen
import com.example.ui.screens.compressor.CompressorViewModel
import com.example.ui.screens.converter.ConverterScreen
import com.example.ui.screens.converter.ConverterViewModel
import com.example.ui.screens.create.CreatePdfScreen
import com.example.ui.screens.create.CreatePdfViewModel
import com.example.ui.screens.documents.DocumentsScreen
import com.example.ui.screens.editor.PdfEditorViewModel
import com.example.ui.screens.editor.PdfViewerEditorScreen
import com.example.ui.screens.home.HomeDashboardScreen
import com.example.ui.screens.home.MainViewModel
import com.example.ui.screens.home.ToolsHubScreen
import com.example.ui.screens.ocr.OcrScreen
import com.example.ui.screens.ocr.OcrViewModel
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.organizer.OrganizerScreen
import com.example.ui.screens.organizer.OrganizerViewModel
import com.example.ui.screens.scanner.ScannerScreen
import com.example.ui.screens.scanner.ScannerViewModel
import com.example.ui.screens.security.SecurityScreen
import com.example.ui.screens.security.SecurityViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.utilities.UtilitiesScreen
import com.example.ui.screens.utilities.UtilitiesViewModel
import com.example.ui.theme.PdfMasterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PdfMasterApp

        setContent {
            PdfMasterProRoot(app = app)
        }
    }
}

@Composable
fun PdfMasterProRoot(app: PdfMasterApp) {
    val container = app.container
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(container))
    val preferences by mainViewModel.userPreferences.collectAsStateWithLifecycle()
    val allDocuments by mainViewModel.allActiveDocuments.collectAsStateWithLifecycle()
    val filteredDocuments by mainViewModel.activeDocuments.collectAsStateWithLifecycle()
    val recentDocuments by mainViewModel.recentDocuments.collectAsStateWithLifecycle()
    val recentlyCreated by mainViewModel.recentlyCreated.collectAsStateWithLifecycle()
    val favoriteDocuments by mainViewModel.favoriteDocuments.collectAsStateWithLifecycle()
    val deletedDocuments by mainViewModel.deletedDocuments.collectAsStateWithLifecycle()
    val historyEntries by mainViewModel.processingHistory.collectAsStateWithLifecycle()
    val storageBreakdown by mainViewModel.storageBreakdown.collectAsStateWithLifecycle()
    val docSearchQuery by mainViewModel.docSearchQuery.collectAsStateWithLifecycle()
    val statusMessage by mainViewModel.statusMessage.collectAsStateWithLifecycle()

    val strings = remember(preferences.language) { stringsFor(preferences.language) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            mainViewModel.clearStatusMessage()
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    val importPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            mainViewModel.importPdf(uri) { doc ->
                navController.navigate(PdfViewerEditorRoute(docId = doc.id, startInEditMode = false))
            }
        }
    }

    fun navigateToTool(toolId: PdfToolId) {
        when (toolId) {
            PdfToolId.CREATE_TEXT -> navController.navigate(CreatePdfRoute("text"))
            PdfToolId.CREATE_IMAGES -> navController.navigate(CreatePdfRoute("images"))
            PdfToolId.CREATE_BLANK -> navController.navigate(CreatePdfRoute("blank"))
            PdfToolId.SCAN_DOCUMENT -> navController.navigate(ScannerRoute)
            PdfToolId.OCR_EXTRACT -> navController.navigate(OcrRoute())
            PdfToolId.EDIT_PDF -> navController.navigate(PdfViewerEditorRoute(startInEditMode = true))
            PdfToolId.MERGE_PDF -> navController.navigate(OrganizerRoute("merge"))
            PdfToolId.SPLIT_PDF -> navController.navigate(OrganizerRoute("split"))
            PdfToolId.ORGANIZE_PAGES -> navController.navigate(OrganizerRoute("organize"))
            PdfToolId.EXTRACT_IMAGES -> navController.navigate(OrganizerRoute("extract_images"))
            PdfToolId.COMPRESS_PDF -> navController.navigate(CompressorRoute())
            PdfToolId.PDF_TO_IMAGES, PdfToolId.OFFICE_CONVERT -> navController.navigate(ConverterRoute())
            PdfToolId.PROTECT_PDF -> navController.navigate(SecurityRoute("protect"))
            PdfToolId.UNLOCK_PDF -> navController.navigate(SecurityRoute("unlock"))
            PdfToolId.WATERMARK_PDF -> navController.navigate(SecurityRoute("watermark"))
            PdfToolId.SIGN_PDF -> navController.navigate(SecurityRoute("sign"))
            PdfToolId.COMPARE_PDF -> navController.navigate(UtilitiesRoute("compare"))
            PdfToolId.METADATA_BOOKMARKS -> navController.navigate(UtilitiesRoute("metadata"))
            PdfToolId.PAGE_NUMBERS -> navController.navigate(UtilitiesRoute("numbers"))
            PdfToolId.SEARCH_PDF -> navController.navigate(UtilitiesRoute("search"))
        }
    }

    val isTopLevelRoute = currentDest?.let { dest ->
        dest.hasRoute<HomeRoute>() ||
            dest.hasRoute<DocumentsRoute>() ||
            dest.hasRoute<ToolsHubRoute>() ||
            dest.hasRoute<SettingsRoute>()
    } ?: false

    val startDestination: Any = if (preferences.onboardingCompleted) HomeRoute else OnboardingRoute

    CompositionLocalProvider(LocalAppStrings provides strings) {
        PdfMasterTheme(
            themeMode = preferences.themeMode,
            language = preferences.language
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 600.dp

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        if (isTopLevelRoute && !useNavRail) {
                            NavigationBar(windowInsets = WindowInsets.navigationBars) {
                                TopNavItems(strings).forEach { item ->
                                    val selected = when (item.route) {
                                        is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                        is DocumentsRoute -> currentDest?.hasRoute<DocumentsRoute>() == true
                                        is ToolsHubRoute -> currentDest?.hasRoute<ToolsHubRoute>() == true
                                        is SettingsRoute -> currentDest?.hasRoute<SettingsRoute>() == true
                                        else -> false
                                    }
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = { Text(item.label) },
                                        modifier = Modifier.testTag("nav_${item.tag}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isTopLevelRoute && useNavRail) {
                            NavigationRail(
                                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                            ) {
                                TopNavItems(strings).forEach { item ->
                                    val selected = when (item.route) {
                                        is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                        is DocumentsRoute -> currentDest?.hasRoute<DocumentsRoute>() == true
                                        is ToolsHubRoute -> currentDest?.hasRoute<ToolsHubRoute>() == true
                                        is SettingsRoute -> currentDest?.hasRoute<SettingsRoute>() == true
                                        else -> false
                                    }
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = { Text(item.label) }
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = startDestination
                            ) {
                                composable<OnboardingRoute> {
                                    OnboardingScreen(
                                        currentLanguage = preferences.language,
                                        onSelectLanguage = mainViewModel::setLanguage,
                                        onFinish = {
                                            mainViewModel.completeOnboarding()
                                            navController.navigate(HomeRoute) {
                                                popUpTo(OnboardingRoute) { inclusive = true }
                                            }
                                        }
                                    )
                                }

                                composable<HomeRoute> {
                                    HomeDashboardScreen(
                                        preferences = preferences,
                                        recentDocuments = recentDocuments,
                                        recentlyCreated = recentlyCreated,
                                        allDocuments = allDocuments,
                                        storageBreakdown = storageBreakdown,
                                        onToolClick = ::navigateToTool,
                                        onOpenDocument = { id ->
                                            navController.navigate(PdfViewerEditorRoute(docId = id, startInEditMode = false))
                                        },
                                        onSeeAllTools = { navController.navigate(ToolsHubRoute) },
                                        onSeeAllDocuments = { navController.navigate(DocumentsRoute()) },
                                        onOpenSettings = { navController.navigate(SettingsRoute) },
                                        onImportPdfClick = { importPdfLauncher.launch(arrayOf("application/pdf")) }
                                    )
                                }

                                composable<DocumentsRoute> { entry ->
                                    val args = entry.toRoute<DocumentsRoute>()
                                    DocumentsScreen(
                                        initialTab = args.initialTab,
                                        searchQuery = docSearchQuery,
                                        onSearchQueryChange = mainViewModel::updateDocSearchQuery,
                                        activeDocuments = filteredDocuments,
                                        recentDocuments = recentDocuments,
                                        favoriteDocuments = favoriteDocuments,
                                        deletedDocuments = deletedDocuments,
                                        historyEntries = historyEntries,
                                        storageBreakdown = storageBreakdown,
                                        fileStorageManager = container.fileStorageManager,
                                        onImportUri = { uri -> mainViewModel.importPdf(uri) },
                                        onOpenViewer = { id, edit ->
                                            navController.navigate(PdfViewerEditorRoute(docId = id, startInEditMode = edit))
                                        },
                                        onToggleFavorite = mainViewModel::toggleFavorite,
                                        onRename = mainViewModel::renameDocument,
                                        onExportUri = mainViewModel::exportDocumentToUri,
                                        onShare = mainViewModel::shareDocument,
                                        onMoveToTrash = mainViewModel::moveToTrash,
                                        onRestoreFromTrash = mainViewModel::restoreFromTrash,
                                        onDeletePermanently = mainViewModel::deletePermanently,
                                        onEmptyTrash = mainViewModel::emptyTrash
                                    )
                                }

                                composable<ToolsHubRoute> {
                                    ToolsHubScreen(
                                        favoriteToolIds = preferences.favoriteToolIds,
                                        onToggleFavoriteTool = mainViewModel::toggleFavoriteTool,
                                        onToolClick = ::navigateToTool
                                    )
                                }

                                composable<CreatePdfRoute> { entry ->
                                    val args = entry.toRoute<CreatePdfRoute>()
                                    val vm: CreatePdfViewModel = viewModel(factory = CreatePdfViewModel.Factory(container))
                                    CreatePdfScreen(
                                        initialMode = args.initialMode,
                                        preferences = preferences,
                                        viewModel = vm,
                                        fileStorageManager = container.fileStorageManager,
                                        onOpenPreview = { id ->
                                            navController.navigate(PdfViewerEditorRoute(docId = id, startInEditMode = false))
                                        },
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable<PdfViewerEditorRoute> { entry ->
                                    val args = entry.toRoute<PdfViewerEditorRoute>()
                                    val vm: PdfEditorViewModel = viewModel(factory = PdfEditorViewModel.Factory(container))
                                    PdfViewerEditorScreen(
                                        docId = args.docId,
                                        startInEditMode = args.startInEditMode,
                                        availableDocs = allDocuments,
                                        viewModel = vm,
                                        fileStorageManager = container.fileStorageManager,
                                        onOpenNewDoc = { id ->
                                            navController.navigate(PdfViewerEditorRoute(docId = id, startInEditMode = false))
                                        },
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable<OrganizerRoute> { entry ->
                                    val args = entry.toRoute<OrganizerRoute>()
                                    val vm: OrganizerViewModel = viewModel(factory = OrganizerViewModel.Factory(container))
                                    OrganizerScreen(
                                        initialTool = args.initialTool,
                                        docId = args.docId,
                                        availableDocs = allDocuments,
                                        viewModel = vm,
                                        fileStorageManager = container.fileStorageManager,
                                        onOpenPreview = { id ->
                                            navController.navigate(PdfViewerEditorRoute(docId = id, startInEditMode = false))
                                        },
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable<ConverterRoute> { entry ->
                                    val args = entry.toRoute<ConverterRoute>()
                                    val vm: ConverterViewModel = viewModel(factory = ConverterViewModel.Factory(container))
                                    ConverterScreen(
                                        initialMode = args.initialMode,
                                        docId = args.docId,
                                        availableDocs = allDocuments,
                                        viewModel = vm,
                                        fileStorageManager = container.fileStorageManager,
                                        onOpenCreateImages = { navController.navigate(CreatePdfRoute("images")) },
                                        onOpenCreateText = { navController.navigate(CreatePdfRoute("text")) },
                                        onOpenPreview = { id ->
                                            navController.navigate(PdfViewerEditorRoute(docId = id, startInEditMode = false))
                                        },
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable<CompressorRoute> { entry ->
                                    val args = entry.toRoute<CompressorRoute>()
                                    val vm: CompressorViewModel = viewModel(
                                        factory = CompressorViewModel.Factory(app, container)
                                    )
                                    CompressorScreen(
                                        docId = args.docId,
                                        defaultLevel = preferences.defaultCompression,
                                        availableDocs = allDocuments,
                                        viewModel = vm,
                                        fileStorageManager = container.fileStorageManager,
                                        onOpenPreview = { id ->
                                            navController.navigate(PdfViewerEditorRoute(docId = id, startInEditMode = false))
                                        },
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable<ScannerRoute> {
                                    val vm: ScannerViewModel = viewModel(factory = ScannerViewModel.Factory(container))
                                    ScannerScreen(
                                        viewModel = vm,
                                        fileStorageManager = container.fileStorageManager,
                                        onOpenPreview = { id ->
                                            navController.navigate(PdfViewerEditorRoute(docId = id, startInEditMode = false))
                                        },
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable<OcrRoute> { entry ->
                                    val args = entry.toRoute<OcrRoute>()
                                    val vm: OcrViewModel = viewModel(factory = OcrViewModel.Factory(container))
                                    OcrScreen(
                                        docId = args.docId,
                                        availableDocs = allDocuments,
                                        viewModel = vm,
                                        fileStorageManager = container.fileStorageManager,
                                        onOpenPreview = { id ->
                                            navController.navigate(PdfViewerEditorRoute(docId = id, startInEditMode = false))
                                        },
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable<SecurityRoute> { entry ->
                                    val args = entry.toRoute<SecurityRoute>()
                                    val vm: SecurityViewModel = viewModel(factory = SecurityViewModel.Factory(container))
                                    SecurityScreen(
                                        initialTab = args.initialTab,
                                        docId = args.docId,
                                        availableDocs = allDocuments,
                                        viewModel = vm,
                                        fileStorageManager = container.fileStorageManager,
                                        onOpenPreview = { id ->
                                            navController.navigate(PdfViewerEditorRoute(docId = id, startInEditMode = false))
                                        },
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable<UtilitiesRoute> { entry ->
                                    val args = entry.toRoute<UtilitiesRoute>()
                                    val vm: UtilitiesViewModel = viewModel(factory = UtilitiesViewModel.Factory(container))
                                    UtilitiesScreen(
                                        initialTab = args.initialTab,
                                        docId = args.docId,
                                        availableDocs = allDocuments,
                                        viewModel = vm,
                                        fileStorageManager = container.fileStorageManager,
                                        onOpenPreview = { id ->
                                            navController.navigate(PdfViewerEditorRoute(docId = id, startInEditMode = false))
                                        },
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable<SettingsRoute> {
                                    SettingsScreen(
                                        preferences = preferences,
                                        storageBreakdown = storageBreakdown,
                                        onSetTheme = mainViewModel::setThemeMode,
                                        onSetLanguage = mainViewModel::setLanguage,
                                        onSetPageSize = mainViewModel::setDefaultPageSize,
                                        onSetOrientation = mainViewModel::setDefaultOrientation,
                                        onSetCompression = mainViewModel::setDefaultCompression,
                                        onSetDefaultFolder = mainViewModel::setDefaultSaveFolderUri,
                                        onSetFontSize = mainViewModel::setDefaultFontSize,
                                        onClearCache = mainViewModel::clearCache,
                                        onClearHistory = mainViewModel::clearHistory
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class TopNavItem(
    val route: Any,
    val label: String,
    val tag: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun TopNavItems(strings: com.example.ui.localization.AppStrings): List<TopNavItem> = listOf(
    TopNavItem(HomeRoute, strings.navHome, "home", Icons.Filled.Home, Icons.Outlined.Home),
    TopNavItem(DocumentsRoute(), strings.navDocuments, "documents", Icons.Filled.Folder, Icons.Outlined.Folder),
    TopNavItem(ToolsHubRoute, strings.navTools, "tools", Icons.Filled.Build, Icons.Outlined.Build),
    TopNavItem(SettingsRoute, strings.navSettings, "settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)
