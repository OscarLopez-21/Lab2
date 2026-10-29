import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Árbol B+ de nombres (Strings), todo en memoria.
 *
 * ======================================================================================
 *  GUÍA RÁPIDA DE LA CLASE
 * ======================================================================================
 *
 *  ¿Qué significa "orden" (M)?
 *      M = número MÁXIMO de hijos que puede tener un nodo.
 *
 *  Límites de ocupación (se calculan con M):
 *      - Máximo de claves por nodo (hoja o interno) .......... M - 1
 *      - Overflow (sobrecupo) ................................. el nodo llega a M claves
 *      - Mínimo de claves en una HOJA que no es raíz .......... techo((M - 1) / 2)
 *      - Mínimo de hijos en un nodo INTERNO que no es raíz .... techo(M / 2)
 *      - Mínimo de claves en un nodo INTERNO que no es raíz ... techo(M / 2) - 1
 *      - La raíz es la excepción: puede ser una hoja con 1 clave, o un interno con 2 hijos.
 *      - Underflow ............................................. un nodo baja de su mínimo
 *
 *  Ejemplo con M = 4:  máx. 3 claves; overflow con 4; hojas mínimo 2; internos mínimo 1 clave.
 *
 *  Reglas propias de un B+ (diferencias con un árbol B):
 *      - Todas las claves REALES están en las hojas. Los nodos internos solo tienen separadores.
 *      - El separador i es la menor clave del subárbol del hijo i+1. Por eso se baja por el
 *        hijo derecho cuando  clave >= separador.
 *      - Split de HOJA: la primera clave de la hoja derecha se COPIA al padre (también queda en la hoja).
 *      - Split de NODO INTERNO: la mediana inferior SUBE al padre (ya no queda en los hijos).
 *      - Las hojas están enlazadas (campo 'next').
 *      - Todas las hojas están siempre a la misma profundidad: la altura solo cambia en la raíz.
 *
 *  Casos de eliminación que imprime el programa (ver constantes CASO_... más abajo):
 *      Caso 1  : se borra de una hoja y no hay underflow.
 *      Caso 2a : underflow en hoja resuelto con PRÉSTAMO de un hermano.
 *      Caso 2b : underflow en hoja resuelto con FUSIÓN (merge) con un hermano.
 *      Caso 3  : el problema sube a un nodo interno (préstamo/fusión interna) o la raíz queda vacía.
 *
 *  Mapa de métodos:
 *      insert -> insertRec -> splitLeaf / splitInternal
 *      search / contains
 *      delete -> deleteRec -> fix -> fixLeaf / fixInternal
 *      print / printNode
 *      validate (solo para pruebas automáticas)
 */
public class BPlusTree {

    /** Resultado de un split: la clave que sube al padre y el nodo nuevo (derecho). */
    private static class Split {
        final String key;   // clave/separador que sube al padre
        final Node right;   // nodo nuevo; el nodo izquierdo es el original, que se modifica "en sitio"
        Split(String key, Node right) {
            this.key = key;
            this.right = right;
        }
    }

    // ---- Nombres de los casos de eliminación ----
    // Si el profesor los define de otra forma, se cambian SOLO aquí (los mensajes los usan).
    static final String CASO_1 = "Caso 1";                    // eliminación simple en hoja, sin underflow
    static final String CASO_PRESTAMO_IZQ = "Caso 2a";        // underflow en hoja -> préstamo del hermano izquierdo
    static final String CASO_PRESTAMO_DER = "Caso 2a";        // underflow en hoja -> préstamo del hermano derecho
    static final String CASO_MERGE_HOJA = "Caso 2b";          // underflow en hoja -> fusión de hojas
    static final String CASO_INTERNO_PRESTAMO = "Caso 3";     // underflow en nodo interno -> préstamo (rotación)
    static final String CASO_INTERNO_MERGE = "Caso 3";        // underflow en nodo interno -> fusión
    static final String CASO_RAIZ = "Caso 3";                 // la raíz queda vacía y el árbol baja un nivel

    /** Orden del árbol (máximo de hijos por nodo). Lo pide Main al iniciar (entre 4 y 9). */
    final int order;

    /** Raíz del árbol. Es null cuando el árbol está vacío (el árbol siempre inicia vacío). */
    Node root;

    /** Si es false no se imprimen mensajes (lo usa la prueba automática). */
    boolean verbose = true;

