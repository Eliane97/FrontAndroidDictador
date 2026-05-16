package com.example.myapplication.service;


import com.example.myapplication.model.PedidoModel; // Importa tu PedidoModel
import com.example.myapplication.model.ProductoModel;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PDFParser {

    private static final String TAG = "PDFParser"; // Etiqueta para los logs en Android

    public enum TipoPDF {
        CLIENTE_UNICO,
        MULTIPLES_CLIENTES,
        SIN_CLIENTE
    }

    /**
     * Parsea pedidos a partir de un archivo PDF usando su InputStream.
     * Este es el método preferido para Android.
     *
     * @param inputStream El InputStream del archivo PDF.
     * @return Una lista de objetos PedidoModel extraídos del PDF.
     * @throws Exception Si ocurre un error durante la extracción o el parseo.
     */
    public static List<PedidoModel> parsePedidos(InputStream inputStream) throws Exception {
        String texto = PDFUtils.extraerTexto(inputStream);
        return parseTextoPdf(texto);
    }

    /**
     * Parsea pedidos a partir de un archivo PDF usando su objeto File.
     * Se mantiene por compatibilidad si es necesario, pero InputStream es más común para URIs.
     *
     * @param archivoPdf El objeto File del archivo PDF.
     * @return Una lista de objetos PedidoModel extraídos del PDF.
     * @throws Exception Si ocurre un error durante la extracción o el parseo.
     */
    public static List<PedidoModel> parsePedidos(File archivoPdf) throws Exception {
        String texto = PDFUtils.extraerTexto(archivoPdf);
        return parseTextoPdf(texto);
    }

    /**
     * Método interno para procesar el texto extraído del PDF y determinar el tipo de parseo.
     *
     * @param texto El texto extraído del PDF.
     * @return Una lista de objetos PedidoModel.
     * @throws Exception Si el tipo de PDF no es manejado correctamente o hay un error de parseo.
     */
    private static List<PedidoModel> parseTextoPdf(String texto) throws Exception {
        Log.d(TAG, "Contenido RAW del PDF extraído (primeras 500 chars):\n---INICIO CONTENIDO PDF---");
        if (texto != null && !texto.isEmpty()) {
            Log.d(TAG, texto.substring(0, Math.min(texto.length(), 500)) + (texto.length() > 500 ? "..." : ""));
        } else {
            Log.d(TAG, "[Texto extraído vacío o nulo]");
        }
        Log.d(TAG, "---FIN CONTENIDO PDF---");

        TipoPDF tipo = detectarTipoPDF(texto);
        Log.d(TAG, "TipoPDF detectado: " + tipo);

        switch (tipo) {
            case CLIENTE_UNICO:
                Log.d(TAG, "El PDF fue clasificado como CLIENTE_UNICO. Llamando a parseClienteUnico...");
                return parseClienteUnico(texto.split("\\r?\\n"));
            case MULTIPLES_CLIENTES:
                Log.d(TAG, "El PDF fue clasificado como MULTIPLES_CLIENTES. Llamando a parseConClientes...");
                return parseConClientes(texto);
            case SIN_CLIENTE:
            default:
                Log.d(TAG, "El PDF fue clasificado como SIN_CLIENTE. Llamando a parseSinCliente...");
                return parseSinCliente(texto);
        }
    }

    private static TipoPDF detectarTipoPDF(String texto) {
        Log.d(TAG, "Iniciando detección de TipoPDF...");

        int cantidadPedidos = 0; // Renombrado de cantidadClientes para mayor claridad
        boolean contieneCuit = false;

        if (texto == null || texto.isEmpty()) {
            Log.d(TAG, "Texto vacío para detección de TipoPDF. Asumiendo SIN_CLIENTE.");
            return TipoPDF.SIN_CLIENTE;
        }

        String[] lineas = texto.split("\\r?\\n");

        for (String linea : lineas) {
            // Revisa si 'pedido' (en cualquier caso) está en la línea
            if (linea.toLowerCase().contains("pedido")) {
                cantidadPedidos++;
            }
            // Revisa si 'cuit' (en cualquier caso) está en la línea
            if (linea.toLowerCase().contains("cuit")) {
                contieneCuit = true;
            }
        }

        Log.d(TAG, "Cantidad de líneas con 'pedido': " + cantidadPedidos);
        Log.d(TAG, "Contiene la palabra 'cuit': " + contieneCuit);

        if (cantidadPedidos == 0) {
            return TipoPDF.SIN_CLIENTE;
        } else if ((cantidadPedidos == 1 || cantidadPedidos == 2) && contieneCuit) {
            // Un PDF con 1 o 2 menciones de "pedido" y "cuit" suele ser de cliente único
            return TipoPDF.CLIENTE_UNICO;
        } else {
            // Más de dos menciones de "pedido" sugiere múltiples clientes
            return TipoPDF.MULTIPLES_CLIENTES;
        }
    }


    /**
     * Objetivo del método: Procesar el texto de un PDF con múltiples clientes,
     * extrayendo productos y el valor numérico del total de cada pedido.
     */
    private static List<PedidoModel> parseConClientes(String texto) {
        List<PedidoModel> pedidos = new ArrayList<>();
        String[] lineas = texto.split("\\r?\\n");

        // Inicialización de variables de control
        String totalActual = "0.00";
        String clienteActual = null;
        List<ProductoModel> productosActuales = new ArrayList<>();

        // Bucle para recorrer cada línea del texto extraído
        for (String linea : lineas) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;

            // 1. Detección de cambio de cliente
            if (linea.contains("PEDIDO:")) {
                if (clienteActual != null && !productosActuales.isEmpty()) {
                    // Se agrega el pedido anterior incluyendo el total capturado
                    pedidos.add(new PedidoModel(clienteActual, productosActuales, totalActual));

                    // Reinicio para el nuevo cliente
                    productosActuales = new ArrayList<>();
                    totalActual = "0.00";
                }
                clienteActual = linea.split("PEDIDO:")[0].trim();
                Log.d(TAG, "DEBUG_PARSE_MULTI: Nuevo cliente detectado: " + clienteActual);
                continue;
            }

            // 2. EXTRACCIÓN DEL TOTAL (Agregado aquí para que 'linea' sea reconocida)
            if (linea.toLowerCase().contains("total")) {
                // Pattern para capturar números con formato moneda tras la palabra "total"
                Pattern pTotal = Pattern.compile("(?i)total[:\\s]*([\\d.,]+)");
                Matcher mTotal = pTotal.matcher(linea);

                if (mTotal.find()) {
                    // Asignamos el valor numérico a nuestra variable temporal
                    totalActual = mTotal.group(1).trim();
                    Log.d(TAG, "Total extraído para " + clienteActual + ": " + totalActual);
                    // No usamos continue para permitir que la lógica de productos revise la línea si fuera necesario
                }
            }

            // 3. Procesamiento de productos (Lógica original)
            ProductoModel producto = parseProductoConCliente(linea);
            if (producto != null) {
                productosActuales.add(producto);
                Log.d(TAG, "DEBUG_PARSE_MULTI: Producto añadido: " + producto.getDescripcion());
            }
        } // <-- Aquí termina el bucle for

        // 4. Guardado del último cliente procesado al finalizar el texto
        if (clienteActual != null && !productosActuales.isEmpty()) {
            pedidos.add(new PedidoModel(clienteActual, productosActuales, totalActual));
        }

        return pedidos;
    }


    private static List<PedidoModel> parseSinCliente(String texto) {
        List<ProductoModel> productos = new ArrayList<>();
        String[] lineas = texto.split("\\r?\\n");

        for (String linea : lineas) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;

            ProductoModel producto = parseProductoSinCliente(linea); // Usa ProductoModel
            if (producto != null) {
                productos.add(producto);
                Log.d(TAG, "DEBUG_PARSE_SIN: Producto añadido: " + producto.getDescripcion());
            }
        }

        List<PedidoModel> lista = new ArrayList<>();
        lista.add(new PedidoModel("", productos)); // Usa PedidoModel
        return lista;
    }

    private static ProductoModel parseProductoSinCliente(String linea) {
        String[] partes = linea.trim().split("\\s+");
        int cantidad = -1;
        boolean cantidadEncontrada = false;
        int indiceInicioDescripcion = -1; // El índice en 'partes' donde comienza la descripción

        // --- PRIMERA PASADA: Identificar la CANTIDAD ---
        // Recorremos las palabras buscando el primer número entero para la cantidad.
        for (int i = 0; i < partes.length; i++) {
            String palabra = partes[i];
            try {
                int posibleCantidad = Integer.parseInt(palabra);
                if (!cantidadEncontrada) { // Solo la primera palabra que sea un número es la cantidad.
                    cantidad = posibleCantidad;
                    cantidadEncontrada = true;
                    indiceInicioDescripcion = i + 1; // Provisionalmente, la descripción comienza después de la cantidad.
                    break; // Una vez encontrada la cantidad, salimos de este bucle.
                }
            } catch (NumberFormatException ignored) {
                // Si la primera palabra no es un número y no hemos encontrado la cantidad,
                // esta línea no cumple con el formato esperado (cantidad al inicio).
                if (!cantidadEncontrada) {
                    return null;
                }
            }
        }

        // Si no encontramos una cantidad, no podemos parsear el producto.
        if (!cantidadEncontrada) {
            return null;
        }

        // --- NUEVA LÓGICA: Saltar el "código" después de la cantidad ---
        // Si hay un elemento inmediatamente después de la cantidad y no es un precio,
        // y asumimos que es el código, lo saltamos y la descripción empieza después de él.
        if (indiceInicioDescripcion < partes.length) {
            String posibleCodigo = partes[indiceInicioDescripcion];
            // Aquí puedes definir mejor qué es un "código" a saltar.
            // Asumimos que es una única palabra inmediatamente después de la cantidad
            // que NO es un precio.
            // Si no cumple el patrón de precio, lo consideramos un código a saltar.
            if (!posibleCodigo.matches("\\d{1,3}(\\.\\d{3})*,\\d{2}")) {
                // No es un precio, entonces lo consideramos el código a saltar
                indiceInicioDescripcion++; // La descripción real comienza después de este "código".
            }
        }


        // --- SEGUNDA PASADA: Identificar el final de la DESCRIPCIÓN (donde empieza el precio) ---
        StringBuilder descripcion = new StringBuilder();
        int indiceFinDescripcion = partes.length; // Por defecto, la descripción va hasta el final de la línea.

        // Iteramos desde donde debería comenzar la descripción hasta el final de la línea.
        // Buscamos un patrón de precio para marcar el final de la descripción.
        for (int i = indiceInicioDescripcion; i < partes.length; i++) {
            String palabra = partes[i];
            // Si encontramos una palabra que coincide con el patrón de un precio (ej: 1.234,56),
            // asumimos que esta palabra y las siguientes son el precio o el final de la línea.
            if (palabra.matches("\\d{1,3}(\\.\\d{3})*,\\d{2}")) {
                indiceFinDescripcion = i; // La descripción termina JUSTO ANTES de esta palabra.
                break; // Rompemos el bucle porque ya encontramos dónde termina la descripción.
            }
        }

        // --- TERCERA PASADA: Construir la DESCRIPCIÓN ---
        // Ahora que sabemos dónde empieza (indiceInicioDescripcion) y dónde termina (indiceFinDescripcion)
        // la descripción, la construimos incluyendo TODAS las palabras en ese rango.
        for (int i = indiceInicioDescripcion; i < indiceFinDescripcion; i++) {
            descripcion.append(partes[i]).append(" ");
        }

        String descripcionFinal = descripcion.toString().trim(); // Limpiamos espacios al inicio/final.

        // Retornamos el ProductoModel si la cantidad fue encontrada y la descripción no está vacía.
        return (cantidad != -1 && !descripcionFinal.isEmpty())
                ? new ProductoModel(cantidad, descripcionFinal)
                : null;
    }
    private static ProductoModel parseProductoConCliente(String linea) {
        String[] partes = linea.trim().split("\\s+");
        int cantidad = -1;
        StringBuilder descripcion = new StringBuilder();
        boolean inicioDescripcion = false;

        for (int i = 0; i < partes.length; i++) {
            String palabra = partes[i];

            try {
                int numero = Integer.parseInt(palabra);

                // Si es el primer elemento y el siguiente también es un número, podría ser parte de un código.
                // Esta heurística es para evitar capturar códigos como cantidades.
                if (i == 0 && partes.length > i + 1 && partes[i + 1].matches("\\d+")) {
                    continue;
                }

                if (cantidad == -1) {
                    cantidad = numero;
                    continue;
                }

            } catch (NumberFormatException ignored) {
                // No es un número, continuar.
            }

            // Si la palabra es un precio (ej: 1.234,56), se asume que termina la descripción
            if (palabra.matches("\\d{1,3}(\\.\\d{3})*,\\d{2}")) break;

            // Marcar el inicio de la descripción cuando se encuentra la primera palabra con letras
            if (!inicioDescripcion && palabra.matches(".*[a-zA-ZáéíóúÁÉÍÓÚñÑ].*")) {
                inicioDescripcion = true;
            }

            if (inicioDescripcion) {
                descripcion.append(palabra).append(" ");
            }
        }

        return (cantidad != -1 && descripcion.length() > 0)
                ? new ProductoModel(cantidad, descripcion.toString().trim()) // Usa ProductoModel
                : null;
    }


    private static List<PedidoModel> parseClienteUnico(String[] lineas) {
        List<ProductoModel> productos = new ArrayList<>(); // Usa ProductoModel
        String cliente = null;

        // Patrón para "Destinatario:"
        Pattern destinatarioPattern = Pattern.compile("(?i)^\\s*destinatario\\s*:\\s*([^,]+)");

        // Patrón para detectar la CABECERA "Razón social.:" (sin asumir el nombre en la misma línea)
        Pattern razonSocialHeaderPattern = Pattern.compile("(?i)^\\s*raz[oó]n social\\s*\\.\\s*:$");

        Log.d(TAG, "--- INICIO DEPURACION CLIENTE UNICO ---");

        // PASO 1: Intentar buscar el nombre del cliente usando "Destinatario:"
        for (String linea : lineas) {
            Log.d(TAG, "DEBUG Cliente Linea (Destinatario): '" + linea.trim() + "'");
            Matcher matcher = destinatarioPattern.matcher(linea);
            if (matcher.find()) {
                cliente = matcher.group(1).trim();
                Log.d(TAG, "DEBUG Cliente (Destinatario) encontrado: '" + cliente + "'");
                break; // Cliente encontrado, salimos del bucle
            }
        }

        // PASO 2: Si no se encontró con "Destinatario:", buscar "Razón social.:" y el cliente en una línea posterior
        if (cliente == null) {
            Log.d(TAG, "DEBUG Destinatario no encontrado, buscando Razón social y cliente en línea posterior...");
            boolean encontradoRazonSocialHeader = false;
            for (int i = 0; i < lineas.length; i++) {
                String linea = lineas[i].trim();
                Log.d(TAG, "DEBUG Cliente Linea (Razón social) - Actual: '" + linea + "'");

                if (!encontradoRazonSocialHeader) {
                    Matcher matcher = razonSocialHeaderPattern.matcher(linea);
                    if (matcher.find()) {
                        encontradoRazonSocialHeader = true;
                        Log.d(TAG, "DEBUG Cabecera 'Razón social.:' encontrada. Buscando cliente en las siguientes líneas...");
                        // No rompemos aquí, continuamos para buscar el nombre en las siguientes líneas
                    }
                } else {
                    // Hemos encontrado la cabecera "Razón social.:".
                    // Ahora buscamos la primera línea no vacía que *no* termine en ':'
                    // y no sea una línea de guiones.
                    // Hemos añadido más condiciones para evitar capturar otras cabeceras.
                    if (!linea.isEmpty() &&
                            !linea.endsWith(":") && // <--- ¡CLAVE! Excluir líneas que terminan en dos puntos.
                            !linea.matches("^-+$") && // Excluir líneas de guiones (separadores)
                            !linea.toLowerCase().contains("nombre de fantasía") &&
                            !linea.toLowerCase().contains("telef./e-mail") &&
                            !linea.toLowerCase().contains("dirección") &&
                            !linea.toLowerCase().contains("próximo de") && // Excluir explícitamente esta cabecera
                            !linea.toLowerCase().contains("barrio") &&
                            !linea.toLowerCase().contains("localidad") &&
                            !linea.toLowerCase().contains("vendedor") &&
                            !linea.toLowerCase().contains("transportadora") &&
                            !linea.toLowerCase().contains("comentarios") &&
                            !linea.toLowerCase().contains("cp........") && // Añadido
                            !linea.toLowerCase().contains("cuit/dni") &&
                            !linea.toLowerCase().contains("dni/cuil") &&
                            !linea.toLowerCase().contains("emisión") &&
                            !linea.toLowerCase().contains("forma cobro") &&
                            !linea.toLowerCase().contains("cond. pago") &&
                            !linea.toLowerCase().contains("tipo flete") &&
                            !linea.toLowerCase().contains("ord.compra") &&
                            !linea.toLowerCase().contains("entrega") &&
                            !linea.toLowerCase().contains("lista") &&
                            !linea.matches("\\d{2}/\\d{2}/\\d{4}.*") // Evita capturar fechas como "Emisión..."
                    ) {
                        cliente = linea;
                        Log.d(TAG, "DEBUG Cliente (Razón social) encontrado en línea: '" + cliente + "'");
                        break; // Cliente encontrado, salimos del bucle
                    }
                }
            }
        } else {
            Log.d(TAG, "DEBUG Cliente ya encontrado por Destinatario: '" + cliente + "'");
        }
        Log.d(TAG, "--- FIN DEPURACION CLIENTE UNICO ---");

        boolean parsingProductos = false;

        for (String linea : lineas) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;

            // Activar el parsing de productos al encontrar la línea de encabezado de la tabla.
            if (linea.contains("Código") && linea.contains("Descripción") && linea.contains("Cant.") && linea.contains("Vlr. unit.")) {
                parsingProductos = true;
                Log.d(TAG, "DEBUG_PARSE_CLIENTE_UNICO: Encabezado de productos encontrado. Iniciando parseo de productos.");
                continue; // Saltar esta línea.
            }

            if (parsingProductos) {
                // Detener el parseo de productos al encontrar la línea de resumen final.
                if (linea.toLowerCase().contains("items") && linea.toLowerCase().contains("peso") && linea.toLowerCase().contains("total")) {
                    Log.d(TAG, "DEBUG_PARSE_CLIENTE_UNICO: Línea de resumen final encontrada. Deteniendo parseo de productos.");
                    break;
                }

                ProductoModel producto = parseProductoClienteUnico(linea); // Usa ProductoModel
                if (producto != null) {
                    productos.add(producto);
                    Log.d(TAG, "DEBUG_PARSE_CLIENTE_UNICO: Producto añadido: " + producto.getDescripcion());
                }
            }
        }

        List<PedidoModel> listaPedidos = new ArrayList<>(); // Usa PedidoModel
        listaPedidos.add(new PedidoModel(cliente != null ? cliente : "", productos)); // Usa PedidoModel
        return listaPedidos;
    }



    /**
     * Parsea una línea de texto para extraer la cantidad y la descripción de un producto.
     * El formato esperado de la línea es: Código Descripción Cantidad Vlr.unit. Vlr.total
     * El código (alfanumérico o numérico) es detectado pero no se incluye en el ProductoModel.
     *
     * @param linea La cadena de texto que representa una línea de producto.
     * @return Un objeto ProductoModel con la cantidad y descripción, o null si la línea no coincide con el patrón
     * o si hay errores de parseo.
     */
    public static ProductoModel parseProductoClienteUnico(String linea) {
        // Patrón para una línea de producto.
        // - ^\\s*: Coincide con el inicio de la línea y cualquier espacio en blanco inicial opcional.
        // - [a-zA-Z0-9]+: Coincide con el "Código" (uno o más caracteres alfanuméricos) pero NO LO CAPTURA.
        // - \\s+: Coincide con uno o más espacios en blanco después del código.
        // - (.+?): Captura la "Descripción" (uno o más caracteres, de forma no codiciosa). ESTE ES EL GRUPO 1.
        // - \\s+: Coincide con uno o más espacios en blanco después de la descripción.
        // - (\\d+): Captura la "Cantidad" (uno o más dígitos). ESTE ES EL GRUPO 2.
        // - \\s+([\\d.,]+): Coincide con espacios y captura el "Vlr.unit." (dígitos, puntos o comas). ESTE ES EL GRUPO 3.
        // - \\s+([\\d.,]+)$: Coincide con espacios y captura el "Vlr.total" hasta el final de la línea. ESTE ES EL GRUPO 4.
        Pattern productoPattern = Pattern.compile(
                "^\\s*[a-zA-Z0-9]+\\s+(.+?)\\s+(\\d+)\\s+([\\d.,]+)\\s+([\\d.,]+)$"
        );
        Matcher matcher = productoPattern.matcher(linea.trim()); // trim() para limpiar espacios al inicio/fin de la línea

        // Verifica si el patrón coincide y si hay al menos los 4 grupos capturados que nos interesan (desc, cant, vlr_unit, vlr_total)
        if (matcher.find() && matcher.groupCount() >= 4) {
            String descripcion = matcher.group(1).trim(); // La descripción es el primer grupo capturado
            String cantidadStr = matcher.group(2).trim(); // La cantidad es el segundo grupo capturado

            try {
                if (cantidadStr.isEmpty()) {
                    Log.w(TAG, "DEBUG_PARSE_CLIENTE_UNICO_PROD: Cantidad vacía en línea: " + linea);
                    return null;
                }
                int cantidad = Integer.parseInt(cantidadStr);

                if (!descripcion.isEmpty()) {
                    return new ProductoModel(cantidad, descripcion); // Crea y devuelve el ProductoModel
                } else {
                    Log.w(TAG, "DEBUG_PARSE_CLIENTE_UNICO_PROD: Descripción vacía en línea: " + linea);
                }
            } catch (NumberFormatException e) {
                // Captura errores si la cantidad no es un número válido
                Log.w(TAG, "DEBUG_PARSE_CLIENTE_UNICO_PROD: Error al parsear cantidad '" + cantidadStr + "' en línea: " + linea + " - " + e.getMessage());
            }
        } else {
            // Si la línea no coincide con el patrón esperado, se registra un mensaje de depuración.
            Log.d(TAG, "DEBUG_PARSE_CLIENTE_UNICO_PROD: Línea no coincide con patrón de producto: '" + linea + "'");
        }
        return null; // Devuelve null si no se pudo parsear
    }

    /**
     * Clase de utilidad de registro simple para demostración.
     * Reemplaza esto con tu sistema de logging real (ej. android.util.Log, java.util.logging.Logger, slf4j).
     */
    static class Log {
        public static void w(String tag, String msg) {
            System.err.println("WARN - " + tag + ": " + msg); // Salida a error para advertencias
        }
        public static void d(String tag, String msg) {
            System.out.println("DEBUG - " + tag + ": " + msg); // Salida estándar para depuración
        }
    }

}
