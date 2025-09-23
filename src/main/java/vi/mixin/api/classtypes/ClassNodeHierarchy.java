package vi.mixin.api.classtypes;

import org.objectweb.asm.tree.ClassNode;

import java.util.*;

public record ClassNodeHierarchy(ClassNode classNode, ClassNodeHierarchy parent, List<ClassNodeHierarchy> children) {

    public List<ClassNode> getAllClassesInHierarchy() {
        return getAllClassesInHierarchy(new HashSet<>()).stream().sorted(Comparator.comparing(a -> a.name)).toList();
    }

    private Set<ClassNode> getAllClassesInHierarchy(Set<ClassNode> visited) {
        if (visited.contains(classNode)) return Collections.emptySet();

        visited.add(classNode);
        Set<ClassNode> classEditors = new HashSet<>();
        classEditors.add(classNode);

        if (parent != null) classEditors.addAll(parent.getAllClassesInHierarchy(visited));
        children.forEach(inner -> classEditors.addAll(inner.getAllClassesInHierarchy(visited)));

        return classEditors;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
