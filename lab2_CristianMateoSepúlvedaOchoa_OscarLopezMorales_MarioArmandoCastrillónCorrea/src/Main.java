import java.util.Scanner;

/**
 * Programa principal del laboratorio: Árbol B+ de nombres.
 *
 * Cómo compilar y ejecutar (desde la carpeta que contiene estos .java):
 *      javac -encoding UTF-8 *.java
 *      java Main
 *
 * Flujo del programa:
 *   1. Pide el orden del árbol y valida que esté entre 4 y 9 (inclusive).
 *   2. Crea el árbol VACÍO (no se precarga ningún dato).
 *   3. Muestra un menú en bucle: imprimir, agregar, buscar, eliminar o salir.
 *
 * Reglas de los nombres (indicadas por el profesor):
 *   - Se pasan a minúsculas para evitar nombres repetidos por mayúsculas/minúsculas.
 *   - Se quitan los espacios sobrantes (de los lados y los repetidos de adentro).
 *   - Máximo 20 caracteres.
 */
public class Main {

    /** Longitud máxima permitida para un nombre. */
    private static final int MAX_NAME_LENGTH = 20;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        // 1) Orden del árbol (se valida dentro de askOrder).
        int order = askOrder(sc);

        // 2) El árbol inicia completamente vacío.
        BPlusTree tree = new BPlusTree(order);
        System.out.println("Árbol B+ de orden " + order + " creado (vacío).");
        System.out.println();

        // 3) Menú principal.
        int option;
        do {
            showMenu();
            option = readInt(sc, "Seleccione una opción: ");
            System.out.println();

            switch (option) {
                case 1:
                    System.out.println("=== ÁRBOL ACTUAL ===");
                    tree.print();
                    break;
                case 2: {
                    String name = readName(sc, "Nombre a agregar: ");
                    System.out.println("--- Agregar '" + name + "' ---");
                    tree.insert(name);
                    break;
                }
                case 3: {
                    String name = readName(sc, "Nombre a buscar: ");
                    System.out.println("--- Buscar '" + name + "' ---");
                    tree.search(name);
                    break;
                }
                case 4: {
                    String name = readName(sc, "Nombre a eliminar: ");
                    System.out.println("--- Eliminar '" + name + "' ---");
                    tree.delete(name);
                    break;
                }
                case 5:
                    System.out.println("Saliendo...");
                    break;
                default:
                    System.out.println("Opción inválida.");
            }
            System.out.println();
        } while (option != 5);

        sc.close();
    }

    /** Pide el orden hasta que sea un entero entre 4 y 9 (ambos inclusive). */
    private static int askOrder(Scanner sc) {
        int order;
        while (true) {
            order = readInt(sc, "Ingrese el orden del árbol B+ (entre 4 y 9): ");
            if (order >= 4 && order <= 9) {
                return order;
            }
            System.out.println("El orden debe estar entre 4 y 9 (ambos inclusive).");
        }
    }

    private static void showMenu() {
        System.out.println("=================================");
        System.out.println("         MENÚ ÁRBOL B+");
        System.out.println("=================================");
        System.out.println("1. Imprimir árbol");
        System.out.println("2. Agregar nombre");
        System.out.println("3. Buscar nombre");
        System.out.println("4. Eliminar nombre");
        System.out.println("5. Salir");
    }

    /** Lee un entero. Si el usuario escribe letras u otra cosa, avisa y vuelve a pedir (el programa no se cae). */
    private static int readInt(Scanner sc, String message) {
        while (true) {
            System.out.print(message);
            if (!sc.hasNextLine()) {
                System.exit(0); // se acabó la entrada (por ejemplo, Ctrl+D)
            }
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Entrada inválida: ingrese un número entero.");
            }
        }
    }

    /**
     * Lee un nombre y lo normaliza: sin espacios sobrantes, en minúsculas, no vacío, máx. 20 caracteres.
     * Ejemplo: "  María   JOSÉ " -> "maría josé".
     */
    private static String readName(Scanner sc, String message) {
        while (true) {
            System.out.print(message);
            if (!sc.hasNextLine()) {
                System.exit(0);
            }
            // trim quita espacios de los lados; replaceAll compacta los espacios repetidos de adentro
            String name = sc.nextLine().trim().toLowerCase().replaceAll("\\s+", " ");
            if (name.isEmpty()) {
                System.out.println("El nombre no puede estar vacío.");
            } else if (name.length() > MAX_NAME_LENGTH) {
                System.out.println("El nombre no puede superar " + MAX_NAME_LENGTH + " caracteres.");
            } else {
                return name;
            }
        }
    }
}
