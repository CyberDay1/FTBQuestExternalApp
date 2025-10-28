package dev.ftbq.editor.importer.snbt.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Options controlling how SNBT quest data is merged into the active pack.
 */
public final class ImportOptions {
    private final ImportConflictPolicy chapterPolicy;
    private final ImportConflictPolicy questPolicy;
    private final String targetGroupId;
    private final boolean copyAssets;
    private final Path assetDestination;
    private final Path assetSource;

    private ImportOptions(Builder builder) {
        this.chapterPolicy = builder.chapterPolicy;
        this.questPolicy = builder.questPolicy;
        this.targetGroupId = builder.targetGroupId;
        this.copyAssets = builder.copyAssets;
        this.assetDestination = builder.assetDestination;
        this.assetSource = builder.assetSource;
    }

    public ImportConflictPolicy chapterPolicy() {
        return chapterPolicy;
    }

    public ImportConflictPolicy questPolicy() {
        return questPolicy;
    }

    public String targetGroupId() {
        return targetGroupId;
    }

    public boolean copyAssets() {
        return copyAssets;
    }

    public Path assetDestination() {
        return assetDestination;
    }

    public Path assetSource() {
        return assetSource;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ImportConflictPolicy chapterPolicy = ImportConflictPolicy.NEW_IDS;
        private ImportConflictPolicy questPolicy = ImportConflictPolicy.NEW_IDS;
        private String targetGroupId;
        private boolean copyAssets = true;
        private Path assetDestination;
        private Path assetSource;

        public Builder chapterPolicy(ImportConflictPolicy policy) {
            this.chapterPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public Builder questPolicy(ImportConflictPolicy policy) {
            this.questPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public Builder targetGroupId(String targetGroupId) {
            this.targetGroupId = targetGroupId;
            return this;
        }

        public Builder copyAssets(boolean copyAssets) {
            this.copyAssets = copyAssets;
            return this;
        }

        public Builder assetDestination(Path assetDestination) {
            this.assetDestination = assetDestination;
            return this;
        }

        public Builder assetSource(Path assetSource) {
            this.assetSource = assetSource;
            return this;
        }

        public ImportOptions build() {
            if (copyAssets && assetDestination == null) {
                throw new IllegalStateException("Asset destination must be set when copyAssets is true");
            }
            if (copyAssets && assetSource == null) {
                throw new IllegalStateException("Asset source must be set when copyAssets is true");
            }
            return new ImportOptions(this);
        }
    }
}
