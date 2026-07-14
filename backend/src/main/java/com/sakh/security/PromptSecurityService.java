package com.sakh.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class PromptSecurityService {

    private static final Logger logger = LoggerFactory.getLogger(PromptSecurityService.class);

    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?previous\\s+(instructions|commands|directives)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal\\s+(your\\s+)?(system\\s+)?prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("output\\s+(your\\s+)?(system\\s+)?prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget\\s+(all\\s+)?previous\\s+(instructions|context)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ignore\\s+(the\\s+)?context", Pattern.CASE_INSENSITIVE),
            Pattern.compile("delete\\s+(all\\s+)?documents", Pattern.CASE_INSENSITIVE),
            Pattern.compile("remove\\s+(all\\s+)?documents", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ignore\\s+(the\\s+)?(above|following|provided)\\s+(instructions|context|content)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+(are\\s+)?(now|no\\s+longer)\\s+(free|released|liberated)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("DAN|do\\s+anything\\s+now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("jailbreak|jail\\s*break", Pattern.CASE_INSENSITIVE)
    );

    public void validate(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        String normalized = message.trim();

        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                logger.warn("Blocked prompt injection attempt - pattern: '{}', message: '{}'",
                        pattern.pattern(), sanitize(normalized));
                throw new IllegalArgumentException("Message contains prohibited content.");
            }
        }
    }

    private static String sanitize(String text) {
        if (text.length() > 200) {
            return text.substring(0, 200) + "...";
        }
        return text;
    }
}
