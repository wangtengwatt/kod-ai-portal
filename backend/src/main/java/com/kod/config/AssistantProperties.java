package com.kod.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 蒜宝助手配置，绑定环境变量 {@code ASSISTANT_*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "assistant")
public class AssistantProperties {

    /** 是否启用助手功能。 */
    private boolean enabled = false;

    /** 上游 LLM API 地址。 */
    private String baseUrl = "https://ai.kaiweb.net/v1";

    /** 上游 LLM API Key。 */
    private String apiKey = "";

    /** 上游模型名称。 */
    private String model = "deepseek-v4-pro";

    /** 允许代理的 host 白名单（逗号分隔）。 */
    private List<String> allowedHosts = List.of();

    /** 连接超时（如 10s）。 */
    private String connectTimeout = "10s";

    /** 流式读取超时（如 45s）。 */
    private String streamTimeout = "45s";

    /** 单条消息最大字符数。 */
    private int maxMessageChars = 1000;

    /** 最大历史消息数。 */
    private int maxMessages = 8;

    /** 上下文总字符数上限。 */
    private int maxContextChars = 4000;

    /** 每 IP 每分钟最大请求数。 */
    private int maxPerMinutePerIp = 10;

    /** 每 IP 每天最大请求数。 */
    private int maxDailyPerIp = 80;

    /** 每设备每天最大请求数。 */
    private int maxDailyPerDevice = 50;

    /** 每设备最大并发数。 */
    private int maxConcurrentPerDevice = 1;
}
