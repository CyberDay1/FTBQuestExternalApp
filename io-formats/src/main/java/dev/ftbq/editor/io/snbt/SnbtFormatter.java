package dev.ftbq.editor.io.snbt;

import dev.ftbq.editor.domain.QuestFile;
import java.util.Objects;

/**
 * Normalizes SNBT text into the canonical quest format used by the editor.
 */
public final class SnbtFormatter {

    private final SnbtQuestMapper mapper;

    public SnbtFormatter() {
        this(new SnbtQuestMapper());
    }

    public SnbtFormatter(SnbtQuestMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public FormatResult format(String snbtText) {
        Objects.requireNonNull(snbtText, "snbtText");
        String sanitized = stripInvalidControlCharacters(snbtText);
        QuestFile questFile;
        try {
            questFile = mapper.fromSnbt(sanitized);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid SNBT quest content", e);
        }
        String formatted = mapper.toSnbt(questFile);
        return new FormatResult(formatted, questFile);
    }

    private String stripInvalidControlCharacters(String text) {
        StringBuilder sanitized = null;
        int length = text.length();
        for (int offset = 0; offset < length; ) {
            int codePoint = text.codePointAt(offset);
            if (isDisallowedControlCharacter(codePoint)) {
                if (sanitized == null) {
                    sanitized = new StringBuilder(length);
                    sanitized.append(text, 0, offset);
                }
            } else if (sanitized != null) {
                sanitized.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return sanitized != null ? sanitized.toString() : text;
    }

    private boolean isDisallowedControlCharacter(int codePoint) {
        if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
            return false;
        }
        return Character.isISOControl(codePoint);
    }

    public record FormatResult(String formattedText, QuestFile questFile) {
        public FormatResult {
            Objects.requireNonNull(formattedText, "formattedText");
            Objects.requireNonNull(questFile, "questFile");
        }
    }
}
