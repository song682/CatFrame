package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.searching.SearchTree;
import decok.dfcdvadstf.catframe.ui.Text;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * <p>
 * 搜索框组件 —— 基于 {@link AbstractEditBox}，集成 {@link SearchTree} 实现实时搜索。<br>
 * 用户输入时自动触发搜索，并通过回调返回匹配结果列表。
 * </p>
 * <p>
 * Searching box component — based on {@link AbstractEditBox}, integrates {@link SearchTree}
 * for real-time searching. Automatically triggers search as the user types and delivers
 * matched results via callback.
 * </p>
 *
 * @param <T> the type of elements being searched / 被搜索元素的类型
 */
public class SearchingBox<T> extends AbstractEditBox {

    private SearchTree<T> searchTree = SearchTree.empty();
    private Consumer<List<T>> resultCallback;
    private Consumer<String> queryCallback;

    public SearchingBox(int x, int y, int width, int height) {
        super(x, y, width, height);
        setHint(Text.translatableString("ui.searching"));
    }

    // ──── Configuration ────

    /**
     * Set the search tree to query against.
     * <p>设置要搜索的搜索树。</p>
     */
    public SearchingBox<T> setSearchTree(SearchTree<T> searchTree) {
        this.searchTree = searchTree != null ? searchTree : SearchTree.empty();
        // Re-run search with current text
        performSearch();
        return this;
    }

    /**
     * Build and set a plain-text search tree from the given elements.
     * <p>从给定元素构建并设置纯文本搜索树。</p>
     *
     * @param elements      the elements to search through / 要搜索的元素列表
     * @param textExtractor extracts searchable text from each element / 从每个元素提取可搜索文本
     */
    public SearchingBox<T> setElements(List<T> elements, Function<T, java.util.stream.Stream<String>> textExtractor) {
        this.searchTree = SearchTree.plainTextTrie(elements, textExtractor);
        performSearch();
        return this;
    }

    /**
     * Set the callback invoked with search results whenever the query changes.
     * <p>设置搜索查询变化时的结果回调。</p>
     */
    public SearchingBox<T> onResults(Consumer<List<T>> callback) {
        this.resultCallback = callback;
        return this;
    }

    /**
     * Set the callback invoked with the raw query string whenever it changes.
     * <p>设置查询文本变化时的原始字符串回调。</p>
     */
    public SearchingBox<T> onQueryChanged(Consumer<String> callback) {
        this.queryCallback = callback;
        return this;
    }

    // ──── Search logic ────

    @Override
    protected void onTextChanged() {
        performSearch();
    }

    private void performSearch() {
        String query = getText().toLowerCase(Locale.ROOT);

        if (queryCallback != null) {
            queryCallback.accept(query);
        }

        if (query.isEmpty()) {
            if (resultCallback != null) {
                resultCallback.accept(Collections.emptyList());
            }
            return;
        }

        List<T> results = searchTree.search(query);
        if (resultCallback != null) {
            resultCallback.accept(results);
        }
    }
}
