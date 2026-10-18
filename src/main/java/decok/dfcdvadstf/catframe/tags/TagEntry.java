package decok.dfcdvadstf.catframe.tags;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One entry of a tag file, mirroring the modern {@code TagEntry}.
 * <p>
 * An entry is either a direct element ({@code "minecraft:wool"}) or a reference
 * to another tag ({@code "#minecraft:logs"}). Entries are required by default;
 * optional entries ({@code "minecraft:stone?"} or
 * {@code {"id": "minecraft:stone", "required": false}}) are silently ignored
 * when missing.
 * </p>
 */
public final class TagEntry {

    private final ResourceLocation id;
    private final boolean tag;
    private final boolean required;

    private TagEntry(ResourceLocation id, boolean tag, boolean required) {
        this.id = id;
        this.tag = tag;
        this.required = required;
    }

    /** Direct, required element. */
    public static TagEntry element(ResourceLocation id) {
        return new TagEntry(id, false, true);
    }

    /** Direct, optional element. */
    public static TagEntry optionalElement(ResourceLocation id) {
        return new TagEntry(id, false, false);
    }

    /** Required reference to another tag. */
    public static TagEntry tag(ResourceLocation id) {
        return new TagEntry(id, true, true);
    }

    /** Optional reference to another tag. */
    public static TagEntry optionalTag(ResourceLocation id) {
        return new TagEntry(id, true, false);
    }

    /**
     * Parses a string entry such as {@code "minecraft:wool"},
     * {@code "#minecraft:logs"} or {@code "minecraft:stone?"}.
     *
     * @throws IllegalArgumentException when the id is malformed
     */
    public static TagEntry parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("tag entry is null");
        }
        String text = value.trim();
        boolean optional = text.endsWith("?");
        if (optional) {
            text = text.substring(0, text.length() - 1);
        }
        boolean isTag = text.startsWith("#");
        if (isTag) {
            text = text.substring(1);
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty tag entry id");
        }
        ResourceLocation id;
        try {
            id = new ResourceLocation(text);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid tag entry id: " + value, e);
        }
        return isTag ? (optional ? optionalTag(id) : tag(id))
                     : (optional ? optionalElement(id) : element(id));
    }

    /**
     * Parses a JSON value: either a string or an object with {@code id} and
     * optional {@code required} fields, as accepted by modern tag files.
     *
     * @throws IllegalArgumentException when the value is malformed
     */
    public static TagEntry fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("tag entry is null");
        }
        if (element.isJsonPrimitive()) {
            return parse(element.getAsString());
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("tag entry must be a string or an object");
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement idElement = object.get("id");
        if (idElement == null || !idElement.isJsonPrimitive()) {
            throw new IllegalArgumentException("tag entry object is missing a string 'id'");
        }
        boolean required = !object.has("required") || object.get("required").getAsBoolean();
        TagEntry parsed = parse(idElement.getAsString());
        if (required) {
            return parsed;
        }
        if (parsed.tag) {
            return optionalTag(parsed.id);
        }
        return optionalElement(parsed.id);
    }

    /**
     * Resolves this entry against a lookup and feeds the result to {@code output}.
     *
     * @return {@code false} when a required entry is missing
     */
    public <T> boolean build(Lookup<T> lookup, Consumer<T> output) {
        if (this.tag) {
            Collection<T> result = lookup.tag(this.id);
            if (result == null) {
                return !this.required;
            }
            result.forEach(output);
        } else {
            T result = lookup.element(this.id, this.required);
            if (result == null) {
                return !this.required;
            }
            output.accept(result);
        }
        return true;
    }

    /** Visits required tag references (used for dependency ordering). */
    public void visitRequiredDependencies(Consumer<ResourceLocation> output) {
        if (this.tag && this.required) {
            output.accept(this.id);
        }
    }

    /** Visits optional tag references (used for dependency ordering). */
    public void visitOptionalDependencies(Consumer<ResourceLocation> output) {
        if (this.tag && !this.required) {
            output.accept(this.id);
        }
    }

    /** @return true when this entry is satisfied (required entries must exist). */
    public boolean verifyIfPresent(Predicate<ResourceLocation> elementCheck, Predicate<ResourceLocation> tagCheck) {
        return !this.required || (this.tag ? tagCheck : elementCheck).test(this.id);
    }

    /** Identifier this entry points at. */
    public ResourceLocation id() {
        return id;
    }

    /** @return true when this entry references another tag. */
    public boolean isTag() {
        return tag;
    }

    /** @return true when this entry must exist. */
    public boolean isRequired() {
        return required;
    }

    /** Compatibility accessor. */
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (this.tag) {
            result.append('#');
        }
        result.append(this.id);
        if (!this.required) {
            result.append('?');
        }
        return result.toString();
    }

    /**
     * Resolves entries to concrete values. Mirrors the modern nested
     * {@code TagEntry.Lookup<T>}.
     */
    public interface Lookup<T> {

        /** Looks up a direct element, or returns {@code null} when absent. */
        T element(ResourceLocation key, boolean required);

        /** Looks up the contents of another tag, or {@code null} when absent. */
        Collection<T> tag(ResourceLocation key);
    }
}
