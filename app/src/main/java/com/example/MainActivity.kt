package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.StudyDatabase
import com.example.data.StudyVideo
import com.example.data.StudyVideoRepository
import com.example.ui.components.YouTubePlayerView
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StudyViewModel
import com.example.ui.viewmodel.StudyViewModelFactory
import com.example.ui.viewmodel.UiState
import com.example.network.YouTubeService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get database and repository
        val database = StudyDatabase.getDatabase(this)
        val repository = StudyVideoRepository(database.studyVideoDao())
        val factory = StudyViewModelFactory(repository)

        setContent {
            MyApplicationTheme {
                val viewModel: StudyViewModel = viewModel(factory = factory)
                StudyApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyApp(viewModel: StudyViewModel) {
    val context = LocalContext.current
    
    // Auth and screen states
    val userEmail by viewModel.userEmail.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val authError by viewModel.authError.collectAsState()

    var isAddingVideo by remember { mutableStateOf(false) }
    var editingVideo by remember { mutableStateOf<StudyVideo?>(null) }
    var activePlayingVideo by remember { mutableStateOf<StudyVideo?>(null) }

    // Handle DB CRUD responses
    val dbActionState by viewModel.dbActionState.collectAsState()
    LaunchedEffect(dbActionState) {
        when (val state = dbActionState) {
            is UiState.Success -> {
                Toast.makeText(context, state.data, Toast.LENGTH_SHORT).show()
                isAddingVideo = false
                editingVideo = null
                viewModel.resetDbActionState()
            }
            is UiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetDbActionState()
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            if (isAdmin && userEmail != null) {
                FloatingActionButton(
                    onClick = { isAddingVideo = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add study content")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (userEmail == null) {
                // Render Login flow
                LoginScreen(viewModel = viewModel)
            } else {
                // Render Dashboard
                DashboardScreen(
                    viewModel = viewModel,
                    activePlayingVideo = activePlayingVideo,
                    onSelectVideo = { video -> activePlayingVideo = video },
                    onClosePlayer = { activePlayingVideo = null },
                    onEditVideo = { video -> editingVideo = video },
                    onDeleteVideo = { video -> viewModel.deleteVideo(video.id) }
                )
            }

            // Dialog for adding video
            if (isAddingVideo) {
                VideoFormDialog(
                    onDismiss = { isAddingVideo = false },
                    onSave = { url, title, desc, thumb, schoolClass, subject, lang, chapter ->
                        viewModel.addVideo(url, title, desc, thumb, schoolClass, subject, lang, chapter)
                    },
                    viewModel = viewModel
                )
            }

            // Dialog for editing video
            if (editingVideo != null) {
                VideoFormDialog(
                    videoToEdit = editingVideo,
                    onDismiss = { editingVideo = null },
                    onSave = { url, title, desc, thumb, schoolClass, subject, lang, chapter ->
                        editingVideo?.let {
                            viewModel.editVideo(it.id, url, title, desc, thumb, schoolClass, subject, lang, chapter)
                        }
                    },
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: StudyViewModel) {
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val authError by viewModel.authError.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1B2A),
                        Color(0xFF1B263B)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xAA1F2C46))
                .padding(28.dp)
        ) {
            // App Branding Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "Graduation Cap Logo",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Study Controller",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Educational Content Management Portal",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Admin Email Input
            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("Admin Email", color = Color.White.copy(alpha = 0.8f)) },
                placeholder = { Text("yourname@studycontroller.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Admin verification key icon",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Admin Password Input
            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("Admin Security Password", color = Color.White.copy(alpha = 0.8f)) },
                placeholder = { Text("Enter security password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Admin lock key icon",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = if (passwordVisible) "Toggle password visibility" else "Toggle password visibility",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Admin Login Button
            Button(
                onClick = {
                    if (emailInput.isBlank()) {
                        Toast.makeText(context, "Please enter an admin email address.", Toast.LENGTH_SHORT).show()
                    } else if (passwordInput.isBlank()) {
                        Toast.makeText(context, "Please enter the admin password.", Toast.LENGTH_SHORT).show()
                    } else {
                        val success = viewModel.loginAsAdmin(emailInput, passwordInput)
                        if (success) {
                            Toast.makeText(context, "Welcome Admin! CRUD privileges authorized.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Text("Authorize Admin Console", fontWeight = FontWeight.SemiBold)
            }

            if (authError != null) {
                Text(
                    text = authError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Divider row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.5f))
                Text(
                    text = "OR",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.LightGray,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.5f))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Student Entry Button (Read Only)
            OutlinedButton(
                onClick = {
                    viewModel.loginAsStudent()
                    Toast.makeText(context, "Student portal opened. Access authorized.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(Color.White, Color.LightGray))
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play icon",
                    tint = Color.White,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Continue as Student", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun DashboardScreen(
    viewModel: StudyViewModel,
    activePlayingVideo: StudyVideo?,
    onSelectVideo: (StudyVideo) -> Unit,
    onClosePlayer: () -> Unit,
    onEditVideo: (StudyVideo) -> Unit,
    onDeleteVideo: (StudyVideo) -> Unit
) {
    val userEmail by viewModel.userEmail.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedClass by viewModel.selectedClass.collectAsState()
    val selectedSubject by viewModel.selectedSubject.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()

    val filteredVideos by viewModel.filteredVideos.collectAsState()
    val classes by viewModel.classesList.collectAsState()
    val subjects by viewModel.subjectsList.collectAsState()
    val languages by viewModel.languagesList.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Study Controller",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.School,
                        contentDescription = "Role indicator icon",
                        tint = if (isAdmin) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAdmin) "Admin Level: CRUD Authorized" else "Student (Read-Only)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAdmin) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            IconButton(onClick = { viewModel.logout() }) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Logout and change user type",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // Video Player Section
            if (activePlayingVideo != null) {
                item {
                    val videoId = YouTubeService.extractVideoId(activePlayingVideo.youtubeUrl) ?: ""
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Player",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = onClosePlayer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Close Player panel",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        YouTubePlayerView(
                            videoId = videoId,
                            videoUrl = activePlayingVideo.youtubeUrl,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = activePlayingVideo.videoName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Chapter: ${activePlayingVideo.chapter}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text(
                            text = activePlayingVideo.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CategoryBadge(text = activePlayingVideo.schoolClass, containerColor = Color(0xFFE28743))
                            CategoryBadge(text = activePlayingVideo.subject, containerColor = Color(0xFF1E88E5))
                            CategoryBadge(text = activePlayingVideo.language, containerColor = Color(0xFF8E24AA))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            } else {
                // Large Hero banner with study workspace graphic if no active video
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.study_hero_banner),
                            contentDescription = "Study header workspace banner decoration",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Learn Any Subject, Anytime",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "High-quality conceptual videos curated for academic success",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }

            // Search Filter controls
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Search box
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchQuery.value = it },
                        placeholder = { Text("Search title, topic, or description...") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search study key") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search query")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Refine Content",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Class filter chips
                    FilterSection(
                        label = "Class",
                        items = classes,
                        selectedItem = selectedClass,
                        onItemSelected = { viewModel.selectedClass.value = it }
                    )

                    // Subject filter chips
                    FilterSection(
                        label = "Subject",
                        items = subjects,
                        selectedItem = selectedSubject,
                        onItemSelected = { viewModel.selectedSubject.value = it }
                    )

                    // Language filter chips
                    FilterSection(
                        label = "Language",
                        items = languages,
                        selectedItem = selectedLanguage,
                        onItemSelected = { viewModel.selectedLanguage.value = it }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // List Title
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Curation Materials (${filteredVideos.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Clear Filters indicator if any is active
                    if (selectedClass != null || selectedSubject != null || selectedLanguage != null) {
                        TextButton(
                            onClick = {
                                viewModel.selectedClass.value = null
                                viewModel.selectedSubject.value = null
                                viewModel.selectedLanguage.value = null
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Reset Filters", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Empty State helper if no results match filters
            if (filteredVideos.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Empty filter results",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Study Materials Match Criteria",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Try adjusting search terms or resetting academic filter chips.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                // Playable items list
                items(filteredVideos, key = { it.id }) { video ->
                    StudyVideoListItem(
                        video = video,
                        isAdmin = isAdmin,
                        onClick = { onSelectVideo(video) },
                        onEdit = { onEditVideo(video) },
                        onDelete = { onDeleteVideo(video) }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterSection(
    label: String,
    items: List<String>,
    selectedItem: String?,
    onItemSelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.width(60.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                InputChip(
                    selected = (selectedItem == null),
                    onClick = { onItemSelected(null) },
                    label = { Text("All") },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
            items(items) { item ->
                InputChip(
                    selected = (selectedItem == item),
                    onClick = { onItemSelected(item) },
                    label = { Text(item) },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}

@Composable
fun StudyVideoListItem(
    video: StudyVideo,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Rounded image container representing thumbnail
                Box(
                    modifier = Modifier
                        .size(100.dp, 64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = "YouTube video study thumbnail representation",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Mini play emblem center
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Mini play overlay visual",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Metadata Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.videoName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Chapter: ${video.chapter}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = video.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Bottom chip categories row & Admin CRUD triggers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CategoryBadge(text = video.schoolClass, containerColor = Color(0xFFE28743))
                    CategoryBadge(text = video.subject, containerColor = Color(0xFF1E88E5))
                    CategoryBadge(text = video.language, containerColor = Color(0xFF8E24AA))
                }

                if (isAdmin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit study item",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete study item",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryBadge(text: String, containerColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = containerColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFormDialog(
    videoToEdit: StudyVideo? = null,
    onDismiss: () -> Unit,
    onSave: (
        url: String,
        title: String,
        desc: String,
        thumb: String,
        schoolClass: String,
        subject: String,
        lang: String,
        chapter: String
    ) -> Unit,
    viewModel: StudyViewModel
) {
    var url by remember { mutableStateOf(videoToEdit?.youtubeUrl ?: "") }
    var title by remember { mutableStateOf(videoToEdit?.videoName ?: "") }
    var description by remember { mutableStateOf(videoToEdit?.description ?: "") }
    var thumbnailUrl by remember { mutableStateOf(videoToEdit?.thumbnailUrl ?: "") }
    var schoolClass by remember { mutableStateOf(videoToEdit?.schoolClass ?: "Class 10") }
    var subject by remember { mutableStateOf(videoToEdit?.subject ?: "Mathematics") }
    var language by remember { mutableStateOf(videoToEdit?.language ?: "English") }
    var chapter by remember { mutableStateOf(videoToEdit?.chapter ?: "") }

    val fetchedMetadataState by viewModel.fetchedMetadataState.collectAsState()
    val context = LocalContext.current

    // Auto update fields when URL fetching succeeds
    LaunchedEffect(fetchedMetadataState) {
        if (fetchedMetadataState is UiState.Success) {
            val data = (fetchedMetadataState as UiState.Success<com.example.network.YouTubeVideoMetadata>).data
            title = data.title
            description = data.description
            thumbnailUrl = data.thumbnailUrl
            Toast.makeText(context, "Metadata fetched automatically from YouTube API!", Toast.LENGTH_SHORT).show()
            viewModel.clearFetchedMetadata()
        } else if (fetchedMetadataState is UiState.Error) {
            val errMsg = (fetchedMetadataState as UiState.Error).message
            Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
            viewModel.clearFetchedMetadata()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = if (videoToEdit == null) "Create Study Material" else "Edit Study Material",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // YouTube URL Field with validation
                item {
                    Column {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("YouTube URL") },
                            placeholder = { Text("https://www.youtube.com/watch?v=...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                val id = YouTubeService.extractVideoId(url)
                                if (id == null) {
                                    Toast.makeText(context, "Please enter a valid YouTube URL to parse.", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.fetchYouTubeMetadata(url)
                                }
                            },
                            enabled = url.isNotBlank() && fetchedMetadataState !is UiState.Loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (fetchedMetadataState is UiState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetching YouTube API...")
                            } else {
                                Icon(imageVector = Icons.Default.FilterList, contentDescription = "Fetch data icon", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Auto-Fetch YouTube Data")
                            }
                        }
                    }
                }

                // Editable Fields
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Video Name / Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Content Description") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }

                item {
                    OutlinedTextField(
                        value = thumbnailUrl,
                        onValueChange = { thumbnailUrl = it },
                        label = { Text("Thumbnail URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = chapter,
                        onValueChange = { chapter = it },
                        label = { Text("Chapter Title / ID") },
                        placeholder = { Text("Chapter 1: Intro") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // School Class Selection
                item {
                    DropdownSelector(
                        label = "Target Class Level",
                        options = listOf("Class 9", "Class 10", "Class 11", "Class 12"),
                        selectedOption = schoolClass,
                        onOptionSelected = { schoolClass = it }
                    )
                }

                // Subject Selection
                item {
                    DropdownSelector(
                        label = "Subject Topic",
                        options = listOf("Mathematics", "Physics", "Chemistry", "Biology", "English", "History"),
                        selectedOption = subject,
                        onOptionSelected = { subject = it }
                    )
                }

                // Language Selection
                item {
                    DropdownSelector(
                        label = "Instruction Language",
                        options = listOf("English", "Hindi", "Spanish", "French", "German"),
                        selectedOption = language,
                        onOptionSelected = { language = it }
                    )
                }

                // Actions
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val videoId = YouTubeService.extractVideoId(url)
                                if (videoId == null) {
                                    Toast.makeText(context, "Error: Enforce strict YouTube URL regex validation.", Toast.LENGTH_SHORT).show()
                                } else if (title.isBlank() || chapter.isBlank()) {
                                    Toast.makeText(context, "Title and Chapter fields cannot be empty.", Toast.LENGTH_SHORT).show()
                                } else {
                                    onSave(
                                        url,
                                        title,
                                        description,
                                        thumbnailUrl.ifEmpty { "https://img.youtube.com/vi/$videoId/hqdefault.jpg" },
                                        schoolClass,
                                        subject,
                                        language,
                                        chapter
                                    )
                                }
                            },
                            enabled = url.isNotBlank() && title.isNotBlank()
                        ) {
                            Text("Save Material")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelector(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

