import java.util.*;

public class UnionFind {
    private final LinkedList<UFNode> connected_components;

    //Constructor---------------------------------------------------------------------
    public UnionFind() {
        connected_components = new LinkedList<>();
    }

    //Methods-------------------------------------------------------------------------
    public UFNode init() {
        UFNode node = new UFNode();
        connected_components.add(node);
        return node;
    }

    public void union(UFNode u, UFNode v) {
        if (u.getFather() != null || v.getFather() != null) {
            throw new RuntimeException("Trying to unite two nodes of which " +
                "at least one is no representative!");
        }
        if (u.getSizeSubtree() <= v.getSizeSubtree()) {
            v.setSizeSubtree(v.getSizeSubtree() + u.getSizeSubtree());
            u.setFather(v);
        } else {
            u.setSizeSubtree(v.getSizeSubtree() + u.getSizeSubtree());
            v.setFather(u);
        }
    }

    public UFNode find(UFNode u) {
        if (u.getFather() == null)
            return u;
        //Path-compression and recursion
        UFNode root = find(u.getFather());
        u.setFather(root);
        return root;
    }
}
