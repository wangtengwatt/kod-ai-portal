package com.kod.controller;

import com.kod.service.ComputePackageProxyService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 交付给外部工具使用的 OpenAI 兼容 Token 套餐代理入口。 */
@RestController
@RequestMapping("/api/compute/proxy/v1")
@RequiredArgsConstructor
public class ComputePackageProxyController {

    private final ComputePackageProxyService proxyService;

    @PostMapping("/chat/completions")
    public void chatCompletions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody String body,
            HttpServletResponse response) {
        proxyService.proxy("/chat/completions", authorization, body, response);
    }

    @PostMapping("/responses")
    public void responses(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody String body,
            HttpServletResponse response) {
        proxyService.proxy("/responses", authorization, body, response);
    }
}
