package decok.dfcdvadstf.catframe.searching;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SuffixTrie<T> implements SearchTree<T> {
	private final TrieNode root = new TrieNode();
	private final List<T> elements = Lists.newArrayList();

	private static class TrieNode {
		final Map<Character, TrieNode> children = new HashMap<Character, TrieNode>();
		final List<Integer> elementIndices = Lists.newArrayList();

		void addIndex(int idx) {
			if (elementIndices.isEmpty() || elementIndices.get(elementIndices.size() - 1) != idx) {
				elementIndices.add(idx);
			}
		}
	}

	public void add(T element, String text) {
		int idx = elements.size();
		elements.add(element);
		int len = text.length();

		for (int start = 0; start < len; start++) {
			TrieNode node = root;

			for (int i = start; i < len; i++) {
				char c = text.charAt(i);
				TrieNode child = node.children.get(c);
				if (child == null) {
					child = new TrieNode();
					node.children.put(c, child);
				}
				node = child;
				node.addIndex(idx);
			}
		}
	}

	@Override
	public List<T> search(String query) {
		TrieNode node = root;

		for (int i = 0; i < query.length(); i++) {
			node = node.children.get(query.charAt(i));
			if (node == null) {
				return Collections.emptyList();
			}
		}

		List<T> result = Lists.newArrayListWithCapacity(node.elementIndices.size());
		for (int idx : node.elementIndices) {
			result.add(elements.get(idx));
		}
		return result;
	}
}
