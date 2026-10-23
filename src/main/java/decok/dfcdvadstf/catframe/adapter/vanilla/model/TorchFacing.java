package decok.dfcdvadstf.catframe.adapter.vanilla.model;

import decok.dfcdvadstf.catframe.core.StringRepresentable;

/**
 * Torch facing vocabulary (torch / redstone_torch / unlit_redstone_torch).
 *
 * <p>The four wall mountings correspond to {@link decok.dfcdvadstf.catframe.core.Direction}, but
 * the ground-standing case has no {@code Direction}, so a dedicated enum is used. Each constant's
 * serialized name matches the blockstate variant-key value exactly (lowercase), letting a
 * {@link decok.dfcdvadstf.catframe.model.state.property.EnumMetaProperty} table decode the
 * non-linear {@code meta & 7} mapping without a positional codec.
 */
public enum TorchFacing implements StringRepresentable {
    EAST("east"),
    WEST("west"),
    SOUTH("south"),
    NORTH("north"),
    STANDING("standing");

    private final String name;

    TorchFacing(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
