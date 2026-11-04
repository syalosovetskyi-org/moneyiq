# Transaction Accounting

`TransactionRepository` owns balance mutations. Keep this invariant intact.

## Add Transaction

- `INCOME`: increase `accountId` balance.
- `EXPENSE`: decrease `accountId` balance.
- `BORROW`: increase `accountId` balance.
- `LEND`: decrease `accountId` balance.
- `REPAY`: decrease `accountId`; if `toAccountId` exists, increase it.
- `TRANSFER`: decrease `accountId`; if `toAccountId` exists, increase it.

## Delete Transaction

Deletion applies the exact inverse of add.

## Update Transaction

Update is a two-phase operation:

1. Roll back old transaction balance effects.
2. Persist the new transaction.
3. Apply new transaction balance effects.

## Regression Rules

- Do not update account balances directly in UI screens.
- Do not insert/update/delete transactions outside `TransactionRepository` unless the balance invariant is explicitly handled.
- Be careful with `toAccountId == null`; transfer-like operations become one-sided balance updates.
- If transaction semantics change, update tests and this document.

## Review regressions (2026-11-04)

- A transfer/repayment credits `toAmount ?: amount`; recurring transactions follow
  the same rule. Backup JSON preserves `toAmount`, including nullable legacy values.
- Changing either account or the source amount invalidates cached credited amount.
  Editing only a note preserves the historical credit even when rates change.
- Account/category insert-or-update uses Room `@Upsert`, never SQLite REPLACE:
  deleting a parent row during REPLACE cascades or nulls transaction references.
- Unknown exchange rates still follow ADR-073 (1:1 plus warning). The publication
  review flags this as a financial risk; that policy has not been changed.
