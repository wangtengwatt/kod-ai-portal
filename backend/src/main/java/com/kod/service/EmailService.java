package com.kod.service;

import com.kod.common.BizException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件服务：发送 HTML 格式的验证码邮件。
 *
 * <p>验证码绝不写入日志。发送失败会返回服务不可用，避免把一个用户
 * 无法收到的验证码写进 Redis 并触发限流。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * 发件人地址，默认取 SMTP 用户名。
     */
    @Value("${spring.mail.username:}")
    private String from;

    /**
     * 发送邮箱验证码。
     *
     * @param toEmail 收件人邮箱
     * @param code    6 位验证码
     */
    public void sendCode(String toEmail, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String sender = (from != null && !from.isEmpty()) ? from : "noreply@kod.com";
            helper.setFrom(sender);
            helper.setTo(toEmail);
            helper.setSubject("kod 验证码");

            String html = buildHtml(code);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("验证码邮件已发送，to={}", toEmail);
        } catch (Exception e) {
            log.warn("发送验证码邮件失败，to={}, error={}", toEmail, e.getMessage());
            throw new BizException(503, "验证码邮件发送失败，请稍后重试", e);
        }
    }

    /**
     * 构建简洁的 HTML 验证码邮件。
     */
    private String buildHtml(String code) {
        return """
                <div style="max-width:480px;margin:0 auto;padding:32px 20px;
                            font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;">
                  <div style="font-size:28px;font-weight:700;color:#4f46e5;margin-bottom:24px;">
                    kod
                  </div>
                  <p style="font-size:16px;color:#374151;margin:0 0 12px;">你好，</p>
                  <p style="font-size:16px;color:#374151;margin:0 0 24px;">
                    你正在注册 kod 账号，请输入以下验证码完成验证：
                  </p>
                  <div style="display:inline-block;padding:14px 32px;
                              background:#eef2ff;border-radius:8px;
                              font-size:28px;font-weight:700;color:#4f46e5;
                              letter-spacing:6px;margin-bottom:24px;">
                    """ + code + """
                  </div>
                  <p style="font-size:14px;color:#9ca3af;margin:0;">
                    验证码 5 分钟内有效，请勿转发给他人。
                  </p>
                  <hr style="border:0;border-top:1px solid #e5e7eb;margin:24px 0;">
                  <p style="font-size:12px;color:#d1d5db;margin:0;">
                    如果你未注册 kod 账号，请忽略此邮件。
                  </p>
                </div>
                """;
    }
}
