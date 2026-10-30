package huffman;

import java.util.Arrays;

/**
 * Code de Huffman canonique sur l'alphabet des octets (256 symboles).
 *
 * Étapes côté compression :
 *   1. fréquences -> arbre de Huffman (algorithme glouton avec un tas min)
 *   2. arbre -> longueur du code de chaque symbole (= profondeur de sa feuille)
 *   3. longueurs -> codes canoniques
 *
 * Côté décompression, on ne transmet QUE les 256 longueurs (256 octets d'en-tête) :
 * l'étape 3 étant déterministe, le décompresseur reconstruit exactement les mêmes codes.
 *
 * Règle canonique : on trie les symboles par (longueur, valeur du symbole) ;
 * le premier reçoit le code 0…0 ; chaque suivant reçoit (code précédent + 1),
 * décalé à gauche quand la longueur augmente.
 */
public final class HuffmanCode {

    public static final int ALPHABET_SIZE = 256;

    /**
     * Les codes sont manipulés dans un long : longueur max 63 bits.
     * Une profondeur d ne peut apparaître que si le fichier contient au moins
     * Fib(d+2) octets ; pour d = 64 il faudrait plus de 17 000 milliards d'octets.
     */
    public static final int MAX_CODE_LENGTH = 63;

    private final int[] lengths = new int[ALPHABET_SIZE]; // 0 = symbole absent
    private final long[] codes = new long[ALPHABET_SIZE];

    // Tables pour le décodage canonique (indexées par longueur 1..maxLength)
    private int maxLength;
    private long[] firstCode;     // premier code de chaque longueur
    private int[] countPerLength; // nombre de codes de chaque longueur
    private int[] firstIndex;     // position du premier symbole de cette longueur dans sortedSymbols
    private int[] sortedSymbols;  // symboles triés par (longueur, symbole)

    private HuffmanCode(int[] lengths) {
        System.arraycopy(lengths, 0, this.lengths, 0, ALPHABET_SIZE);
        buildCanonicalCodes();
    }

    // ------------------------------------------------------------------
    // Fabriques
    // ------------------------------------------------------------------

    /** Construit le code optimal à partir des fréquences observées. */
    public static HuffmanCode fromFrequencies(long[] frequencies) {
        if (frequencies.length != ALPHABET_SIZE) {
            throw new IllegalArgumentException("256 fréquences attendues");
        }
        int[] lengths = new int[ALPHABET_SIZE];

        MinHeap heap = new MinHeap(ALPHABET_SIZE);
        for (int s = 0; s < ALPHABET_SIZE; s++) {
            if (frequencies[s] > 0) {
                heap.add(new Node(s, frequencies[s]));
            }
        }

        if (heap.size() == 0) {
            return new HuffmanCode(lengths); // fichier vide : aucun code
        }
        if (heap.size() == 1) {
            // Cas particulier : un seul symbole distinct (ex. "aaaa").
            // L'arbre serait réduit à une feuille de profondeur 0, donc un code vide :
            // impossible à écrire. On lui attribue arbitrairement un code de 1 bit.
            lengths[heap.poll().symbol] = 1;
            return new HuffmanCode(lengths);
        }

        // Algorithme de Huffman : fusionner répétitivement les deux arbres les plus légers.
        while (heap.size() > 1) {
            Node a = heap.poll();
            Node b = heap.poll();
            heap.add(new Node(a, b));
        }
        Node root = heap.poll();
        assignDepths(root, 0, lengths);
        return new HuffmanCode(lengths);
    }

    /** Reconstruit le code à partir des longueurs lues dans l'en-tête du fichier compressé. */
    public static HuffmanCode fromLengths(int[] lengths) {
        if (lengths.length != ALPHABET_SIZE) {
            throw new IllegalArgumentException("256 longueurs attendues");
        }
        return new HuffmanCode(lengths);
    }

    /**
     * Parcours en profondeur itératif (une pile explicite évite tout risque
     * de StackOverflowError sur un arbre très déséquilibré).
     */
    private static void assignDepths(Node root, int rootDepth, int[] lengths) {
        Node[] stack = new Node[2 * ALPHABET_SIZE];
        int[] depths = new int[2 * ALPHABET_SIZE];
        int top = 0;
        stack[top] = root;
        depths[top] = rootDepth;
        top++;
        while (top > 0) {
            top--;
            Node n = stack[top];
            int d = depths[top];
            if (n.isLeaf()) {
                if (d > MAX_CODE_LENGTH) {
                    throw new IllegalStateException("Code de longueur " + d + " > " + MAX_CODE_LENGTH);
                }
                lengths[n.symbol] = d;
            } else {
                stack[top] = n.left;
                depths[top] = d + 1;
                top++;
                stack[top] = n.right;
                depths[top] = d + 1;
                top++;
            }
        }
    }

