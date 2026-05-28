package decok.dfcdvadstf.catframe.model;

public interface IBlockJsonModel {
  /**
   * Call and return the in {@code Block#getRenderType.}
   */
  default int renderType() {
    return JsonBlock.IDMap.get(this);
  }
}
