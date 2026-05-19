 package com.example.myapplication.service;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.Adapters.ProductoAdapter;
import com.example.myapplication.R;
import com.example.myapplication.model.ProductoModel;
import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MAIN OBJECTIVE OF THE CLASS:
 * Orquestar la interfaz principal del catálogo de Distribuidora Godoy. Permite la importación de
 * datos desde archivos CSV (locales o compartidos por WhatsApp), clasifica de forma inteligente los
 * productos en categorías comerciales mediante sets de códigos fijos y memoria persistente (SharedPreferences),
 * renderiza el listado en un RecyclerView y exporta los resultados a dos documentos PDF independientes
 * con ordenamiento personalizado y estricto en hojas tamaño A4.
 */
public class ListaPreciosActivity extends AppCompatActivity {

    // Componentes gráficos vinculados al layout XML principal
    private MaterialButton btnCargarXml;
    private RecyclerView rvListaProductos;
    private MaterialButton btnDescargarPdf;

    // Lista dinámica en memoria y su adaptador para manejar la vista de tabla
    private List<ProductoModel> listaProductos;
    private ProductoAdapter productoAdapter;

    // Lanzador registrado para la captura de archivos manuales locales
    private ActivityResultLauncher<Intent> selectorArchivoLauncher;

    // Sets globales de códigos por proveedor para centralizar y unificar la clasificación en toda la app
    private Set<Integer> codigosPascuas;
    private Set<Integer> codigosLerithier;
    private Set<Integer> codigosNevares;
    private Set<Integer> codigosImportadora;
    private Set<Integer> codigosQuento;
    private Set<Integer> codigosAnalgesicos;
    private Set<Integer> codigosPerfumeria;
    private Set<Integer> codigosPoxipol;
    private Set<Integer> codigosPrime;
    private Set<Integer> codigosKaufer;
    private Set<Integer> codigosTrio;
    private Set<Integer> codigosCigarrillos;
    private Set<Integer> codigosVarios;
    private Set<Integer> codigosYerbas;

    // Array estático con el orden de las categorías del backend para persistencia e importación inicial
    private final String[] nombresCategorias = {
            "Pascuas", "Lerithier", "Nevares", "Importadora Sudamericana",
            "Quento", "Analgesicos", "Perfumeria", "Poxipol", "Varios",
            "Prime", "Kaufer", "Trio", "Cigarrillos", "Yerbas"
    };

