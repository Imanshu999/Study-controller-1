package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.StudyVideo
import com.example.data.StudyVideoRepository
import com.example.network.YouTubeService
import com.example.security.JwtTokenService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    object Idle : UiState<Nothing>
    object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class StudyViewModel(private val repository: StudyVideoRepository) : ViewModel() {

    init {
        viewModelScope.launch {
            try {
                // To fulfill "Delete fack data videos", scan the database and delete any leftover seeded videos.
                val fakeUrls = listOf(
                    "https://www.youtube.com/watch?v=fNk_zzaMoEs",
                    "https://www.youtube.com/watch?v=b1t41Q3xRM8",
                    "https://www.youtube.com/watch?v=QiiyDXgT890"
                )
                val currentVideos = repository.allVideos.first()
                val systemToken = JwtTokenService.generateToken("system@studycontroller.com", "Admin")
                currentVideos.forEach { video ->
                    if (fakeUrls.contains(video.youtubeUrl)) {
                        repository.deleteVideoSecured(systemToken, video.id)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Authentication States
    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail = _userEmail.asStateFlow()

    private val _userRole = MutableStateFlow<String>("Student") // Default to Student
    val userRole = _userRole.asStateFlow()

    private val _jwtToken = MutableStateFlow<String?>(null)
    val jwtToken = _jwtToken.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError = _authError.asStateFlow()

    val isAdmin = _userRole.map { it == "Admin" }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Search & Filtering States
    val searchQuery = MutableStateFlow("")
    val selectedClass = MutableStateFlow<String?>(null)
    val selectedSubject = MutableStateFlow<String?>(null)
    val selectedLanguage = MutableStateFlow<String?>(null)

    // YouTube URL Fetch state
    private val _fetchedMetadataState = MutableStateFlow<UiState<com.example.network.YouTubeVideoMetadata>>(UiState.Idle)
    val fetchedMetadataState = _fetchedMetadataState.asStateFlow()

    // Database action state
    private val _dbActionState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val dbActionState = _dbActionState.asStateFlow()

    // Reactive list of filtered videos
    val filteredVideos: StateFlow<List<StudyVideo>> = combine(
        repository.allVideos,
        searchQuery,
        selectedClass,
        selectedSubject,
        selectedLanguage
    ) { videos, query, cl, subj, lang ->
        videos.filter { video ->
            val matchesQuery = query.isBlank() ||
                    video.videoName.contains(query, ignoreCase = true) ||
                    video.description.contains(query, ignoreCase = true)

            val matchesClass = cl == null || video.schoolClass.equals(cl, ignoreCase = true)
            val matchesSubject = subj == null || video.subject.equals(subj, ignoreCase = true)
            val matchesLanguage = lang == null || video.language.equals(lang, ignoreCase = true)

            matchesQuery && matchesClass && matchesSubject && matchesLanguage
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unique classes, subjects, languages for filtering dynamically from DB plus defaults
    val classesList: StateFlow<List<String>> = repository.allVideos.map { videos ->
        val list = videos.map { it.schoolClass.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        (listOf("Class 9", "Class 10", "Class 11", "Class 12") + list).distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Class 9", "Class 10", "Class 11", "Class 12"))

    val subjectsList: StateFlow<List<String>> = repository.allVideos.map { videos ->
        val list = videos.map { it.subject.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        (listOf("Mathematics", "Physics", "Chemistry", "Biology", "English", "History") + list).distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Mathematics", "Physics", "Chemistry", "Biology"))

    val languagesList: StateFlow<List<String>> = repository.allVideos.map { videos ->
        val list = videos.map { it.language.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        (listOf("English", "Hindi", "Spanish", "French", "German") + list).distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("English", "Hindi", "Spanish"))

    // Pre-registered list of admin emails
    private val preRegisteredAdminEmails = setOf(
        "dome74677@gmail.com",
        "n4062226@gmail.com"
    )

    fun loginAsStudent() {
        _userRole.value = "Student"
        _userEmail.value = "student@studycontroller.com"
        _jwtToken.value = null
        _authError.value = null
    }

    fun loginAsAdmin(email: String, password: String): Boolean {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        val hasAdminDomain = trimmedEmail.endsWith("@studycontroller.com", ignoreCase = true)
        val isPreRegistered = preRegisteredAdminEmails.contains(trimmedEmail.lowercase())

        if (!(hasAdminDomain || isPreRegistered)) {
            _authError.value = "Unauthorized: Email address is not registered on the Admin list or domain."
            return false
        }

        val requiredPassword = "1os9B2d44fXl1VTOpukwbmfg5euGibSWrVLQpffF"
        if (trimmedPassword != requiredPassword) {
            _authError.value = "Unauthorized: Invalid admin security password."
            return false
        }

        val token = JwtTokenService.generateToken(trimmedEmail, "Admin")
        _userEmail.value = trimmedEmail
        _userRole.value = "Admin"
        _jwtToken.value = token
        _authError.value = null
        return true
    }

    fun logout() {
        _userEmail.value = null
        _userRole.value = "Student"
        _jwtToken.value = null
        _authError.value = null
    }

    // URL Fetching
    fun fetchYouTubeMetadata(url: String) {
        viewModelScope.launch {
            _fetchedMetadataState.value = UiState.Loading
            val videoId = YouTubeService.extractVideoId(url)
            if (videoId == null) {
                _fetchedMetadataState.value = UiState.Error("Invalid YouTube URL. Please use watch, shorts, or embed formats.")
                return@launch
            }

            val result = YouTubeService.fetchVideoMetadata(url)
            result.fold(
                onSuccess = { metadata ->
                    _fetchedMetadataState.value = UiState.Success(metadata)
                },
                onFailure = { error ->
                    _fetchedMetadataState.value = UiState.Error(error.message ?: "Failed to fetch video metadata")
                }
            )
        }
    }

    fun clearFetchedMetadata() {
        _fetchedMetadataState.value = UiState.Idle
    }

    // CRUD database actions secured with JWT token
    fun addVideo(
        youtubeUrl: String,
        title: String,
        description: String,
        thumbnailUrl: String,
        schoolClass: String,
        subject: String,
        language: String,
        chapter: String
    ) {
        viewModelScope.launch {
            _dbActionState.value = UiState.Loading
            val token = _jwtToken.value
            if (token == null) {
                _dbActionState.value = UiState.Error("Session error: Admin is not logged in or token is missing.")
                return@launch
            }

            val video = StudyVideo(
                youtubeUrl = youtubeUrl.trim(),
                videoName = title.trim(),
                description = description.trim(),
                thumbnailUrl = thumbnailUrl.trim(),
                schoolClass = schoolClass.trim(),
                subject = subject.trim(),
                language = language.trim(),
                chapter = chapter.trim()
            )

            val result = repository.insertVideoSecured(token, video)
            result.fold(
                onSuccess = {
                    _dbActionState.value = UiState.Success("Video added successfully!")
                },
                onFailure = { error ->
                    _dbActionState.value = UiState.Error(error.message ?: "Failed to add video")
                }
            )
        }
    }

    fun editVideo(
        id: Int,
        youtubeUrl: String,
        title: String,
        description: String,
        thumbnailUrl: String,
        schoolClass: String,
        subject: String,
        language: String,
        chapter: String
    ) {
        viewModelScope.launch {
            _dbActionState.value = UiState.Loading
            val token = _jwtToken.value
            if (token == null) {
                _dbActionState.value = UiState.Error("Session error: Admin is not logged in.")
                return@launch
            }

            val video = StudyVideo(
                id = id,
                youtubeUrl = youtubeUrl.trim(),
                videoName = title.trim(),
                description = description.trim(),
                thumbnailUrl = thumbnailUrl.trim(),
                schoolClass = schoolClass.trim(),
                subject = subject.trim(),
                language = language.trim(),
                chapter = chapter.trim()
            )

            val result = repository.updateVideoSecured(token, video)
            result.fold(
                onSuccess = {
                    _dbActionState.value = UiState.Success("Video updated successfully!")
                },
                onFailure = { error ->
                    _dbActionState.value = UiState.Error(error.message ?: "Failed to update video")
                }
            )
        }
    }

    fun deleteVideo(videoId: Int) {
        viewModelScope.launch {
            _dbActionState.value = UiState.Loading
            val token = _jwtToken.value
            if (token == null) {
                _dbActionState.value = UiState.Error("Session error: Admin is not logged in.")
                return@launch
            }

            val result = repository.deleteVideoSecured(token, videoId)
            result.fold(
                onSuccess = {
                    _dbActionState.value = UiState.Success("Video deleted successfully!")
                },
                onFailure = { error ->
                    _dbActionState.value = UiState.Error(error.message ?: "Failed to delete video")
                }
            )
        }
    }

    fun resetDbActionState() {
        _dbActionState.value = UiState.Idle
    }
}

class StudyViewModelFactory(private val repository: StudyVideoRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StudyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
