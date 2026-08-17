package com.kod.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 将第三方响应统一为美元/GPU/小时。 */
final class ComputeMarketPriceParser {

    static final List<String> TRACKED_MODELS = List.of(
            "H100", "H200", "A100", "RTX 4090", "L40S", "RTX 5090", "RTX 3090",
            "RTX PRO 6000 Blackwell", "RTX 4000 Ada", "RTX 6000 Quadro");

    private ComputeMarketPriceParser() {
    }

    static Map<String, ParsedQuote> parseVast(JsonNode root) {
        JsonNode offers = root.path("offers");
        if (!offers.isArray() && root.isArray()) offers = root;
        if (!offers.isArray()) return Map.of();

        Map<String, List<BigDecimal>> prices = new LinkedHashMap<>();
        for (JsonNode offer : offers) {
            String model = normalizeVastModel(text(offer, "gpu_name", "gpuName"));
            if (model == null) continue;
            BigDecimal total = decimal(offer, "dph_total", "dphTotal");
            BigDecimal gpuCount = decimal(offer, "num_gpus", "numGpus");
            if (total == null || total.signum() <= 0 || gpuCount == null || gpuCount.signum() <= 0) continue;
            prices.computeIfAbsent(model, ignored -> new ArrayList<>())
                    .add(total.divide(gpuCount, 8, RoundingMode.HALF_UP));
        }

        Map<String, ParsedQuote> result = new LinkedHashMap<>();
        prices.forEach((model, values) -> result.put(model, new ParsedQuote(median(values), values.size())));
        return result;
    }

    static Map<String, ParsedQuote> parseAkamai(JsonNode root) {
        JsonNode types = root.path("data");
        if (!types.isArray()) return Map.of();
        Map<String, List<BigDecimal>> prices = new LinkedHashMap<>();
        for (JsonNode type : types) {
            if (!"gpu".equalsIgnoreCase(type.path("class").asText())) continue;
            String model = normalizeAkamaiModel(type.path("label").asText());
            BigDecimal hourly = type.path("price").path("hourly").isNumber()
                    ? type.path("price").path("hourly").decimalValue() : null;
            BigDecimal gpuCount = type.path("gpus").isNumber() ? type.path("gpus").decimalValue() : null;
            if (model == null || hourly == null || hourly.signum() <= 0
                    || gpuCount == null || gpuCount.signum() <= 0) continue;
            prices.computeIfAbsent(model, ignored -> new ArrayList<>())
                    .add(hourly.divide(gpuCount, 8, RoundingMode.HALF_UP));
        }
        Map<String, ParsedQuote> result = new LinkedHashMap<>();
        prices.forEach((model, values) -> result.put(model,
                new ParsedQuote(values.stream().min(Comparator.naturalOrder()).orElseThrow()
                        .setScale(6, RoundingMode.HALF_UP), values.size())));
        return result;
    }

    private static String normalizeVastModel(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.toUpperCase(Locale.ROOT).replace("NVIDIA", "").trim();
        if (value.contains("H200")) return "H200";
        if (value.contains("H100")) return "H100";
        if (value.contains("A100")) return "A100";
        if (value.contains("4090")) return "RTX 4090";
        if (value.contains("L40S")) return "L40S";
        if (value.contains("5090")) return "RTX 5090";
        if (value.contains("3090")) return "RTX 3090";
        if (value.contains("PRO 6000") && value.contains("BLACKWELL")) return "RTX PRO 6000 Blackwell";
        if (value.contains("4000") && value.contains("ADA")) return "RTX 4000 Ada";
        if (value.contains("RTX 6000") || value.contains("QUADRO RTX 6000")) return "RTX 6000 Quadro";
        return null;
    }

    private static BigDecimal median(List<BigDecimal> input) {
        List<BigDecimal> values = input.stream().sorted(Comparator.naturalOrder()).toList();
        int middle = values.size() / 2;
        if (values.size() % 2 == 1) return values.get(middle).setScale(6, RoundingMode.HALF_UP);
        return values.get(middle - 1).add(values.get(middle))
                .divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
    }

    private static String normalizeAkamaiModel(String raw) {
        String value = raw == null ? "" : raw.toUpperCase(Locale.ROOT);
        if (value.contains("PRO 6000") && value.contains("BLACKWELL")) return "RTX PRO 6000 Blackwell";
        if (value.contains("RTX4000") && value.contains("ADA")
                || value.contains("RTX 4000") && value.contains("ADA")) return "RTX 4000 Ada";
        if (value.contains("RTX6000") || value.contains("RTX 6000")) return "RTX 6000 Quadro";
        return null;
    }

    private static String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual()) return value.asText();
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isNumber()) return value.decimalValue();
            if (value != null && value.isTextual()) {
                try {
                    return new BigDecimal(value.asText());
                } catch (NumberFormatException ignored) {
                    // Continue with compatible aliases.
                }
            }
        }
        return null;
    }

    record ParsedQuote(BigDecimal priceUsdPerGpuHour, int sampleSize) {
    }
}
