package org.syalosovetskyi.onemoney.ui.shots

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.syalosovetskyi.onemoney.data.db.dao.CategorySpending
import org.syalosovetskyi.onemoney.data.db.dao.TransactionWithDetails
import org.syalosovetskyi.onemoney.data.db.entities.AccountEntity
import org.syalosovetskyi.onemoney.data.db.entities.AccountType
import org.syalosovetskyi.onemoney.data.db.entities.CategoryEntity
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.repository.AppMonth
import org.syalosovetskyi.onemoney.data.repository.AppSettings
import org.syalosovetskyi.onemoney.ui.accounts.AccountsUiState
import org.syalosovetskyi.onemoney.ui.budget.BudgetCatRow
import org.syalosovetskyi.onemoney.ui.budget.BudgetSectionData
import org.syalosovetskyi.onemoney.ui.budget.BudgetSelMonth
import org.syalosovetskyi.onemoney.ui.budget.BudgetUiState
import org.syalosovetskyi.onemoney.ui.categories.CategoriesUiState
import org.syalosovetskyi.onemoney.ui.categories.SelectedMonth
import org.syalosovetskyi.onemoney.ui.data.BackupEntry
import org.syalosovetskyi.onemoney.ui.data.DataUiState
import org.syalosovetskyi.onemoney.ui.main.MainUiState
import org.syalosovetskyi.onemoney.ui.overview.CategorySegment
import org.syalosovetskyi.onemoney.ui.overview.DayBar
import org.syalosovetskyi.onemoney.ui.overview.OverviewCatRow
import org.syalosovetskyi.onemoney.ui.overview.OverviewSelMonth
import org.syalosovetskyi.onemoney.ui.overview.OverviewUiState
import org.syalosovetskyi.onemoney.ui.reports.ReportsUiState
import org.syalosovetskyi.onemoney.ui.settings.data.CURRENCIES_ALL
import org.syalosovetskyi.onemoney.ui.transactions.AddTransactionUiState
import org.syalosovetskyi.onemoney.ui.transactions.TxListUiState
import org.syalosovetskyi.onemoney.ui.transactions.TxSelectedMonth
import org.syalosovetskyi.onemoney.workers.DriveBackupEntry
import org.syalosovetskyi.onemoney.workers.MonoFlowScenarioHarness
import java.util.Calendar

/**
 * «Важкі» дані для знімків (serg/tasks#3037): довгі українські назви, суми до 1 234 567,89 ₴,
 * рахунки в кількох валютах, 12 операцій, бюджет з перевитратою. Категорії — з фікстури
 * mono-flow через [MonoFlowScenarioHarness] (сценарій fresh) + одна власна довга категорія.
 *
 * Період зафіксовано (листопад 2026), щоб знімки не «пливли» від дати прогону.
 */
object ShotData {
    const val YEAR = 2026
    const val MONTH = 10 // 0-based: листопад
    const val DAYS = 30
    const val DAYS_PASSED = 15
    val appMonth = AppMonth(YEAR, MONTH)
    const val MONTH_LABEL = "Листопад 2026"

    fun at(day: Int, hour: Int = 12, minute: Int = 0): Long = Calendar.getInstance().apply {
        clear(); set(YEAR, MONTH, day, hour, minute)
    }.timeInMillis

    // ── Рахунки ─────────────────────────────────────────────────────────────
    val accMono = AccountEntity(id = 1, name = "Monobank Чорна картка USD", type = AccountType.CARD,
        balance = 12_345.67, currency = "USD", colorHex = "#212121", icon = "credit_card", isDefault = true,
        description = "Основна картка для витрат за кордоном та підписок")
    val accCash = AccountEntity(id = 2, name = "Готівка в гаманці", type = AccountType.CASH,
        balance = 1_234_567.89, currency = "UAH", colorHex = "#4CAF50", icon = "account_balance_wallet")
    val accPrivat = AccountEntity(id = 3, name = "ПриватБанк Універсальна картка", type = AccountType.CARD,
        balance = 48_210.05, currency = "UAH", colorHex = "#8BC34A", icon = "credit_card", sortOrder = 2)
    val accDeposit = AccountEntity(id = 4, name = "Ощадний депозит у євро", type = AccountType.SAVING,
        balance = 9_876.54, currency = "EUR", colorHex = "#1E88E5", icon = "savings", sortOrder = 3)
    val accCredit = AccountEntity(id = 5, name = "Кредитна картка Альфа-Банк", type = AccountType.DEBT,
        balance = -25_000.00, currency = "UAH", colorHex = "#E53935", icon = "credit_card", sortOrder = 4,
        creditLimit = 50_000.0)
    val accounts = listOf(accMono, accCash, accPrivat, accDeposit, accCredit)
    const val TOTAL_BALANCE = 1_802_345.67

