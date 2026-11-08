package com.example.aitask.logging;

import java.util.regex.Pattern;

/** 日志正文脱敏、单行化和截断工具，防止密钥泄露及单条日志无限膨胀。 */
public final class LogSanitizer {
    private static final Pattern SENSITIVE_JSON = Pattern.compile(
            "(?i)(\"(?:password|apiKey|api_key|authorization|accessToken|refreshToken)\"\\s*:\\s*\")"
                    + "(?:\\\\.|[^\"\\\\])*+(\")");
    private static final Pattern LINE_BREAK = Pattern.compile("[\\r\\n]+");

    private LogSanitizer() {
    }

    /** 返回适合写入单行日志的安全文本。 */
    public static String sanitize(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String masked = SENSITIVE_JSON.matcher(value).replaceAll("$1***$2");
        String singleLine = LINE_BREAK.matcher(masked).replaceAll("\\n");
        if (singleLine.length() <= maxChars) {
            return singleLine;
        }
        return singleLine.substring(0, maxChars) + "...[truncated,totalChars=" + singleLine.length() + "]";
    }
}
