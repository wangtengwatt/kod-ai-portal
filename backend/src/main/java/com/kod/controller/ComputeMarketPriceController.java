package com.kod.controller;

import com.kod.common.Result;
import com.kod.service.ComputeMarketPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 无需登录即可浏览的第三方 GPU 参考行情。 */
@RestController
@RequestMapping("/api/compute/market-prices")
@RequiredArgsConstructor
public class ComputeMarketPriceController {

    private final ComputeMarketPriceService marketPriceService;

    @GetMapping("/latest")
    public Result<Map<String, Object>> latest() {
        return Result.ok(marketPriceService.latestSnapshot());
    }

    @GetMapping("/history")
    public Result<Map<String, Object>> history(
            @RequestParam(defaultValue = "H100") String model,
            @RequestParam(defaultValue = "24h") String range) {
        return Result.ok(marketPriceService.history(model, range));
    }
}