    // ── Категорії ───────────────────────────────────────────────────────────
    const val LONG_CATEGORY = "Комунальні послуги та інтернет"
    private const val LONG_ID = 9_000_001L

    /** Усі категорії фікстури після синку (включно з архівними і підкатегоріями) + довга власна. */
    val categories: List<CategoryEntity> by lazy {
        val db = MonoFlowScenarioHarness.newDb(ApplicationProvider.getApplicationContext())
        val loaded = try {
            runBlocking {
                MonoFlowScenarioHarness.fresh(db)
                db.categoryDao().getAllCategoriesOnce()
            }
        } finally {
            db.close()
        }
        val withBudgets = loaded.map { c ->
            val budget = if (c.parentId == null && !c.archived) BUDGETS[c.name] else null
            if (budget != null && c.type == TransactionType.EXPENSE) c.copy(budgetAmount = budget) else c
        }
        withBudgets + CategoryEntity(id = LONG_ID, name = LONG_CATEGORY, type = TransactionType.EXPENSE,
            colorHex = "#6D4C41", icon = "family", budgetAmount = 2_000.0, sortOrder = 99)
    }

    /** Бюджети коренів витрат; «Продукти» і довга категорія — з перевитратою. */
    private val BUDGETS = mapOf("Продукти" to 8_000.0, "Транспорт" to 3_000.0, "Ресторація" to 2_500.0,
        "Здоров'я" to 1_500.0)

    private val SPEND = mapOf("Продукти" to 9_876.54, "Транспорт" to 1_234.50, "Ресторація" to 2_480.00,
        "Здоров'я" to 640.00, "Покупки" to 15_999.99, "Дозвілля" to 1_100.00, "Сім'я" to 420.00,
        "Робота" to 3_870.00, "Подарунки" to 1_250.00, LONG_CATEGORY to 3_456.78)
    private val EARN = mapOf("Зарплата" to 1_234_567.89, "Кешбек" to 312.40, "Переказ від людей" to 5_000.0)

    val expenseCategories: List<CategoryEntity> get() = categories.filter { it.type == TransactionType.EXPENSE }
    val incomeCategories: List<CategoryEntity> get() = categories.filter { it.type == TransactionType.INCOME }
    val expenseRoots: List<CategoryEntity> get() = expenseCategories.filter { it.parentId == null && !it.archived }
    val incomeRoots: List<CategoryEntity> get() = incomeCategories.filter { it.parentId == null && !it.archived }

    fun cat(name: String, type: TransactionType = TransactionType.EXPENSE): CategoryEntity =
        categories.first { it.name == name && it.type == type && it.parentId == null && !it.archived }

    val spending: Map<Long, Double> get() = expenseRoots.mapNotNull { c -> SPEND[c.name]?.let { c.id to it } }.toMap()
    val income: Map<Long, Double> get() = incomeRoots.mapNotNull { c -> EARN[c.name]?.let { c.id to it } }.toMap()
    val totalExpense: Double get() = spending.values.sum()
    val totalIncome: Double get() = income.values.sum()

    // ── Операції ────────────────────────────────────────────────────────────
    private fun tx(id: Long, type: TransactionType, amount: Double, acc: AccountEntity, cat: CategoryEntity?,
                   note: String, day: Int, hour: Int = 12, to: AccountEntity? = null) = TransactionWithDetails(
        id = id, type = type, amount = amount, accountId = acc.id, accountName = acc.name,
        accountColor = acc.colorHex, accountCurrency = acc.currency, toAccountId = to?.id, toAccountName = to?.name,
        categoryId = cat?.id, categoryName = cat?.name, categoryColor = cat?.colorHex, categoryIcon = cat?.icon,
        note = note, date = at(day, hour))

