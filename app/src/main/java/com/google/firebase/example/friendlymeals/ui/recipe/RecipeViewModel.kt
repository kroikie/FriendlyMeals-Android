package com.google.firebase.example.friendlymeals.ui.recipe

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.google.firebase.example.friendlymeals.MainViewModel
import com.google.firebase.example.friendlymeals.data.model.Like
import com.google.firebase.example.friendlymeals.data.model.Review
import com.google.firebase.example.friendlymeals.data.repository.AIRepository
import com.google.firebase.example.friendlymeals.data.repository.AuthRepository
import com.google.firebase.example.friendlymeals.data.repository.DatabaseRepository
import com.google.firebase.example.friendlymeals.ui.shared.AudioComponent
import com.google.firebase.example.friendlymeals.ui.shared.AudioPlayerHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class RecipeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val databaseRepository: DatabaseRepository,
    private val aiRepository: AIRepository,
    private val audioPlayerHelper: AudioPlayerHelper
) : MainViewModel() {
    private val recipeRoute = savedStateHandle.toRoute<RecipeRoute>()
    private val recipeId: String = recipeRoute.recipeId

    private val _recipeViewState = MutableStateFlow(RecipeViewState())
    val recipeViewState: StateFlow<RecipeViewState>
        get() = _recipeViewState.asStateFlow()

    val userId: String get() = authRepository.currentUser?.uid.orEmpty()

    private val audioComponent = AudioComponent()
    private var cachedAudioData: ByteArray? = null
    private var cachedPairingText: String? = null
    private var cachedInstructionsAudioBytes: ByteArray? = null

    init {
        loadRecipe()
    }

    fun loadRecipe() {
        launchCatching {
            val recipe = databaseRepository.getRecipe(recipeId)
            _recipeViewState.value = _recipeViewState.value.copy(
                recipeId = recipeId,
                recipe = recipe,
                favorite = loadFavorite(),
                rating = loadRating(),
                audioState = RecipeAudioState.Idle
            )
        }
    }

    fun onPairingClick() {
        if (_recipeViewState.value.instructionsAudioState == AudioState.PLAYING) {
            pauseAudio()
        }
        when (_recipeViewState.value.audioState) {
            is RecipeAudioState.Playing -> {
                audioComponent.pause()
                _recipeViewState.value = _recipeViewState.value.copy(
                    audioState = RecipeAudioState.Paused
                )
            }
            is RecipeAudioState.Paused -> {
                audioComponent.resume()
                _recipeViewState.value = _recipeViewState.value.copy(
                    audioState = RecipeAudioState.Playing
                )
            }
            is RecipeAudioState.LoadingAudio -> {
                // Already loading audio, do nothing
            }
            is RecipeAudioState.Idle, is RecipeAudioState.Error -> {
                if (cachedAudioData != null) {
                    playCachedAudio()
                } else {
                    launchCatching { generatePairingAndAudio() }
                }
            }
        }
    }

    private fun playCachedAudio() {
        val audioData = cachedAudioData ?: return

        _recipeViewState.value = _recipeViewState.value.copy(
            audioState = RecipeAudioState.Playing

        )
        audioComponent.play(audioData) {
            _recipeViewState.value = _recipeViewState.value.copy(
                audioState = RecipeAudioState.Idle
            )
        }
    }

    private suspend fun generatePairingAndAudio() {
        _recipeViewState.value = _recipeViewState.value.copy(
            audioState = RecipeAudioState.LoadingAudio
        )


        if (cachedPairingText.isNullOrBlank()) {
            val recipe = _recipeViewState.value.recipe
            val generated = aiRepository.craftRecipePairing(recipe.title, recipe.ingredients)
            cachedPairingText = generated
        }

        val audio = aiRepository.generateSpeech(cachedPairingText)

        if (audio != null && audio.isNotEmpty()) {
            cachedAudioData = audio
            _recipeViewState.value = _recipeViewState.value.copy(
                audioState = RecipeAudioState.Playing
            )
            audioComponent.play(audio) {
                _recipeViewState.value = _recipeViewState.value.copy(
                    audioState = RecipeAudioState.Idle
                )
            }
        } else {
            _recipeViewState.value = _recipeViewState.value.copy(
                audioState = RecipeAudioState.Error("Could not generate audio")
            )
        }

    }

    private suspend fun loadFavorite(): Boolean {
        return databaseRepository.getFavorite(userId, recipeId)
    }

    private suspend fun loadRating(): Int {
        return databaseRepository.getRating(userId, recipeId)
    }

    fun toggleFavorite() {
        val like = Like(
            recipeId = recipeId,
            userId = userId
        )

        launchCatching {
            if (_recipeViewState.value.favorite) {
                databaseRepository.removeFavorite(like)
            } else {
                databaseRepository.setFavorite(like)
            }

            _recipeViewState.value = _recipeViewState.value.copy(
                favorite = loadFavorite()
            )
        }
    }

    fun leaveReview(rating: Int) {
        launchCatching {
            databaseRepository.setReview(
                Review(
                    userId = userId,
                    recipeId = recipeId,
                    rating = rating
                )
            )

            _recipeViewState.value = _recipeViewState.value.copy(
                rating = loadRating()
            )
        }
    }

    fun addIngredientsToGroceryList(ingredients: List<String>, onSuccess: () -> Unit) {
        if (userId.isEmpty() || ingredients.isEmpty()) return

        launchCatching {
            databaseRepository.addIngredientsToGroceries(userId, ingredients)
            onSuccess()
        }
    }

    fun speakInstructions() {
        val currentAudioState = _recipeViewState.value.instructionsAudioState
        if (currentAudioState == AudioState.PLAYING) {
            pauseAudio()
            return
        }

        pausePairingIfPlaying()

        val cached = cachedInstructionsAudioBytes
        if (cached != null) {
            playAudio()
            return
        }

        val instructions = _recipeViewState.value.recipe.instructions
        if (instructions.isBlank()) return

        _recipeViewState.value = _recipeViewState.value.copy(
            instructionsAudioState = AudioState.GENERATING,
            audioErrorMessage = null
        )

        launchCatching {
            val audioBytes = aiRepository.generateInstructionsSpeech(instructions)
            if (audioBytes == null || audioBytes.isEmpty()) {
                _recipeViewState.value = _recipeViewState.value.copy(
                    instructionsAudioState = AudioState.ERROR,
                    audioErrorMessage = "Failed to generate audio"
                )
            } else {
                cachedInstructionsAudioBytes = audioBytes
                playAudioInternal(audioBytes)
            }
        }
    }

    fun playAudio() {
        val bytes = cachedInstructionsAudioBytes ?: return
        pausePairingIfPlaying()
        if (_recipeViewState.value.instructionsAudioState == AudioState.PAUSED) {
            audioPlayerHelper.resume()
            _recipeViewState.value = _recipeViewState.value.copy(instructionsAudioState = AudioState.PLAYING)
        } else {
            playAudioInternal(bytes)
        }
    }

    fun restartAudio() {
        val bytes = cachedInstructionsAudioBytes ?: return
        pausePairingIfPlaying()
        if (audioPlayerHelper.isPlaying || _recipeViewState.value.instructionsAudioState == AudioState.PAUSED) {
            audioPlayerHelper.restart()
            _recipeViewState.value = _recipeViewState.value.copy(instructionsAudioState = AudioState.PLAYING)
        } else {
            playAudioInternal(bytes)
        }
    }

    private fun playAudioInternal(bytes: ByteArray) {
        audioPlayerHelper.play(bytes) {
            _recipeViewState.value = _recipeViewState.value.copy(instructionsAudioState = AudioState.READY)
        }
        _recipeViewState.value = _recipeViewState.value.copy(instructionsAudioState = AudioState.PLAYING)
    }

    fun pauseAudio() {
        audioPlayerHelper.pause()
        _recipeViewState.value = _recipeViewState.value.copy(instructionsAudioState = AudioState.PAUSED)
    }

    fun stopAudio() {
        audioComponent.stop()
        if (_recipeViewState.value.audioState is RecipeAudioState.Playing ||
            _recipeViewState.value.audioState is RecipeAudioState.Paused
        ) {
            _recipeViewState.value = _recipeViewState.value.copy(audioState = RecipeAudioState.Idle)
        }

        audioPlayerHelper.stop()
        if (_recipeViewState.value.instructionsAudioState == AudioState.PLAYING ||
            _recipeViewState.value.instructionsAudioState == AudioState.PAUSED
        ) {
            _recipeViewState.value = _recipeViewState.value.copy(instructionsAudioState = AudioState.READY)
        }
    }

    private fun pausePairingIfPlaying() {
        if (_recipeViewState.value.audioState is RecipeAudioState.Playing) {
            audioComponent.pause()
            _recipeViewState.value = _recipeViewState.value.copy(audioState = RecipeAudioState.Paused)
        }
    }

    override fun onCleared() {
        audioComponent.release()
        audioPlayerHelper.release()
        super.onCleared()
    }
}