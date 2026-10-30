package huffman;

import java.io.IOException;
import java.io.InputStream;

/**
 * Lit des bits un par un depuis un flux d'octets (bit de poids fort en premier),
 * symétrique de {@link BitOutputStream}.
 */
public final class BitInputStream {

    private final InputStream in;
    private int currentByte;   // octet en cours de lecture
    private int bitsRemaining; // bits non encore lus dans currentByte

    public BitInputStream(InputStream in) {
        this.in = in;
    }

    /**
     * @return le bit suivant (0 ou 1), ou -1 si la fin du flux est atteinte.
     */
    public int readBit() throws IOException {
        if (bitsRemaining == 0) {
            currentByte = in.read();
            if (currentByte == -1) {
                return -1;
            }
            bitsRemaining = 8;
        }
        bitsRemaining--;
        return (currentByte >>> bitsRemaining) & 1;
    }
}