    // Variables auxiliares de una inserción en curso (para informar duplicado y cantidad de splits).
    private boolean duplicate;
    private int splitCount;

    public BPlusTree(int order) {
        this.order = order;
        this.root = null;
    }

    public boolean isEmpty() {
        return root == null;
    }

    // ======================================================================================
    //  PARÁMETROS DERIVADOS DEL ORDEN
    // ======================================================================================

    /** Máximo de claves permitido en cualquier nodo: M - 1. */
    private int maxKeys() {
        return order - 1;
    }

    /** Mínimo de claves en una hoja que no es raíz: techo((M-1)/2). Con enteros equivale a M/2. */
    private int minLeafKeys() {
        return ((order - 1) + 1) / 2;
    }

    /** Mínimo de claves en un nodo interno que no es raíz: techo(M/2) - 1. */
    private int minInternalKeys() {
        return (order + 1) / 2 - 1;
    }

    /** Imprime un mensaje de trazabilidad (solo si verbose está activo). */
    private void log(String msg) {
        if (verbose) {
            System.out.println("   > " + msg);
        }
    }

    // ======================================================================================
    //  UTILIDADES DE BÚSQUEDA DENTRO DE UN NODO
    // ======================================================================================

    /**
     * Decide por cuál hijo bajar: es la cantidad de separadores que son menores o iguales a 'key'.
     * Ejemplo: separadores [carlos | maría], key = "dina"  ->  dina >= carlos pero dina < maría -> hijo #1.
     */
    private int childIndex(Node n, String key) {
        int i = 0;
        while (i < n.keys.size() && key.compareTo(n.keys.get(i)) >= 0) {
            i++;
        }
        return i;
    }

    /**
     * Posición donde va (o está) 'key' en una lista ordenada: el primer elemento que sea >= key.
     * Se usa para insertar en orden y para ubicar un nombre dentro de una hoja.
     */
    private int lowerBound(List<String> keys, String key) {
        int i = 0;
        while (i < keys.size() && key.compareTo(keys.get(i)) > 0) {
            i++;
        }
        return i;
    }

    /** Baja siempre por el hijo izquierdo hasta llegar a la primera hoja (la de los nombres menores). */
    private Node leftmostLeaf(Node n) {
        while (!n.leaf) {
            n = n.children.get(0);
        }
        return n;
    }

    // ======================================================================================
    //  BÚSQUEDA
    //  La búsqueda SIEMPRE termina en una hoja. Encontrar un nombre igual a un separador en un
    //  nodo interno NO cuenta: el dato real está en la hoja.
    // ======================================================================================

    /** Búsqueda silenciosa (sin mensajes). La usan delete y las pruebas. */
    public boolean contains(String key) {
        if (root == null) {
            return false;
        }
        Node n = root;
        while (!n.leaf) {
            n = n.children.get(childIndex(n, key));
        }
        int i = lowerBound(n.keys, key);
        return i < n.keys.size() && n.keys.get(i).equals(key);
    }

    /** Búsqueda que explica el recorrido en consola e informa si el nombre existe o no. */
    public boolean search(String key) {
        if (root == null) {
            System.out.println("   > El árbol está vacío: '" + key + "' NO existe.");
            return false;
        }
        Node n = root;
        int level = 0;
        // 1) Descender por los nodos internos eligiendo el hijo según los separadores.
        while (!n.leaf) {
            int i = childIndex(n, key);
            System.out.println("   > Nivel " + level + ": " + n + " -> se baja por el hijo #" + i);
            n = n.children.get(i);
            level++;
        }
        // 2) Ya en la hoja, buscar el nombre dentro de ella.
        System.out.println("   > Nivel " + level + " (hoja): " + n);
        int i = lowerBound(n.keys, key);
        boolean found = i < n.keys.size() && n.keys.get(i).equals(key);
        if (found) {
            System.out.println("   > '" + key + "' SÍ existe (posición " + i + " de la hoja).");
        } else {
            System.out.println("   > '" + key + "' NO existe en el árbol.");
        }
        return found;
    }

    // ======================================================================================
    //  INSERCIÓN
    //  Siempre empieza en una hoja y, si hay overflow, el problema se propaga hacia arriba
    //  (split de hoja -> split interno -> ... -> nueva raíz).
    // ======================================================================================

