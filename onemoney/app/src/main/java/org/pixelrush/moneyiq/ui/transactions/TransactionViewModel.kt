package org.syalosovetskyi.onemoney.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.repository.AccountRepository
import org.syalosovetskyi.onemoney.data.repository.CategoryRepository
import org.syalosovetskyi.onemoney.data.repository.TransactionRepository
import javax.inject.Inject

data class AddTransactionUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val selectedAccountId: Long? = null,
    val selectedToAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val note: String = "",
    val date: Long = System.currentTimeMillis(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val isEditMode: Boolean = false
)

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val txRepo: TransactionRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val editingId: Long? = savedStateHandle.get<Long>("txId")?.takeIf { it > 0 }
    private var originalTx: TransactionEntity? = null

    private val _state = MutableStateFlow(AddTransactionUiState())
    val state: StateFlow<AddTransactionUiState> = _state.asStateFlow()

    init {
        // Загрузка списков счетов и категорий
        viewModelScope.launch {
            combine(
                accountRepo.getAllAccounts(),
                categoryRepo.getAll()
            ) { accounts, categories ->
                _state.update {
                    it.copy(
                        accounts = accounts,
                        categories = categories,
                        selectedAccountId = it.selectedAccountId ?: accounts.firstOrNull()?.id
                    )
                }
            }.collect()
        }

        // Если режим редактирования — загрузить существующую транзакцию
        if (editingId != null) {
            viewModelScope.launch {
                val tx = txRepo.getById(editingId)
                if (tx != null) {
                    originalTx = tx
                    _state.update {
                        it.copy(
                            type = tx.type,
                            amount = tx.amount.toBigDecimal().stripTrailingZeros().toPlainString(),
                            selectedAccountId = tx.accountId,
                            selectedToAccountId = tx.toAccountId,
                            selectedCategoryId = tx.categoryId,
                            note = tx.note,
                            date = tx.date,
                            isEditMode = true
                        )
                    }
                }
            }
        }
    }

    fun setType(type: TransactionType) {
        _state.update { it.copy(type = type, selectedCategoryId = null) }
    }

    fun setAmount(v: String) { _state.update { it.copy(amount = v) } }
    fun setAccount(id: Long?) { _state.update { it.copy(selectedAccountId = id) } }
    fun setToAccount(id: Long?) { _state.update { it.copy(selectedToAccountId = id) } }
    fun setCategory(id: Long?) { _state.update { it.copy(selectedCategoryId = id) } }
    fun setNote(v: String) { _state.update { it.copy(note = v) } }
    fun setDate(v: Long) { _state.update { it.copy(date = v) } }

    fun save() {
        val s = _state.value
        val amount = s.amount.replace(",", ".").toDoubleOrNull()
        if (amount == null || !amount.isFinite() || amount <= 0) {
            _state.update { it.copy(error = "Введіть коректну суму") }
            return
        }
        if (s.selectedAccountId == null) {
            _state.update { it.copy(error = "Оберіть рахунок") }
            return
        }
        if (s.type == TransactionType.TRANSFER && s.selectedToAccountId == null) {
            _state.update { it.copy(error = "Оберіть рахунок призначення") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                if (s.isEditMode && editingId != null && originalTx != null) {
                    // Редактирование существующей транзакции
                    val newTx = originalTx!!.copy(
                        type = s.type,
                        amount = amount,
                        accountId = s.selectedAccountId,
                        toAccountId = s.selectedToAccountId,
                        categoryId = s.selectedCategoryId,
                        note = s.note,
                        date = s.date
                    )
                    txRepo.updateTransaction(originalTx!!, newTx)
                } else {
                    // Новая транзакция
                    txRepo.addTransaction(
                        TransactionEntity(
                            type = s.type,
                            amount = amount,
                            accountId = s.selectedAccountId,
                            toAccountId = s.selectedToAccountId,
                            categoryId = s.selectedCategoryId,
                            note = s.note,
                            date = s.date
                        )
                    )
                }
                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("TransactionViewModel", "Transaction write failed", e)
                _state.update { it.copy(isSaving = false, error = e.message ?: "Помилка збереження") }
            }
        }
    }

    fun delete() {
        val orig = originalTx ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                txRepo.deleteTransaction(orig)
                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("TransactionViewModel", "Transaction write failed", e)
                _state.update { it.copy(isSaving = false, error = e.message ?: "Помилка видалення") }
            }
        }
    }
}
