package dev.ftbq.editor.validation;

import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.validation.Validator.ItemResolver;
import java.util.List;

public final class BrokenReferenceValidator implements Validator {
    @Override
    public List<ValidationIssue> validate(QuestFile qf, ItemResolver resolver) {
        return List.of();
    }
}
