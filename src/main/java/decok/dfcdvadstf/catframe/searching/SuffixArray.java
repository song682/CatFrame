package decok.dfcdvadstf.catframe.searching;

import com.google.common.collect.Lists;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SuffixArray<T> {
	private final List<T> elements = Lists.newArrayList();
	private final List<String> texts = Lists.newArrayList();
	private TIntList suffixElements = new TIntArrayList();
	private TIntList suffixOffsets = new TIntArrayList();

	public void add(final T element, final String text) {
		int idx = elements.size();
		elements.add(element);
		texts.add(text);

		for (int i = 0; i < text.length(); i++) {
			suffixElements.add(idx);
			suffixOffsets.add(i);
		}
	}

	public void generate() {
		int n = suffixElements.size();
		if (n <= 1) return;

		// 用 Integer[] 索引 + Arrays.sort 进行后缀排序
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) order[i] = i;

		Arrays.sort(order, (a, b) -> {
			String textA = texts.get(suffixElements.get(a));
			int offA = suffixOffsets.get(a);
			String textB = texts.get(suffixElements.get(b));
			int offB = suffixOffsets.get(b);

			while (offA < textA.length() && offB < textB.length()) {
				char cA = textA.charAt(offA);
				char cB = textB.charAt(offB);
				if (cA != cB) {
					return Character.compare(cA, cB);
				}
				offA++;
				offB++;
			}

			return Integer.compare(textA.length() - offA, textB.length() - offB);
		});

		// 按排序结果重建 suffix 数组
		TIntArrayList newElements = new TIntArrayList(n);
		TIntArrayList newOffsets = new TIntArrayList(n);
		for (int i = 0; i < n; i++) {
			int idx = order[i];
			newElements.add(suffixElements.get(idx));
			newOffsets.add(suffixOffsets.get(idx));
		}
		suffixElements = newElements;
		suffixOffsets = newOffsets;
	}

	public List<T> search(final String query) {
		int n = suffixElements.size();
		int low = 0;
		int high = n;

		while (low < high) {
			int mid = low + (high - low) / 2;
			int cmp = compareSuffix(mid, query);
			if (cmp < 0) {
				low = mid + 1;
			} else {
				high = mid;
			}
		}

		int lowerBound = low;
		if (lowerBound >= n) {
			return Collections.emptyList();
		}

		high = n;
		while (low < high) {
			int mid = low + (high - low) / 2;
			int cmp = compareSuffix(mid, query);
			if (cmp <= 0) {
				low = mid + 1;
			} else {
				high = mid;
			}
		}

		int upperBound = low;
		if (lowerBound >= upperBound) {
			return Collections.emptyList();
		}

		// boolean[] visited 线性去重
		boolean[] visited = new boolean[elements.size()];
		List<T> result = Lists.newArrayList();

		for (int i = lowerBound; i < upperBound; i++) {
			int idx = suffixElements.get(i);
			if (!visited[idx]) {
				visited[idx] = true;
				result.add(elements.get(idx));
			}
		}

		return result;
	}

	private int compareSuffix(int suffixIndex, String query) {
		String text = texts.get(suffixElements.get(suffixIndex));
		int offset = suffixOffsets.get(suffixIndex);
		int qLen = query.length();
		int tLen = text.length() - offset;
		int minLen = Math.min(qLen, tLen);

		for (int i = 0; i < minLen; i++) {
			char c1 = query.charAt(i);
			char c2 = text.charAt(offset + i);
			if (c1 != c2) {
				return Character.compare(c1, c2);
			}
		}

		return Integer.compare(qLen, tLen);
	}
}
