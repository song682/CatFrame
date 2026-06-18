package net.dragon.jsonmodel;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

@SuppressWarnings("unused")
public class BlockModelBase extends Block implements IBlockJsonModel {
  private int renderTypeId = -1;

  protected BlockModelBase(Material p_i45394_1_) {
    super(p_i45394_1_);
  }

  @Override
  public int getRenderType() {
    if (renderTypeId == -1) {
      renderTypeId = this.renderType();
    }
    return renderTypeId;
  }
}