    /** Inserta un nombre. Devuelve false si ya existía (duplicado). */
    public boolean insert(String key) {

        // Caso especial: árbol vacío -> la raíz es una hoja con la primera clave.
        if (root == null) {
            root = new Node(true);
            root.keys.add(key);
            log("Árbol vacío: se crea la raíz (hoja) " + root + ".");
            log("Inserción simple, NO hubo split.");
            return true;
        }

        duplicate = false;
        splitCount = 0;

        // insertRec devuelve un Split si el nodo raíz quedó dividido; si no, null.
        Split s = insertRec(root, key);

        if (duplicate) {
            log("La clave '" + key + "' ya existe (duplicado). No se inserta.");
            return false;
        }

        // Si la raíz se dividió, el árbol crece un nivel: se crea una raíz nueva con 1 separador y 2 hijos.
        if (s != null) {
            Node newRoot = new Node(false);
            newRoot.keys.add(s.key);
            newRoot.children.add(root);
            newRoot.children.add(s.right);
            root = newRoot;
            log("El split llegó hasta la raíz: nueva raíz " + root + " (el árbol crece un nivel).");
        }
        if (splitCount == 0) {
            log("Inserción simple, NO hubo split.");
        } else {
            log("Total de splits en esta inserción: " + splitCount + ".");
        }
        return true;
    }

    /**
     * Inserta 'key' en el subárbol de 'n'. Devuelve un Split si 'n' quedó en overflow y se dividió
     * (el padre debe agregar el separador y el nodo nuevo); devuelve null si no hubo división.
     */
    private Split insertRec(Node n, String key) {

        // --- Caso base: estamos en una hoja ---
        if (n.leaf) {
            int pos = lowerBound(n.keys, key);
            if (pos < n.keys.size() && n.keys.get(pos).equals(key)) {
                duplicate = true;   // ya estaba: no se inserta nada
                return null;
            }
            n.keys.add(pos, key);   // inserción ordenada
            log("'" + key + "' se inserta en la hoja -> " + n);

            // Overflow: la hoja llegó a M claves (una más que el máximo permitido).
            if (n.keys.size() > maxKeys()) {
                log("OVERFLOW en la hoja (" + n.keys.size() + " claves, máximo " + maxKeys() + ").");
                return splitLeaf(n);
            }
            return null;
        }

        // --- Caso recursivo: nodo interno, bajamos por el hijo que corresponde ---
        int idx = childIndex(n, key);
        Split s = insertRec(n.children.get(idx), key);
        if (s == null) {
            return null;            // el hijo no se dividió (o era duplicado): nada que reparar aquí
        }

        // El hijo se dividió: agregamos el separador y el nodo nuevo justo a su derecha.
        n.keys.add(idx, s.key);
        n.children.add(idx + 1, s.right);
        log("El separador '" + s.key + "' se agrega al nodo interno -> " + n);

        // Este nodo interno también puede quedar en overflow.
        if (n.keys.size() > maxKeys()) {
            log("OVERFLOW en nodo interno (" + n.keys.size() + " claves, máximo " + maxKeys() + ").");
            return splitInternal(n);
        }
        return null;
    }

    /**
     * SPLIT DE HOJA (se COPIA la clave al padre).
     * Ejemplo (M=4): [10|20|30|40] -> izquierda [10|20] y derecha [30|40]; al padre sube 30,
     * pero 30 sigue estando en la hoja derecha porque las hojas conservan todas las claves.
     */
    private Split splitLeaf(Node n) {
        String before = n.toString();
        int leftCount = (n.keys.size() + 1) / 2;       // la izquierda se queda con la mitad (redondeo hacia arriba)

        Node right = new Node(true);
        right.keys.addAll(n.keys.subList(leftCount, n.keys.size()));
        n.keys.subList(leftCount, n.keys.size()).clear();

        // Mantener la lista enlazada de hojas: n -> right -> (lo que seguía a n)
        right.next = n.next;
        n.next = right;

        splitCount++;
        log("SPLIT de hoja " + before + " -> izquierda " + n + " y derecha " + right
                + ". Sube (se copia) '" + right.keys.get(0) + "' al padre.");
        return new Split(right.keys.get(0), right);
    }

