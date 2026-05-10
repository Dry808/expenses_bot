package com.expensebot.model;

public enum UserState {
    IDLE,
    // Account
    AWAIT_ACCOUNT_NAME,
    AWAIT_ACCOUNT_EMOJI,
    AWAIT_ACCOUNT_CURRENCY,
    // Category
    AWAIT_CATEGORY_NAME,
    AWAIT_CATEGORY_EMOJI,
    AWAIT_CATEGORY_TYPE,
    // Transaction
    AWAIT_TX_AMOUNT,
    AWAIT_TX_CATEGORY,
    AWAIT_TX_ACCOUNT,
    AWAIT_TX_NOTE,
    AWAIT_TX_DATE,
    // Budget
    AWAIT_BUDGET_NAME,
    AWAIT_BUDGET_AMOUNT,
    AWAIT_BUDGET_CATEGORIES,
}
