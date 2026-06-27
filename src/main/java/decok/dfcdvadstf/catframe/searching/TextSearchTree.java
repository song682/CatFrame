package decok.dfcdvadstf.catframe.searching;

import com.google.common.collect.Lists;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class TextSearchTree<T> implements SearchTree<T> {
	private final SearchTree<T> first;
	private final SearchTree<T> second;
	private final Comparator<T> order;

	public TextSearchTree(final SearchTree<T> first, final SearchTree<T> second, final Comparator<T> order) {
		this.first = first;
		this.second = second;
		this.order = order;
	}

	@Override
	public List<T> search(final String query) {
		List<T> firstResults = first.search(query);
		List<T> secondResults = second.search(query);

		if (firstResults.isEmpty()) {
			return secondResults;
		}

		if (secondResults.isEmpty()) {
			return firstResults;
		}

		Iterator<T> merged = new MergingUniqueIterator<>(firstResults.iterator(), secondResults.iterator(), order);
		List<T> result = Lists.newArrayList();
		while (merged.hasNext()) {
			result.add(merged.next());
		}
		return result;
	}
}
