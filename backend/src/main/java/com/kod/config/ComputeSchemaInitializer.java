package com.kod.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** 仅供明确开启的本机内测环境初始化独立 compute_* 表。 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class ComputeSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final ComputeCenterProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSchemaInitEnabled()) return;
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("compute-center-schema.sql"));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
        // CREATE TABLE IF NOT EXISTS 不会为早期 MVP 表补列；逐列探测可保留所有旧数据。
        ensureColumn("compute_product", "node_id", "BIGINT NULL AFTER supplier_user_id");
        ensureColumn("compute_card_hour_lot", "asset_type", "VARCHAR(16) NOT NULL DEFAULT 'STANDARD' AFTER owner_user_id");
        ensureColumn("compute_card_hour_lot", "gpu_model", "VARCHAR(128) NULL AFTER asset_type");
        ensureColumn("compute_card_hour_lot", "issuer_user_id", "BIGINT NULL AFTER gpu_model");
        ensureColumn("compute_card_hour_lot", "node_id", "BIGINT NULL AFTER issuer_user_id");
        ensureColumn("compute_card_hour_lot", "rate_version", "VARCHAR(32) NULL AFTER node_id");
        ensureColumn("compute_card_hour_lot", "rate_multiplier", "DECIMAL(12,4) NULL AFTER rate_version");
        ensureColumn("compute_card_hour_lot", "custody_status", "VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' AFTER rate_multiplier");
        ensureColumn("compute_card_hour_lot", "custody_fee_accrued", "DECIMAL(20,3) NOT NULL DEFAULT 0.000 AFTER custody_status");
        ensureColumn("compute_card_hour_lot", "parent_lot_id", "BIGINT NULL AFTER custody_fee_accrued");
        ensureColumn("compute_card_hour_redemption", "buyer_public_key", "MEDIUMTEXT NULL AFTER end_time");
        ensureColumn("compute_card_hour_redemption", "stop_reminded_at", "DATETIME NULL AFTER delivered_at");
        ensureColumn("compute_product", "package_prompt_tokens", "BIGINT NULL AFTER completion_rate_per_million");
        ensureColumn("compute_product", "package_completion_tokens", "BIGINT NULL AFTER package_prompt_tokens");
        ensureColumn("compute_product", "package_price_card_hours", "DECIMAL(20,3) NULL AFTER package_completion_tokens");
        ensureColumn("compute_product", "upstream_station_id", "BIGINT NULL AFTER package_price_card_hours");
        ensureColumn("compute_product", "upstream_key_id", "BIGINT NULL AFTER upstream_station_id");
        ensureColumn("compute_product", "is_test", "TINYINT NOT NULL DEFAULT 0 AFTER sla_description");
        ensureColumn("compute_product", "trade_mode", "VARCHAR(32) NOT NULL DEFAULT 'LEGACY_RESERVATION' AFTER price_per_gpu_hour");
        ensureColumn("compute_product", "package_duration_hours", "INT NULL AFTER trade_mode");
        ensureColumn("compute_product", "delivery_deadline_hours", "INT NULL AFTER package_duration_hours");
        ensureColumn("compute_api_usage_charge", "deducted_prompt_tokens", "BIGINT NOT NULL DEFAULT 0 AFTER completion_tokens");
        ensureColumn("compute_api_usage_charge", "deducted_completion_tokens", "BIGINT NOT NULL DEFAULT 0 AFTER deducted_prompt_tokens");
        ensureColumn("compute_api_usage_charge", "gifted_prompt_tokens", "BIGINT NOT NULL DEFAULT 0 AFTER deducted_completion_tokens");
        ensureColumn("compute_api_usage_charge", "gifted_completion_tokens", "BIGINT NOT NULL DEFAULT 0 AFTER gifted_prompt_tokens");
        ensureColumn("compute_reservation", "status_before_incident", "VARCHAR(24) NULL AFTER status");
        ensureColumn("compute_reservation", "incident_reason", "VARCHAR(512) NOT NULL DEFAULT '' AFTER status_before_incident");
        ensureColumn("compute_reservation", "resolution_type", "VARCHAR(24) NULL AFTER incident_reason");
        ensureColumn("compute_reservation", "resolution_card_hours", "DECIMAL(20,3) NULL AFTER resolution_type");
        ensureColumn("compute_reservation", "resolved_by", "BIGINT NULL AFTER resolution_card_hours");
        ensureColumn("compute_reservation", "resolved_at", "DATETIME NULL AFTER resolved_by");
        ensureColumn("compute_reservation", "trade_mode", "VARCHAR(32) NOT NULL DEFAULT 'LEGACY_RESERVATION' AFTER delivery_ciphertext");
        ensureColumn("compute_reservation", "buyer_public_key", "MEDIUMTEXT NULL AFTER trade_mode");
        ensureColumn("compute_reservation", "delivery_deadline_at", "DATETIME NULL AFTER buyer_public_key");
        ensureColumn("compute_reservation", "auto_confirm_at", "DATETIME NULL AFTER delivery_deadline_at");
        ensureColumn("compute_reservation", "buyer_confirmed_at", "DATETIME NULL AFTER auto_confirm_at");
        ensureColumn("compute_reservation", "dispute_reason", "VARCHAR(1000) NOT NULL DEFAULT '' AFTER buyer_confirmed_at");
        ensureColumn("compute_reservation", "dispute_evidence", "VARCHAR(2000) NOT NULL DEFAULT '' AFTER dispute_reason");
        ensureColumn("compute_reservation", "disputed_at", "DATETIME NULL AFTER dispute_evidence");
        ensureColumn("compute_gpu_node", "proof_file_id", "VARCHAR(160) NULL AFTER network_description");
        ensureColumn("compute_gpu_node", "proof_mime_type", "VARCHAR(64) NULL AFTER proof_file_id");
        ensureColumn("compute_api_package_purchase", "access_key_hash", "CHAR(64) NULL AFTER status");
        ensureColumn("compute_api_package_purchase", "access_key_ciphertext", "TEXT NULL AFTER access_key_hash");
        ensureColumn("compute_api_package_purchase", "access_key_last4", "VARCHAR(8) NOT NULL DEFAULT '' AFTER access_key_ciphertext");
        ensureColumn("compute_api_package_purchase", "key_status", "VARCHAR(24) NOT NULL DEFAULT 'CONFIG_REQUIRED' AFTER access_key_last4");
        ensureColumn("compute_api_package_purchase", "suspended_reason", "VARCHAR(512) NOT NULL DEFAULT '' AFTER key_status");
        ensureColumn("compute_api_package_purchase", "in_flight", "TINYINT NOT NULL DEFAULT 0 AFTER suspended_reason");
        ensureIndex("compute_api_package_purchase", "uk_compute_api_package_access_key",
                "CREATE UNIQUE INDEX uk_compute_api_package_access_key ON compute_api_package_purchase(access_key_hash)");
        ensureIndex("compute_reservation", "idx_compute_reservation_marketplace",
                "CREATE INDEX idx_compute_reservation_marketplace ON compute_reservation(trade_mode,status,delivery_deadline_at,auto_confirm_at)");
        ensureIndex("compute_card_hour_lot", "idx_compute_lot_asset",
                "CREATE INDEX idx_compute_lot_asset ON compute_card_hour_lot(owner_user_id,asset_type,gpu_model,custody_status,expires_at)");
        migrateLegacyCardHourLots();
        migrateNodeStatuses();
        repairLegacySelfReviews();
        // 早期版本把购买、发放、转入都计成了“收益”；收益只应来自供应方销售结算。
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE compute_account a SET lifetime_income=(
                      SELECT COALESCE(SUM(l.amount),0) FROM compute_ledger l
                      WHERE l.user_id=a.user_id AND l.direction='CREDIT'
                        AND l.entry_type IN ('SUPPLIER_INCOME','GPU_RENTAL_INCOME','API_SALES_INCOME')
                    )
                    """);
            statement.executeUpdate("UPDATE compute_setting SET setting_value='0' WHERE setting_key='platform_fee_rate'");
        } catch (Exception e) {
            throw new IllegalStateException("算力中心收益口径修正失败", e);
        }
        log.info("本机算力中心 compute_* 表初始化完成");
    }

    private void migrateLegacyCardHourLots() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE compute_card_hour_lot SET asset_type='STANDARD' WHERE asset_type IS NULL OR asset_type=''");
            statement.executeUpdate("UPDATE compute_card_hour_lot SET custody_status='ACTIVE' WHERE custody_status IS NULL OR custody_status=''");
            statement.executeUpdate("""
                    INSERT INTO compute_card_hour_lot(owner_user_id,asset_type,source_type,source_ref,
                        original_amount,remaining_amount,frozen_amount,custody_status,expires_at)
                    SELECT a.user_id,'STANDARD','LEGACY_MIGRATION','旧版资产迁移',
                        a.available_card_hours+a.frozen_card_hours,
                        a.available_card_hours+a.frozen_card_hours,a.frozen_card_hours,'ACTIVE',NULL
                    FROM compute_account a
                    WHERE a.available_card_hours+a.frozen_card_hours>0
                      AND NOT EXISTS(SELECT 1 FROM compute_card_hour_lot l WHERE l.owner_user_id=a.user_id)
                    """);
        } catch (Exception e) {
            throw new IllegalStateException("算力中心历史卡时批次迁移失败", e);
        }
    }

    private void migrateNodeStatuses() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE compute_gpu_node SET status=CASE WHEN is_test=1 THEN 'RUNNING' ELSE 'DEPLOYING' END WHERE status='APPROVED'");
            statement.executeUpdate("UPDATE compute_product p JOIN compute_gpu_node n ON n.id=p.node_id SET p.status='PAUSED' WHERE p.product_type='GPU' AND p.status='PUBLISHED' AND n.status<>'RUNNING'");
        } catch (Exception e) {
            throw new IllegalStateException("算力中心设备状态迁移失败", e);
        }
    }

    private void repairLegacySelfReviews() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            Long replacement = null;
            for (String email : properties.getAdminEmails()) {
                try (ResultSet rows = statement.executeQuery("SELECT id FROM sys_user WHERE LOWER(email)='" + email.replace("'", "''") + "' LIMIT 1")) {
                    if (rows.next()) {
                        replacement = rows.getLong(1);
                        break;
                    }
                }
            }
            if (replacement == null) return;
            statement.executeUpdate("UPDATE compute_identity_verification SET reviewed_by=" + replacement + " WHERE reviewed_by=user_id AND user_id<>" + replacement);
            statement.executeUpdate("UPDATE compute_supplier SET reviewed_by=" + replacement + " WHERE reviewed_by=user_id AND user_id<>" + replacement);
            statement.executeUpdate("UPDATE compute_gpu_node SET reviewed_by=" + replacement + " WHERE reviewed_by=supplier_user_id AND supplier_user_id<>" + replacement);
            statement.executeUpdate("UPDATE compute_product SET reviewed_by=" + replacement + " WHERE supplier_user_id IS NOT NULL AND reviewed_by=supplier_user_id AND supplier_user_id<>" + replacement);
        } catch (Exception e) {
            throw new IllegalStateException("历史内测自审记录修正失败", e);
        }
    }

    private void ensureColumn(String table, String column, String definition) {
        try (Connection connection = dataSource.getConnection()) {
            boolean exists;
            try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
                exists = columns.next();
            }
            if (!exists) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                }
                log.info("已为旧表 {} 补充字段 {}", table, column);
            }
        } catch (Exception e) {
            throw new IllegalStateException("算力中心旧表字段迁移失败：" + table + "." + column, e);
        }
    }

    private void ensureIndex(String table, String indexName, String ddl) {
        try (Connection connection = dataSource.getConnection()) {
            boolean exists = false;
            try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                    connection.getCatalog(), null, table, false, false)) {
                while (indexes.next()) {
                    if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                        exists = true;
                        break;
                    }
                }
            }
            if (!exists) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(ddl);
                }
                log.info("已为旧表 {} 补充索引 {}", table, indexName);
            }
        } catch (Exception e) {
            throw new IllegalStateException("算力中心旧表索引迁移失败：" + table + "." + indexName, e);
        }
    }
}