    val transactions: List<TransactionWithDetails> by lazy {
        val e = TransactionType.EXPENSE; val i = TransactionType.INCOME
        listOf(
            tx(1, i, 1_234_567.89, accCash, cat("Зарплата", i), "Зарплата за жовтень з премією за квартал", 15, 9),
            tx(2, e, 3_456.78, accPrivat, cat(LONG_CATEGORY), "Оплата комунальних послуг і домашнього інтернету", 15, 10),
            tx(3, e, 9_876.54, accCash, cat("Продукти"), "Сільпо: закупівля на тиждень для всієї родини", 14, 18),
            tx(4, TransactionType.TRANSFER, 25_000.0, accPrivat, null, "Поповнення депозиту", 14, 11, to = accDeposit),
            tx(5, e, 15_999.99, accMono, cat("Покупки"), "Навушники", 13, 20),
            tx(6, e, 1_234.50, accCash, cat("Транспорт"), "Таксі до аеропорту Бориспіль вночі", 12, 3),
            tx(7, e, 2_480.0, accPrivat, cat("Ресторація"), "Вечеря в ресторані з друзями", 11, 21),
            tx(8, i, 312.40, accMono, cat("Кешбек", i), "", 10, 8),
            tx(9, e, 640.0, accCash, cat("Здоров'я"), "Аптека", 9, 14),
            tx(10, e, 3_870.0, accMono, cat("Робота"), "Хостинг і хмарні сервіси на рік", 8, 10),
            tx(11, i, 5_000.0, accPrivat, cat("Переказ від людей", i), "Повернення боргу від Олександра", 6, 19),
            tx(12, e, 1_250.0, accCash, cat("Подарунки"), "Квіти й листівка на день народження бабусі", 3, 16),
        )
    }

    // ── UiState ─────────────────────────────────────────────────────────────
    val accountsState: AccountsUiState get() = AccountsUiState(accounts, TOTAL_BALANCE)
    val accountsEmpty = AccountsUiState()

    val categoriesState: CategoriesUiState get() = CategoriesUiState(
        expenseCategories = expenseCategories, incomeCategories = incomeCategories,
        monthSpending = spending, monthIncome = income,
        monthTxCounts = (spending.keys + income.keys).associateWith { 3 },
        selectedMonth = SelectedMonth(YEAR, MONTH), appMonth = appMonth, daysInMonth = DAYS, pillBadge = "$DAYS",
        totalExpense = totalExpense, totalIncome = totalIncome, accounts = accounts)
    val categoriesEmpty = CategoriesUiState(selectedMonth = SelectedMonth(YEAR, MONTH), appMonth = appMonth,
        daysInMonth = DAYS, pillBadge = "$DAYS")

    val txListState: TxListUiState get() {
        val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        return TxListUiState(selectedMonth = TxSelectedMonth(YEAR, MONTH), appMonth = appMonth,
            daysInMonth = DAYS, pillBadge = "$DAYS", transactions = transactions, baseTransactions = transactions,
            totalIncome = income, totalExpense = expense, openingBalance = 612_345.12,
            closingBalance = 612_345.12 + income - expense, accounts = accounts,
            expenseCategories = expenseCategories, incomeCategories = incomeCategories)
    }
    val txListEmpty = TxListUiState(selectedMonth = TxSelectedMonth(YEAR, MONTH), appMonth = appMonth,
        daysInMonth = DAYS, pillBadge = "$DAYS")

    private fun budgetRows(): List<BudgetCatRow> = expenseRoots.map { c ->
        BudgetCatRow(c, spending[c.id] ?: 0.0, c.budgetAmount)
    }

    val budgetState: BudgetUiState get() {
        val rows = budgetRows()
        val incomeRows = incomeRoots.map { BudgetCatRow(it, income[it.id] ?: 0.0, 0.0) }
        return BudgetUiState(selectedMonth = BudgetSelMonth(YEAR, MONTH), appMonth = appMonth,
            daysInMonth = DAYS, daysPassed = DAYS_PASSED, pillBadge = "$DAYS", totalBalance = TOTAL_BALANCE,
            expenseSection = BudgetSectionData(rows.sumOf { it.budgetForPeriod }, rows.sumOf { it.amount }, rows),
            incomeSection = BudgetSectionData(0.0, incomeRows.sumOf { it.amount }, incomeRows))
    }
    val budgetEmpty = BudgetUiState(selectedMonth = BudgetSelMonth(YEAR, MONTH), appMonth = appMonth,
        daysInMonth = DAYS, daysPassed = DAYS_PASSED, pillBadge = "$DAYS")

    val overviewRows: List<OverviewCatRow> get() {
        val total = totalExpense
        return expenseRoots.filter { (spending[it.id] ?: 0.0) > 0 }
            .sortedByDescending { spending[it.id] }
            .map { c ->
                val amount = spending.getValue(c.id)
                OverviewCatRow(c.id, c.name, c.colorHex, c.icon, amount, (amount / total).toFloat(), c.budgetAmount)
            }
    }

