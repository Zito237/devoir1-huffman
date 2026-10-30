package huffman;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Point d'entrée en ligne de commande.
 *
 *   java -cp bin huffman.Main compress   <entrée> <sortie.huf>
 *   java -cp bin huffman.Main decompress <entrée.huf> <sortie>
 *   java -cp bin huffman.Main stats      <entrée>
 */
public final class Main {

    public static void main(String[] args) {
        if (args.length < 2) {
            usage();
            System.exit(1);
        }
        try {
            switch (args[0]) {
                case "compress" -> {
                    requireArgs(args, 3);
                    Path in = Paths.get(args[1]);
                    Path out = Paths.get(args[2]);
                    long t0 = System.nanoTime();
                    HuffmanCompressor.compressFile(in, out);
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    printRatio(Files.size(in), Files.size(out), ms);
                }
                case "decompress" -> {
                    requireArgs(args, 3);
                    long t0 = System.nanoTime();
                    HuffmanCompressor.decompressFile(Paths.get(args[1]), Paths.get(args[2]));
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    System.out.printf("Décompression terminée (%d ms) -> %s%n", ms, args[2]);
                }
                case "stats" -> printStats(Paths.get(args[1]));
                default -> {
                    usage();
                    System.exit(1);
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Erreur : " + e.getMessage());
            System.exit(2);
        }
    }

    private static void requireArgs(String[] args, int n) {
        if (args.length < n) {
            usage();
            System.exit(1);
        }
    }

    private static void usage() {
        System.err.println("""
                Usage :
                  java -cp bin huffman.Main compress   <entrée> <sortie.huf>
                  java -cp bin huffman.Main decompress <entrée.huf> <sortie>
                  java -cp bin huffman.Main stats      <entrée>""");
    }

    private static void printRatio(long original, long compressed, long ms) {
        double ratio = original == 0 ? 0 : 100.0 * compressed / original;
        System.out.printf(Locale.FRANCE,
                "Compression terminée (%d ms)%n  taille originale : %,d octets%n"
                        + "  taille compressée : %,d octets (en-tête %d inclus)%n  ratio : %.2f %%%n",
                ms, original, compressed, HuffmanCompressor.HEADER_SIZE, ratio);
    }

    /**
     * Affiche la table des codes et compare la longueur moyenne obtenue
     * à l'entropie de Shannon, borne inférieure théorique.
     */
    private static void printStats(Path file) throws IOException {
        long[] freq;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            freq = HuffmanCompressor.countFrequencies(in);
        }
        long total = 0;
        for (long f : freq) total += f;
        if (total == 0) {
            System.out.println("Fichier vide.");
            return;
        }
        HuffmanCode code = HuffmanCode.fromFrequencies(freq);

        double entropy = 0;
        double avgLength = 0;
        int distinct = 0;
        System.out.println("Symbole   Fréquence   Proba      Longueur  Code");
        System.out.println("-------   ---------   --------   --------  ----");
        for (int s = 0; s < HuffmanCode.ALPHABET_SIZE; s++) {
            if (freq[s] == 0) continue;
            distinct++;
            double p = (double) freq[s] / total;
            entropy -= p * (Math.log(p) / Math.log(2));
            avgLength += p * code.length(s);
            System.out.printf(Locale.US, "%-9s %9d   %8.5f   %8d  %s%n",
                    label(s), freq[s], p, code.length(s), code.codeAsString(s));
        }
        System.out.println();
        System.out.printf(Locale.FRANCE, "Octets : %,d   Symboles distincts : %d%n", total, distinct);
        System.out.printf(Locale.FRANCE, "Entropie de Shannon H      : %.4f bits/symbole%n", entropy);
        System.out.printf(Locale.FRANCE, "Longueur moyenne Huffman L : %.4f bits/symbole%n", avgLength);
        System.out.printf(Locale.FRANCE, "Vérification H <= L < H+1  : %s%n",
                (entropy <= avgLength + 1e-12 && avgLength < entropy + 1) ? "OK" : "NON (cas 1 symbole : L=1, H=0)");
        System.out.printf(Locale.FRANCE, "Taille estimée : %,d octets de données + %d octets d'en-tête%n",
                (long) Math.ceil(avgLength * total / 8), HuffmanCompressor.HEADER_SIZE);
    }

    private static String label(int s) {
        if (s == ' ') return "' '";
        if (s == '\n') return "\\n";
        if (s == '\r') return "\\r";
        if (s == '\t') return "\\t";
        if (s >= 33 && s <= 126) return "'" + (char) s + "'";
        return String.format("0x%02X", s);
    }
}
