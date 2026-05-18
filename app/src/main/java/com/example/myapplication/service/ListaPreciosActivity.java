package com.example.myapplication.service;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
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
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * OBJETIVO PRINCIPAL:
 * Esta clase coordina de forma integral la pantalla de Lista de Precios de Distribuidora Godoy.
 * Habilita al usuario tanto a buscar un archivo de manera manual abriendo el explorador del teléfono,
 * como a interceptar un archivo compartido directamente desde aplicaciones externas (como WhatsApp),
 * procesando y volcando de forma inmediata el catálogo CSV en la interfaz gráfica del RecyclerView.
 */
public class ListaPreciosActivity extends AppCompatActivity {

    // Componentes gráficos vinculados al layout XML principal
    private MaterialButton btnCargarXml;
    private RecyclerView rvListaProductos;

    // Lista dinámica en memoria y su adaptador para manejar la vista de tabla
    private List<ProductoModel> listaProductos;
    private ProductoAdapter productoAdapter;

    // Lanzador registrado para la captura de archivos manuales locales
    private ActivityResultLauncher<Intent> selectorArchivoLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Inicializa el ciclo de vida básico de la actividad
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_precios);

        // Vinculación de los IDs del archivo XML
        btnCargarXml = findViewById(R.id.btn_cargar_xml);
        rvListaProductos = findViewById(R.id.rv_lista_productos);

        // Instancia del contenedor dinámico principal
        listaProductos = new ArrayList<>();

        // Configuración estructural del RecyclerView
        rvListaProductos.setLayoutManager(new LinearLayoutManager(this));
        productoAdapter = new ProductoAdapter(listaProductos);
        rvListaProductos.setAdapter(productoAdapter);

        // Inicializa el callback del explorador interno para la selección manual
        inicializarSelectorArchivo();

        // Listener del botón de extracción manual de datos
        btnCargarXml.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirSelectorDeArchivos();
            }
        });

        // Verifica de manera inmediata en el arranque si llegó un archivo mediante la acción compartir
        verificarIntentCompartido();
    }

    /**
     * Intercepta el Intent de inicio. Si fue disparado por el botón "Compartir" de WhatsApp,
     * extrae los metadatos reales del archivo para asegurar que sea un formato CSV válido y lo lee.
     */
    private void verificarIntentCompartido() {
        Intent intent = getIntent();
        String accion = intent.getAction();
        String tipoMime = intent.getType();

        // Evalúa si el disparador es una acción de envío/compartido de datos
        if (Intent.ACTION_SEND.equals(accion) && tipoMime != null) {
            Uri uriArchivoStream = intent.getParcelableExtra(Intent.EXTRA_STREAM);

            if (uriArchivoStream != null) {
                // Obtiene el nombre real del archivo usando el ContentResolver de Android
                String nombreArchivoReal = obtenerNombreDelArchivo(uriArchivoStream);

                // Validación de seguridad estricta: solo procesamos si el archivo real termina en .csv
                if (nombreArchivoReal != null && nombreArchivoReal.toLowerCase().endsWith(".csv")) {
                    // Lee el flujo físico de texto del archivo compartido
                    leerArchivoSeleccionado(uriArchivoStream);
                } else {
                    // Si el usuario intentó compartir un formato inválido, frena el flujo de forma segura
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
        // Si el esquema es de tipo contenido seguro de Android (content://)
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    // Busca el índice de la columna que contiene el nombre legible del archivo
                    int indiceNombre = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (indiceNombre != -1) {
                        resultado = cursor.getString(indiceNombre);
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        // Alternativa de respaldo por si el esquema es una ruta física de archivo clásica (file://)
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
                                // Obtiene el nombre real del archivo seleccionado (ej: Documento de Eli🌸.csv)
                                String nombreReal = obtenerNombreDelArchivo(uriArchivo);

                                // CORRECCIÓN: Filtra por extensión en caliente para garantizar compatibilidad absoluta
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

        // CORRECCIÓN DEFINITIVA: Desbloquea la selección de cualquier archivo en el explorador de carpetas
        intent.setType("*/*");

        intent.addCategory(Intent.CATEGORY_OPENABLE);
        selectorArchivoLauncher.launch(Intent.createChooser(intent, "Seleccione la Planilla CSV de Precios"));
    }

    /**
     * Abre el flujo secuencial de bytes del archivo real, decodifica el texto plano
     * renglón por renglón y consolida el string masivo para el parser.
     */
    private void leerArchivoSeleccionado(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return;

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            String linea;

            while ((linea = reader.readLine()) != null) {
                stringBuilder.append(linea).append("\n");
            }

            reader.close();
            inputStream.close();

            // Delega la cadena final al algoritmo de extracción CSV
            importarCatalogoDesdeCsv(stringBuilder.toString());

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error crítico al abrir el flujo físico del archivo", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Recorre e interpreta la estructura de la planilla compacta de Distribuidora Godoy.
     * Mapea el valor de la columna de precios directamente como String, evitando conversiones
     * matemáticas numéricas para no alterar el formato original de puntos y comas de Excel.
     */
    /**
     * MAIN OBJECTIVE:
     * Leer y procesar el contenido de texto plano de un archivo CSV para transformarlo
     * en una lista en memoria de objetos ProductoModel.
     * Utiliza una expresión regular avanzada en el trozado (split) para evitar que las
     * comas internas de los millares (ej. "2,093.00") se confundan con separadores de columna,
     * e inicializa el modelo respetando el orden exacto de su constructor.
     */
    private void importarCatalogoDesdeCsv(String csvContent) {
        try {
            // Inicializa el lector de flujo basado en la cadena de texto cruda recibida
            BufferedReader reader = new BufferedReader(new StringReader(csvContent));
            String linea;

            // Restablece por completo la colección global en memoria para realizar una carga limpia
            listaProductos.clear();

            // Guardamos el índice variable detectado dinámicamente donde se ubica el precio unitario
            int posPrecioVentaFijo = -1;

            while ((linea = reader.readLine()) != null) {

                // Ignora renglones vacíos o metadatos de encabezado ajenos a los artículos comerciales
                if (linea.trim().isEmpty() || linea.startsWith("Versión") ||
                        linea.startsWith("Representada") || linea.startsWith("Vendedor")) {
                    continue;
                }

                // DETECCIÓN Y TROZADO INTELIGENTE DE COLUMNAS:
                String[] columnas;
                if (linea.contains(";")) {
                    // Si la línea contiene punto y coma, procesa bajo el divisor regional clásico
                    columnas = linea.split(";", -1);
                } else {
                    // REGEX EXPLICACIÓN: Divide la línea usando comas, pero ignora aquellas comas
                    // que se encuentren contenidas dentro de comillas (como las comas de miles "2,093.00").
                    columnas = linea.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                }

                // DETECCIÓN DE ENCABEZADOS: Localiza la posición de la columna del precio unitario
                if (columnas.length > 0 && (columnas[0].contains("Cód. producto") || columnas[0].contains("Código"))) {
                    for (int i = 0; i < columnas.length; i++) {
                        String cabecera = columnas[i].replace("\"", "").trim();
                        if (cabecera.equalsIgnoreCase("Vlr. unit.")) {
                            posPrecioVentaFijo = i;
                            break;
                        }
                    }
                    continue; // Salta de inmediato el procesamiento de esta fila ya que no es un producto
                }

                // RESPALDO ESTRUCTURAL: Si no se detectó el encabezado, usa por defecto la columna del índice 3
                int indiceFinalAProcesar = (posPrecioVentaFijo != -1) ? posPrecioVentaFijo : 3;

                // Valida que el arreglo de columnas recuperado cuente con la longitud mínima requerida
                if (columnas.length > indiceFinalAProcesar && columnas.length >= 2) {

                    // Extrae los componentes de texto quitando comillas y espacios remanentes
                    String strCodigo = columnas[0].replace("\"", "").trim();
                    String strDescripcion = columnas[1].replace("\"", "").trim();
                    String strPrecio = columnas[indiceFinalAProcesar].replace("\"", "").trim();

                    // Omite el procesamiento si las celdas identificadoras críticas están vacías
                    if (strCodigo.isEmpty() || strPrecio.isEmpty()) {
                        continue;
                    }

                    // Ignora registros residuales en cero del sistema o descripciones inválidas
                    if (strCodigo.equals("0") || strCodigo.equalsIgnoreCase("null") || strDescripcion.isEmpty()) {
                        continue;
                    }

                    try {
                        // Convierte la cadena del código comercial a una variable de tipo entero nativo
                        int codigoProducto = Integer.parseInt(strCodigo);
                        String descProducto = strDescripcion;
                        String precioFinal = strPrecio;

                        // En la lista de precios no manejamos stock/cantidad comprada, por ende pasamos 0
                        int cantidadPorDefecto = 0;

                        // INSTANCIACIÓN EN SINTONÍA CON TU MODELO:
                        // Sigue el orden exacto de tu constructor: (cantidad, descripcion, precio, codigo)
                        listaProductos.add(new ProductoModel(cantidadPorDefecto, descProducto, precioFinal, codigoProducto));

                    } catch (NumberFormatException nfe) {
                        // Si una línea específica posee datos alfanuméricos corruptos, imprime el rastro sin detener la app
                        nfe.printStackTrace();
                    }
                }
            }

            // Cierra el lector de flujo para liberar los recursos de memoria asignados
            reader.close();

            // Notifica de forma inmediata al adaptador para redibujar el RecyclerView en la UI con los nuevos Strings
            productoAdapter.notifyDataSetChanged();

            // Despliega una notificación emergente confirmando el éxito total de la operación
            Toast.makeText(this, "Catálogo actualizado de forma idéntica al CSV.", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            // Captura fallas de lectura críticas sobre el archivo de texto
            e.printStackTrace();
            Toast.makeText(this, "Error de lectura estructural al procesar el archivo CSV.", Toast.LENGTH_SHORT).show();
        }
    }
}