    /**
     * SPLIT DE NODO INTERNO (la mediana inferior SUBE, no se copia).
     * Ejemplo (M=4): [30|50|70|90] -> sube 50; izquierda [30] y derecha [70|90].
     * Además hay que repartir los hijos: k claves siempre tienen k+1 hijos.
     */
    private Split splitInternal(Node n) {
        String before = n.toString();
        int mid = (n.keys.size() - 1) / 2;              // mediana inferior
        String promoted = n.keys.get(mid);

        Node right = new Node(false);
        right.keys.addAll(n.keys.subList(mid + 1, n.keys.size()));
        right.children.addAll(n.children.subList(mid + 1, n.children.size()));

        // El nodo original queda como hijo izquierdo: conserva las claves antes de la mediana
        // y los hijos hasta el índice mid (inclusive). La mediana ya no queda en este nivel.
        n.keys.subList(mid, n.keys.size()).clear();
        n.children.subList(mid + 1, n.children.size()).clear();

        splitCount++;
        log("SPLIT de nodo interno " + before + " -> izquierda " + n + " y derecha " + right
                + ". Sube '" + promoted + "' al padre.");
        return new Split(promoted, right);
    }

    // ======================================================================================
    //  ELIMINACIÓN
    //  Orden de reparación ante un underflow:  1) PRÉSTAMO de un hermano adyacente
    //                                          2) si ninguno puede prestar -> FUSIÓN (merge)
    //  Un hermano "puede prestar" si tiene MÁS claves que el mínimo.
    // ======================================================================================

    /** Elimina un nombre. Devuelve false si no existía. */
    public boolean delete(String key) {
        if (root == null) {
            log("El árbol está vacío: no hay nada que eliminar.");
            return false;
        }
        if (!contains(key)) {
            log("La clave '" + key + "' NO existe en el árbol. No se elimina nada.");
            return false;
        }

        deleteRec(root, key);

        // Ajustes finales sobre la raíz (la raíz tiene reglas especiales):
        if (!root.leaf && root.keys.isEmpty()) {
            // Una fusión dejó a la raíz interna sin separadores: su único hijo es la nueva raíz.
            root = root.children.get(0);
            log(CASO_RAIZ + ": la raíz quedó sin claves; su único hijo " + root
                    + " pasa a ser la nueva raíz (el árbol disminuye un nivel).");
        } else if (root.leaf && root.keys.isEmpty()) {
            // Se borró el último nombre: el árbol vuelve a estar vacío.
            root = null;
            log("Se eliminó la última clave: el árbol quedó vacío.");
        }
        return true;
    }

    /** ¿El nodo tiene menos claves que su mínimo? (la raíz no se evalúa aquí) */
    private boolean underflow(Node n) {
        return n.leaf ? n.keys.size() < minLeafKeys() : n.keys.size() < minInternalKeys();
    }

    /**
     * Elimina 'key' del subárbol de 'n'. Primero baja hasta la hoja y borra; al regresar (subida de la
     * recursión) cada padre revisa si su hijo quedó en underflow y lo repara con fix().
     */
    private void deleteRec(Node n, String key) {

        // --- Caso base: hoja. Se borra el nombre y se informa si hay underflow (lo corrige el padre) ---
        if (n.leaf) {
            n.keys.remove(lowerBound(n.keys, key));
            if (n == root) {
                log(CASO_1 + ": eliminación simple en la hoja raíz -> " + n);
            } else if (n.keys.size() >= minLeafKeys()) {
                log(CASO_1 + ": eliminación simple en hoja, sin underflow (" + n.keys.size()
                        + " claves, mínimo " + minLeafKeys() + ") -> " + n);
            } else {
                log("Se elimina '" + key + "' de la hoja -> " + n + " (" + n.keys.size()
                        + " claves, mínimo " + minLeafKeys() + "): UNDERFLOW, se corrige en el padre.");
            }
            return;
        }

        // --- Caso recursivo: bajar por el hijo que contiene la clave ---
        int idx = childIndex(n, key);
        Node child = n.children.get(idx);
        deleteRec(child, key);

        // Al volver: si el hijo quedó en underflow, este nodo (el padre) lo repara.
        if (underflow(child)) {
            fix(n, idx);
        }

        // Actualizar separadores: si este nodo todavía tiene como separador la clave que se borró,
        // se reemplaza por la menor clave del subárbol derecho (el separador debe seguir siendo
        // "la menor clave del hijo derecho").
        for (int j = 0; j < n.keys.size(); j++) {
            if (n.keys.get(j).equals(key)) {
                String newKey = leftmostLeaf(n.children.get(j + 1)).keys.get(0);
                n.keys.set(j, newKey);
                log("Se actualiza el separador '" + key + "' -> '" + newKey + "' en " + n);
            }
        }
    }

