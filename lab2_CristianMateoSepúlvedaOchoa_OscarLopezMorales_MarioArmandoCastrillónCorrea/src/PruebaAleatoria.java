import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Random;
import java.util.TreeSet;

/**
 * PRUEBA AUTOMÁTICA (opcional; no hace parte del programa del laboratorio).
 *
 * Qué hace: ejecuta miles de inserciones y eliminaciones aleatorias en todos los órdenes (4 a 9).
 * Después de CADA operación comprueba que:
 *   - el resultado (insertó/eliminó o no) coincide con un TreeSet de referencia,
 *   - el contenido del árbol es exactamente el del TreeSet,
 *   - el árbol cumple todas las propiedades de un B+ (método validate()).
 *
 * Cómo ejecutarla (después de compilar):   java PruebaAleatoria
 */
public class PruebaAleatoria {

    public static void main(String[] args) {
        PrintStream original = System.out;
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        int totalOps = 0;

        for (int order = 4; order <= 9; order++) {
            for (int seed = 0; seed < 40; seed++) {
                Random rnd = new Random(seed * 100L + order);
                BPlusTree tree = new BPlusTree(order);
                tree.verbose = (seed == 0); // en una corrida se captura el log para contar casos
                TreeSet<String> ref = new TreeSet<>();
                int universe = 20 + rnd.nextInt(180);

                if (tree.verbose) {
                    System.setOut(new PrintStream(capture));
                }
                for (int step = 0; step < 600; step++) {
                    String key = "k" + String.format("%03d", rnd.nextInt(universe));
                    // Al inicio se inserta más (70 %) para que el árbol crezca; luego se elimina más.
                    boolean doInsert = rnd.nextInt(100) < (step < 200 ? 70 : 45);
                    if (doInsert) {
                        boolean r = tree.insert(key);
                        check(r == ref.add(key), "insert resultado distinto", order, seed, step);
                    } else {
                        boolean r = tree.delete(key);
                        check(r == ref.remove(key), "delete resultado distinto", order, seed, step);
                    }
                    check(tree.contains(key) == ref.contains(key), "contains distinto", order, seed, step);
                    String err = tree.validate();
                    check(err == null, String.valueOf(err), order, seed, step);
                    check(tree.allKeys().equals(new ArrayList<>(ref)), "contenido distinto", order, seed, step);
                    totalOps++;
                }
                System.setOut(original);
            }
        }

        String log = capture.toString();
        System.out.println("OK: " + totalOps + " operaciones aleatorias validadas (ordenes 4 a 9).");
        System.out.println("Apariciones en el log -> SPLIT hoja: " + count(log, "SPLIT de hoja")
                + ", SPLIT interno: " + count(log, "SPLIT de nodo interno")
                + ", Caso 1: " + count(log, "Caso 1")
                + ", Caso 2a: " + count(log, "Caso 2a")
                + ", Caso 2b: " + count(log, "Caso 2b")
                + ", Caso 3: " + count(log, "Caso 3"));
    }

    /** Si la condición falla, imprime dónde falló (orden, semilla, paso) y termina. */
    private static void check(boolean cond, String msg, int order, int seed, int step) {
        if (!cond) {
            System.setOut(System.err);
            System.err.println("FALLO (orden " + order + ", semilla " + seed + ", paso " + step + "): " + msg);
            System.exit(1);
        }
    }

    private static int count(String text, String sub) {
        int c = 0;
        for (int i = text.indexOf(sub); i >= 0; i = text.indexOf(sub, i + 1)) {
            c++;
        }
        return c;
    }
}
