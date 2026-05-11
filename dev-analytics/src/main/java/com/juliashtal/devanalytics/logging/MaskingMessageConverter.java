package com.juliashtal.devanalytics.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback message converter that masks sensitive values before they are written to any appender.
 *
 * <p>Register in {@code logback-spring.xml} with:
 * <pre>{@code
 * <conversionRule conversionWord="maskedMsg"
 *                 converterClass="com.juliashtal.devanalytics.logging.MaskingMessageConverter"/>
 * }</pre>
 * Then use {@code %maskedMsg} instead of {@code %msg} in your patterns.</p>
 *
 * <p>Patterns masked:
 * <ul>
 *   <li>Bearer tokens: {@code Bearer <value>} → {@code Bearer [MASKED]}</li>
 *   <li>JSON credential fields: password, token, secret, apiKey, accessToken, refreshToken</li>
 *   <li>Standalone JWT strings (three base64url segments)</li>
 * </ul>
 * </p>
 */
public class MaskingMessageConverter extends ClassicConverter {

    private static final String MASK = "[MASKED]";

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            // Bearer <token>
            Pattern.compile("(Bearer\\s+)[A-Za-z0-9\\-._~+/]+=*", Pattern.CASE_INSENSITIVE),
            // JSON credential fields: "password":"<value>", "token":"<value>", etc.
            Pattern.compile(
                    "(\"(?:password|token|secret|apiKey|api_key|accessToken|access_token|refreshToken|refresh_token)\"\\s*:\\s*\")([^\"]*)(\")",
                    Pattern.CASE_INSENSITIVE
            ),
            // Standalone JWT (header.payload.signature — all base64url)
            Pattern.compile("\\beyJ[A-Za-z0-9\\-_]+\\.eyJ[A-Za-z0-9\\-_]+\\.[A-Za-z0-9\\-_.]+")
    );

    @Override
    public String convert(ILoggingEvent event) {
        return mask(event.getFormattedMessage());
    }

    private String mask(String message) {
        if (message == null) {
            return null;
        }
        String result = message;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            Matcher matcher = pattern.matcher(result);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String replacement;
                if (matcher.groupCount() == 1) {
                    // Group 1 = prefix (e.g. "Bearer ")
                    replacement = matcher.group(1) + MASK;
                } else if (matcher.groupCount() == 3) {
                    // Group 1 = key+quote, group 2 = value, group 3 = closing quote
                    replacement = matcher.group(1) + MASK + matcher.group(3);
                } else {
                    replacement = MASK;
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }
}