    /** Repara el underflow del hijo número 'idx' de 'parent'. Decide si es hoja o nodo interno. */
    private void fix(Node parent, int idx) {
        Node child = parent.children.get(idx);
        Node left = idx > 0 ? parent.children.get(idx - 1) : null;                              // hermano izquierdo
        Node right = idx < parent.children.size() - 1 ? parent.children.get(idx + 1) : null;    // hermano derecho

        if (child.leaf) {
            fixLeaf(parent, idx, child, left, right);
        } else {
            fixInternal(parent, idx, child, left, right);
        }
    }

    /**
     * Reparación en HOJAS (casos 2a y 2b).
     *  1. Préstamo del hermano izquierdo (si tiene de sobra): pasa su MAYOR clave a la hoja.
     *  2. Préstamo del hermano derecho (si tiene de sobra): pasa su MENOR clave a la hoja.
     *  3. Si ninguno puede prestar: fusión con el izquierdo (o con el derecho si no hay izquierdo).
     * En préstamos se actualiza el separador del padre; en fusiones se elimina un separador del padre.
     * Importante: en hojas NO baja la clave del padre (el separador solo se descarta).
     */
    private void fixLeaf(Node parent, int idx, Node child, Node left, Node right) {
        if (left != null && left.keys.size() > minLeafKeys()) {
            // PRÉSTAMO IZQUIERDO: la mayor clave del hermano izquierdo pasa al inicio de la hoja.
            String borrowed = left.keys.remove(left.keys.size() - 1);
            child.keys.add(0, borrowed);
            parent.keys.set(idx - 1, child.keys.get(0));    // nuevo separador = menor clave de la hoja
            log(CASO_PRESTAMO_IZQ + ": PRÉSTAMO del hermano izquierdo. '" + borrowed + "' pasa a la hoja " + child
                    + "; el separador del padre ahora es '" + parent.keys.get(idx - 1) + "'. Hermano: " + left);

        } else if (right != null && right.keys.size() > minLeafKeys()) {
            // PRÉSTAMO DERECHO: la menor clave del hermano derecho pasa al final de la hoja.
            String borrowed = right.keys.remove(0);
            child.keys.add(borrowed);
            parent.keys.set(idx, right.keys.get(0));        // nuevo separador = nueva menor clave del hermano derecho
            log(CASO_PRESTAMO_DER + ": PRÉSTAMO del hermano derecho. '" + borrowed + "' pasa a la hoja " + child
                    + "; el separador del padre ahora es '" + parent.keys.get(idx) + "'. Hermano: " + right);

        } else if (left != null) {
            // FUSIÓN CON EL IZQUIERDO: todas las claves de la hoja pasan al hermano izquierdo.
            String before = left + " + " + child;
            left.keys.addAll(child.keys);
            left.next = child.next;                         // la hoja desaparece de la lista enlazada
            String removedSep = parent.keys.remove(idx - 1);
            parent.children.remove(idx);
            log(CASO_MERGE_HOJA + ": FUSIÓN con el hermano izquierdo " + before + " -> " + left
                    + ". Se elimina el separador '" + removedSep + "' del padre -> " + parent);

        } else {
            // FUSIÓN CON EL DERECHO (la hoja es la primera): las claves del derecho pasan a esta hoja.
            String before = child + " + " + right;
            child.keys.addAll(right.keys);
            child.next = right.next;
            String removedSep = parent.keys.remove(idx);
            parent.children.remove(idx + 1);
            log(CASO_MERGE_HOJA + ": FUSIÓN con el hermano derecho " + before + " -> " + child
                    + ". Se elimina el separador '" + removedSep + "' del padre -> " + parent);
        }
    }

