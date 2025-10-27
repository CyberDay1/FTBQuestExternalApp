package dev.ftbq.editor.services.templates;

import dev.ftbq.editor.domain.CommandReward;
import dev.ftbq.editor.domain.LootTableReward;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.RewardCommand;
import dev.ftbq.editor.domain.XpLevelReward;
import dev.ftbq.editor.domain.XpReward;

public final class RewardTemplates {

    private RewardTemplates() {
    }

    public static Reward xpLevels(int levels) {
        return new XpLevelReward(levels);
    }

    public static Reward xpAmount(int amount) {
        return new XpReward(amount);
    }

    public static Reward lootTable(String nsPath) {
        return new LootTableReward(nsPath);
    }

    public static Reward command(String cmd, boolean asServer) {
        return new CommandReward(new RewardCommand(cmd, asServer));
    }
}
