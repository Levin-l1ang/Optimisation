/**
 * The connected components are considered as tree, its nodes are these UFNodes.
 * They only know their father in the tree and how many nodes originate in them.
 * This information is needed for a efficient implementation of the UnionFind
 * data-structure.
 */
public class UFNode {
    private UFNode father;
    private int size_subtree;

    //Constructor--------------------------------------------------------------------------
    public UFNode() {
        father = null;
        size_subtree = 0;
    }

    //Getter-------------------------------------------------------------------------------
    public UFNode getFather() {
        return father;
    }

    public int getSizeSubtree() {
        return size_subtree;
    }

    //Setter------------------------------------------------------------------------------
    public void setFather(UFNode father) {
        this.father = father;
    }

    public void setSizeSubtree(int size) {
        size_subtree = size;
    }
}