    /**
     * Reparación en NODOS INTERNOS (caso 3). Aquí SÍ participan los separadores del padre y los hijos:
     *  - Préstamo (rotación): el separador del padre BAJA al nodo, y el del hermano SUBE al padre;
     *    además el nodo recibe también un hijo (puntero) del hermano.
     *  - Fusión: el separador del padre BAJA y se une con las claves de ambos nodos y sus hijos.
     * Después de una fusión el padre pierde un separador y un hijo, así que puede quedar en underflow
     * y el problema sigue subiendo (lo detecta el nivel superior de la recursión).
     */
    private void fixInternal(Node parent, int idx, Node child, Node left, Node right) {
        if (left != null && left.keys.size() > minInternalKeys()) {
            // ROTACIÓN DERECHA (préstamo del izquierdo)
            String down = parent.keys.get(idx - 1);
            child.keys.add(0, down);
            child.children.add(0, left.children.remove(left.children.size() - 1));
            parent.keys.set(idx - 1, left.keys.remove(left.keys.size() - 1));
            log(CASO_INTERNO_PRESTAMO + ": underflow en nodo interno. PRÉSTAMO del hermano interno izquierdo (rotación): baja '"
                    + down + "' y sube '" + parent.keys.get(idx - 1) + "'. Nodo: " + child + ", hermano: " + left);

        } else if (right != null && right.keys.size() > minInternalKeys()) {
            // ROTACIÓN IZQUIERDA (préstamo del derecho)
            String down = parent.keys.get(idx);
            child.keys.add(down);
            child.children.add(right.children.remove(0));
            parent.keys.set(idx, right.keys.remove(0));
            log(CASO_INTERNO_PRESTAMO + ": underflow en nodo interno. PRÉSTAMO del hermano interno derecho (rotación): baja '"
                    + down + "' y sube '" + parent.keys.get(idx) + "'. Nodo: " + child + ", hermano: " + right);

        } else if (left != null) {
            // FUSIÓN CON EL IZQUIERDO: izquierdo + separador del padre + este nodo
            String before = left + " + " + child;
            left.keys.add(parent.keys.remove(idx - 1));
            left.keys.addAll(child.keys);
            left.children.addAll(child.children);
            parent.children.remove(idx);
            log(CASO_INTERNO_MERGE + ": underflow en nodo interno. FUSIÓN con el hermano izquierdo " + before + " -> " + left
                    + " (el separador del padre baja al nodo fusionado). Padre -> " + parent);

        } else {
            // FUSIÓN CON EL DERECHO: este nodo + separador del padre + derecho
            String before = child + " + " + right;
            child.keys.add(parent.keys.remove(idx));
            child.keys.addAll(right.keys);
            child.children.addAll(right.children);
            parent.children.remove(idx + 1);
            log(CASO_INTERNO_MERGE + ": underflow en nodo interno. FUSIÓN con el hermano derecho " + before + " -> " + child
                    + " (el separador del padre baja al nodo fusionado). Padre -> " + parent);
        }
    }

    // ======================================================================================
    //  IMPRESIÓN DEL ÁRBOL
    //  Tres vistas: por niveles, jerárquica (con ramas) y cadena de hojas enlazadas.
    // ======================================================================================

    public void print() {
        if (root == null) {
            System.out.println("El árbol está vacío.");
            return;
        }

        // Vista 1: por niveles (recorrido en anchura con una cola).
        System.out.println("--- Por niveles ---");
        Queue<Object[]> queue = new LinkedList<>();
        queue.add(new Object[] { root, 0 });          // cada elemento: {nodo, nivel}
        int currentLevel = -1;
        StringBuilder line = new StringBuilder();
        while (!queue.isEmpty()) {
            Object[] item = queue.poll();
            Node n = (Node) item[0];
            int level = (Integer) item[1];
            if (level != currentLevel) {               // empieza un nuevo nivel: se imprime el anterior
                if (currentLevel != -1) {
                    System.out.println(line);
                }
                currentLevel = level;
                line = new StringBuilder("Nivel " + level + ": ");
            }
            line.append(n).append(' ');
            if (!n.leaf) {
                for (Node c : n.children) {
                    queue.add(new Object[] { c, level + 1 });
                }
            }
        }
        System.out.println(line);

        // Vista 2: jerárquica, con ramas, indicando si cada nodo es hoja, interno o raíz.
        System.out.println();
        System.out.println("--- Estructura jerárquica ---");
        printNode(root, "", true, true);

        // Vista 3: recorrer las hojas usando los enlaces 'next' (muestra los nombres en orden alfabético).
        System.out.println();
        System.out.println("--- Hojas enlazadas (orden alfabético) ---");
        StringBuilder chain = new StringBuilder();
        for (Node l = leftmostLeaf(root); l != null; l = l.next) {
            chain.append(l);
            if (l.next != null) {
                chain.append(" -> ");
            }
        }
        System.out.println(chain);
    }