    // ------------------------------------------------------------------
    // Construction canonique
    // ------------------------------------------------------------------

    private void buildCanonicalCodes() {
        maxLength = 0;
        for (int len : lengths) {
            if (len < 0 || len > MAX_CODE_LENGTH) {
                throw new IllegalArgumentException("Longueur de code invalide : " + len);
            }
            maxLength = Math.max(maxLength, len);
        }

        countPerLength = new int[maxLength + 2];
        for (int len : lengths) {
            if (len > 0) {
                countPerLength[len]++;
            }
        }

        // Inégalité de Kraft : sum 2^-l <= 1, sinon aucun code préfixe n'existe.
        // On la vérifie pour rejeter un en-tête corrompu.
        if (maxLength > 0) {
            long available = 1; // nombre de codes disponibles à la longueur courante
            for (int len = 1; len <= maxLength; len++) {
                available = available * 2 - countPerLength[len];
                if (available < 0) {
                    throw new IllegalArgumentException("Longueurs incohérentes (inégalité de Kraft violée)");
                }
            }
        }

        // Symboles triés par (longueur, symbole) : le parcours 0..255 donne déjà
        // l'ordre des symboles, il suffit de les ranger par longueur.
        int used = 0;
        for (int len : lengths) {
            if (len > 0) used++;
        }
        sortedSymbols = new int[used];
        firstIndex = new int[maxLength + 2];
        int idx = 0;
        for (int len = 1; len <= maxLength; len++) {
            firstIndex[len] = idx;
            for (int s = 0; s < ALPHABET_SIZE; s++) {
                if (lengths[s] == len) {
                    sortedSymbols[idx++] = s;
                }
            }
        }

        // Attribution des codes canoniques.
        firstCode = new long[maxLength + 2];
        long code = 0;
        for (int len = 1; len <= maxLength; len++) {
            firstCode[len] = code;
            for (int i = 0; i < countPerLength[len]; i++) {
                codes[sortedSymbols[firstIndex[len] + i]] = code;
                code++;
            }
            code <<= 1; // passage à la longueur suivante
        }
    }

    // ------------------------------------------------------------------
    // Accès
    // ------------------------------------------------------------------

    public int length(int symbol) {
        return lengths[symbol];
    }

    public long code(int symbol) {
        return codes[symbol];
    }

    public int[] lengths() {
        return Arrays.copyOf(lengths, ALPHABET_SIZE);
    }

    /** Représentation lisible du code, ex. "0110". */
    public String codeAsString(int symbol) {
        int len = lengths[symbol];
        if (len == 0) return "";
        StringBuilder sb = new StringBuilder(len);
        for (int i = len - 1; i >= 0; i--) {
            sb.append(((codes[symbol] >>> i) & 1L) == 0 ? '0' : '1');
        }
        return sb.toString();
    }

    /**
     * Décode un symbole en lisant les bits un à un.
     *
     * Propriété clé du code canonique : parmi les codes de longueur L,
     * les valeurs sont consécutives à partir de firstCode[L].
     * Après avoir lu L bits formant la valeur v, si 0 <= v - firstCode[L] < count[L],
     * on a trouvé le symbole ; sinon on lit un bit de plus.
     * Aucun arbre n'est nécessaire en mémoire.
     */
    public int decodeSymbol(BitInputStream in) throws java.io.IOException {
        long value = 0;
        for (int len = 1; len <= maxLength; len++) {
            int bit = in.readBit();
            if (bit == -1) {
                throw new java.io.EOFException("Fin de fichier inattendue pendant le décodage");
            }
            value = (value << 1) | bit;
            long offset = value - firstCode[len];
            if (offset >= 0 && offset < countPerLength[len]) {
                return sortedSymbols[firstIndex[len] + (int) offset];
            }
        }
        throw new java.io.IOException("Séquence de bits invalide : aucun code ne correspond");
    }
}
