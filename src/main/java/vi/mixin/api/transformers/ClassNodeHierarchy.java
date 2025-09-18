package vi.mixin.api.transformers;

import org.objectweb.asm.tree.ClassNode;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClassNodeHierarchy {
    private final ClassNode classNode;
    private final ClassNodeHierarchy parent;
    private final List<ClassNodeHierarchy> children;

    public ClassNodeHierarchy(ClassNode classNode, ClassNodeHierarchy parent, List<ClassNodeHierarchy> children) {
        this.classNode = classNode;
        this.parent = parent;
        this.children = children;
    }

    public ClassNode getClassNode() {
        return classNode;
    }

    public ClassNodeHierarchy getParent() {
        return parent;
    }

    public List<ClassNodeHierarchy> getChildren() {
        return children;
    }

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
}
