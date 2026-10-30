package huffman;

/**
 * Nœud de l'arbre de Huffman.
 *
 * Une feuille porte un symbole (un octet 0..255) ; un nœud interne porte deux fils.
 * Le poids d'un nœud interne est la somme des poids de ses fils.
 *
 * L'ordre défini par {@link #compareTo} rend la construction déterministe :
 * à poids égal, on départage par le plus petit symbole contenu dans le sous-arbre.
 * Sans cela, deux exécutions pourraient produire des arbres différents
 * (tous optimaux, mais c'est plus difficile à tester et à expliquer).
 */
final class Node implements Comparable<Node> {

    final long weight;
    final int symbol;      // -1 pour un nœud interne
    final int minSymbol;   // plus petit symbole du sous-arbre (départage)
    final Node left;
    final Node right;

    /** Construit une feuille. */
    Node(int symbol, long weight) {
        this.symbol = symbol;
        this.weight = weight;
        this.minSymbol = symbol;
        this.left = null;
        this.right = null;
    }

    /** Construit un nœud interne à partir de deux sous-arbres. */
    Node(Node left, Node right) {
        this.symbol = -1;
        this.weight = left.weight + right.weight;
        this.minSymbol = Math.min(left.minSymbol, right.minSymbol);
        this.left = left;
        this.right = right;
    }

    boolean isLeaf() {
        return left == null && right == null;
    }

    @Override
    public int compareTo(Node other) {
        int byWeight = Long.compare(this.weight, other.weight);
        return byWeight != 0 ? byWeight : Integer.compare(this.minSymbol, other.minSymbol);
    }
}
