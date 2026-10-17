package com.mealpick.app.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MealViewModel(application: Application) : AndroidViewModel(application) {
    private val repositories = MEAL_IDS.associateWith {
        MealRepository(MealDatabase.get(application), application.assets, it)
    }
    private val initialized = CompletableDeferred<Unit>()
    private val mutableState = MutableStateFlow(MealState())
    val state: StateFlow<MealState> = mutableState.asStateFlow()
    private var selectedMealId = DEFAULT_MEAL_ID
    private var selectionVersion = 0L
    private var observation: Job? = null

    init {
        viewModelScope.launch {
            repositories.values.forEach { it.initialize() }
            initialized.complete(Unit)
            observeSelectedMeal()
        }
    }

    fun selectMeal(mealId: String) {
        require(mealId in repositories) { "Unknown meal: $mealId" }
        if (mealId == selectedMealId) return
        selectedMealId = mealId
        selectionVersion++
        observation?.cancel()
        // Do not render the previous meal's rows while the next snapshot loads.
        mutableState.value = MealState(language = mutableState.value.language, mealId = mealId)
        if (initialized.isCompleted) observeSelectedMeal()
    }

    private fun observeSelectedMeal() {
        observation?.cancel()
        val mealId = selectedMealId
        val version = selectionVersion
        observation = viewModelScope.launch {
            repositories.getValue(mealId).state.collect {
                if (selectionVersion == version) mutableState.value = it
            }
        }
    }

    private fun change(operation: suspend MealRepository.() -> Unit) {
        // Capture the originating meal before suspension; navigation cannot reroute a write.
        val repository = repositories.getValue(selectedMealId)
        viewModelScope.launch {
            initialized.await()
            repository.operation()
        }
    }

    fun setLanguage(language: String) = change { setLanguage(language) }
    fun addGroup(scene: String, zone: String, name: String) = change { addGroup(scene, zone, name) }
    fun renameGroup(id: String, name: String) = change { renameGroup(id, name) }
    fun deleteGroup(id: String) = change { deleteGroup(id) }
    fun addItem(groupId: String, name: String, notes: String) = change { addItem(groupId, name, notes) }
    fun updateItem(id: String, name: String, notes: String) = change { updateItem(id, name, notes) }
    fun setCount(id: String, count: Long) = change { setCount(id, count) }
    fun pick(id: String) = change { pick(id) }
    fun deleteItem(id: String) = change { deleteItem(id) }
    fun reorderItems(groupId: String, orderedIds: List<String>) = change { reorderItems(groupId, orderedIds) }
    fun reorderGroups(scene: String, zone: String, orderedIds: List<String>) = change { reorderGroups(scene, zone, orderedIds) }
    fun addToCart(id: String) = change { addToCart(id) }
    fun toggleCart(id: String) = change { toggleCart(id) }
    fun removeFromCart(id: String) = change { removeFromCart(id) }
    fun setPurchased(id: String, purchased: Boolean) = change { setPurchased(id, purchased) }
    fun addToCookingMenu(id: String) = change { addToCookingMenu(id) }
    fun toggleCookingMenu(id: String) = change { toggleCookingMenu(id) }
    fun removeFromCookingMenu(id: String) = change { removeFromCookingMenu(id) }
    fun setCookingCompleted(id: String, completed: Boolean) = change { setCookingCompleted(id, completed) }
    fun transferPurchasedToMenu(id: String? = null) = change { transferPurchasedToMenu(id) }
    fun setCookingTotal(count: Long) = change { setCookingTotal(count) }
    fun completeCookingMeal(expectedSessionId: String, onResult: (PurchaseResult) -> Unit) {
        val repository = repositories.getValue(selectedMealId)
        val version = selectionVersion
        viewModelScope.launch {
            initialized.await()
            val result = try {
                repository.completeCookingMeal(expectedSessionId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PurchaseResult.Failed
            }
            if (selectionVersion == version) onResult(result)
        }
    }
}
