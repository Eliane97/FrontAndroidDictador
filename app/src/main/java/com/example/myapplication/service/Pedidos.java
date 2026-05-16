package com.example.myapplication.service; // O el nombre de tu paquete si es diferente

import android.content.Intent;
import android.content.pm.PackageManager; // Importación necesaria para permisos
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.widget.PopupMenu;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;

import com.example.myapplication.R;
import com.example.myapplication.model.PedidoModel;
import com.example.myapplication.model.ProductoModel;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Pedidos extends AppCompatActivity {

    // Constante TAG para Logcat, buena práctica
    private static final String TAG = "MainActivity";
    // Código para identificar la solicitud de permiso de almacenamiento
    private static final int REQUEST_READ_STORAGE = 777;

    // Vistas de la UI
    private View viewOverlay;
    private ImageButton btnBack;
    private TextView tvPlayingNow;
    private ImageButton btnImport;
    private FrameLayout artistImageContainer;
    private ImageView artistImage;
    private ImageView imageView3;

    private TextView nombreCliente;
    private TextView textoCliente;
    private ImageButton btnShuffle;
    private LinearLayout controlButtonsLayout;
    private ImageButton btnPrevious;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private ImageButton btnMoreOptions;

    // Lanzadores y servicios
    private ActivityResultLauncher<String> selectPdfLauncher;
    private TextToSpeech textToSpeech;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Datos de la aplicación
    private List<PedidoModel> currentPedidosList;
    private int currentPedidoIndex = 0;
    private int currentProductoIndex = 0;

    // IDs para el TextToSpeech (para rastrear el progreso del habla)
    private static final String UTTERANCE_ID_CLIENTE = "cliente_name_utterance";
    private static final String UTTERANCE_ID_ITEM = "item_utterance";
    private static final String UTTERANCE_ID_FINAL_LIST = "final_list_utterance";
    private static final String UTTERANCE_ID_ERROR = "error_utterance";
    private static final String UTTERANCE_ID_GENERAL_MESSAGE = "general_message_utterance";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pedidos);
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
        btnShuffle = findViewById(R.id.btn_repetir);

        controlButtonsLayout = findViewById(R.id.control_buttons_layout);
        btnPrevious = findViewById(R.id.btn_previous);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnNext = findViewById(R.id.btn_next);

        // --- 2. Solicitud de Permisos ---
        // Llama a este método al inicio de onCreate para verificar y solicitar el permiso


        // --- 3. Configuración del lanzador de actividad para seleccionar PDF ---
        selectPdfLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        processPdfLocally(uri);
                    } else {
                        Log.d(TAG, "DEBUG_PDF_SELECT: No se seleccionó ningún archivo.");
                        speakText("No se seleccionó ningún archivo PDF.", UTTERANCE_ID_GENERAL_MESSAGE);
                        nombreCliente.setText("Selección cancelada"); // Feedback visual
                        textoCliente.setText("");
                    }
                }
        );

        // --- 4. Inicialización de TextToSpeech ---
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("es", "ES"));

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Idioma no soportado o faltan datos del idioma. Intentando instalarlo.");
                    Intent installIntent = new Intent();
                    installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    startActivity(installIntent);
                } else {
                    Log.d(TAG, "TextToSpeech inicializado con éxito.");
                }
            } else {
                Log.e(TAG, "Error al inicializar TextToSpeech. Código: " + status);
                speakText("Error al inicializar el motor de voz.", UTTERANCE_ID_ERROR);
                nombreCliente.setText("Error TTS"); // Feedback visual
                textoCliente.setText("Revisar motor de voz.");
            }
        });

        // Configuración del listener de progreso de TextToSpeech
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "TTS: Inicio dictado de '" + utteranceId + "'");
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "TTS: Fin dictado de '" + utteranceId + "'");
                if (utteranceId.equals(UTTERANCE_ID_CLIENTE)) {
                    runOnUiThread(() -> displayAndSpeakCurrentItem());
                }
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS: Error en dictado de '" + utteranceId + "'");
                nombreCliente.setText("Error de voz"); // Feedback visual
                textoCliente.setText("Intente de nuevo.");
            }

            @Override
            public void onStop(String utteranceId, boolean interrupted) {
                Log.d(TAG, "TTS: Detenido dictado de '" + utteranceId + "'. Interrumpido: " + interrupted);
            }

            @Override
            public void onRangeStart(String utteranceId, int start, int end, int frame) { /* No es necesario implementar */ }
        });


        // --- 5. Configuración de Listeners para los Botones ---
        // Configura el evento de escucha para detectar el toque del usuario
        btnMoreOptions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Llama al método para construir y mostrar el menú
                mostrarMenu(v);
            }
        });
        btnBack.setOnClickListener(v -> onBackPressed());
        btnImport.setOnClickListener(v -> {

            Log.d(TAG, "DEBUG_IMPORT: Seleccionando archivo PDF...");
            speakText("Seleccionando archivo PDF.", UTTERANCE_ID_GENERAL_MESSAGE);

            nombreCliente.setText("Seleccionando PDF...");
            textoCliente.setText("");

            // 👉 SIEMPRE lanzar el selector
            selectPdfLauncher.launch("application/pdf");
        });

        btnPlayPause.setOnClickListener(v -> {
            if (btnPlayPause.getTag() == null || btnPlayPause.getTag().equals("paused")) {
                btnPlayPause.setImageResource(R.drawable.ic_pause); // Asegúrate de tener ic_pause
                btnPlayPause.setTag("playing");
                Log.d(TAG, "DEBUG_UI_EVENT: Reproduciendo.");
                if (currentPedidosList != null && !currentPedidosList.isEmpty()) {
                    Log.d(TAG, "DEBUG_BTN_PLAY: Iniciando dictado del elemento actual.");
                    String currentClientName = currentPedidosList.get(currentPedidoIndex).getCliente();
                    speakText("Pedido para el cliente: " + currentClientName + ".", UTTERANCE_ID_CLIENTE);
                } else {
                    speakText("Cargue un documento para comenzar.", UTTERANCE_ID_GENERAL_MESSAGE);
                    Log.d(TAG, "DEBUG_BTN_PLAY: Solicitando cargar documento.");
                }
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_play); // Asegúrate de tener ic_play
                btnPlayPause.setTag("paused");
                Log.d(TAG, "DEBUG_UI_EVENT: Pausado.");
                if (textToSpeech != null && textToSpeech.isSpeaking()) {
                    textToSpeech.stop();
                    Log.d(TAG, "DEBUG_TTS_STOP: Pausa, deteniendo dictado.");
                }
            }
        });


        btnNext.setOnClickListener(v -> {
            if (currentPedidosList == null || currentPedidosList.isEmpty()) {
                Log.d(TAG, "DEBUG_BTN_NEXT: Lista de pedidos vacía.");
                speakText("No hay pedidos para navegar.", UTTERANCE_ID_GENERAL_MESSAGE);
                nombreCliente.setText("No hay pedidos"); // Feedback visual
                textoCliente.setText("");
                return;
            }

            int oldPedidoIndex = currentPedidoIndex;

            PedidoModel currentPedido = currentPedidosList.get(currentPedidoIndex);
            List<ProductoModel> productos = currentPedido.getProductos();

            if (!productos.isEmpty() && currentProductoIndex < productos.size() - 1) {
                // Caso: Siguiente producto dentro del MISMO pedido.
                currentProductoIndex++;
                Log.d(TAG, "DEBUG_BTN_NEXT: Siguiente artículo dentro del mismo cliente.");
                displayAndSpeakCurrentItem(); // Solo dictar el producto.
            } else {
                // Caso: No hay más productos en el pedido actual o es un pedido sin productos.
                // Mover al siguiente pedido si existe.
                if (currentPedidoIndex < currentPedidosList.size() - 1) {
                    currentPedidoIndex++;
                    currentProductoIndex = 0; // Reiniciar el índice de producto para el nuevo pedido.

                    // --- LÓGICA DE CAMBIO DE PEDIDO: ANUNCIAR EL NUEVO CLIENTE SI EXISTE ---
                    String nuevoCliente = currentPedidosList.get(currentPedidoIndex).getCliente();
                    if (nuevoCliente != null && !nuevoCliente.trim().isEmpty()) {
                        speakText("Siguiente pedido para el cliente: " + nuevoCliente + ".", UTTERANCE_ID_CLIENTE);
                        Log.d(TAG, "DEBUG_VOZ: Siguiente cliente: " + nuevoCliente);
                    } else {
                        speakText("Siguiente pedido sin cliente.", UTTERANCE_ID_CLIENTE);
                        Log.d(TAG, "DEBUG_VOZ: Siguiente pedido sin cliente.");
                    }
                    // displayAndSpeakCurrentItem() se llamará cuando el dictado del cliente termine (onDone).

                } else {
                    speakText("Has llegado al final de todos los pedidos.", UTTERANCE_ID_FINAL_LIST);
                    Log.d(TAG, "DEBUG_BTN_NEXT: Fin de la lista de pedidos.");
                    nombreCliente.setText("Fin de pedidos"); // Feedback visual
                    textoCliente.setText("No hay más para mostrar.");
                    btnNext.setEnabled(false);
                    btnPrevious.setEnabled(true);
                    return;
                }
            }
            updateNavigationButtonsState();
        });

