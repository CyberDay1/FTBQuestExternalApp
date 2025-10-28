package dev.ftbq.editor.services.cli;

import dev.ftbq.editor.importer.snbt.validation.SnbtValidationReport;
import dev.ftbq.editor.importer.snbt.validation.SnbtValidationService;
import dev.ftbq.editor.io.snbt.SnbtIo;
import dev.ftbq.editor.validation.ValidationIssue;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SnbtValidateCli {
    private static final String DEFAULT_FILE = "questbook/data.snbt";

    private SnbtValidateCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
            return;
        }
        SnbtValidationService service = new SnbtValidationService();
        boolean hadErrors = false;
        for (String argument : args) {
            Path path = resolveTarget(argument);
            if (path == null) {
                hadErrors = true;
                continue;
            }
            String snbt = SnbtIo.read(path.toFile());
            SnbtValidationReport report = service.validate(snbt);
            System.out.println("== " + path + " ==");
            if (report.issues().isEmpty()) {
                System.out.println("Validation succeeded with no errors or warnings.");
            } else {
                for (ValidationIssue issue : report.issues()) {
                    System.out.println(issue.severity() + " " + issue.path() + " " + issue.message());
                }
                if (report.errors().isEmpty()) {
                    System.out.println("Completed with warnings only.");
                } else {
                    System.out.println("Completed with " + report.errors().size() + " error(s).");
                    hadErrors = true;
                }
            }
            System.out.println();
        }
        if (hadErrors) {
            System.exit(2);
        }
    }

    private static Path resolveTarget(String argument) {
        Path input = Path.of(argument).toAbsolutePath().normalize();
        if (Files.isDirectory(input)) {
            input = input.resolve(DEFAULT_FILE);
        }
        if (!Files.exists(input)) {
            System.err.println("Could not locate SNBT file at " + input);
            return null;
        }
        return input;
    }

    private static void printUsage() {
        System.out.println("Usage: snbt-validate <path-to-data.snbt or quest directory> [additional files...]");
        System.out.println("If a directory is provided, '" + DEFAULT_FILE + "' is assumed.");
    }
}
