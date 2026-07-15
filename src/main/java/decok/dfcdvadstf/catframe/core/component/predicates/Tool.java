package decok.dfcdvadstf.catframe.core.component.predicates;

import decok.dfcdvadstf.catframe.core.component.ComponentSerializer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 工具属性 - 挖掘速度、挖掘等级等。
 * <p>
 * 对应 26.1.2 {@code net.minecraft.world.item.component.Tool}。
 * 在 1.7.10 中主要用于存储自定义工具的行为规则。
 */
public final class Tool {

    private static final Tool EMPTY = new Tool(Collections.emptyList(), 0.0F, 0);

    private final List<Rule> rules;
    private final float defaultMiningSpeed;
    private final int damagePerBlock;

    private Tool(List<Rule> rules, float defaultMiningSpeed, int damagePerBlock) {
        this.rules = rules;
        this.defaultMiningSpeed = defaultMiningSpeed;
        this.damagePerBlock = damagePerBlock;
    }

    // ========== 工厂方法 ==========

    public static Tool empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ========== 查询 ==========

    public List<Rule> getRules() { return rules; }
    public float getDefaultMiningSpeed() { return defaultMiningSpeed; }
    public int getDamagePerBlock() { return damagePerBlock; }
    public boolean isEmpty() { return rules.isEmpty() && defaultMiningSpeed == 0.0F && damagePerBlock == 0; }

    // ========== 对象约定 ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tool)) return false;
        Tool tool = (Tool) o;
        return Float.compare(tool.defaultMiningSpeed, defaultMiningSpeed) == 0
                && damagePerBlock == tool.damagePerBlock
                && rules.equals(tool.rules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rules, defaultMiningSpeed, damagePerBlock);
    }

    @Override
    public String toString() {
        return "Tool{rules=" + rules + ", speed=" + defaultMiningSpeed + "}";
    }

    // ========== Rule 类型 ==========

    public static final class Rule {
        private final Set<String> blocks;
        private final float speed;
        private final boolean correctForDrops;

        public Rule(Set<String> blocks, float speed, boolean correctForDrops) {
            this.blocks = blocks;
            this.speed = speed;
            this.correctForDrops = correctForDrops;
        }

        public Set<String> getBlocks() { return blocks; }
        public float getSpeed() { return speed; }
        public boolean isCorrectForDrops() { return correctForDrops; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Rule)) return false;
            Rule rule = (Rule) o;
            return Float.compare(rule.speed, speed) == 0
                    && correctForDrops == rule.correctForDrops
                    && blocks.equals(rule.blocks);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blocks, speed, correctForDrops);
        }
    }

    // ========== Builder ==========

    public static final class Builder {
        private final List<Rule> rules = new ArrayList<>();
        private float defaultMiningSpeed = 1.0F;
        private int damagePerBlock = 1;

        private Builder() {}

        public Builder addRule(Rule rule) {
            rules.add(rule);
            return this;
        }

        public Builder defaultMiningSpeed(float speed) {
            this.defaultMiningSpeed = speed;
            return this;
        }

        public Builder damagePerBlock(int damage) {
            this.damagePerBlock = damage;
            return this;
        }

        public Tool build() {
            return rules.isEmpty() && defaultMiningSpeed == 1.0F && damagePerBlock == 1
                    ? EMPTY
                    : new Tool(Collections.unmodifiableList(new ArrayList<>(rules)), defaultMiningSpeed, damagePerBlock);
        }
    }

    // ========== 序列化器 ==========

    public static final ComponentSerializer<Tool> SERIALIZER = new ComponentSerializer<Tool>() {
        @Override
        public void write(NBTTagCompound nbt, Tool value) {
            NBTTagCompound tag = new NBTTagCompound();
            NBTTagList rulesList = new NBTTagList();
            for (Rule rule : value.rules) {
                NBTTagCompound ruleTag = new NBTTagCompound();
                // Simplified storage - extensible in the future
                rulesList.appendTag(ruleTag);
            }
            if (rulesList.tagCount() > 0) tag.setTag("rules", rulesList);
            if (value.defaultMiningSpeed != 1.0F) tag.setFloat("default_mining_speed", value.defaultMiningSpeed);
            if (value.damagePerBlock != 1) tag.setInteger("damage_per_block", value.damagePerBlock);
            nbt.setTag("Tool", tag);
        }

        @Nullable
        @Override
        public Tool read(NBTTagCompound nbt) {
            if (!nbt.hasKey("Tool", 10)) return null;
            NBTTagCompound tag = nbt.getCompoundTag("Tool");
            float speed = tag.hasKey("default_mining_speed") ? tag.getFloat("default_mining_speed") : 1.0F;
            int damage = tag.hasKey("damage_per_block") ? tag.getInteger("damage_per_block") : 1;
            return builder()
                    .defaultMiningSpeed(speed)
                    .damagePerBlock(damage)
                    .build();
        }
    };
}
