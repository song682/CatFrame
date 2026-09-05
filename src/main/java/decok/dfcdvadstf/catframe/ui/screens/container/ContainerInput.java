package decok.dfcdvadstf.catframe.ui.screens.container;

/**
 * <p>
 * Container click action type — semantic replacement for the magic-number
 * {@code mode} parameter of vanilla {@code Container.slotClick}.<br>
 * ID values are intentionally aligned with the vanilla {@code mode} integers
 * so that bridging is a simple cast.
 * </p>
 * <p>
 * 容器点击操作类型——替代原版 {@code Container.slotClick} 魔数 {@code mode} 参数的语义化枚举。<br>
 * ID 值与原版 {@code mode} 整数对齐，桥接只需简单转型。
 * </p>
 */
public enum ContainerInput {

    /** Pick up / place items. / 拾取或放置物品。 */
    PICKUP(0),

    /** Shift-click transfer. / Shift+点击转移物品。 */
    QUICK_MOVE(1),

    /** Swap with hotbar slot. / 与热键栏槽位交换。 */
    SWAP(2),

    /** Creative-mode clone. / 创造模式克隆。 */
    CLONE(3),

    /** Drop item. / 丢弃物品。 */
    THROW(4),

    /** Drag-distribute across slots. / 拖拽分配到多个槽位。 */
    QUICK_CRAFT(5),

    /** Collect all matching items. / 收集所有匹配物品。 */
    PICKUP_ALL(6);

    private static final ContainerInput[] BY_ID = values();

    private final int id;

    ContainerInput(final int id) {
        this.id = id;
    }

    /**
     * @return the vanilla {@code mode} integer this input maps to
     *         / 本操作对应的原版 {@code mode} 整数值
     */
    public int id() {
        return this.id;
    }

    /**
     * Resolve a vanilla {@code mode} integer to the corresponding enum constant.
     * <p>将原版 {@code mode} 整数解析为对应的枚举常量。</p>
     *
     * @param id the vanilla mode integer / 原版 mode 整数
     * @return the matching {@code ContainerInput}, or {@link #PICKUP} if out of range
     */
    public static ContainerInput byId(final int id) {
        if (id >= 0 && id < BY_ID.length) {
            return BY_ID[id];
        }
        return PICKUP;
    }
}
