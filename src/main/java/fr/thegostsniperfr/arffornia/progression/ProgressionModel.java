package fr.thegostsniperfr.arffornia.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The part of the published snapshot the game needs to tell whether a milestone is available (spec 017 rule 3):
 * its stage and the milestones it requires, by key.
 */
public record ProgressionModel(int version, String etag, Map<String, Rule> milestones) {
    public static final ProgressionModel NONE = new ProgressionModel(0, "", Map.of());

    /** @param title display name by language ({@code en}, {@code fr}) */
    public record Rule(int stage, List<String> requires, Map<String, String> title) {
        static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("stage").forGetter(Rule::stage),
                Codec.STRING.listOf().fieldOf("requires").forGetter(Rule::requires),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("title", Map.of()).forGetter(Rule::title)
        ).apply(instance, Rule::new));

        public Rule {
            requires = List.copyOf(requires);
            title = Map.copyOf(title);
        }
    }

    public static final Codec<ProgressionModel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("version").forGetter(ProgressionModel::version),
            Codec.STRING.fieldOf("etag").forGetter(ProgressionModel::etag),
            Codec.unboundedMap(Codec.STRING, Rule.CODEC).fieldOf("milestones").forGetter(ProgressionModel::milestones)
    ).apply(instance, ProgressionModel::new));

    public ProgressionModel {
        milestones = Map.copyOf(milestones);
    }

    public static ProgressionModel of(ArfforniaApiDtos.ModelDocument document, String etag) {
        Map<String, Rule> rules = new HashMap<>();
        if (document.milestones() != null) {
            for (ArfforniaApiDtos.ModelMilestone milestone : document.milestones()) {
                if (milestone.key() != null) {
                    rules.put(milestone.key(), new Rule(milestone.stage(), milestone.requires() == null ? List.of() : milestone.requires(),
                            milestone.title() == null ? Map.of() : milestone.title()));
                }
            }
        }
        return new ProgressionModel(document.version(), etag == null ? "" : etag, rules);
    }

    /** Rule 3: its stage is open and every milestone it requires is done. A milestone the model does not know is never available. */
    public boolean isAvailable(String milestoneKey, ProgressionSnapshot progression) {
        Rule rule = milestones.get(milestoneKey);
        return rule != null && progression.isStageOpen(rule.stage()) && progression.completedKeys().containsAll(rule.requires());
    }

    /** The milestones {@code milestoneKey} requires that {@code progression} has not done, in model order. */
    public List<String> missingRequirements(String milestoneKey, ProgressionSnapshot progression) {
        Rule rule = milestones.get(milestoneKey);
        return rule == null ? List.of() : rule.requires().stream().filter(key -> !progression.isCompleted(key)).toList();
    }

    /** The milestone's title in {@code language} (a Minecraft locale such as {@code fr_fr}), else English, else its key. */
    public String titleOf(String milestoneKey, String language) {
        Rule rule = milestones.get(milestoneKey);
        if (rule == null) {
            return milestoneKey;
        }
        String shortLanguage = language.length() >= 2 ? language.substring(0, 2) : language;
        return rule.title().getOrDefault(shortLanguage, rule.title().getOrDefault("en", milestoneKey));
    }
}
