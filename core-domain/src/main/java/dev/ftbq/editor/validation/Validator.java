package dev.ftbq.editor.validation;

import dev.ftbq.editor.domain.QuestFile;
import java.util.List;

public interface Validator {
    interface ItemResolver {
        boolean exists(String itemId);
    }

    List<ValidationIssue> validate(QuestFile qf, ItemResolver resolver);
}
