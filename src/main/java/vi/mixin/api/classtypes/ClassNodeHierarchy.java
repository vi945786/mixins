package vi.mixin.api.classtypes;

import org.objectweb.asm.tree.ClassNode;

import java.util.*;

public record ClassNodeHierarchy(ClassNode mixinNode, ClassNode targetNodeClone, ClassNodeHierarchy parent, List<ClassNodeHierarchy> children) {

    public ClassNode targetNodeClone() {
        ClassNode clone = new ClassNode();
        targetNodeClone.accept(clone);
        return clone;
    }

    @SuppressWarnings("unused")
    public List<ClassNodeHierarchy> children() {
        return new ArrayList<>(children);
    }

    public boolean hasParentMixin() {
        return parent != null && parent.mixinNode != null;
    }

    public List<ClassNode> getAllMixinClassesInHierarchy() {
        return getAllMixinClassesInHierarchy(new HashSet<>()).stream().filter(Objects::nonNull).sorted(Comparator.comparing(a -> a.name)).toList();
    }

    private Set<ClassNode> getAllMixinClassesInHierarchy(Set<ClassNode> visited) {
        if (mixinNode == null || visited.contains(mixinNode)) return Collections.emptySet();

        visited.add(mixinNode);
        Set<ClassNode> classEditors = new HashSet<>();
        classEditors.add(mixinNode);

        if (parent != null) classEditors.addAll(parent.getAllMixinClassesInHierarchy(visited));
        children.forEach(inner -> classEditors.addAll(inner.getAllMixinClassesInHierarchy(visited)));

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
