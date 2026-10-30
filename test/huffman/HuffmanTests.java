package huffman;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

/**
 * Tests sans framework (aucune bibliothèque externe, donc pas de JUnit).
 * Chaque test lève une AssertionError en cas d'échec ; le bilan s'affiche à la fin.
 *
 *   java -cp bin huffman.HuffmanTests
 */
public final class HuffmanTests {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        run("fichier vide", HuffmanTests::emptyInput);
        run("un seul octet", HuffmanTests::singleByte);
        run("un seul symbole répété", HuffmanTests::singleSymbolRepeated);
        run("deux symboles", HuffmanTests::twoSymbols);
        run("texte français (UTF-8)", HuffmanTests::frenchText);
        run("les 256 octets", HuffmanTests::all256Bytes);
        run("données aléatoires 1 Mo", HuffmanTests::randomData);
        run("fréquences de Fibonacci (arbre très profond)", HuffmanTests::fibonacciFrequencies);
        run("propriété du préfixe", HuffmanTests::prefixProperty);
        run("inégalité de Kraft (égalité pour Huffman)", HuffmanTests::kraftEquality);
        run("borne de Shannon H <= L < H+1", HuffmanTests::shannonBound);
        run("exemple du cours ABRACADABRA", HuffmanTests::abracadabra);
        run("rejet d'un fichier non compressé", HuffmanTests::rejectBadMagic);
        run("rejet d'un fichier tronqué", HuffmanTests::rejectTruncated);

