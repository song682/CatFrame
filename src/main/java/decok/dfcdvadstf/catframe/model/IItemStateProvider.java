package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import decok.dfcdvadstf.catframe.model.state.property.ItemPropertyRegistry;
import decok.dfcdvadstf.catframe.model.state.property.ItemPropertyProvider;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Item state declaration interface — the single access abstraction on the CatFrame item side.
 * <p>
 * This interface only carries the <b>declaration and data-supply</b> responsibility and
 * contains no rendering behavior:
 * <ul>
 *   <li><b>Discovery marker</b>: once an external {@link net.minecraft.item.Item} implements
 *       this interface, CatFrame automatically discovers it and collects its textures during
 *       {@link ModelManagerDataLoader#init()}.</li>
 *   <li><b>Data supply</b>: the implementation delivers a decision tree
 *       ({@link #getItemStateRoot()}) or locates the ItemState JSON
 *       ({@link #getItemStateNamespace()} / {@link #getItemStateName()}); actual rendering
 *       is completed by the pipeline-side {@link ItemStateModel} driven by
 *       {@link RenderJsonItemModel}.</li>
 * </ul>
 * <p>
 * Mirrors the block-side {@link IBlockStateProvider} (pure declaration, no render
 * behavior): whether CatFrame renders an item is a routing decision owned by the
 * render pipeline and the registry, never by this interface.
 * <p>
 * Model discovery is handled by a four-tier system (items/ ItemState → model_mappings →
 * convention path); this interface is the code-level declaration tier within it.
 *
 * <h3>Four-tier discovery priority</h3>
 * <ol>
 *   <li>{@code items/{item}.json} (ItemState decision tree) — highest priority</li>
 *   <li>{@code model_mappings.json} items field — legacy flat mapping</li>
 *   <li>Item implements {@code IItemStateProvider} (this interface) — code-level registration</li>
 *   <li>convention path {@code assets/{namespace}/models/item/{name}.json} — lazy-discovery fallback</li>
 * </ol>
 *
 * <h3>Implementation as takeover</h3>
 * Implementing this interface means the item is handed over to the CatFrame rendering
 * domain; if you do not want it taken over, do not implement this interface (when the
 * registry has no matching model, rendering automatically falls back to the vanilla path).
 * Stage-level differences (which model to use for GUI / hand / dropped) are <b>data</b>,
 * expressed by the decision tree via {@code display_context} property branches, not by
 * interface methods.
 * <p>
 * <br>Mirroring the optional declaration hook {@link IBlockStateProvider#getStateDefinition()}
 * on the block side, {@link #getPropertyDefinitions()} lets implementations declare
 * custom item properties in place, auto-registered during discovery.
 *
 * <h3>Implementation as declaration</h3>
 * Aligned with the block-side philosophy: a registered item implementing this interface
 * IS the declaration — texture collection is interface-driven
 * ({@link #getDeclaredModelPaths()}), the ItemState JSON path may be declared explicitly
 * or fall back to the registry name, late registrations are picked up on the next
 * texture stitch, and explicit registration stays optional, never a prerequisite.
 */
public interface IItemStateProvider {

    /**
     * Deliver the ItemState decision tree root built in code, or {@code null} to let
     * CatFrame resolve the tree from {@code assets/<namespace>/items/<name>.json}
     * (located via {@link #getItemStateNamespace()} / {@link #getItemStateName()}).
     * <p>
     * Mirrors the block-side data-delivery hook {@link IBlockStateProvider#getBlockState}:
     * the interface supplies <b>data</b>; evaluation and quad submission belong to the
     * pipeline-side {@link ItemStateModel}. The delivered root is consumed at the code-level
     * discovery tier, so an {@code items/} JSON tree resolved for the same item still wins
     * (four-tier priority is unchanged).
     *
     * @return decision tree root node, or {@code null} to fall back to the ItemState JSON
     */
    @Nullable
    default ItemStateNode getItemStateRoot() {
        return null;
    }

    /**
     * Optionally declares the custom properties this item needs
     * ({@code modid:name} → provider). Mirrors the interface-based declaration
     * paradigm of {@link IBlockStateProvider#getStateDefinition()}: when a non-empty
     * map is returned, CatFrame validates and registers the entries through
     * {@link ItemPropertyRegistry} during discovery, so implementations never need to
     * call the registration facade manually. Keys must be fully namespaced
     * ({@code modid:name}); bare names are rejected with a warning.
     * <p>
     * Default returns an empty map (no property declarations).
     *
     * @return property declaration Map ({@code modid:name} → provider), may be empty
     */
    default Map<String, ItemPropertyProvider> getPropertyDefinitions() {
        return Collections.emptyMap();
    }

    /**
     * Optional namespace of this item's ItemState JSON
     * ({@code assets/<namespace>/items/<name>.json}). Mirrors the block-side
     * {@link IBlockStateProvider#getBlockstateNamespace()}: an empty string falls
     * back to the item's registry name (no colon → {@code minecraft}), and explicit
     * declarations win.
     *
     * @return ItemState namespace; empty string means derive from registry name
     */
    default String getItemStateNamespace() {
        return "";
    }

    /**
     * Optional name part of this item's ItemState JSON, paired with
     * {@link #getItemStateNamespace()}. Falls back to the name part of the item's
     * registry name when empty; explicit declarations win.
     *
     * @return ItemState name; empty string means derive from registry name
     */
    default String getItemStateName() {
        return "";
    }

    /**
     * Declare model paths this item needs textures for (interface-driven texture collection,
     * replacing the historical instanceof special case).
     * <p>
     * Implementation-as-declaration: discovery pass reruns this on every texture stitch
     * (collection sets are idempotent), so late declarations are picked up on the
     * next pass; returned paths are routed to the block/item atlas by prefix.
     *
     * @return list of model paths (may include namespace), may be empty
     */
    default List<String> getDeclaredModelPaths() {
        return Collections.emptyList();
    }
}
