package com.han.battery.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han.battery.data.model.BatteryPerformancePost
import com.han.battery.data.model.SohCondition
import com.han.battery.data.model.CommunityFilterRequest
import com.han.battery.data.repository.CommunityRepository
import com.han.battery.data.storage.UserManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BoardUiState(
    val posts: List<BatteryPerformancePost> = emptyList(),
    val selectedCategory: BoardFilterCategory = BoardFilterCategory.ALL,
    val selectedValue: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val phoneModels: List<String> = emptyList(),
    val manufacturers: List<String> = emptyList(),
    val capacities: List<String> = emptyList(),
    val currentUserName: String? = null
) {
    val filteredPosts: List<BatteryPerformancePost>
        get() {
            val baseFiltered = posts
            return if (searchQuery.isBlank()) {
                baseFiltered
            } else {
                baseFiltered.filter { post ->
                    post.userName.contains(searchQuery, ignoreCase = true) ||
                    post.comment.contains(searchQuery, ignoreCase = true) ||
                    post.smartphoneModel.orEmpty().contains(searchQuery, ignoreCase = true) ||
                    post.powerBankModel.contains(searchQuery, ignoreCase = true) ||
                    post.powerBankManufacturer.orEmpty().contains(searchQuery, ignoreCase = true)
                }
            }
        }

    val filterOptions: List<String>
        get() = when (selectedCategory) {
            BoardFilterCategory.ALL -> emptyList()
            BoardFilterCategory.PHONE -> phoneModels
            BoardFilterCategory.MANUFACTURER -> manufacturers
            BoardFilterCategory.CAPACITY -> capacities
        }
}

enum class BoardFilterCategory(val label: String) {
    ALL("전체"),
    PHONE("폰 기종"),
    MANUFACTURER("제조사"),
    CAPACITY("용량")
}

class BoardViewModel(
    private val communityRepository: CommunityRepository,
    private val userManager: UserManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(BoardUiState(isLoading = true))
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(currentUserName = userManager.getCurrentUser()) }
        loadFilterOptionsAndRefresh()
    }

    private fun loadFilterOptionsAndRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            // 서버에서 필터 옵션 적재
            communityRepository.getFilterOptions()
                .onSuccess { options ->
                    _uiState.update { state ->
                        state.copy(
                            phoneModels = options.phone_models.sorted(),
                            manufacturers = options.manufacturers.sorted(),
                            capacities = options.capacities.sorted().map { "${it}mAh" }
                        )
                    }
                }
            
            // 초기 포스트 로드
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val currentState = _uiState.value
            val result = if (currentState.selectedCategory == BoardFilterCategory.ALL || currentState.selectedValue == null) {
                communityRepository.getCommunity()
            } else {
                val request = buildFilterRequest(currentState.selectedCategory, currentState.selectedValue)
                communityRepository.getCommunityFiltered(request)
            }

            result
                .onSuccess { posts ->
                    _uiState.update { state ->
                        state.copy(
                            posts = posts,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "커뮤니티 데이터를 불러오지 못했습니다."
                        )
                    }
                }
        }
    }

    fun selectCategory(category: BoardFilterCategory) {
        _uiState.update { state ->
            val nextState = state.copy(selectedCategory = category)
            val defaultValue = if (category == BoardFilterCategory.ALL) null else nextState.filterOptions.firstOrNull()
            nextState.copy(selectedValue = defaultValue)
        }
        refresh()
    }

    fun selectFilterValue(value: String) {
        _uiState.update { state ->
            state.copy(selectedValue = value)
        }
        refresh()
    }

    private fun buildFilterRequest(category: BoardFilterCategory, value: String): CommunityFilterRequest {
        return when (category) {
            BoardFilterCategory.PHONE -> CommunityFilterRequest(
                phone_models = listOf(value)
            )
            BoardFilterCategory.MANUFACTURER -> CommunityFilterRequest(
                manufacturers = listOf(value)
            )
            BoardFilterCategory.CAPACITY -> {
                val rawValue = value.replace("mAh", "").toIntOrNull()
                CommunityFilterRequest(
                    capacities = if (rawValue != null) listOf(rawValue) else null
                )
            }
            BoardFilterCategory.ALL -> CommunityFilterRequest()
        }
    }

    fun deletePost(sharedReportId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            communityRepository.deleteShare(sharedReportId)
                .onSuccess {
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "공유를 취소하지 못했습니다."
                        )
                    }
                }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
}
