package com.kod.service;

import com.kod.config.ComputeMarketPriceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/** 第三方 GPU 行情缓存、分钟采样与公开查询。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComputeMarketPriceService {

    private static final String VAST = "VAST_AI";
    private static final String AKAMAI = "AKAMAI";
    private static final Map<String, String> SOURCE_LABELS = Map.of(VAST, "Vast.ai", AKAMAI, "Akamai Cloud");

    private final JdbcTemplate jdbc;
    private final ComputeMarketPriceGateway gateway;
    private final ComputeMarketPriceProperties properties;
    private final ComputeCenterService computeCenterService;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private final Map<QuoteKey, LiveQuote> latest = new ConcurrentHashMap<>();
    private final Map<String, SourceHealth> health = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastHistorySample = new AtomicReference<>(Instant.EPOCH);

    @Scheduled(fixedDelayString = "${kod.compute.market-price-refresh-millis:5000}", initialDelay = 1000)
    public void refresh() {
        if (!properties.isEnabled() || !refreshLock.tryLock()) return;
        try {
            refreshVast();
            refreshAkamai();
            persistMinuteSample();
        } finally {
            refreshLock.unlock();
        }
    }

    @Scheduled(cron = "0 20 3 * * *")
    public void cleanupHistory() {
        try {
            jdbc.update("DELETE FROM compute_market_price_history WHERE sampled_at < ?",
                    LocalDateTime.now().minusDays(properties.getHistoryRetentionDays()));
        } catch (DataAccessException error) {
            log.debug("GPU 行情历史表尚未创建，跳过清理：{}", error.getMostSpecificCause().getMessage());
        }
    }

    public Map<String, Object> latestSnapshot() {
        Map<String, Object> config = computeCenterService.publicConfig();
        BigDecimal usdCnyRate = decimal(config.get("usdCnyRate"));
        BigDecimal cardHourCnyRate = decimal(config.get("cardHourCnyRate"));
        List<Map<String, Object>> quotes = new ArrayList<>();
        for (String model : ComputeMarketPriceParser.TRACKED_MODELS) {
            quotes.add(view(VAST, model, usdCnyRate, cardHourCnyRate));
            quotes.add(view(AKAMAI, model, usdCnyRate, cardHourCnyRate));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trackedModels", ComputeMarketPriceParser.TRACKED_MODELS);
        result.put("quotes", quotes);
        result.put("usdCnyRate", usdCnyRate);
        result.put("cardHourCnyRate", cardHourCnyRate);
        result.put("refreshIntervalSeconds", properties.getRefreshMillis() / 1000);
        result.put("historySampleSeconds", properties.getHistorySampleMillis() / 1000);
        result.put("historyRetentionDays", properties.getHistoryRetentionDays());
        result.put("generatedAt", LocalDateTime.now());
        return result;
    }

    public Map<String, Object> history(String rawModel, String rawRange) {
        String model = validateModel(rawModel);
        String range = normalizeRange(rawRange);
        LocalDateTime since = LocalDateTime.now().minusMinutes(rangeMinutes(range));
        Map<String, Object> config = computeCenterService.publicConfig();
        BigDecimal usdCnyRate = decimal(config.get("usdCnyRate"));
        BigDecimal cardHourCnyRate = decimal(config.get("cardHourCnyRate"));
        List<Map<String, Object>> points;
        try {
            points = jdbc.queryForList("""
                    SELECT source, gpu_model AS gpuModel, quote_type AS quoteType,
                           price_usd_per_gpu_hour AS priceUsdPerGpuHour,
                           sample_size AS sampleSize, sampled_at AS sampledAt
                    FROM compute_market_price_history
                    WHERE gpu_model=? AND sampled_at>=?
                    ORDER BY sampled_at, source
                    """, model, since);
        } catch (DataAccessException error) {
            points = List.of();
        }
        points.forEach(point -> addConversions(point, decimal(point.get("priceUsdPerGpuHour")), usdCnyRate,
                cardHourCnyRate));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gpuModel", model);
        result.put("range", range);
        result.put("points", points);
        result.put("usdCnyRate", usdCnyRate);
        result.put("cardHourCnyRate", cardHourCnyRate);
        return result;
    }

    private void refreshVast() {
        Instant attempt = Instant.now();
        if (properties.getVastApiKey().isBlank()) {
            health.put(VAST, new SourceHealth(attempt, healthSuccess(VAST), "未配置 VAST_API_KEY"));
            return;
        }
        try {
            Map<String, ComputeMarketPriceParser.ParsedQuote> parsed =
                    ComputeMarketPriceParser.parseVast(gateway.fetchVastOffers());
            replaceSourceQuotes(VAST, "MEDIAN_AVAILABLE", parsed, attempt);
            health.put(VAST, new SourceHealth(attempt, attempt, ""));
        } catch (Exception error) {
            health.put(VAST, new SourceHealth(attempt, healthSuccess(VAST), shortError(error)));
            log.warn("Vast.ai 行情刷新失败：{}", error.getMessage());
        }
    }

    private void refreshAkamai() {
        Instant attempt = Instant.now();
        try {
            Map<String, ComputeMarketPriceParser.ParsedQuote> parsed =
                    ComputeMarketPriceParser.parseAkamai(gateway.fetchAkamaiTypes());
            if (parsed.isEmpty()) throw new IllegalStateException("未从 Akamai 官方 Types API 识别到 GPU 小时标价");
            replaceSourceQuotes(AKAMAI, "OFFICIAL_LIST", parsed, attempt);
            health.put(AKAMAI, new SourceHealth(attempt, attempt, ""));
        } catch (Exception error) {
            health.put(AKAMAI, new SourceHealth(attempt, healthSuccess(AKAMAI), shortError(error)));
            log.warn("Akamai GPU 官方标价刷新失败：{}", error.getMessage());
        }
    }

    private void replaceSourceQuotes(String source, String quoteType,
                                     Map<String, ComputeMarketPriceParser.ParsedQuote> parsed, Instant sampledAt) {
        latest.keySet().removeIf(key -> key.source().equals(source) && !parsed.containsKey(key.gpuModel()));
        parsed.forEach((model, quote) -> latest.put(new QuoteKey(source, model),
                new LiveQuote(source, model, quoteType, quote.priceUsdPerGpuHour(), quote.sampleSize(), sampledAt)));
    }

    private Map<String, Object> view(String source, String model, BigDecimal usdCnyRate,
                                     BigDecimal cardHourCnyRate) {
        LiveQuote quote = latest.get(new QuoteKey(source, model));
        SourceHealth sourceHealth = health.get(source);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", source);
        result.put("sourceLabel", SOURCE_LABELS.get(source));
        result.put("gpuModel", model);
        result.put("sourceUrl", source.equals(VAST) ? "https://vast.ai/" : properties.getAkamaiProductUrl());
        result.put("lastAttemptAt", local(sourceHealth == null ? null : sourceHealth.lastAttempt()));
        result.put("lastSuccessAt", local(sourceHealth == null ? null : sourceHealth.lastSuccess()));
        result.put("errorMessage", sourceHealth == null ? "行情采集中" : sourceHealth.errorMessage());
        if (quote == null) {
            result.put("status", source.equals(VAST) && properties.getVastApiKey().isBlank()
                    ? "UNCONFIGURED" : sourceHealth != null && sourceHealth.lastSuccess() == null ? "UNAVAILABLE" : "NO_QUOTE");
            return result;
        }
        long staleAfter = Math.max(20_000, properties.getRefreshMillis() * 4);
        boolean stale = sourceHealth == null || sourceHealth.lastSuccess() == null
                || sourceHealth.lastSuccess().isBefore(Instant.now().minusMillis(staleAfter));
        result.put("status", stale ? "STALE" : "OK");
        result.put("quoteType", quote.quoteType());
        result.put("priceUsdPerGpuHour", quote.priceUsdPerGpuHour());
        result.put("sampleSize", quote.sampleSize());
        result.put("sampledAt", local(quote.sampledAt()));
        addConversions(result, quote.priceUsdPerGpuHour(), usdCnyRate, cardHourCnyRate);
        return result;
    }

    private void persistMinuteSample() {
        Instant now = Instant.now();
        Instant previous = lastHistorySample.get();
        if (previous.plusMillis(properties.getHistorySampleMillis()).isAfter(now)
                || !lastHistorySample.compareAndSet(previous, now)) return;
        LocalDateTime sampledMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        try {
            for (LiveQuote quote : latest.values()) {
                jdbc.update("""
                        INSERT INTO compute_market_price_history(
                            source,gpu_model,quote_type,price_usd_per_gpu_hour,sample_size,sampled_at)
                        VALUES (?,?,?,?,?,?)
                        ON DUPLICATE KEY UPDATE quote_type=VALUES(quote_type),
                            price_usd_per_gpu_hour=VALUES(price_usd_per_gpu_hour),sample_size=VALUES(sample_size)
                        """, quote.source(), quote.gpuModel(), quote.quoteType(), quote.priceUsdPerGpuHour(),
                        quote.sampleSize(), sampledMinute);
            }
        } catch (DataAccessException error) {
            log.debug("GPU 行情历史表尚未创建，实时行情仍可使用：{}", error.getMostSpecificCause().getMessage());
        }
    }

    private static void addConversions(Map<String, Object> target, BigDecimal usd, BigDecimal usdCnyRate,
                                       BigDecimal cardHourCnyRate) {
        BigDecimal cny = usd.multiply(usdCnyRate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal cardHours = cardHourCnyRate.signum() == 0 ? BigDecimal.ZERO
                : cny.divide(cardHourCnyRate, 4, RoundingMode.HALF_UP);
        target.put("priceCnyPerGpuHour", cny);
        target.put("cardHoursPerGpuHour", cardHours);
    }

    private Instant healthSuccess(String source) {
        SourceHealth current = health.get(source);
        return current == null ? null : current.lastSuccess();
    }

    private static LocalDateTime local(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        return new BigDecimal(String.valueOf(value));
    }

    private static String validateModel(String raw) {
        String requested = raw == null ? "H100" : raw.trim();
        return ComputeMarketPriceParser.TRACKED_MODELS.stream()
                .filter(model -> model.equalsIgnoreCase(requested))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的 GPU 型号"));
    }

    private static String normalizeRange(String raw) {
        String range = raw == null ? "24h" : raw.trim().toLowerCase(Locale.ROOT);
        if (!List.of("1h", "6h", "24h", "7d").contains(range)) {
            throw new IllegalArgumentException("行情范围仅支持 1h、6h、24h、7d");
        }
        return range;
    }

    private static long rangeMinutes(String range) {
        return switch (range) {
            case "1h" -> 60;
            case "6h" -> 360;
            case "7d" -> 10_080;
            default -> 1_440;
        };
    }

    private static String shortError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "第三方行情请求失败";
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    private record QuoteKey(String source, String gpuModel) {
    }

    private record LiveQuote(String source, String gpuModel, String quoteType,
                             BigDecimal priceUsdPerGpuHour, int sampleSize, Instant sampledAt) {
    }

    private record SourceHealth(Instant lastAttempt, Instant lastSuccess, String errorMessage) {
    }
}
