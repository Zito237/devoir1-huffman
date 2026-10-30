package huffman;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Écrit des bits un par un dans un flux d'octets.
 *
 * Les bits sont accumulés dans un tampon de 8 bits (bit de poids fort en premier).
 * Quand le tampon est plein, l'octet est écrit dans le flux sous-jacent.
 * À la fermeture, le dernier octet incomplet est complété par des 0 (padding).
 */
public final class BitOutputStream implements AutoCloseable {

    private final OutputStream out;
    private int buffer;      // octet en cours de construction
    private int bitCount;    // nombre de bits déjà placés dans buffer (0..7)

    public BitOutputStream(OutputStream out) {
        this.out = out;
    }

    /** Écrit un seul bit (0 ou 1). */
    public void writeBit(int bit) throws IOException {
        if (bit != 0 && bit != 1) {
            throw new IllegalArgumentException("Un bit vaut 0 ou 1, reçu : " + bit);
        }
        buffer = (buffer << 1) | bit;
        bitCount++;
        if (bitCount == 8) {
            out.write(buffer);
            buffer = 0;
            bitCount = 0;
        }
    }

    /**
     * Écrit les {@code length} bits de poids faible de {@code code},
     * du plus significatif au moins significatif.
     */
    public void writeBits(long code, int length) throws IOException {
        for (int i = length - 1; i >= 0; i--) {
            writeBit((int) ((code >>> i) & 1L));
        }
    }

    /** Complète l'octet courant avec des 0 et l'écrit. */
    public void flushPartialByte() throws IOException {
        while (bitCount != 0) {
            writeBit(0);
        }
    }

    @Override
    public void close() throws IOException {
        flushPartialByte();
        out.flush();
    }
}
