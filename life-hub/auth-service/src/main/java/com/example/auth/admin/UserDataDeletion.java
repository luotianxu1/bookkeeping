package com.example.auth.admin;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Runs only inside AdminService's transaction, after authorization and the target user lock. */
final class UserDataDeletion {
    private final JdbcTemplate jdbc;
    private final Set<String> tables;

    UserDataDeletion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        // Auth-only installations do not have business tables. Do not swallow SQL/permission failures.
        this.tables = jdbc.execute((ConnectionCallback<Set<String>>) connection -> {
            Set<String> names = new HashSet<>();
            try (var rows = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), "%", new String[]{"TABLE"})) {
                while (rows.next()) names.add(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
            return names;
        });
    }

    void delete(long userId) {
        removeFamilyMembership(userId);

        children("food_order_items", "order_id", "food_orders", userId);
        owned("food_orders", userId);
        children("food_dish_steps", "dish_id", "food_dishes", userId);
        children("food_dish_ingredients", "dish_id", "food_dishes", userId);
        owned("food_dishes", userId);
        owned("food_ingredients", userId);
        owned("food_categories", userId);

        children("travel_plan_expenses", "travel_plan_id", "travel_plans", userId);
        children("travel_plan_companions", "travel_plan_id", "travel_plans", userId);
        if (tables.contains("travel_plan_itineraries")) {
            jdbc.update("""
                DELETE FROM travel_plan_itineraries WHERE travel_plan_day_id IN (
                    SELECT d.id FROM travel_plan_days d JOIN travel_plans p ON p.id = d.travel_plan_id WHERE p.user_id = ?)
                """, userId);
        }
        children("travel_plan_days", "travel_plan_id", "travel_plans", userId);
        owned("travel_plans", userId);

        children("savings_goal_entries", "goal_id", "savings_goals", userId);
        owned("savings_goals", userId);
        owned("term_deposit_operations", userId);
        if (tables.contains("term_deposits")) {
            jdbc.update("UPDATE term_deposits SET parent_deposit_id = NULL WHERE user_id = ?", userId);
        }
        owned("term_deposits", userId);
        owned("investment_auto_invest_plans", userId);
        owned("investment_dividend_records", userId);
        owned("investment_transactions", userId);
        owned("investment_positions", userId);
        owned("investment_fixed_expenses", userId);
        owned("photography_orders", userId);
        owned("renewal_subscriptions", userId);
        owned("expense_recovery_followups", userId);
        if (tables.contains("transactions")) {
            // Refunds have a required negative amount + original reference; delete them before originals.
            jdbc.update("DELETE FROM transactions WHERE user_id = ? AND original_transaction_id IS NOT NULL", userId);
        }
        owned("transactions", userId);
        if (tables.contains("debt_records")) {
            if (!jdbc.queryForList("""
                SELECT child.id FROM debt_records child JOIN debt_records parent ON parent.id = child.parent_record_id
                WHERE parent.user_id = ? AND child.user_id <> ? FOR UPDATE
                """, Long.class, userId, userId).isEmpty()) {
                throw new IllegalArgumentException("借还款存在跨用户关联，删除已全部回滚，请先检查数据归属");
            }
            // Avoid relying on cascade order for repayments of the same user's borrowing records.
            jdbc.update("UPDATE debt_records SET parent_record_id = NULL WHERE user_id = ?", userId);
        }
        owned("debt_records", userId);
        owned("human_relation_records", userId);
        owned("liability_records", userId);
        owned("category_budgets", userId);
        owned("monthly_budgets", userId);
        owned("accounts", userId);
        owned("categories", userId);
        owned("contacts", userId);
        for (String table : new String[]{"todo_items", "anniversaries", "salary_month_records", "salary_account_records",
            "salary_special_deductions", "salary_profiles", "weight_records", "health_profiles", "reminder_states",
            "reminder_settings", "accounting_requests", "asset_daily_snapshots", "user_sessions", "user_security"}) {
            owned(table, userId);
        }
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    private void owned(String table, long userId) {
        if (tables.contains(table)) jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", userId);
    }

    private void children(String table, String parentKey, String parent, long userId) {
        if (tables.contains(table)) {
            jdbc.update("DELETE FROM " + table + " WHERE " + parentKey + " IN (SELECT id FROM " + parent + " WHERE user_id = ?)", userId);
        }
    }

    private void removeFamilyMembership(long userId) {
        if (!tables.contains("families")) return;
        var families = jdbc.queryForList("SELECT id FROM families WHERE owner_user_id = ? ORDER BY id FOR UPDATE", Long.class, userId);
        for (long familyId : families) {
            // Current reads serialize with join/unbind. Never remove another member's personal data.
            var members = jdbc.queryForList("SELECT id FROM users WHERE family_id = ? AND id <> ? AND status = 'active' ORDER BY id FOR UPDATE",
                Long.class, familyId, userId);
            Long successor = null;
            for (long memberId : members) {
                if (jdbc.queryForList("SELECT id FROM families WHERE owner_user_id = ? FOR UPDATE", Long.class, memberId).isEmpty()) {
                    successor = memberId;
                    break;
                }
            }
            if (successor != null) {
                jdbc.update("UPDATE families SET owner_user_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", successor, familyId);
            } else {
                jdbc.update("UPDATE users SET family_id = NULL, updated_at = CURRENT_TIMESTAMP WHERE family_id = ?", familyId);
                jdbc.update("DELETE FROM families WHERE id = ?", familyId);
            }
        }
    }
}
