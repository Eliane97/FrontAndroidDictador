package com.example.myapplication.service;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.FileProvider;

import com.example.myapplication.R;
import com.example.myapplication.model.PedidoModel;
import com.example.myapplication.model.ProductoModel;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Pedidos extends AppCompatActivity {

    private static final String TAG = "PedidosActivity";
    private static final int REQUEST_READ_STORAGE = 777;

    // --- VISTAS DE LA UI ---
    private View viewOverlay;
    private ImageButton btnBack;
    private TextView tvPlayingNow;
    private ImageButton btnImport;
    private ImageButton btnMoreOptions;

    private FrameLayout artistImageContainer;
    private ImageView artistImage;
    private ImageView imageView3;
    private ImageButton btnVerLista; // Declarar junto a los demás ImageButton

    private TextView nombreCliente;
    private TextView textoCliente;
    private ImageButton btnShuffle;
    private ImageButton btnFaltante;
    private final List<ProductoModel> listaFaltantes = new ArrayList<>();

    private LinearLayout controlButtonsLayout;
    private ImageButton btnPrevClient;  // <<
    private ImageButton btnPrevious;    // <
    private ImageButton btnPlayPause;   // Play/Pause
    private ImageButton btnNext;        // >
    private ImageButton btnNextClient;  // >>

    // --- LAUNCHERS Y SERVICIOS ---
    private ActivityResultLauncher<String> selectPdfLauncher;
    private TextToSpeech textToSpeech;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // --- DATOS DEL PEDIDO ---
    private List<PedidoModel> currentPedidosList;
    private int currentPedidoIndex = 0;
    private int currentProductoIndex = 0;

    // --- IDS PARA TEXT TO SPEECH ---
    private static final String UTTERANCE_ID_CLIENTE = "cliente_name_utterance";
    private static final String UTTERANCE_ID_ITEM = "item_utterance";
    private static final String UTTERANCE_ID_FINAL_LIST = "final_list_utterance";
    private static final String UTTERANCE_ID_ERROR = "error_utterance";
    private static final String UTTERANCE_ID_GENERAL_MESSAGE = "general_message_utterance";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pedidos);

        // Inicializar PDFBox para Android
        PDFBoxResourceLoader.init(getApplicationContext());
        Log.d(TAG, "PDFBox inicializado correctamente");

        // --- 1. Inicialización de Vistas ---
        viewOverlay = findViewById(R.id.view2);
        btnBack = findViewById(R.id.btn_back);
        tvPlayingNow = findViewById(R.id.tv_playing_now);
        btnImport = findViewById(R.id.btn_import);
        btnMoreOptions = findViewById(R.id.btn_more_options);

        artistImageContainer = findViewById(R.id.artist_image_container);
        artistImage = findViewById(R.id.artist_image);
        imageView3 = findViewById(R.id.imageView3);

        nombreCliente = findViewById(R.id.nombreCliente);
        textoCliente = findViewById(R.id.textoCliente);

        controlButtonsLayout = findViewById(R.id.control_buttons_layout);
        btnPrevClient = findViewById(R.id.btn_prev_client);
        btnPrevious = findViewById(R.id.btn_previous);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnNext = findViewById(R.id.btn_next);
        btnNextClient = findViewById(R.id.btn_next_client);
        // Dentro de onCreate(), en la sección de inicialización de vistas:
        btnVerLista = findViewById(R.id.btn_ver_lista);
        btnFaltante = findViewById(R.id.btn_faltante);
        btnFaltante.setOnClickListener(v -> mostrarDialogoRegistrarFaltante());



        // --- 2. Launcher para Seleccionar PDF ---
        selectPdfLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        processPdfLocally(uri);
                    } else {
                        Log.d(TAG, "No se seleccionó ningún archivo.");
                        speakText("No se seleccionó ningún archivo PDF.", UTTERANCE_ID_GENERAL_MESSAGE);
                        nombreCliente.setText("Selección cancelada");
                        textoCliente.setText("");
                    }
                }
        );

        // --- 3. Inicialización de TextToSpeech ---
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("es", "ES"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Idioma no soportado. Redirigiendo a instalación.");
                    Intent installIntent = new Intent();
                    installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    startActivity(installIntent);
                } else {
                    Log.d(TAG, "TextToSpeech inicializado con éxito.");
                }
            } else {
                Log.e(TAG, "Error al inicializar TextToSpeech. Código: " + status);
                speakText("Error al inicializar el motor de voz.", UTTERANCE_ID_ERROR);
                nombreCliente.setText("Error TTS");
                textoCliente.setText("Revisar motor de voz.");
            }
        });

        // Eventos del reproductor de voz
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "TTS Inicio: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "TTS Fin: " + utteranceId);
                // Si terminó de decir el nombre del cliente, pasamos automáticamente al primer producto
                if (UTTERANCE_ID_CLIENTE.equals(utteranceId)) {
                    runOnUiThread(() -> displayAndSpeakCurrentItem());
                }
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS Error: " + utteranceId);
            }

            @Override
            public void onStop(String utteranceId, boolean interrupted) {
                Log.d(TAG, "TTS Detenido: " + utteranceId);
            }
        });

        // --- 4. Listeners para Botones ---
        btnMoreOptions.setOnClickListener(v -> mostrarMenu(v));
        btnBack.setOnClickListener(v -> onBackPressed());
        // En la sección de Listeners:
        btnVerLista.setOnClickListener(v -> mostrarDialogoListaProductos());

        btnImport.setOnClickListener(v -> {
            speakText("Seleccionando archivo PDF.", UTTERANCE_ID_GENERAL_MESSAGE);
            nombreCliente.setText("Seleccionando PDF...");
            textoCliente.setText("");
            selectPdfLauncher.launch("application/pdf");
        });

        btnPlayPause.setOnClickListener(v -> {
            if (btnPlayPause.getTag() == null || btnPlayPause.getTag().equals("paused")) {
                btnPlayPause.setImageResource(R.drawable.ic_pause);
                btnPlayPause.setTag("playing");
                if (currentPedidosList != null && !currentPedidosList.isEmpty()) {
                    String currentClientName = currentPedidosList.get(currentPedidoIndex).getCliente();
                    speakText("Pedido para el cliente: " + currentClientName + ".", UTTERANCE_ID_CLIENTE);
                } else {
                    speakText("Cargue un documento para comenzar.", UTTERANCE_ID_GENERAL_MESSAGE);
                }
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_play);
                btnPlayPause.setTag("paused");
                if (textToSpeech != null && textToSpeech.isSpeaking()) {
                    textToSpeech.stop();
                }
            }
        });

        // Navegación Ítem por Ítem
        btnNext.setOnClickListener(v -> navegarSiguienteItem());
        btnPrevious.setOnClickListener(v -> navegarItemAnterior());

        // Navegación Cliente por Cliente
        btnNextClient.setOnClickListener(v -> saltarSiguienteCliente());
        btnPrevClient.setOnClickListener(v -> saltarClienteAnterior());




        // Deshabilitar botones inicialmente
        updateNavigationButtonsState();
    }

    // --- MÉTODOS DE NAVEGACIÓN ENTRE PRODUCTOS ---

    private void navegarSiguienteItem() {
        if (currentPedidosList == null || currentPedidosList.isEmpty()) return;

        PedidoModel currentPedido = currentPedidosList.get(currentPedidoIndex);
        List<ProductoModel> productos = currentPedido.getProductos();

        if (!productos.isEmpty() && currentProductoIndex < productos.size() - 1) {
            currentProductoIndex++;
            displayAndSpeakCurrentItem();
        } else if (currentPedidoIndex < currentPedidosList.size() - 1) {
            saltarAPedidoCliente(currentPedidoIndex + 1);
        } else {
            speakText("Has llegado al final de todos los pedidos.", UTTERANCE_ID_FINAL_LIST);
        }
        updateNavigationButtonsState();
    }

    private void navegarItemAnterior() {
        if (currentPedidosList == null || currentPedidosList.isEmpty()) return;

        PedidoModel currentPedido = currentPedidosList.get(currentPedidoIndex);
        List<ProductoModel> productos = currentPedido.getProductos();

        if (!productos.isEmpty() && currentProductoIndex > 0) {
            currentProductoIndex--;
            displayAndSpeakCurrentItem();
        } else if (currentPedidoIndex > 0) {
            currentPedidoIndex--;
            PedidoModel prevPedido = currentPedidosList.get(currentPedidoIndex);
            currentProductoIndex = prevPedido.getProductos().isEmpty() ? 0 : prevPedido.getProductos().size() - 1;

            String cliente = prevPedido.getCliente();
            speakText("Pedido anterior para el cliente: " + (cliente != null ? cliente : "Sin nombre") + ".", UTTERANCE_ID_CLIENTE);
        } else {
            speakText("Has llegado al principio de todos los pedidos.", UTTERANCE_ID_FINAL_LIST);
        }
        updateNavigationButtonsState();
    }

    // --- MÉTODOS DE NAVEGACIÓN ENTRE CLIENTES ---

    private void saltarSiguienteCliente() {
        if (currentPedidosList == null || currentPedidosList.isEmpty()) return;

        if (currentPedidoIndex < currentPedidosList.size() - 1) {
            saltarAPedidoCliente(currentPedidoIndex + 1);
        } else {
            speakText("Ya estás en el último cliente.", UTTERANCE_ID_GENERAL_MESSAGE);
        }
    }

    private void saltarClienteAnterior() {
        if (currentPedidosList == null || currentPedidosList.isEmpty()) return;

        if (currentPedidoIndex > 0) {
            saltarAPedidoCliente(currentPedidoIndex - 1);
        } else {
            speakText("Ya estás en el primer cliente.", UTTERANCE_ID_GENERAL_MESSAGE);
        }
    }

    private void saltarAPedidoCliente(int pedidoIndex) {
        if (currentPedidosList == null || pedidoIndex < 0 || pedidoIndex >= currentPedidosList.size()) return;

        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }

        currentPedidoIndex = pedidoIndex;
        currentProductoIndex = 0;

        PedidoModel pedidoSeleccionado = currentPedidosList.get(currentPedidoIndex);
        String cliente = pedidoSeleccionado.getCliente();

        nombreCliente.setText(cliente != null && !cliente.isEmpty() ? cliente : "Cliente sin nombre");
        textoCliente.setText("Cargando productos...");

        speakText("Pedido para el cliente: " + (cliente != null ? cliente : "sin nombre") + ".", UTTERANCE_ID_CLIENTE);
        updateNavigationButtonsState();
    }

    // --- LÓGICA DE VISUALIZACIÓN Y HABLA ---

    private void displayAndSpeakCurrentItem() {
        if (currentPedidosList == null || currentPedidosList.isEmpty() ||
                currentPedidoIndex < 0 || currentPedidoIndex >= currentPedidosList.size()) return;

        PedidoModel currentPedido = currentPedidosList.get(currentPedidoIndex);
        String cliente = currentPedido.getCliente();
        nombreCliente.setText(cliente != null && !cliente.isEmpty() ? cliente : "Cliente sin nombre");

        List<ProductoModel> productos = currentPedido.getProductos();
        if (productos != null && !productos.isEmpty() &&
                currentProductoIndex >= 0 && currentProductoIndex < productos.size()) {

            ProductoModel producto = productos.get(currentProductoIndex);
            String textoProd = producto.getCantidad() + " - " + producto.getDescripcion();
            textoCliente.setText(textoProd);

            speakText(producto.getCantidad() + " de " + producto.getDescripcion(), UTTERANCE_ID_ITEM);
        } else {
            textoCliente.setText("Sin productos");
            speakText("Este pedido no tiene productos registrados.", UTTERANCE_ID_GENERAL_MESSAGE);
        }

        updateNavigationButtonsState();
    }

    private void speakText(String text, String utteranceId) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        }
    }

    // --- ACTUALIZACIÓN DE BOTONES (ESTADOS) ---

    private void updateNavigationButtonsState() {
        if (currentPedidosList == null || currentPedidosList.isEmpty()) {
            btnNext.setEnabled(false);
            btnPrevious.setEnabled(false);
            btnNextClient.setEnabled(false);
            btnPrevClient.setEnabled(false);
            return;
        }

        boolean canGoNextItem = currentPedidoIndex < currentPedidosList.size() - 1 ||
                currentProductoIndex < currentPedidosList.get(currentPedidoIndex).getProductos().size() - 1;

        boolean canGoPrevItem = currentPedidoIndex > 0 || currentProductoIndex > 0;

        boolean canGoNextClient = currentPedidoIndex < currentPedidosList.size() - 1;
        boolean canGoPrevClient = currentPedidoIndex > 0;

        btnNext.setEnabled(canGoNextItem);
        btnPrevious.setEnabled(canGoPrevItem);
        btnNextClient.setEnabled(canGoNextClient);
        btnPrevClient.setEnabled(canGoPrevClient);
    }

    // --- MENÚS Y DIÁLOGOS ---


    private void mostrarDialogoSeleccionarCliente() {
        if (currentPedidosList == null || currentPedidosList.isEmpty()) {
            speakText("No hay pedidos cargados para seleccionar.", UTTERANCE_ID_GENERAL_MESSAGE);
            return;
        }

        String[] nombresClientes = new String[currentPedidosList.size()];
        for (int i = 0; i < currentPedidosList.size(); i++) {
            String nombre = currentPedidosList.get(i).getCliente();
            int totalItems = currentPedidosList.get(i).getProductos().size();
            nombresClientes[i] = (i + 1) + ". " +
                    (nombre != null && !nombre.isEmpty() ? nombre : "Cliente " + (i + 1)) +
                    " (" + totalItems + " ítems)";
        }

        new AlertDialog.Builder(this)
                .setTitle("Seleccionar Cliente")
                .setItems(nombresClientes, (dialog, which) -> saltarAPedidoCliente(which))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // --- PROCESAMIENTO DE PDF EN HILO DE FONDO ---

    private void processPdfLocally(Uri pdfUri) {
        runOnUiThread(() -> {
            nombreCliente.setText("Procesando PDF...");
            textoCliente.setText("Por favor, espere.");
            updateNavigationButtonsState();
        });

        executorService.execute(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(pdfUri)) {
                if (inputStream == null) {
                    throw new IOException("No se pudo abrir el InputStream del PDF.");
                }

                final List<PedidoModel> pedidosResultantes = PDFParser.parsePedidos(inputStream);

                runOnUiThread(() -> {
                    currentPedidosList = pedidosResultantes;
                    currentPedidoIndex = 0;
                    currentProductoIndex = 0;

                    if (currentPedidosList.isEmpty()) {
                        nombreCliente.setText("Sin pedidos");
                        textoCliente.setText("Documento vacío.");
                        speakText("No se encontraron pedidos en el documento.", UTTERANCE_ID_GENERAL_MESSAGE);
                    } else {
                        PedidoModel primerPedido = currentPedidosList.get(0);
                        String primerCliente = primerPedido.getCliente();

                        if (primerCliente != null && !primerCliente.trim().isEmpty()) {
                            speakText("Pedido para el cliente: " + primerCliente + ".", UTTERANCE_ID_CLIENTE);
                        } else {
                            speakText("Primer pedido sin cliente.", UTTERANCE_ID_CLIENTE);
                        }
                    }
                    updateNavigationButtonsState();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error procesando PDF localmente", e);
                runOnUiThread(() -> {
                    nombreCliente.setText("Error PDF");
                    textoCliente.setText("No se pudo leer el archivo.");
                    speakText("Ocurrió un error al procesar el archivo PDF.", UTTERANCE_ID_ERROR);
                    updateNavigationButtonsState();
                });
            }
        });
    }
    /**
     * Despliega un diálogo visual con la lista completa de productos del cliente actual.
     * Destaca el producto en reproducción y permite saltar a cualquiera al tocarlo.
     */
    private void mostrarDialogoListaProductos() {
        if (currentPedidosList == null || currentPedidosList.isEmpty()) {
            speakText("No hay productos cargados para mostrar.", UTTERANCE_ID_GENERAL_MESSAGE);
            return;
        }

        PedidoModel pedidoActual = currentPedidosList.get(currentPedidoIndex);
        List<ProductoModel> productos = pedidoActual.getProductos();

        if (productos == null || productos.isEmpty()) {
            speakText("El cliente actual no tiene productos registrados.", UTTERANCE_ID_GENERAL_MESSAGE);
            return;
        }

        // Construir la lista visual con formato (marcando el ítem activo)
        String[] itemsProductos = new String[productos.size()];
        for (int i = 0; i < productos.size(); i++) {
            ProductoModel prod = productos.get(i);
            String marcador = (i == currentProductoIndex) ? "▶ " : "    ";
            itemsProductos[i] = marcador + (i + 1) + ". " + prod.getCantidad() + " x " + prod.getDescripcion();
        }

        String cliente = pedidoActual.getCliente();
        String titulo = "Lista: " + (cliente != null && !cliente.isEmpty() ? cliente : "Cliente actual");

        // Desplegar diálogo con selección rápida
        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setItems(itemsProductos, (dialog, which) -> {
                    // Si el usuario toca un producto, salta a ese índice y lo dicta
                    currentProductoIndex = which;
                    displayAndSpeakCurrentItem();
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }
    /**
     * Función helper para generar el PDF de faltantes en caché y devolver su Uri.
     */
    private Uri generarPDFFaltantes(List<ProductoModel> listaFaltantes) throws Exception {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);

        PDPageContentStream contentStream = new PDPageContentStream(document, page);

        // Encabezado
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18);
        contentStream.newLineAtOffset(50, 750);
        contentStream.showText(limpiarTextoParaPdf("REPORTE DE FALTANTES"));
        contentStream.endText();

        // Fecha
        String fecha = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA, 10);
        contentStream.newLineAtOffset(50, 730);
        contentStream.showText("Fecha: " + fecha);
        contentStream.endText();

        // Listado de ítems
        int y = 680;
        contentStream.setFont(PDType1Font.HELVETICA, 12);

        for (ProductoModel item : listaFaltantes) {
            if (y < 50) {
                contentStream.close();
                page = new PDPage();
                document.addPage(page);
                contentStream = new PDPageContentStream(document, page);
                y = 750;
                contentStream.setFont(PDType1Font.HELVETICA, 12);
            }

            String descLimpia = limpiarTextoParaPdf(item.getDescripcion());
            String lineaTexto = "- Faltan " + item.getCantidad() + " de " + descLimpia;

            contentStream.beginText();
            contentStream.newLineAtOffset(50, y);
            contentStream.showText(lineaTexto);
            contentStream.endText();

            y -= 25;
        }

        contentStream.close();

        // =========================================================================
        // RUTA EXACTA COMPATIBLE CON TU <external-files-path path="Download/" />
        // =========================================================================

        // 1. Obtener la carpeta privada de descargas de la App
        File pdfFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (pdfFolder != null && !pdfFolder.exists()) {
            pdfFolder.mkdirs();
        }

        // 2. Crear la referencia al archivo
        File pdfFile = new File(pdfFolder, "Faltantes_" + System.currentTimeMillis() + ".pdf");

        // 3. Guardar el PDF pasando la ruta como String (.getAbsolutePath())
        document.save(pdfFile.getAbsolutePath());
        document.close();

        // 4. Retornar la URI y finalizar el método (AQUÍ TERMINA LA FUNCIÓN)
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdfFile);
    }

        // Generar URI con tu FileProvider

    private void exportarYCompartirFaltantes() {
        if (listaFaltantes == null || listaFaltantes.isEmpty()) {
            speakText("No hay productos faltantes registrados.", UTTERANCE_ID_GENERAL_MESSAGE);
            return;
        }

        executorService.execute(() -> {
            try {
                Uri pdfUri = generarPDFFaltantes(listaFaltantes);

                runOnUiThread(() -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("application/pdf");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    startActivity(Intent.createChooser(shareIntent, "Compartir PDF de Faltantes"));
                });

            } catch (Exception e) {
                // Imprime el fallo completo en Android Studio (Logcat -> tag: PedidosActivity)
                Log.e(TAG, "Error detallado generando PDF: ", e);

                runOnUiThread(() ->
                        speakText("Error al crear el archivo PDF. Revisa el log.", UTTERANCE_ID_ERROR)
                );
            }
        });
    }
    private void mostrarMenu(View view) {
        PopupMenu popup = new PopupMenu(Pedidos.this, view);
        popup.getMenu().add(0, 1, 0, "Seleccionar Cliente");
        popup.getMenu().add(0, 2, 1, "Crear hoja de ruta");
        popup.getMenu().add(0, 3, 2, "Exportar/Compartir Faltantes (" + listaFaltantes.size() + ")");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    mostrarDialogoSeleccionarCliente();
                    return true;
                case 2:
                    // ... lógica existente ...
                    return true;
                case 3:
                    exportarYCompartirFaltantes();
                    return true;
            }
            return false;
        });
        popup.show();
    }

    // --- MÉTODO PARA INGRESAR LA CANTIDAD DE FALTANTES ---
    private void mostrarDialogoRegistrarFaltante() {
        if (currentPedidosList == null || currentPedidosList.isEmpty()) {
            speakText("No hay pedidos cargados.", UTTERANCE_ID_GENERAL_MESSAGE);
            return;
        }

        PedidoModel pedidoActual = currentPedidosList.get(currentPedidoIndex);
        if (pedidoActual.getProductos() == null || pedidoActual.getProductos().isEmpty()) return;

        ProductoModel prodActual = pedidoActual.getProductos().get(currentProductoIndex);

        // Crear un EditText en el diálogo para ingresar el número de faltantes
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Cantidad faltante (Total esperado: " + prodActual.getCantidad() + ")");

        new AlertDialog.Builder(this)
                .setTitle("Reportar Faltante")
                .setMessage("Producto: " + prodActual.getDescripcion())
                .setView(input)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    String cantStr = input.getText().toString().trim();
                    if (!cantStr.isEmpty()) {
                        try {
                            int cantFaltante = Integer.parseInt(cantStr);
                            if (cantFaltante > 0) {
                                // Registrar o actualizar faltante
                                listaFaltantes.add(new ProductoModel(cantFaltante, prodActual.getDescripcion()));
                                speakText("Registrado " + cantFaltante + " faltante de " + prodActual.getDescripcion(), UTTERANCE_ID_GENERAL_MESSAGE);
                            }
                        } catch (NumberFormatException e) {
                            speakText("Cantidad no válida.", UTTERANCE_ID_ERROR);
                        }
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
    /**
     * Normaliza el texto para evitar que PDFBox falle con tildes, ñ o símbolos especiales.
     */
    private String limpiarTextoParaPdf(String texto) {
        if (texto == null || texto.isEmpty()) return "Sin descripcion";

        // Normalizar tildes/acentos (ej: 'ó' -> 'o', 'ñ' -> 'n')
        String textoSinAcentos = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        // Mantener solo caracteres ASCII imprimibles simples
        String limpio = textoSinAcentos.replaceAll("[^a-zA-Z0-9\\s\\-\\.,:_]", "");
        return limpio.trim().isEmpty() ? "Producto" : limpio;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        executorService.shutdown();
    }
}