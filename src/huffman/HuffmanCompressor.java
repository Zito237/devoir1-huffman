package huffman;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Compression / décompression de fichiers par codage de Huffman canonique.
 *
 * Format du fichier compressé (.huf) :
 *
 *   +---------+----------------------+-----------------------+----------------------+
 *   | "HUF1"  | taille originale     | 256 longueurs de code | données codées       |
 *   | 4 octets| 8 octets (long)      | 256 octets            | bits, complétés à 0  |
 *   +---------+----------------------+-----------------------+----------------------+
 *
 * La taille originale indique au décompresseur quand s'arrêter :
 * les bits de bourrage du dernier octet ne sont donc jamais décodés.
 */
public final class HuffmanCompressor {

    static final byte[] MAGIC = {'H', 'U', 'F', '1'};
    static final int HEADER_SIZE = MAGIC.length + 8 + HuffmanCode.ALPHABET_SIZE;

    private HuffmanCompressor() {
    }

    // ------------------------------------------------------------------
    // API fichiers (deux passes sur l'entrée : comptage puis codage)
    // ------------------------------------------------------------------

    public static void compressFile(Path input, Path output) throws IOException {
        long[] freq;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(input))) {
            freq = countFrequencies(in);
        }
        try (InputStream in = new BufferedInputStream(Files.newInputStream(input));
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(output))) {
            encode(in, out, freq);
        }
    }

    public static void decompressFile(Path input, Path output) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(input));
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(output))) {
            decode(in, out);
        }
    }

    // ------------------------------------------------------------------
    // API mémoire (pratique pour les tests)
    // ------------------------------------------------------------------

    public static byte[] compress(byte[] data) throws IOException {
        long[] freq = countFrequencies(new ByteArrayInputStream(data));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        encode(new ByteArrayInputStream(data), out, freq);
        return out.toByteArray();
    }

    public static byte[] decompress(byte[] compressed) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        decode(new ByteArrayInputStream(compressed), out);
        return out.toByteArray();
    }

    // ------------------------------------------------------------------
    // Cœur de l'algorithme
    // ------------------------------------------------------------------

    /** Passe 1 : compte les occurrences de chaque octet. */
    public static long[] countFrequencies(InputStream in) throws IOException {
        long[] freq = new long[HuffmanCode.ALPHABET_SIZE];
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            for (int i = 0; i < n; i++) {
                freq[buf[i] & 0xFF]++; // & 0xFF : un byte Java est signé (-128..127)
            }
        }
        return freq;
    }

    /** Passe 2 : écrit l'en-tête puis le code de chaque octet. */
    static void encode(InputStream in, OutputStream rawOut, long[] freq) throws IOException {
        HuffmanCode code = HuffmanCode.fromFrequencies(freq);
        long originalSize = 0;
        for (long f : freq) originalSize += f;

        DataOutputStream header = new DataOutputStream(rawOut);
        header.write(MAGIC);
        header.writeLong(originalSize);
        for (int s = 0; s < HuffmanCode.ALPHABET_SIZE; s++) {
            header.writeByte(code.length(s));
        }

        BitOutputStream bits = new BitOutputStream(rawOut);
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            for (int i = 0; i < n; i++) {
                int s = buf[i] & 0xFF;
                bits.writeBits(code.code(s), code.length(s));
            }
        }
        bits.close(); // écrit le dernier octet partiel
    }

    /** Lit l'en-tête, reconstruit le code canonique, puis décode symbole par symbole. */
    static void decode(InputStream rawIn, OutputStream out) throws IOException {
        DataInputStream header = new DataInputStream(rawIn);
        byte[] magic = new byte[MAGIC.length];
        header.readFully(magic);
        for (int i = 0; i < MAGIC.length; i++) {
            if (magic[i] != MAGIC[i]) {
                throw new IOException("Ce fichier n'est pas un fichier compressé HUF1");
            }
        }
        long originalSize = header.readLong();
        if (originalSize < 0) {
            throw new IOException("En-tête corrompu : taille négative");
        }
        int[] lengths = new int[HuffmanCode.ALPHABET_SIZE];
        for (int s = 0; s < HuffmanCode.ALPHABET_SIZE; s++) {
            lengths[s] = header.readUnsignedByte();
        }

        HuffmanCode code;
        try {
            code = HuffmanCode.fromLengths(lengths);
        } catch (IllegalArgumentException e) {
            throw new IOException("En-tête corrompu : " + e.getMessage(), e);
        }

        BitInputStream bits = new BitInputStream(rawIn);
        for (long i = 0; i < originalSize; i++) {
            out.write(code.decodeSymbol(bits));
        }
        out.flush();
    }
}