        System.out.printf("%n%d réussi(s), %d échoué(s)%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------------

    private static void emptyInput() throws IOException {
        byte[] c = roundTrip(new byte[0]);
        check(c.length == HuffmanCompressor.HEADER_SIZE, "un fichier vide ne contient que l'en-tête");
    }

    private static void singleByte() throws IOException {
        roundTrip(new byte[]{42});
    }

    private static void singleSymbolRepeated() throws IOException {
        byte[] data = new byte[10_000];
        Arrays.fill(data, (byte) 'a');
        byte[] c = roundTrip(data);
        // 1 bit par symbole -> 10 000 / 8 = 1 250 octets de données
        check(c.length == HuffmanCompressor.HEADER_SIZE + 1250, "1 bit par symbole attendu, obtenu " + c.length);
    }

    private static void twoSymbols() throws IOException {
        roundTrip("abababababbbbbbbbbaaa".getBytes(StandardCharsets.US_ASCII));
    }

    private static void frenchText() throws IOException {
        String text = "Le codage de Huffman est un algorithme de compression sans perte. "
                + "Les symboles fréquents reçoivent des codes courts, les symboles rares des codes longs. "
                + "Été, élève, à côté, où, ça, Noël, cœur.";
        byte[] data = text.repeat(50).getBytes(StandardCharsets.UTF_8);
        byte[] c = roundTrip(data);
        check(c.length < data.length * 0.65, "un texte doit être compressé à moins de 65 %");
    }

    private static void all256Bytes() throws IOException {
        byte[] data = new byte[256 * 40];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        byte[] c = roundTrip(data);
        // Distribution uniforme sur 256 symboles : chaque code fait exactement 8 bits
        check(c.length == HuffmanCompressor.HEADER_SIZE + data.length,
                "distribution uniforme : aucun gain possible");
    }

    private static void randomData() throws IOException {
        byte[] data = new byte[1 << 20];
        new Random(2026).nextBytes(data);
        roundTrip(data);
    }

    /**
     * Fréquences 1,1,2,3,5,8,... : chaque fusion prend le nouvel arbre et la feuille suivante,
     * l'arbre est un "peigne" et la profondeur maximale vaut (nombre de symboles - 1).
     */
    private static void fibonacciFrequencies() throws IOException {
        int symbols = 25;
        long a = 1, b = 1;
        long[] freq = new long[256];
        for (int s = 0; s < symbols; s++) {
            long f = (s < 2) ? 1 : a + b;
            if (s >= 2) { a = b; b = f; }
            freq[s] = f;
        }
        int maxLen = 0;
        HuffmanCode code = HuffmanCode.fromFrequencies(freq);
        for (int s = 0; s < 256; s++) maxLen = Math.max(maxLen, code.length(s));
        check(maxLen == symbols - 1, "profondeur attendue " + (symbols - 1) + ", obtenue " + maxLen);

        // Aller-retour réel sur un fichier qui a ces fréquences (environ 200 000 octets)
        byte[] data = new byte[(int) Arrays.stream(freq).sum()];
        int pos = 0;
        for (int s = 0; s < symbols; s++) for (long k = 0; k < freq[s]; k++) data[pos++] = (byte) s;
        shuffle(data, new Random(7));
        roundTrip(data);
    }

    /** Aucun code n'est le préfixe d'un autre : c'est ce qui rend le décodage non ambigu. */
    private static void prefixProperty() {
        HuffmanCode code = HuffmanCode.fromFrequencies(randomFrequencies(new Random(1)));
        for (int x = 0; x < 256; x++) {
            for (int y = 0; y < 256; y++) {
                if (x == y || code.length(x) == 0 || code.length(y) == 0) continue;
                String cx = code.codeAsString(x), cy = code.codeAsString(y);
                check(!cy.startsWith(cx), "le code de " + x + " (" + cx + ") préfixe celui de " + y + " (" + cy + ")");
            }
        }
    }

    /** Un arbre de Huffman est complet : la somme des 2^-l vaut exactement 1. */
    private static void kraftEquality() {
        HuffmanCode code = HuffmanCode.fromFrequencies(randomFrequencies(new Random(3)));
        double sum = 0;
        for (int s = 0; s < 256; s++) if (code.length(s) > 0) sum += Math.pow(2, -code.length(s));
        check(Math.abs(sum - 1.0) < 1e-12, "somme de Kraft = " + sum);
    }

    private static void shannonBound() {
        Random rnd = new Random(11);
        for (int trial = 0; trial < 50; trial++) {
            long[] freq = randomFrequencies(rnd);
            HuffmanCode code = HuffmanCode.fromFrequencies(freq);
            long total = Arrays.stream(freq).sum();
            double h = 0, l = 0;
            for (int s = 0; s < 256; s++) {
                if (freq[s] == 0) continue;
                double p = (double) freq[s] / total;
                h -= p * Math.log(p) / Math.log(2);
                l += p * code.length(s);
            }
            check(h <= l + 1e-9 && l < h + 1, "H=" + h + " L=" + l);
        }
    }

    /** A:5, B:2, R:2, C:1, D:1 -> 23 bits au total pour 11 symboles (valeur classique). */
    private static void abracadabra() throws IOException {
        byte[] data = "ABRACADABRA".getBytes(StandardCharsets.US_ASCII);
        long[] freq = new long[256];
        for (byte b : data) freq[b & 0xFF]++;
        HuffmanCode code = HuffmanCode.fromFrequencies(freq);
        long bits = 0;
        for (int s = 0; s < 256; s++) bits += freq[s] * code.length(s);
        check(bits == 23, "23 bits attendus, obtenu " + bits);
        roundTrip(data);
    }

    private static void rejectBadMagic() {
        try {
            HuffmanCompressor.decompress("pas un fichier huffman, juste du texte quelconque".repeat(10).getBytes());
            throw new AssertionError("une exception était attendue");
        } catch (IOException expected) {
            // attendu
        }
    }

    private static void rejectTruncated() throws IOException {
        byte[] c = HuffmanCompressor.compress("Bonjour le monde, bonjour Huffman !".repeat(20).getBytes());
        byte[] truncated = Arrays.copyOf(c, c.length - 10);
        try {
            HuffmanCompressor.decompress(truncated);
            throw new AssertionError("une exception était attendue");
        } catch (IOException expected) {
            // attendu
        }
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private static byte[] roundTrip(byte[] data) throws IOException {
        byte[] compressed = HuffmanCompressor.compress(data);
        byte[] restored = HuffmanCompressor.decompress(compressed);
        check(Arrays.equals(data, restored), "les données décompressées diffèrent de l'original");
        return compressed;
    }

    private static long[] randomFrequencies(Random rnd) {
        long[] freq = new long[256];
        for (int s = 0; s < 256; s++) {
            freq[s] = rnd.nextInt(4) == 0 ? 0 : 1 + rnd.nextInt(10_000);
        }
        return freq;
    }

    private static void shuffle(byte[] a, Random rnd) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            byte t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface TestBody {
        void run() throws Exception;
    }

    private static void run(String name, TestBody body) {
        try {
            body.run();
            passed++;
            System.out.println("  [OK]    " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("  [ECHEC] " + name + " : " + t.getMessage());
        }
    }
}