    /** Imprime un nodo y, recursivamente, sus hijos con sangría y ramas (|-- y `--). */
    private void printNode(Node n, String prefix, boolean last, boolean isRoot) {
        String label = n + (n.leaf ? "  (hoja)" : "  (interno)");
        if (isRoot) {
            System.out.println(label + "  <- raíz");
        } else {
            System.out.println(prefix + (last ? "`-- " : "|-- ") + label);
        }
        String childPrefix = isRoot ? "" : prefix + (last ? "    " : "|   ");
        for (int i = 0; i < n.children.size(); i++) {
            printNode(n.children.get(i), childPrefix, i == n.children.size() - 1, false);
        }
    }

    // ======================================================================================
    //  VALIDACIÓN DE PROPIEDADES (SOLO PARA LA PRUEBA AUTOMÁTICA; no se usa en el menú)
    //  Devuelve null si el árbol es válido, o un texto que describe el primer error encontrado.
    // ======================================================================================

    String validate() {
        if (root == null) {
            return null;
        }
        List<Integer> leafDepths = new ArrayList<>();
        String err = validateNode(root, 0, true, leafDepths);
        if (err != null) {
            return err;
        }
        // Propiedad: todas las hojas a la misma profundidad.
        for (int d : leafDepths) {
            if (d != leafDepths.get(0)) {
                return "Hojas a distinta profundidad";
            }
        }
        // Propiedad: la cadena de hojas recorre todas las claves en orden estricto.
        String prev = null;
        for (Node l = leftmostLeaf(root); l != null; l = l.next) {
            for (String k : l.keys) {
                if (prev != null && prev.compareTo(k) >= 0) {
                    return "Cadena de hojas desordenada en " + k;
                }
                prev = k;
            }
        }
        return null;
    }

    private String validateNode(Node n, int depth, boolean isRoot, List<Integer> leafDepths) {
        // Claves ordenadas y sin pasarse del máximo.
        for (int i = 1; i < n.keys.size(); i++) {
            if (n.keys.get(i - 1).compareTo(n.keys.get(i)) >= 0) {
                return "Claves desordenadas en " + n;
            }
        }
        if (n.keys.size() > maxKeys()) {
            return "Demasiadas claves en " + n;
        }
        if (n.leaf) {
            leafDepths.add(depth);
            if (isRoot ? n.keys.isEmpty() : n.keys.size() < minLeafKeys()) {
                return "Hoja con pocas claves: " + n;
            }
            return null;
        }
        // Nodo interno: k claves -> k+1 hijos, y mínimo de ocupación.
        if (n.children.size() != n.keys.size() + 1) {
            return "Hijos != claves + 1 en " + n;
        }
        if (isRoot ? n.children.size() < 2 : n.keys.size() < minInternalKeys()) {
            return "Nodo interno con pocas claves: " + n;
        }
        for (int i = 0; i < n.children.size(); i++) {
            Node c = n.children.get(i);
            String err = validateNode(c, depth + 1, false, leafDepths);
            if (err != null) {
                return err;
            }
            // Cada separador debe dejar a la izquierda claves menores y a la derecha mayores o iguales.
            List<String> all = new ArrayList<>();
            collect(c, all);
            String min = Collections.min(all);
            String max = Collections.max(all);
            if (i > 0 && min.compareTo(n.keys.get(i - 1)) < 0) {
                return "Subárbol " + i + " tiene '" + min + "' < separador '" + n.keys.get(i - 1) + "' en " + n;
            }
            if (i < n.keys.size() && max.compareTo(n.keys.get(i)) >= 0) {
                return "Subárbol " + i + " tiene '" + max + "' >= separador '" + n.keys.get(i) + "' en " + n;
            }
        }
        return null;
    }

    /** Junta todas las claves de las hojas de un subárbol. */
    private void collect(Node n, List<String> out) {
        if (n.leaf) {
            out.addAll(n.keys);
        } else {
            for (Node c : n.children) {
                collect(c, out);
            }
        }
    }

    /** Todas las claves en orden, recorriendo la lista enlazada de hojas. */
    List<String> allKeys() {
        List<String> out = new ArrayList<>();
        if (root != null) {
            for (Node l = leftmostLeaf(root); l != null; l = l.next) {
                out.addAll(l.keys);
            }
        }
        return out;
    }
}
