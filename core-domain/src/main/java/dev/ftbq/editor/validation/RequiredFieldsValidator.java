package dev.ftbq.editor.validation;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.validation.Validator.ItemResolver;

import java.util.ArrayList;
import java.util.List;

public final class RequiredFieldsValidator implements Validator {
    @Override
    public List<ValidationIssue> validate(QuestFile qf, ItemResolver resolver) {
        List<ValidationIssue> issues = new ArrayList<>();

        if (qf.id() == null || qf.id().isBlank()) {
            issues.add(new ValidationIssue("ERROR", "questFile", "QuestFile id is required"));
        }
        if (qf.title() == null || qf.title().isBlank()) {
            issues.add(new ValidationIssue("ERROR", "questFile", "QuestFile title is required"));
        }

        for (Chapter chapter : qf.chapters()) {
            String chapterPath = "chapters/" + chapter.id();
            if (chapter.id() == null || chapter.id().isBlank()) {
                issues.add(new ValidationIssue("ERROR", chapterPath, "Chapter id is required"));
            }
            if (chapter.title() == null || chapter.title().isBlank()) {
                issues.add(new ValidationIssue("WARNING", chapterPath, "Chapter title is empty"));
            }

            for (Quest quest : chapter.quests()) {
                String questPath = chapterPath + "/quests/" + quest.id();
                if (quest.id() == null || quest.id().isBlank()) {
                    issues.add(new ValidationIssue("ERROR", questPath, "Quest id is required"));
                }
                if (quest.title() == null || quest.title().isBlank()) {
                    issues.add(new ValidationIssue("WARNING", questPath, "Quest title is empty"));
                }
            }
        }

        return issues;
    }
}
