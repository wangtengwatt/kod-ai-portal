package com.kod.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComputeMarketPriceParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void calculatesMedianPerGpuHourFromVastAvailableOffers() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {"offers":[
                  {"gpu_name":"NVIDIA H100 SXM","num_gpus":1,"dph_total":2.0},
                  {"gpu_name":"H100 PCIe","num_gpus":2,"dph_total":6.0},
                  {"gpu_name":"H100 NVL","num_gpus":1,"dph_total":4.0},
                  {"gpu_name":"RTX 4090","num_gpus":2,"dph_total":1.0}
                ]}
                """);

        Map<String, ComputeMarketPriceParser.ParsedQuote> quotes = ComputeMarketPriceParser.parseVast(root);

        assertThat(quotes.get("H100").priceUsdPerGpuHour()).isEqualByComparingTo(new BigDecimal("3.000000"));
        assertThat(quotes.get("H100").sampleSize()).isEqualTo(3);
        assertThat(quotes.get("RTX 4090").priceUsdPerGpuHour()).isEqualByComparingTo(new BigDecimal("0.500000"));
    }

    @Test
    void extractsOfficialAkamaiGpuStartingPricesFromPublicTypesApi() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"data":[
                  {"class":"gpu","label":"Dedicated 32GB + RTX6000 GPU x1","gpus":1,"price":{"hourly":1.50}},
                  {"class":"gpu","label":"Dedicated 64GB + RTX6000 GPU x2","gpus":2,"price":{"hourly":3.00}},
                  {"class":"gpu","label":"RTX4000 Ada x1 Small","gpus":1,"price":{"hourly":0.52}},
                  {"class":"gpu","label":"RTX4000 Ada x1 Large","gpus":1,"price":{"hourly":0.96}},
                  {"class":"gpu","label":"RTX PRO 6000 Blackwell x1","gpus":1,"price":{"hourly":2.50}},
                  {"class":"standard","label":"Shared CPU","gpus":0,"price":{"hourly":0.01}}
                ]}
                """);

        Map<String, ComputeMarketPriceParser.ParsedQuote> quotes =
                ComputeMarketPriceParser.parseAkamai(response);

        assertThat(quotes.get("RTX PRO 6000 Blackwell").priceUsdPerGpuHour())
                .isEqualByComparingTo("2.50");
        assertThat(quotes.get("RTX 4000 Ada").priceUsdPerGpuHour()).isEqualByComparingTo("0.52");
        assertThat(quotes.get("RTX 6000 Quadro").priceUsdPerGpuHour()).isEqualByComparingTo("1.50");
        assertThat(quotes.get("RTX 4000 Ada").sampleSize()).isEqualTo(2);
        assertThat(quotes).doesNotContainKey("H100");
    }
}
