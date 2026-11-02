package decok.dfcdvadstf.catframe.model.state.property;

import decok.dfcdvadstf.catframe.adapter.vanilla.model.TorchFacing;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for {@link Property#getNames()} using the serialized codec domain.
 *
 * <p>The blockstate variant-key validator matches JSON keys (lowercase serialized names)
 * against {@code getNames()}. When getNames went through {@code toString()}, enum-valued
 * properties whose constants override {@code getName()} (StringRepresentable vocabulary such
 * as {@link TorchFacing}) reported uppercase enum names, every real variant key was judged
 * invalid and the whole blockstate fell back to builtin/missing — the torch / redstone_torch
 * rendering bug. getNames must stay the exact mirror of {@link Property#getValueByName}.
 */
public class PropertyNamesSerializedTest {

    @Test
    public void enumMetaPropertyNamesAreSerializedLowerCase() {
        EnumMetaProperty<TorchFacing> facing = EnumMetaProperty.withTable("facing", 7, 0,
                TorchFacing.STANDING,
                TorchFacing.STANDING, TorchFacing.EAST, TorchFacing.WEST, TorchFacing.SOUTH,
                TorchFacing.NORTH, TorchFacing.STANDING, TorchFacing.STANDING, TorchFacing.STANDING);

        assertTrue(facing.getNames().contains("east"));
        assertTrue(facing.getNames().contains("standing"));
        assertFalse(facing.getNames().contains("EAST"));
        // Every name round-trips back through the parser (domain symmetry with getValueByName)
        for (String name : facing.getNames()) {
            assertEquals(name, facing.getName(facing.getValueByName(name)));
        }
    }

    @Test
    public void stringPropertyNamesAreTheValuesThemselves() {
        Property<String> prop = Property.create("color", String.class,
                java.util.Arrays.asList("white", "light_gray"));
        assertEquals(java.util.Arrays.asList("white", "light_gray"), prop.getNames());
    }

    @Test
    public void booleanPropertyNamesAreLowerCase() {
        BooleanProperty powered = BooleanProperty.create("powered");
        assertTrue(powered.getNames().contains("false"));
        assertTrue(powered.getNames().contains("true"));
    }
}
