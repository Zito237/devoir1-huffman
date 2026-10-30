package huffman;

/**
 * File de priorité (tas binaire minimum) écrite à la main.
 *
 * On pourrait utiliser java.util.PriorityQueue, mais l'implémenter soi-même
 * montre le mécanisme qui donne à Huffman sa complexité O(n log n) :
 * insertion et extraction du minimum en O(log n).
 *
 * Représentation : tableau où les fils du nœud i sont en 2i+1 et 2i+2,
 * et le parent du nœud i est en (i-1)/2.
 */
final class MinHeap {

    private Node[] heap;
    private int size;

    MinHeap(int initialCapacity) {
        heap = new Node[Math.max(1, initialCapacity)];
    }

    int size() {
        return size;
    }

    void add(Node node) {
        if (size == heap.length) {
            Node[] bigger = new Node[heap.length * 2];
            System.arraycopy(heap, 0, bigger, 0, size);
            heap = bigger;
        }
        heap[size] = node;
        siftUp(size);
        size++;
    }

    Node poll() {
        if (size == 0) {
            throw new IllegalStateException("Tas vide");
        }
        Node min = heap[0];
        size--;
        heap[0] = heap[size];
        heap[size] = null;
        if (size > 0) {
            siftDown(0);
        }
        return min;
    }

    /** Fait remonter l'élément i tant qu'il est plus petit que son parent. */
    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (heap[i].compareTo(heap[parent]) >= 0) {
                return;
            }
            swap(i, parent);
            i = parent;
        }
    }

    /** Fait descendre l'élément i tant qu'il est plus grand que son plus petit fils. */
    private void siftDown(int i) {
        while (true) {
            int left = 2 * i + 1;
            int right = left + 1;
            int smallest = i;
            if (left < size && heap[left].compareTo(heap[smallest]) < 0) {
                smallest = left;
            }
            if (right < size && heap[right].compareTo(heap[smallest]) < 0) {
                smallest = right;
            }
            if (smallest == i) {
                return;
            }
            swap(i, smallest);
            i = smallest;
        }
    }

    private void swap(int a, int b) {
        Node tmp = heap[a];
        heap[a] = heap[b];
        heap[b] = tmp;
    }
}