    // RE-ORDENAMIENTO EXIGIDO PARA EL PDF:
    // Array estático de referencia que dicta la secuencia numérica exacta de renderizado en el catálogo impreso.
    private final String[] nombresCategoriasPdf = {
            "Lerithier", "Nevares", "Importadora Sudamericana", "Quento", "Analgesicos",
            "Perfumeria", "Poxipol", "Prime", "Kaufer", "Trio", "Varios", "Yerbas", "Cigarrillos"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Inicializa el ciclo de vida básico de la actividad llamando a la superclase
        super.onCreate(savedInstanceState);
        // Vincula el archivo de diseño XML correspondiente a la interfaz de la lista de precios
        setContentView(R.layout.activity_lista_precios);

        // MAIN OBJECTIVE OF THIS BLOCK: Inicializar componentes de UI, estructurar colecciones y registrar listeners.

        // Inicialización de los conjuntos de códigos numéricos fijos extraídos de las planillas comerciales
        inicializarColeccionesDeCodigos();

        // Vinculación de los componentes gráficos del archivo de layout XML
        btnCargarXml = findViewById(R.id.btn_cargar_xml);
        rvListaProductos = findViewById(R.id.rv_lista_productos);
        btnDescargarPdf = findViewById(R.id.btn_descargar_pdf);

        // Instancia el contenedor dinámico principal donde se acumularán los objetos ProductoModel
        listaProductos = new ArrayList<>();

        // Configuración estructural del RecyclerView para renderizado vertical fluido
        rvListaProductos.setLayoutManager(new LinearLayoutManager(this));
        productoAdapter = new ProductoAdapter(listaProductos);
        rvListaProductos.setAdapter(productoAdapter);

        // Inicializa el callback del explorador de almacenamiento de Android para la captura de planillas
        inicializarSelectorArchivo();

        // Listener para el botón encargado de compilar y descargar secuencialmente ambos documentos PDF
        btnDescargarPdf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Valida estrictamente que existan artículos cargados en memoria antes de disparar el pipeline gráfico
                if (listaProductos != null && !listaProductos.isEmpty()) {
                    generarYDescargarPdfLocal();
                } else {
                    Toast.makeText(ListaPreciosActivity.this, "Primero debe cargar y extraer los datos de una planilla CSV", Toast.LENGTH_LONG).show();
                }
            }
        });

        // Listener para el botón encargado de lanzar el explorador de archivos local
        btnCargarXml.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirSelectorDeArchivos(); // Invoca el Launcher con filtro plano abierto (*/*)
            }
        });

        // Intercepta de manera inmediata en el arranque si el archivo CSV ingresó redirigido desde otra app (ej: WhatsApp)
        verificarIntentCompartido();
    }

    /**
     * MAIN OBJECTIVE: Instanciar los HashSets inmutables con los códigos de barra correspondientes
     * a cada proveedor de la distribuidora para garantizar búsquedas en tiempo constante O(1).
     */
    private void inicializarColeccionesDeCodigos() {
        codigosPascuas = new HashSet<>(Arrays.asList(493, 248, 540, 474, 473, 492, 496));
        codigosLerithier = new HashSet<>(Arrays.asList(
                44, 25, 23, 416, 506, 503, 50, 51, 49, 35, 37, 36, 31, 19, 32, 449, 15, 22, 505, 17,
                412, 498, 499, 33, 34, 504, 507, 451, 502, 29, 26, 27, 14, 28, 410, 414, 447, 448,
                400, 42, 358, 18, 16, 40, 508, 510, 450, 403, 20, 30, 24, 46, 47, 45, 501, 409, 500, 21, 43, 41
        ));
        codigosNevares = new HashSet<>(Arrays.asList(
                154, 153, 152, 167, 166, 170, 169, 150, 151, 168, 164, 165, 537, 538, 161, 160, 159, 155,
                375, 376, 377, 378, 156, 539, 163, 162, 149, 374, 158, 453, 435, 434, 157, 379, 380, 436,
                454, 147, 148, 417, 404
        ));
        codigosImportadora = new HashSet<>(Arrays.asList(
                104, 103, 105, 106, 424, 138, 102, 425, 114, 252, 95, 457, 111, 112, 110, 497, 490, 491,
                99, 100, 101, 98, 113, 109, 423, 142, 136, 135, 418, 463, 422, 421, 82, 85, 86, 228,
                535, 432, 462, 120, 97, 223, 143, 129, 125, 128, 124, 127, 126, 420, 92, 90, 91, 89,
                132, 134, 133, 131, 130, 115, 458, 464, 225, 227, 226, 218, 419, 217, 94, 93, 108, 96,
                216, 139, 140, 461, 215, 117, 119, 399, 81, 431, 107, 118, 116, 121, 122, 123
        ));
        codigosQuento = new HashSet<>(Arrays.asList(
                534, 331, 327, 319, 320, 323, 322, 315, 317, 314, 326, 316, 325, 318, 330, 321, 324, 328,
                356, 395, 394, 396, 397, 445, 354, 382, 384, 383, 329, 402, 489
        ));
        codigosAnalgesicos = new HashSet<>(Arrays.asList(
                271, 272, 275, 353, 273, 274, 276, 388, 277, 278, 513, 279, 281, 280, 446, 284, 515, 283,
                533, 286, 287, 285, 430, 289, 291, 292, 290, 470, 293, 294, 295, 296, 297, 303, 298, 299,
                300, 301, 304, 306, 305, 467, 308, 469, 495, 310, 307, 517, 288
        ));
        codigosPerfumeria = new HashSet<>(Arrays.asList(
                429, 254, 255, 266, 257, 258, 259, 260, 263, 261, 256, 411, 253
        ));
        codigosPoxipol = new HashSet<>(Arrays.asList(
                200, 196, 187, 188, 186, 185, 184, 189, 192, 193, 190, 191, 199, 194, 195, 198, 197
        ));
        codigosPrime = new HashSet<>(Arrays.asList(
                74, 401, 60, 64, 55, 70, 73, 71, 72, 62, 68, 67, 63, 65, 58, 56, 57, 66, 59, 61, 213, 212, 69, 80, 214, 79, 78
        ));
        codigosKaufer = new HashSet<>(Arrays.asList(
                175, 176, 174, 173, 179, 178, 177, 180, 181, 398
        ));
        codigosTrio = new HashSet<>(Arrays.asList(
                520, 519, 441, 443, 523, 524, 522, 525, 442, 518, 521, 440, 444
        ));
        codigosCigarrillos = new HashSet<>(Arrays.asList(
                337, 339, 338, 334, 335, 336, 10, 8, 210, 211, 6, 352, 465, 343, 350, 349, 386, 345,
                347, 348, 466, 344, 1, 536, 7, 3, 4, 385, 439, 428, 221, 433, 205, 203, 351, 342,
                346, 340, 341, 437, 9, 5, 2, 206, 220, 219, 208, 207
        ));
        codigosVarios = new HashSet<>(Arrays.asList(
                233, 230, 229, 239, 240, 232, 231, 355, 531, 512, 511, 381, 532, 526, 530, 527,
                528, 529, 426, 427, 247, 494, 235, 238, 236, 237, 251, 234, 472, 471, 224, 242,
                509, 222, 357, 246, 514, 438, 415
        ));
        codigosYerbas = new HashSet<>(Arrays.asList(
                405, 413, 87, 408, 407, 368, 406, 452, 516
        ));
    }

    /**
     * MAIN OBJECTIVE: Centralizar la verificación posicional y analítica de un código de producto.
     * Evalúa si pertenece a un Set estático predefinido y devuelve el nombre de la categoría exacta.
     */
    private String obtenerCategoriaPorCodigo(int codigo) {
        if (codigosPascuas.contains(codigo)) return "Pascuas";
        if (codigosLerithier.contains(codigo)) return "Lerithier";
        if (codigosNevares.contains(codigo)) return "Nevares";
        if (codigosImportadora.contains(codigo)) return "Importadora Sudamericana";
        if (codigosQuento.contains(codigo)) return "Quento";
        if (codigosAnalgesicos.contains(codigo)) return "Analgesicos";
        if (codigosPerfumeria.contains(codigo)) return "Perfumeria";
        if (codigosPoxipol.contains(codigo)) return "Poxipol";
        if (codigosPrime.contains(codigo)) return "Prime";
        if (codigosKaufer.contains(codigo)) return "Kaufer";
        if (codigosTrio.contains(codigo)) return "Trio";
        if (codigosCigarrillos.contains(codigo)) return "Cigarrillos";
        if (codigosVarios.contains(codigo)) return "Varios";
        if (codigosYerbas.contains(codigo)) return "Yerbas";
        return ""; // Retorna vacío si el código es completamente inédito
    }

    /**
     * MAIN OBJECTIVE: Realizar una limpieza final preventiva sobre la descripción del artículo,
     * normalizando formatos de comillas comerciales y asegurando la estabilidad del texto.
     */
    private String desinfectarTextoOcr(String texto) {
        if (texto == null) return "";

        return texto
                // Normaliza comillas dobles especiales que suelen romper la estética del catálogo
                .replace("â€œ", "\"")
                .replace("â€\u009d", "\"")
                .replace("\"", "") // Quita comillas remanentes para que no ensucien el lienzo del PDF
                .trim();
    }
    /**
     * Intercepta el Intent de inicio. Si fue disparado por el botón "Compartir" de WhatsApp,
     * extrae los metadatos reales del archivo para asegurar que sea un formato CSV válido y lo lee.
     */
    private void verificarIntentCompartido() {
        Intent intent = getIntent();
        String accion = intent.getAction();
        String tipoMime = intent.getType();

        if (Intent.ACTION_SEND.equals(accion) && tipoMime != null) {
            Uri uriArchivoStream = intent.getParcelableExtra(Intent.EXTRA_STREAM);

            if (uriArchivoStream != null) {
                String nombreArchivoReal = obtenerNombreDelArchivo(uriArchivoStream);

                if (nombreArchivoReal != null && nombreArchivoReal.toLowerCase().endsWith(".csv")) {
                    leerArchivoSeleccionado(uriArchivoStream);
                } else {
                    Toast.makeText(this, "El archivo compartido no es un archivo CSV válido.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    /**
     * Consulta los metadatos lógicos de la URI provista a través de un Cursor,
     * aislando el nombre original con el que se identificó el archivo en el sistema.
     */
    private String obtenerNombreDelArchivo(Uri uri) {
        String resultado = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int indiceNombre = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (indiceNombre != -1) {
                        resultado = cursor.getString(indiceNombre);
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        if (resultado == null) {
            resultado = uri.getPath();
            if (resultado != null) {
                int corte = resultado.lastIndexOf('/');
                if (corte != -1) {
                    resultado = resultado.substring(corte + 1);
                }
            }
        }
        return resultado;
    }

    /**
     * Callback encargado de recibir el archivo seleccionado manualmente en la carpeta Descargas.
     * Implementa un filtro manual para validar el nombre real antes de comenzar el parseo.
     */
    private void inicializarSelectorArchivo() {
        selectorArchivoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uriArchivo = result.getData().getData();
                            if (uriArchivo != null) {
                                String nombreReal = obtenerNombreDelArchivo(uriArchivo);

                                if (nombreReal != null && nombreReal.toLowerCase().endsWith(".csv")) {
                                    leerArchivoSeleccionado(uriArchivo);
                                } else {
                                    Toast.makeText(ListaPreciosActivity.this, "Por favor, seleccione un archivo con extensión .csv", Toast.LENGTH_LONG).show();
                                }
                            }
                        } else {
                            Toast.makeText(ListaPreciosActivity.this, "Selección cancelada", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    private void abrirSelectorDeArchivos() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        selectorArchivoLauncher.launch(Intent.createChooser(intent, "Seleccione la Planilla CSV de Precios"));
    }

    /**
     * MAIN OBJECTIVE: Abre el flujo secuencial de bytes del archivo real, aplicando codificación
     * ISO-8859-1 (Windows Latin 1) para interpretar correctamente los archivos exportados por Excel
     * y asegurar que las tildes y eñes se lean de forma nativa sin romperse.
     */
    private void leerArchivoSeleccionado(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return;

            // CAMBIO CLAVE: Cambiamos UTF_8 por "ISO-8859-1". Esto lee de forma directa y nativa
            // las planillas generadas por Excel en español con sus tildes y eñes perfectas.
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "ISO-8859-1"));
            StringBuilder stringBuilder = new StringBuilder();
            String linea;

            while ((linea = reader.readLine()) != null) {
                stringBuilder.append(linea).append("\n");
            }

            reader.close();
            inputStream.close();

            // Delega la cadena con las tildes ya perfectamente interpretadas al algoritmo de extracción
            importarCatalogoDesdeCsv(stringBuilder.toString());

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error crítico al abrir el flujo físico del archivo", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * MAIN OBJECTIVE:
     * Procesar el catálogo de Distribuidora Godoy clasificando los artículos mediante las listas
     * de códigos, sanitizando caracteres rotos (tildes/eñes) e insertando de forma secuencial
     * y ordenada los elementos para la UI del RecyclerView.
     */
    private void importarCatalogoDesdeCsv(String csvContent) {
        try {
            // Inicializa el lector de cadenas en memoria para procesar línea por línea
            BufferedReader reader = new BufferedReader(new StringReader(csvContent));
            String linea;

            // Limpia la lista global antes de la nueva importación para evitar duplicados
            listaProductos.clear();

            // Estructura el mapa temporal respetando el orden secuencial de las categorías
            Map<String, List<ProductoModel>> mapaCategorias = new LinkedHashMap<>();
            for (String cat : nombresCategorias) {
                mapaCategorias.put(cat, new ArrayList<ProductoModel>());
            }

            // Abre el archivo de preferencias persistentes para recuperar clasificaciones manuales previas
            SharedPreferences memoriaCategorias = getSharedPreferences("MemoriaCodigosGodoy", MODE_PRIVATE);

            // Variable de control para detectar dinámicamente la columna del precio mayorista
            int posPrecioVentaFijo = -1;

            // Bucle principal de lectura del archivo CSV
            while ((linea = reader.readLine()) != null) {

                // Descarta renglones vacíos o encabezados institucionales de metadatos de la planilla
                if (linea.trim().isEmpty() || linea.startsWith("Versión") ||
                        linea.startsWith("Representada") || linea.startsWith("Vendedor")) {
                    continue;
                }

                // Identifica el divisor de columnas: prioriza punto y coma, o procesa comas respetando celdas entrecomilladas
                String[] columnas;
                if (linea.contains(";")) {
                    columnas = linea.split(";", -1);
                } else {
                    columnas = linea.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                }

                // Detecta la fila de títulos para encontrar el índice exacto de la columna "Vlr. unit."
                if (columnas.length > 0 && (columnas[0].contains("Cód. producto") || columnas[0].contains("Código"))) {
                    for (int i = 0; i < columnas.length; i++) {
                        String cabecera = columnas[i].replace("\"", "").trim();
                        if (cabecera.equalsIgnoreCase("Vlr. unit.")) {
                            posPrecioVentaFijo = i;
                            break;
                        }
                    }
                    continue; // Salta a la siguiente línea ya que esta fue solo de configuración estructural
                }

                // Determina el índice de lectura del precio: usa el dinámico encontrado o el valor por defecto (columna 3)
                int indiceFinalAProcesar = (posPrecioVentaFijo != -1) ? posPrecioVentaFijo : 3;

                // Valida que la fila tenga las columnas suficientes para extraer los datos básicos
                if (columnas.length > indiceFinalAProcesar && columnas.length >= 2) {

                    // Aísla y limpia las comillas y espacios de los tres campos requeridos
                    String strCodigo = columnas[0].replace("\"", "").trim();
                    String strDescripcion = columnas[1].replace("\"", "").trim();
                    String strPrecio = columnas[indiceFinalAProcesar].replace("\"", "").trim();

                    // Ignora celdas incompletas, vacías o artículos con código de control "0"
                    if (strCodigo.isEmpty() || strPrecio.isEmpty() || strCodigo.equals("0") || strDescripcion.isEmpty()) {
                        continue;
                    }

                    // CORRECCIÓN CLAVE: Sanea en caliente los símbolos rotos antes de instanciar el modelo de datos
                    strDescripcion = desinfectarTextoOcr(strDescripcion);

                    try {
                        // Convierte el string del código a un entero primitivo para las búsquedas numéricas
                        int codigoProducto = Integer.parseInt(strCodigo);
                        int cantidadPorDefecto = 0;

                        // Instancia el objeto de negocio con la descripción y codificación ya reparadas
                        ProductoModel nuevoProducto = new ProductoModel(cantidadPorDefecto, strDescripcion, strPrecio, codigoProducto);

                        String categoriaDestino = "";

                        // Determina el rubro comercial consultando la memoria de SharedPreferences o los sets estáticos fijos
                        if (memoriaCategorias.contains(String.valueOf(codigoProducto))) {
                            categoriaDestino = memoriaCategorias.getString(String.valueOf(codigoProducto), "");
                        } else {
                            categoriaDestino = obtenerCategoriaPorCodigo(codigoProducto);
                        }

                        // Si se identificó la categoría, se añade el producto a su grupo; si no, se le pregunta al usuario
                        if (!categoriaDestino.isEmpty() && mapaCategorias.containsKey(categoriaDestino)) {
                            mapaCategorias.get(categoriaDestino).add(nuevoProducto);
                        } else {
                            solicitarCategoriaAlUsuario(nuevoProducto, nombresCategorias, mapaCategorias, memoriaCategorias);
                        }

                    } catch (NumberFormatException nfe) {
                        // Captura errores si el código extraído de la columna no es un número válido
                        nfe.printStackTrace();
                    }
                }
            }

            // Cierra el flujo del lector en memoria
            reader.close();

            // Vuelca de manera secuencial los productos agrupados del mapa a la lista principal de la UI
            for (String cabecera : nombresCategorias) {
                List<ProductoModel> articulosDeSeccion = mapaCategorias.get(cabecera);
                if (articulosDeSeccion != null && !articulosDeSeccion.isEmpty()) {
                    listaProductos.addAll(articulosDeSeccion);
                }
            }

            // Notifica los cambios estructurales al adaptador para refrescar inmediatamente el RecyclerView
            productoAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Catálogo importado y clasificado con éxito.", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            // Captura fallas de lectura o corrupción en el flujo de datos del string
            e.printStackTrace();
            Toast.makeText(this, "Falla crítica al interpretar el archivo CSV.", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * MAIN OBJECTIVE: Mostrar un diálogo interactivo tipo lista en el hilo de la interfaz de usuario (UI Thread)
     * para que el preventista asigne un código de producto nuevo a una categoría existente.
     */
    private void solicitarCategoriaAlUsuario(final ProductoModel producto, final String[] categorias,
                                             final Map<String, List<ProductoModel>> mapa, final SharedPreferences preferencias) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder constructorDialogo = new AlertDialog.Builder(ListaPreciosActivity.this);
                constructorDialogo.setTitle("Asignar: " + producto.getDescripcion() + " (" + producto.getCodigo() + ")");

                constructorDialogo.setItems(categorias, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int indexSeleccionado) {
                        String categoriaElegida = categorias[indexSeleccionado];

                        SharedPreferences.Editor editor = preferencias.edit();
                        editor.putString(String.valueOf(producto.getCodigo()), categoriaElegida);
                        editor.apply();

                        if (mapa.containsKey(categoriaElegida)) {
                            mapa.get(categoriaElegida).add(producto);
                        }

                        listaProductos.add(producto);
                        productoAdapter.notifyDataSetChanged();
                    }
                });

                constructorDialogo.show();
            }
        });
    }

    /**
     * MAIN OBJECTIVE OF THE METHOD:
     * Orquestar la generación secuencial de los dos archivos PDF requeridos por el negocio.
     * Invoca de forma consecutiva la rutina de dibujo gráfico para exportar el catálogo completo
     * y, de inmediato, el catálogo selectivo que excluye el rubro de cigarrillos.
     */
    private void generarYDescargarPdfLocal() {
        // 1. Genera el primer documento PDF que incluye todas las categorías comerciales configuradas
        construirArchivoPdfGenuino("Lista_Precios_Distribuidora_Godoy.pdf", true);

        // 2. Genera el segundo documento PDF omitiendo rigurosamente la categoría de Cigarrillos
        construirArchivoPdfGenuino("Lista_Precios_Distribuidora_Godoy_SinCig.pdf", false);
    }

    /**
     * MAIN OBJECTIVE:
     * Renderizar el catálogo sobre el Canvas incrementando el tamaño tipográfico de los artículos
     * y compactando el espacio vertical entre filas para que la descripción y el precio se visualicen
     * más grandes, juntos y alineados simétricamente en una hoja A4.
     */
    private void construirArchivoPdfGenuino(String nombreArchivo, boolean incluirCigarrillos) {
        // 1. Instancia el orquestador nativo para la composición y renderizado de páginas PDF
        PdfDocument documento = new PdfDocument();

        // 2. Definición y configuración estética de pinceles gráficos (Paint)
        Paint pincelTitulo = new Paint();
        pincelTitulo.setColor(Color.parseColor("#1A365D")); // Tono azul oscuro corporativo
        pincelTitulo.setTextSize(22f);
        pincelTitulo.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        Paint pincelCategoria = new Paint();
        pincelCategoria.setColor(Color.parseColor("#2B6CB0")); // Azul medio para identificar secciones
        pincelCategoria.setTextSize(13f);
        pincelCategoria.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        // CORRECCIÓN: Se aumentó el tamaño de 9.5f a 12f para que la descripción se vea más grande
        Paint pincelTexto = new Paint();
        pincelTexto.setColor(Color.parseColor("#2D3748")); // Gris oscuro de alta densidad
        pincelTexto.setTextSize(12f);

        // CORRECCIÓN: Se aumentó el tamaño de 9.5f a 12f para emparejarlo con la descripción
        Paint pincelPrecio = new Paint();
        pincelPrecio.setColor(Color.parseColor("#2B6CB0")); // Azul comercial
        pincelPrecio.setTextSize(12f);
        pincelPrecio.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        pincelPrecio.setTextAlign(Paint.Align.RIGHT); // Alineación a la derecha matemática

        Paint pincelLinea = new Paint();
        pincelLinea.setColor(Color.parseColor("#E2E8F0")); // Gris tenue para separación simétrica
        pincelLinea.setStrokeWidth(0.8f);

        // 3. Parámetros de estructuración física de la hoja A4 (72 DPI estándar de impresión)
        int anchoPagina = 595;
        int altoPagina = 842;
        int numeroPagina = 1;

        // 4. Apertura y arranque de la primera página del documento
        PdfDocument.PageInfo configuracionPagina = new PdfDocument.PageInfo.Builder(anchoPagina, altoPagina, numeroPagina).create();
        PdfDocument.Page paginaActual = documento.startPage(configuracionPagina);
        Canvas lienzo = paginaActual.getCanvas();

        // 5. Estampado inicial de cabecera institucional estática
        int coordenadaY = 50;
        lienzo.drawText("DISTRIBUIDORA GODOY", 40, coordenadaY, pincelTitulo);
        coordenadaY += 20;

        Paint pincelSub = new Paint();
        pincelSub.setColor(Color.parseColor("#718096"));
        pincelSub.setTextSize(9.5f);
        pincelSub.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        lienzo.drawText("Lista de Precios Oficial — Organizada por Categorías", 40, coordenadaY, pincelSub);
        coordenadaY += 40;

        // 6. MAPEO Y AGRUPACIÓN: Prepara la estructura respetando el orden solicitado para el PDF
        Map<String, List<ProductoModel>> mapaAgrupado = new LinkedHashMap<>();
        for (String cat : nombresCategoriasPdf) {
            if (!incluirCigarrillos && cat.equalsIgnoreCase("Cigarrillos")) {
                continue;
            }
            mapaAgrupado.put(cat, new ArrayList<ProductoModel>());
        }

        // Recuperamos la memoria guardada en SharedPreferences por si hay algún mapeo manual aprendido
        SharedPreferences memoriaCategorias = getSharedPreferences("MemoriaCodigosGodoy", MODE_PRIVATE);

        // Agrupa cada artículo de la lista actual usando la lógica unificada de códigos
        for (ProductoModel p : listaProductos) {
            String destino = "";
            if (memoriaCategorias.contains(String.valueOf(p.getCodigo()))) {
                destino = memoriaCategorias.getString(String.valueOf(p.getCodigo()), "");
            } else {
                destino = obtenerCategoriaPorCodigo(p.getCodigo());
            }

            if (destino.isEmpty()) {
                destino = "Varios";
            }

            if (!incluirCigarrillos && destino.equalsIgnoreCase("Cigarrillos")) {
                continue;
            }

            if (!mapaAgrupado.containsKey(destino)) {
                destino = "Varios";
            }

            mapaAgrupado.get(destino).add(p);
        }

        // 7. BUCLE GRÁFICO DE RENDERIZADO: Recorre e imprime los bloques sobre el lienzo
        for (String rubro : nombresCategoriasPdf) {
            if (!mapaAgrupado.containsKey(rubro)) {
                continue;
            }

            List<ProductoModel> productosDeCategoria = mapaAgrupado.get(rubro);

            if (productosDeCategoria == null || productosDeCategoria.isEmpty()) {
                continue;
            }

            // Validación de salto preventivo antes de imprimir la etiqueta del rubro
            if (coordenadaY > altoPagina - 60) {
                documento.finishPage(paginaActual);
                numeroPagina++;
                configuracionPagina = new PdfDocument.PageInfo.Builder(anchoPagina, altoPagina, numeroPagina).create();
                paginaActual = documento.startPage(configuracionPagina);
                lienzo = paginaActual.getCanvas();
                coordenadaY = 50;
            }

            // Dibuja el banner del rubro comercial en la hoja
            coordenadaY += 15;
            lienzo.drawText("■ " + rubro.toUpperCase(), 40, coordenadaY, pincelCategoria);
            coordenadaY += 20;

            // Renderiza cada artículo individual perteneciente al bloque actual
            for (ProductoModel prod : productosDeCategoria) {
                // Control estricto de fin de página vertical adaptado al nuevo tamaño de letra
                if (coordenadaY > altoPagina - 40) {
                    documento.finishPage(paginaActual);
                    numeroPagina++;
                    configuracionPagina = new PdfDocument.PageInfo.Builder(anchoPagina, altoPagina, numeroPagina).create();
                    paginaActual = documento.startPage(configuracionPagina);
                    lienzo = paginaActual.getCanvas();
                    coordenadaY = 60; // Margen de gracia superior
                }

                // Imprime la descripción y el precio compartiendo la misma coordenada Y (misma línea horizontal)
                String lineaArticulo = prod.getCodigo() + " - " + prod.getDescripcion();
                lienzo.drawText(lineaArticulo, 45, coordenadaY, pincelTexto);
                lienzo.drawText("$ " + prod.getPrecio(), anchoPagina - 45, coordenadaY, pincelPrecio);

// Traza la sutil línea horizontal justo debajo del texto
                coordenadaY += 4;
                lienzo.drawLine(40, coordenadaY, anchoPagina - 40, coordenadaY, pincelLinea);

// CORRECCIÓN: Se redujo el incremento de 14 a 11 para compactar las filas y juntar más los renglones
                coordenadaY += 11;
            }
            coordenadaY += 10; // Espaciado entre categorías
        }

        // 8. CIERRE LOGÍSTICO Y ESCRITURA EN DISCO FÍSICO
        documento.finishPage(paginaActual);

        File rutaDescargas = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File archivoPdfFinal = new File(rutaDescargas, nombreArchivo);

        try {
            documento.writeTo(new FileOutputStream(archivoPdfFinal));
            Toast.makeText(this, "Descargado: " + nombreArchivo, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error de escritura en: " + nombreArchivo, Toast.LENGTH_SHORT).show();
        } finally {
            documento.close(); // Libera la memoria nativa del documento
        }
    }
}

