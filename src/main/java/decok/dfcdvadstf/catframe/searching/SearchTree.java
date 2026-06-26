package decok.dfcdvadstf.catframe.searching;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;

@FunctionalInterface
public interface SearchTree<T> {
	List<T> search(String query);

	static <T> SearchTree<T> empty() {
		return query -> Collections.emptyList();
	}

	static <T> SearchTree<T> plainText(final List<T> elements, final Function<T, Stream<String>> textExtractor) {
		if (elements.isEmpty()) {
			return empty();
		}

		SuffixArray<T> tree = new SuffixArray<>();

		for (T element : elements) {
			((Stream<?>)textExtractor.apply(element)).forEach(t -> tree.add(element, ((String)t).toLowerCase(Locale.ROOT)));
		}

		tree.generate();
		return tree::search;
	}

	static <T> SearchTree<T> plainTextTrie(final List<T> elements, final Function<T, Stream<String>> textExtractor) {
		if (elements.isEmpty()) {
			return empty();
		}

		SuffixTrie<T> trie = new SuffixTrie<>();

		for (T element : elements) {
			((Stream<?>)textExtractor.apply(element)).forEach(t -> trie.add(element, ((String)t).toLowerCase(Locale.ROOT)));
		}

		return trie::search;
	}
}