    val overviewState: OverviewUiState get() {
        val bars = (1..DAYS).map { d ->
            val dayTx = transactions.filter { it.type == TransactionType.EXPENSE && it.date in at(d, 0)..at(d, 23, 59) }
            DayBar(day = d, amount = dayTx.sumOf { it.amount }, isFuture = d > DAYS_PASSED,
                segments = dayTx.map { CategorySegment(it.categoryColor ?: "#9E9E9E", it.amount) })
        }
        return OverviewUiState(selectedMonth = OverviewSelMonth(YEAR, MONTH), appMonth = appMonth,
            daysInMonth = DAYS, totalBalance = TOTAL_BALANCE, monthExpense = totalExpense,
            monthIncome = totalIncome, txCount = transactions.size, dailyAvg = totalExpense / DAYS_PASSED,
            todayAmount = 3_456.78, weekAmount = 33_047.81, dayBars = bars, categories = overviewRows,
            transactions = transactions, totalExpenseBudget = expenseRoots.sumOf { it.budgetAmount },
            totalIncomeBudget = 1_000_000.0)
    }
    val overviewEmpty = OverviewUiState(selectedMonth = OverviewSelMonth(YEAR, MONTH), appMonth = appMonth,
        daysInMonth = DAYS, dayBars = (1..DAYS).map { DayBar(it, 0.0, it > DAYS_PASSED) })

    val reportsState: ReportsUiState get() = ReportsUiState(
        periodLabel = MONTH_LABEL, income = totalIncome, expense = totalExpense,
        incomeByCategory = incomeRoots.mapNotNull { c -> income[c.id]?.let { CategorySpending(c.id, c.name, c.colorHex, c.icon, it, 2) } },
        expenseByCategory = expenseRoots.mapNotNull { c -> spending[c.id]?.let { CategorySpending(c.id, c.name, c.colorHex, c.icon, it, 3) } })
    val reportsEmpty = ReportsUiState(periodLabel = MONTH_LABEL)

    fun addTxState(type: TransactionType): AddTransactionUiState = AddTransactionUiState(
        type = type, amount = "1234567.89", selectedAccountId = accCash.id,
        selectedToAccountId = if (type == TransactionType.TRANSFER) accDeposit.id else null,
        selectedCategoryId = when (type) {
            TransactionType.EXPENSE -> cat(LONG_CATEGORY).id
            TransactionType.INCOME -> cat("Зарплата", TransactionType.INCOME).id
            else -> null
        },
        note = "Оплата комунальних послуг і домашнього інтернету за листопад", date = at(15, 10),
        accounts = accounts, categories = categories)
    val addTxEmpty = AddTransactionUiState(date = at(15, 10))

    val settings = AppSettings(defaultCurrency = "UAH", notificationHour = 21, notificationMinute = 30,
        monoflowUrl = "https://monoflow.example", monoflowAutoSync = true)

    val mainState: MainUiState get() = MainUiState(totalBalance = TOTAL_BALANCE, monthIncome = totalIncome,
        monthExpense = totalExpense, accounts = accounts, recentTransactions = transactions, isLoading = false)

    val dataState = DataUiState(
        localBackups = listOf(BackupEntry(at(14, 23), 1_234, 5, 92), BackupEntry(at(1, 23), 987, 5, 90)),
        driveBackups = listOf(DriveBackupEntry("doc-1", "zerno-backup-2026-11-14.json", at(14, 23), 1_234_567)),
        driveFolderUri = "content://drive/folder", driveFolderName = "Резервні копії Zerno",
        driveBackupEnabled = true, driveLastBackupMs = at(14, 23),
        monoflowUrl = "https://monoflow.example", monoflowToken = "token", monoflowAutoSync = true,
        monoflowLastSyncMs = at(15, 9))

    /**
     * Латинські рядки, які НЕ є дефектом інтерфейсу: дані фікстур і тестові дані, валюти.
     * Довші — першими: [LayoutAudit] вирізає їх із тексту по черзі.
     */
    val latinAllowList: List<String> by lazy {
        (categories.map { it.name } + accounts.flatMap { listOf(it.name, it.description) } +
            transactions.map { it.note } + dataState.driveBackups.map { it.name } +
            listOf(dataState.monoflowUrl, dataState.driveFolderName, "Zerno") +
            CURRENCIES_ALL.flatMap { listOf(it.code, it.name, it.symbol) })
            .filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
    }
}
