package vi.mixin.api.classtypes;

import org.objectweb.asm.tree.ClassNode;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ClassNodeHierarchy(ClassNode classNode, ClassNodeHierarchy parent, List<ClassNodeHierarchy> children) {

    public Set<ClassNode> getAllClassesInHierarchy() {
        return getAllClassesInHierarchy(new HashSet<>());
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
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
