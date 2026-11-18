package com.google.firebase.example.friendlymeals

import com.google.firebase.example.friendlymeals.data.model.Recipe
import com.google.firebase.example.friendlymeals.ui.recipe.AudioState
import com.google.firebase.example.friendlymeals.ui.recipe.RecipeViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeViewStateTest {

    @Test
    fun defaultState_hasIdleAudioState() {
        val state = RecipeViewState()
        assertEquals(AudioState.IDLE, state.instructionsAudioState)
        assertNull(state.audioErrorMessage)
    }

    @Test
    fun copyState_updatesAudioStateProperly() {
        val state = RecipeViewState(recipe = Recipe(title = "Test Soup", instructions = "Simmer vegetables"))
        val generatingState = state.copy(instructionsAudioState = AudioState.GENERATING)
        assertEquals(AudioState.GENERATING, generatingState.instructionsAudioState)

        val playingState = generatingState.copy(instructionsAudioState = AudioState.PLAYING)
        assertEquals(AudioState.PLAYING, playingState.instructionsAudioState)

        val pausedState = playingState.copy(instructionsAudioState = AudioState.PAUSED)
        assertEquals(AudioState.PAUSED, pausedState.instructionsAudioState)

        val readyState = pausedState.copy(instructionsAudioState = AudioState.READY)
        assertEquals(AudioState.READY, readyState.instructionsAudioState)

        val errorState = state.copy(instructionsAudioState = AudioState.ERROR, audioErrorMessage = "Network error")
        assertEquals(AudioState.ERROR, errorState.instructionsAudioState)
        assertEquals("Network error", errorState.audioErrorMessage)
    }
}

