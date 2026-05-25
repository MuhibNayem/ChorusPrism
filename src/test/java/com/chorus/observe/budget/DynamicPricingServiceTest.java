package com.chorus.observe.budget;

import com.chorus.observe.config.ChorusObserveProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicPricingServiceTest {

    private ChorusObserveProperties properties;
    private PricingTable pricingTable;
    private ObjectMapper mapper;
    private DynamicPricingService service;

    @BeforeEach
    void setUp() {
        properties = new ChorusObserveProperties();
        pricingTable = new PricingTable();
        mapper = new ObjectMapper();
        service = new DynamicPricingService(properties, pricingTable, mapper);
    }

    @Test
    void shouldParseLiteLlmPricingJsonCorrectly() {
        String testJson = """
            {
                "gpt-4o-custom": {
                    "input_cost_per_token": 0.0000025,
                    "output_cost_per_token": 0.000015,
                    "litellm_provider": "openai",
                    "mode": "chat"
                },
                "claude-3-custom-20260525": {
                    "input_cost_per_token": 0.000003,
                    "output_cost_per_token": 0.000015,
                    "litellm_provider": "anthropic"
                }
            }
            """;

        // Invoke background parser
        properties.getPricing().setDynamicEnabled(true);
        
        // Directly test JSON parsing
        java.lang.reflect.Method method;
        try {
            method = DynamicPricingService.class.getDeclaredMethod("parseAndRegisterPrices", String.class);
            method.setAccessible(true);
            method.invoke(service, testJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Verify prices were registered in the table
        PricingTable.ModelPricing gptPricing = pricingTable.lookup("gpt-4o-custom");
        assertThat(gptPricing).isNotNull();
        // 0.0000025 * 1000 = 0.0025
        assertThat(gptPricing.inputPricePer1k().doubleValue()).isCloseTo(0.0025, org.assertj.core.data.Offset.offset(0.00001));
        assertThat(gptPricing.outputPricePer1k().doubleValue()).isCloseTo(0.015, org.assertj.core.data.Offset.offset(0.00001));

        // Verify prefix fallback works
        PricingTable.ModelPricing claudePrefixPricing = pricingTable.lookup("claude-3-custom");
        assertThat(claudePrefixPricing).isNotNull();
        assertThat(claudePrefixPricing.inputPricePer1k().doubleValue()).isCloseTo(0.003, org.assertj.core.data.Offset.offset(0.00001));
    }

    @Test
    void shouldGracefullyHandleInvalidJson() {
        String invalidJson = "this is not valid json";
        
        java.lang.reflect.Method method;
        try {
            method = DynamicPricingService.class.getDeclaredMethod("parseAndRegisterPrices", String.class);
            method.setAccessible(true);
            method.invoke(service, invalidJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Ensure lookups still work (retaining defaults) without throwing exceptions
        PricingTable.ModelPricing defaultGpt = pricingTable.lookup("gpt-4o");
        assertThat(defaultGpt).isNotNull();
    }
}
