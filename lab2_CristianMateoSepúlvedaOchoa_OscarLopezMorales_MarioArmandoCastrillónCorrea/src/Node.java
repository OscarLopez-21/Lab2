import java.util.ArrayList;
import java.util.List;

/**
 * Nodo del árbol B+.
 *
 * Un mismo tipo de nodo sirve para los dos papeles que existen en un B+:
 *
 *  - HOJA (leaf = true):
 *      * 'keys' guarda los nombres reales, siempre ordenados alfabéticamente.
 *      * 'children' queda vacío.
 *      * 'next' apunta a la siguiente hoja (lista enlazada de hojas), lo que permite
 *        recorrer todos los nombres en orden sin subir y bajar por el árbol.
 *
 *  - NODO INTERNO (leaf = false):
 *      * 'keys' guarda SEPARADORES (copias de nombres que están en las hojas); solo sirven
 *        para decidir por qué hijo bajar al buscar.
 *      * 'children' tiene siempre (keys.size() + 1) hijos.
 *      * 'next' no se usa.
 *
 * Ejemplo de un nodo interno con 2 separadores y 3 hijos:
 *
 *            [ carlos | maría ]            <- separadores
 *           /         |        \
 *   [ana|bob]  [carlos|dina]  [maría|sofía]   <- hojas (menores que carlos, entre carlos y maría, desde maría)
 */
public class Node {

    /** true = hoja (guarda los nombres), false = nodo interno (solo separadores). */
    boolean leaf;

    /** Nombres (si es hoja) o separadores (si es interno). Siempre ordenados. */
    List<String> keys = new ArrayList<>();

    /** Hijos del nodo. Solo se usa en nodos internos. */
    List<Node> children = new ArrayList<>();

    /** Siguiente hoja a la derecha (solo hojas). Es null en la última hoja. */
    Node next;

    Node(boolean leaf) {
        this.leaf = leaf;
    }

    /** Imprime el nodo como [clave1 | clave2 | clave3]. Se usa en todos los mensajes de consola. */
    @Override
    public String toString() {
        return "[" + String.join(" | ", keys) + "]";
    }
}
