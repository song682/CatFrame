package decok.dfcdvadstf.catframe.tags;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Dependency-ordered visitor, mirroring the modern
 * {@code net.minecraft.util.DependencySorter}.
 * <p>
 * Required dependencies are ordered before their dependants. Optional
 * dependencies are added only when they do not introduce a cycle, so a cyclic
 * tag graph still builds instead of dead-locking.
 * </p>
 */
public final class DependencySorter<K, V extends DependencySorter.Entry<K>> {

    private final Map<K, V> contents = new HashMap<>();

    public DependencySorter<K, V> addEntry(K id, V value) {
        this.contents.put(id, value);
        return this;
    }

    private void visitDependenciesAndElement(Multimap<K, K> dependencies, Set<K> alreadyVisited,
                                             K id, BiConsumer<K, V> output) {
        if (alreadyVisited.add(id)) {
            for (K dependency : dependencies.get(id)) {
                visitDependenciesAndElement(dependencies, alreadyVisited, dependency, output);
            }
            V current = this.contents.get(id);
            if (current != null) {
                output.accept(id, current);
            }
        }
    }

    private static <K> boolean isCyclic(Multimap<K, K> directDependencies, K from, K to) {
        Collection<K> dependencies = directDependencies.get(to);
        if (dependencies.contains(from)) {
            return true;
        }
        for (K dependency : dependencies) {
            if (isCyclic(directDependencies, from, dependency)) {
                return true;
            }
        }
        return false;
    }

    /** @return true when the edge was added; false when it would close a cycle. */
    private static <K> boolean addDependencyIfNotCyclic(Multimap<K, K> directDependencies, K from, K to) {
        if (isCyclic(directDependencies, from, to)) {
            return false;
        }
        directDependencies.put(from, to);
        return true;
    }

    /** Visits every entry after its dependencies. */
    public void orderByDependencies(BiConsumer<K, V> output) {
        orderByDependencies(output, null);
    }

    /**
     * Visits every entry after its dependencies, reporting required edges that
     * had to be dropped because they would close a cycle.
     *
     * @param rejectedRequired receives {@code (from, to)} for each dropped
     *                         required edge; may be {@code null}
     */
    public void orderByDependencies(BiConsumer<K, V> output, BiConsumer<K, K> rejectedRequired) {
        Multimap<K, K> directDependencies = HashMultimap.create();
        this.contents.forEach((id, value) ->
                value.visitRequiredDependencies(dep -> {
                    if (!addDependencyIfNotCyclic(directDependencies, id, dep) && rejectedRequired != null) {
                        rejectedRequired.accept(id, dep);
                    }
                }));
        this.contents.forEach((id, value) ->
                value.visitOptionalDependencies(dep -> addDependencyIfNotCyclic(directDependencies, id, dep)));

        Set<K> alreadyVisited = new HashSet<>();
        for (K topId : this.contents.keySet()) {
            visitDependenciesAndElement(directDependencies, alreadyVisited, topId, output);
        }
    }

    /** One sortable node. */
    public interface Entry<K> {

        void visitRequiredDependencies(Consumer<K> output);

        void visitOptionalDependencies(Consumer<K> output);
    }
}