// ---

        btnPrevious.setOnClickListener(v -> {
            if (currentPedidosList == null || currentPedidosList.isEmpty()) {
                Log.d(TAG, "DEBUG_BTN_PREVIOUS: Lista de pedidos vacía.");
                speakText("No hay pedidos para navegar.", UTTERANCE_ID_GENERAL_MESSAGE);
                nombreCliente.setText("No hay pedidos"); // Feedback visual
                textoCliente.setText("");
                return;
            }

            int oldPedidoIndex = currentPedidoIndex;
            PedidoModel currentPedido = currentPedidosList.get(currentPedidoIndex);
            List<ProductoModel> productos = currentPedido.getProductos();

            if (!productos.isEmpty() && currentProductoIndex > 0) {
                // Caso: Producto anterior dentro del MISMO pedido.
                currentProductoIndex--;
                Log.d(TAG, "DEBUG_BTN_PREVIOUS: Artículo anterior dentro del mismo cliente.");
                displayAndSpeakCurrentItem(); // Solo dictar el producto.
            } else {
                // Caso: No hay más productos previos en el pedido actual o es el primer producto.
                // Mover al pedido anterior si existe.
                if (currentPedidoIndex > 0) {
                    currentPedidoIndex--;
                    PedidoModel previousPedido = currentPedidosList.get(currentPedidoIndex);
                    // Si el pedido anterior tiene productos, ir al último producto de ese pedido.
                    currentProductoIndex = previousPedido.getProductos().size() > 0 ? previousPedido.getProductos().size() - 1 : 0;

                    // --- LÓGICA DE CAMBIO DE PEDIDO: ANUNCIAR EL CLIENTE ANTERIOR SI EXISTE ---
                    String clienteAnterior = currentPedidosList.get(currentPedidoIndex).getCliente();
                    if (clienteAnterior != null && !clienteAnterior.trim().isEmpty()) {
                        speakText("Pedido anterior para el cliente: " + clienteAnterior + ".", UTTERANCE_ID_CLIENTE);
                        Log.d(TAG, "DEBUG_VOZ: Cliente anterior: " + clienteAnterior);
                    } else {
                        speakText("Pedido anterior sin cliente.", UTTERANCE_ID_CLIENTE);
                        Log.d(TAG, "DEBUG_VOZ: Pedido anterior sin cliente.");
                    }
                    // displayAndSpeakCurrentItem() se llamará cuando el dictado del cliente termine (onDone).

                } else {
                    speakText("Has llegado al principio de todos los pedidos.", UTTERANCE_ID_FINAL_LIST);
                    Log.d(TAG, "DEBUG_BTN_PREVIOUS: Principio de la lista de pedidos.");
                    nombreCliente.setText("Inicio de pedidos"); // Feedback visual
                    textoCliente.setText("No hay más para mostrar.");
                    btnPrevious.setEnabled(false);
                    btnNext.setEnabled(true);
                    return;
                }
            }
            updateNavigationButtonsState();
        });

        // Estado inicial de los botones
        btnNext.setEnabled(false);
        btnPrevious.setEnabled(false);

        btnShuffle.setOnClickListener(v -> {
            Log.d(TAG, "DEBUG_UI_EVENT: Repetir pedido.");
            if (currentPedidosList != null && !currentPedidosList.isEmpty() &&
                    currentPedidoIndex >= 0 && currentPedidoIndex < currentPedidosList.size() &&
                    !currentPedidosList.get(currentPedidoIndex).getProductos().isEmpty() &&
                    currentProductoIndex >= 0 && currentProductoIndex < currentPedidosList.get(currentPedidoIndex).getProductos().size()) {
                // Si hay un pedido y un producto válidos, lo repite
                speakText("Repitiendo: " + nombreCliente.getText().toString() + ". " + textoCliente.getText().toString(), UTTERANCE_ID_GENERAL_MESSAGE);
            } else {
                speakText("No hay nada que repetir.", UTTERANCE_ID_GENERAL_MESSAGE);
                nombreCliente.setText("Nada que repetir"); // Feedback visual
                textoCliente.setText("");
            }
        });
    }

    /**
     * Objetivo del método: Desplegar el menú administrativo y gestionar la navegación.
     * @param view La vista que sirve de ancla para el PopupMenu.
     */
    /**
     * Objetivo: Desplegar el menú administrativo y gestionar la navegación de forma segura.
     * Se añade un dismiss() explícito y un pequeño delay para liberar recursos gráficos.
     */
    /**
     * Objetivo de la clase/método: Desplegar el menú administrativo y gestionar la transición
     * segura a la hoja de ruta, minimizando el impacto en el hardware gráfico (SurfaceFlinger).
     */
    private void mostrarMenu(View view) {
        PopupMenu popup = new PopupMenu(Pedidos.this, view);
        popup.getMenu().add(0, 1, 0, "Crear hoja de ruta");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                // 1. Detener audio (TTS)
                if (textToSpeech != null) {
                    textToSpeech.stop();
                }

                // 2. Navegación con limpieza de stack
                Intent intent = new Intent(Pedidos.this, HojaRutaActivity.class);

                /** * FLAG_ACTIVITY_REORDER_TO_FRONT ayuda a que el sistema no intente
                 * recrear sesiones de PQ duplicadas de forma agresiva.
                 */
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

                startActivity(intent);

                // 3. Quitar animaciones (evita que SurfaceFlinger colapse)
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
        popup.show();
    }
    /**
     * Callback para el resultado de la solicitud de permisos.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido por el usuario
                Log.d(TAG, "Permiso READ_EXTERNAL_STORAGE concedido.");
                speakText("Permiso de almacenamiento concedido.", UTTERANCE_ID_GENERAL_MESSAGE);
            } else {
                // Permiso denegado por el usuario
                Log.e(TAG, "Permiso READ_EXTERNAL_STORAGE denegado.");
                // Informa al usuario que la funcionalidad de importación de PDF no funcionará
                speakText("Permiso de almacenamiento denegado. No se puede leer el PDF.", UTTERANCE_ID_ERROR);
                nombreCliente.setText("Permiso Denegado");
                textoCliente.setText("Activa el permiso en ajustes de la app.");
            }
        }
    }


    /**
     * Procesa el PDF seleccionado localmente usando PDFUtils y PDFParser.
     * Esta operación se realiza en un hilo de fondo.
     * @param pdfUri La URI del archivo PDF seleccionado por el usuario.
     */

        private void processPdfLocally(Uri pdfUri) {
            // Mostrar mensaje de procesamiento en la UI inmediatamente
            runOnUiThread(() -> {
                nombreCliente.setText("Procesando PDF...");
                textoCliente.setText("Por favor, espere.");
                btnNext.setEnabled(false);
                btnPrevious.setEnabled(false);
            });

            executorService.execute(() -> {
                try (InputStream inputStream = getContentResolver().openInputStream(pdfUri)) {
                    if (inputStream == null) {
                        throw new IOException("No se pudo abrir el InputStream del PDF para la URI: " + pdfUri.toString());
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
                            Log.d(TAG, "DEBUG_PROCESS_PDF: PDF procesado, pero sin pedidos. Dictando mensaje.");
                        } else {
                            Log.d(TAG, "DEBUG_PROCESS_PDF: PDF procesado exitosamente.");
                            // --- LÓGICA DE INICIO DE PEDIDO: ANUNCIAR EL PRIMER CLIENTE SI EXISTE ---
                            PedidoModel primerPedido = currentPedidosList.get(0);
                            String primerCliente = primerPedido.getCliente();

                            if (primerCliente != null && !primerCliente.trim().isEmpty()) {
                                speakText("Pedido para el cliente: " + primerCliente + ".", UTTERANCE_ID_CLIENTE);
                                Log.d(TAG, "DEBUG_VOZ: Iniciando dictado del primer cliente: " + primerCliente);
                            }

                            else {
                                speakText("Informe de cargamento. Primer pedido sin cliente.", UTTERANCE_ID_CLIENTE);
                                Log.d(TAG, "DEBUG_VOZ: Iniciando dictado del primer pedido sin cliente.");
                            }

                            // El displayAndSpeakCurrentItem() se llamará cuando termine de hablar el cliente,
                            // gracias al UtteranceProgressListener para UTTERANCE_ID_CLIENTE.
                            // Esto asegura la secuencia: Cliente -> Producto
                        }
                        updateNavigationButtonsState();
                    });

                } catch (SecurityException e) {
                    Log.e(TAG, "Error de seguridad al procesar el PDF: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        nombreCliente.setText("Permiso Denegado");
                        textoCliente.setText("Verifique permisos de acceso al archivo.");
                        updateNavigationButtonsState();
                        speakText("Error de permiso al acceder al documento PDF.", UTTERANCE_ID_ERROR);
                    });
                }
                catch (IOException e) {
                    Log.e(TAG, "Error de I/O al procesar el PDF localmente: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        nombreCliente.setText("Error de lectura");
                        textoCliente.setText("No se pudo acceder al archivo.");
                        updateNavigationButtonsState();
                        speakText("Error al leer el documento PDF. Podría estar corrupto o inaccesible.", UTTERANCE_ID_ERROR);
                    });
                }
                catch (Exception e) { // Captura cualquier otra excepción inesperada del parseador
                    Log.e(TAG, "Error inesperado al procesar el PDF: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        nombreCliente.setText("Error al parsear");
                        textoCliente.setText("Formato de PDF no reconocido.");
                        updateNavigationButtonsState();
                        speakText("Error al procesar el contenido del documento PDF.", UTTERANCE_ID_ERROR);
                    });
                }
            });
        }



    /**
     * Gestiona el dictado de texto usando TextToSpeech, deteniendo cualquier dictado previo.
     * @param text El texto a dictar.
     * @param utteranceId El ID para identificar el dictado en el UtteranceProgressListener.
     */
    private void speakText(String text, String utteranceId) {
        if (textToSpeech != null && textToSpeech.getEngines().size() > 0) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            Log.d(TAG, "Dictando: '" + text + "' (Utterance ID: " + utteranceId + ")");
        } else {
            Log.e(TAG, "TextToSpeech no está inicializado o no hay motores de voz. No se puede dictar: " + text);
        }
    }

    /**
     * Muestra el pedido y producto actual en la UI y lo dicta.
     */
    private void displayAndSpeakCurrentItem() {
        if (currentPedidosList == null || currentPedidosList.isEmpty()) {
            nombreCliente.setText("No hay pedidos");
            textoCliente.setText("");
            Log.d(TAG, "DEBUG_DISPLAY_SPEAK: No hay pedidos para mostrar.");
            updateNavigationButtonsState();
            return;
        }

        // Asegurar que el índice de pedido esté dentro de los límites
        if (currentPedidoIndex < 0) {
            currentPedidoIndex = 0;
            Log.w(TAG, "DEBUG_DISPLAY_SPEAK: Índice de pedido < 0. Ajustando a 0.");
        }
        if (currentPedidoIndex >= currentPedidosList.size()) {
            currentPedidoIndex = currentPedidosList.size() - 1; // Último pedido
            Log.w(TAG, "DEBUG_DISPLAY_SPEAK: Índice de pedido fuera de rango. Ajustando al último.");
        }


        PedidoModel currentPedido = currentPedidosList.get(currentPedidoIndex);
        nombreCliente.setText(currentPedido.getCliente());

        StringBuilder speechOutput = new StringBuilder();

        List<ProductoModel> productos = currentPedido.getProductos();

        if (productos.isEmpty()) {
            textoCliente.setText("No hay productos para este cliente");
            speechOutput.append("No hay productos para este cliente.");
            Log.d(TAG, "DEBUG_PRODUCTO_TTS: Cliente '" + currentPedido.getCliente() + "' sin productos.");
            speakText(speechOutput.toString(), UTTERANCE_ID_ITEM);
        } else {
            // Asegurar que el índice de producto esté dentro de los límites
            if (currentProductoIndex < 0) {
                currentProductoIndex = 0;
                Log.w(TAG, "DEBUG_DISPLAY_SPEAK: Índice de producto < 0. Ajustando a 0.");
            }
            if (currentProductoIndex >= productos.size()) {
                currentProductoIndex = productos.size() - 1; // Último producto de ese pedido
                Log.w(TAG, "DEBUG_DISPLAY_SPEAK: Índice de producto fuera de rango. Ajustando al último.");
            }

            ProductoModel currentProducto = productos.get(currentProductoIndex);
            textoCliente.setText("Cant: " + currentProducto.getCantidad() + ", Desc: " + currentProducto.getDescripcion());

            speechOutput.append(currentProducto.getCantidad()).append(" de ").append(currentProducto.getDescripcion()).append(".");
            Log.d(TAG, "DEBUG_PRODUCTO_TTS: Preparando dictado de producto: " + speechOutput.toString());
            speakText(speechOutput.toString(), UTTERANCE_ID_ITEM);
        }
        updateNavigationButtonsState();
    }

    /**
     * Actualiza el estado de los botones de navegación (Next, Previous)
     * basándose en la posición actual en la lista de pedidos y productos.
     */
    private void updateNavigationButtonsState() {
        if (currentPedidosList == null || currentPedidosList.isEmpty()) {
            btnNext.setEnabled(false);
            btnPrevious.setEnabled(false);
            return;
        }

        boolean canGoNext = false;
        boolean canGoPrevious = false;

        // Comprobación para avanzar
        // Si no es el último pedido
        if (currentPedidoIndex < currentPedidosList.size() - 1) {
            canGoNext = true;
        }
        // O si es el último pedido pero no el último producto de ese pedido
        else if (currentPedidoIndex == currentPedidosList.size() - 1) {
            PedidoModel currentPedido = currentPedidosList.get(currentPedidoIndex);
            if (currentProductoIndex < currentPedido.getProductos().size() - 1) {
                canGoNext = true;
            }
        }

        // Comprobación para retroceder
        // Si no es el primer pedido
        if (currentPedidoIndex > 0) {
            canGoPrevious = true;
        }
        // O si es el primer pedido pero no el primer producto de ese pedido
        else if (currentPedidoIndex == 0) {
            if (currentProductoIndex > 0) {
                canGoPrevious = true;
            }
        }

        btnNext.setEnabled(canGoNext);
        btnPrevious.setEnabled(canGoPrevious);

        Log.d(TAG, "DEBUG_BUTTON_STATE: Next=" + canGoNext + ", Previous=" + canGoPrevious +
                " (Pedido: " + currentPedidoIndex + ", Producto: " + currentProductoIndex + ")");
    }

    // El método formatTime parece no estar directamente relacionado con el parseo del PDF
    // o la navegación de pedidos, pero se mantiene como estaba.
    private String formatTime(int progress, int max) {
        int totalSeconds = 217;
        int currentSeconds = (int) (totalSeconds * (progress / 100.0));

        int minutes = currentSeconds / 60;
        int seconds = currentSeconds % 60;

        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Si el TTS está hablando al cambiar de pantalla, lo silenciamos
        // para evitar que el buffer de audio choque con la nueva Activity.
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    @Override
    protected void onDestroy() {
        // Es vital cerrar el executor para evitar fugas de memoria (Memory Leaks)
        if (executorService != null) {
            executorService.shutdown();
        }
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}

