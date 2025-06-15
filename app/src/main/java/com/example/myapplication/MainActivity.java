package com.example.myapplication; // O el nombre de tu paquete si es diferente

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech; // Importación necesaria
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.AppiService;
import com.example.myapplication.PedidoModel;
import com.example.myapplication.ProductoModel;
import com.example.myapplication.R;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale; // Importación necesaria

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
public class MainActivity extends AppCompatActivity {

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


    private AppiService apiService;
    private ActivityResultLauncher<String> selectPdfLauncher;
    private TextToSpeech textToSpeech;

    private List<PedidoModel> currentPedidosList;
    private int currentPedidoIndex = 0;
    private int currentProductoIndex = 0;

    // Constantes para los IDs de las "utterances"
    private static final String UTTERANCE_ID_CLIENTE = "cliente_name_utterance";
    private static final String UTTERANCE_ID_ITEM = "item_utterance";
    private static final String UTTERANCE_ID_FINAL_LIST = "final_list_utterance";
    private static final String UTTERANCE_ID_ERROR = "error_utterance";
    private static final String UTTERANCE_ID_GENERAL_MESSAGE = "general_message_utterance";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewOverlay = findViewById(R.id.view2);
        btnBack = findViewById(R.id.btn_back);
        tvPlayingNow = findViewById(R.id.tv_playing_now);
        btnImport = findViewById(R.id.btn_import);

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



        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);


        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://192.168.1.148:8080/") // ¡VERIFICA QUE ESTA IP SEA CORRECTA PARA TU SERVIDOR!
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        apiService = retrofit.create(AppiService.class);

        selectPdfLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadPdfFile(uri);
                    } else {
                        Log.d("PedidosApp", "DEBUG_PDF_SELECT: No se seleccionó ningún archivo.");
                        speakText("No se seleccionó ningún archivo PDF.", UTTERANCE_ID_GENERAL_MESSAGE);
                    }
                }
        );

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("es", "ES"));

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("PedidosApp", "Idioma no soportado o faltan datos del idioma. Intentando instalarlo.");
                    Intent installIntent = new Intent();
                    installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    startActivity(installIntent);
                } else {
                    Log.d("PedidosApp", "TextToSpeech inicializado con éxito.");
                }
            } else {
                Log.e("PedidosApp", "Error al inicializar TextToSpeech. Código: " + status);
                speakText("Error al inicializar el motor de voz.", UTTERANCE_ID_ERROR);
            }
        });

        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d("PedidosApp", "TTS: Inicio dictado de '" + utteranceId + "'");
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d("PedidosApp", "TTS: Fin dictado de '" + utteranceId + "'");
                // Si el dictado del cliente terminó, podemos reproducir el primer ítem
                if (utteranceId.equals(UTTERANCE_ID_CLIENTE)) {
                    // Asegurarse de que esto se ejecuta en el hilo principal
                    runOnUiThread(() -> displayAndSpeakCurrentItem());
                }
            }

            @Override
            public void onError(String utteranceId) {
                Log.e("PedidosApp", "TTS: Error en dictado de '" + utteranceId + "'");
            }

            @Override
            public void onStop(String utteranceId, boolean interrupted) {
                Log.d("PedidosApp", "TTS: Detenido dictado de '" + utteranceId + "'. Interrumpido: " + interrupted);
            }

            @Override
            public void onRangeStart(String utteranceId, int start, int end, int frame) { /* No es necesario implementar */ }
        });


        btnBack.setOnClickListener(v -> onBackPressed());

        btnImport.setOnClickListener(v -> {
            Log.d("PedidosApp", "DEBUG_IMPORT: Seleccionando archivo PDF...");
            speakText("Seleccionando archivo PDF.", UTTERANCE_ID_GENERAL_MESSAGE);
            selectPdfLauncher.launch("application/pdf");
        });


        btnPlayPause.setOnClickListener(v -> {
            if (btnPlayPause.getTag() == null || btnPlayPause.getTag().equals("paused")) {
                btnPlayPause.setImageResource(R.drawable.ic_pause);
                btnPlayPause.setTag("playing");
                Log.d("PedidosApp", "DEBUG_UI_EVENT: Reproduciendo.");
                if (currentPedidosList != null && !currentPedidosList.isEmpty()) {
                    Log.d("PedidosApp", "DEBUG_BTN_PLAY: Iniciando dictado del elemento actual.");
                    // Re-dictar el cliente y luego el item
                    String currentClientName = currentPedidosList.get(currentPedidoIndex).getCliente();
                    speakText("Pedido para el cliente: " + currentClientName + ".", UTTERANCE_ID_CLIENTE);
                } else {
                    speakText("Cargue un documento para comenzar.", UTTERANCE_ID_GENERAL_MESSAGE);
                    Log.d("PedidosApp", "DEBUG_BTN_PLAY: Solicitando cargar documento.");
                }
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_play);
                btnPlayPause.setTag("paused");
                Log.d("PedidosApp", "DEBUG_UI_EVENT: Pausado.");
                if (textToSpeech != null && textToSpeech.isSpeaking()) {
                    textToSpeech.stop();
                    Log.d("PedidosApp", "DEBUG_TTS_STOP: Pausa, deteniendo dictado.");
                }
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentPedidosList == null || currentPedidosList.isEmpty()) {
                Log.d("PedidosApp", "DEBUG_BTN_NEXT: Lista de pedidos vacía.");
                speakText("No hay pedidos para navegar.", UTTERANCE_ID_GENERAL_MESSAGE);
                return;
            }

            int oldPedidoIndex = currentPedidoIndex;

            PedidoModel currentPedido = currentPedidosList.get(currentPedidoIndex);
            List<ProductoModel> productos = currentPedido.getProductos();

            if (!productos.isEmpty() && currentProductoIndex < productos.size() - 1) {
                currentProductoIndex++;
                Log.d("PedidosApp", "DEBUG_BTN_NEXT: Siguiente artículo dentro del mismo cliente.");
                displayAndSpeakCurrentItem();
            } else {
                if (currentPedidoIndex < currentPedidosList.size() - 1) {
                    currentPedidoIndex++;
                    currentProductoIndex = 0;

                    if (currentPedidoIndex != oldPedidoIndex) {
                        String clientName = currentPedidosList.get(currentPedidoIndex).getCliente();
                        Log.d("PedidosApp", "DEBUG_CLIENTE_TTS: Cambiando al cliente: " + clientName);
                        speakText("Cambiando al cliente: " + clientName + ".", UTTERANCE_ID_CLIENTE);
                    } else {
                        Log.d("PedidosApp", "DEBUG_BTN_NEXT: No hay más productos, pero no cambia de cliente o final de lista inminente.");
                        displayAndSpeakCurrentItem();
                    }
                } else {
                    speakText("Has llegado al final de todos los pedidos.", UTTERANCE_ID_FINAL_LIST);
                    Log.d("PedidosApp", "DEBUG_BTN_NEXT: Fin de la lista de pedidos.");
                    return;
                }
            }
        });

        btnPrevious.setOnClickListener(v -> {
            if (currentPedidosList == null || currentPedidosList.isEmpty()) {
                Log.d("PedidosApp", "DEBUG_BTN_PREVIOUS: Lista de pedidos vacía.");
                speakText("No hay pedidos para navegar.", UTTERANCE_ID_GENERAL_MESSAGE);
                return;
            }

            int oldPedidoIndex = currentPedidoIndex;
            PedidoModel currentPedido = currentPedidosList.get(currentPedidoIndex);
            List<ProductoModel> productos = currentPedido.getProductos();

            if (!productos.isEmpty() && currentProductoIndex > 0) {
                currentProductoIndex--;
                Log.d("PedidosApp", "DEBUG_BTN_PREVIOUS: Artículo anterior dentro del mismo cliente.");
                displayAndSpeakCurrentItem();
            } else {
                if (currentPedidoIndex > 0) {
                    currentPedidoIndex--;
                    PedidoModel previousPedido = currentPedidosList.get(currentPedidoIndex);
                    currentProductoIndex = previousPedido.getProductos().size() > 0 ? previousPedido.getProductos().size() - 1 : 0;

                    if (currentPedidoIndex != oldPedidoIndex) {
                        String clientName = currentPedidosList.get(currentPedidoIndex).getCliente();
                        Log.d("PedidosApp", "DEBUG_CLIENTE_TTS: Regresando al cliente: " + clientName);
                        speakText("Regresando al cliente: " + clientName + ".", UTTERANCE_ID_CLIENTE);
                    } else {
                        Log.d("PedidosApp", "DEBUG_BTN_PREVIOUS: No hay más productos, pero no cambia de cliente o inicio de lista inminente.");
                        displayAndSpeakCurrentItem();
                    }
                } else {
                    speakText("Has llegado al principio de todos los pedidos.", UTTERANCE_ID_FINAL_LIST);
                    Log.d("PedidosApp", "DEBUG_BTN_PREVIOUS: Principio de la lista de pedidos.");
                    return;
                }
            }
        });

        btnNext.setEnabled(false);
        btnPrevious.setEnabled(false);

        btnShuffle.setOnClickListener(v -> { Log.d("PedidosApp", "DEBUG_UI_EVENT: Repetir pedido."); });

    }



      //      ### Métodos Auxiliares

   // `speakText` ahora gestiona la cola de reproducción:


    private void speakText(String text, String utteranceId) {
        if (textToSpeech != null && textToSpeech.getEngines().size() > 0) {
            // Detiene lo que se esté reproduciendo y limpia la cola, luego añade el nuevo texto.
            // Esto asegura que cada llamada a speakText inicia una nueva secuencia limpia.
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            Log.d("PedidosApp", "Dictando: '" + text + "' (Utterance ID: " + utteranceId + ")");
        } else {
            Log.e("PedidosApp", "TextToSpeech no está inicializado o no hay motores de voz. No se puede dictar: " + text);
        }
    }

    private void displayAndSpeakCurrentItem() {
        if (currentPedidosList == null || currentPedidosList.isEmpty()) {
            nombreCliente.setText("No hay pedidos");
            textoCliente.setText("");
            btnNext.setEnabled(false);
            btnPrevious.setEnabled(false);
            Log.d("PedidosApp", "DEBUG_DISPLAY_SPEAK: No hay pedidos para mostrar.");
            return;
        }

        if (currentPedidoIndex < 0 || currentPedidoIndex >= currentPedidosList.size()) {
            Log.e("PedidosApp", "DEBUG_DISPLAY_SPEAK: Índice de pedido fuera de rango: " + currentPedidoIndex + ". Ajustando a 0.");
            currentPedidoIndex = 0;
            currentProductoIndex = 0;
            return;
        }

        PedidoModel currentPedido = currentPedidosList.get(currentPedidoIndex);
        nombreCliente.setText(currentPedido.getCliente());

        StringBuilder speechOutput = new StringBuilder();

        List<ProductoModel> productos = currentPedido.getProductos();

        if (productos.isEmpty()) {
            textoCliente.setText("No hay productos para este cliente");
            speechOutput.append("No hay productos para este cliente.");
            Log.d("PedidosApp", "DEBUG_PRODUCTO_TTS: Cliente '" + currentPedido.getCliente() + "' sin productos.");
            speakText(speechOutput.toString(), UTTERANCE_ID_ITEM); // Se le asigna un ID para que el listener sepa que es un item
        } else {
            if (currentProductoIndex < 0 || currentProductoIndex >= productos.size()) {
                Log.e("PedidosApp", "DEBUG_DISPLAY_SPEAK: Índice de producto fuera de rango: " + currentProductoIndex + " para el cliente: " + currentPedido.getCliente() + ". Ajustando a 0.");
                currentProductoIndex = 0;
            }
            ProductoModel currentProducto = productos.get(currentProductoIndex);
            textoCliente.setText("Cant: " + currentProducto.getCantidad() + ", Desc: " + currentProducto.getDescripcion());

            speechOutput.append(currentProducto.getCantidad()).append(" de ").append(currentProducto.getDescripcion()).append(".");
            Log.d("PedidosApp", "DEBUG_PRODUCTO_TTS: Preparando dictado de producto: " + speechOutput.toString());
            speakText(speechOutput.toString(), UTTERANCE_ID_ITEM); // Se le asigna un ID
        }
    }

    private String formatTime(int progress, int max) {
        int totalSeconds = 217;
        int currentSeconds = (int) (totalSeconds * (progress / 100.0));

        int minutes = currentSeconds / 60;
        int seconds = currentSeconds % 60;

        return String.format("%d:%02d", minutes, seconds);
    }

    private void uploadPdfFile(Uri pdfUri) {
        final File tempFile;
        try {
            tempFile = createTempFileFromUri(pdfUri);
            if (tempFile == null) {
                Log.e("PedidosApp", "DEBUG_UPLOAD: Error al crear archivo temporal.");
                speakText("Error al crear archivo temporal.", UTTERANCE_ID_ERROR);
                return;
            }

            RequestBody requestFile = RequestBody.create(MediaType.parse("application/pdf"), tempFile);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", tempFile.getName(), requestFile);

            Call<List<PedidoModel>> call = apiService.procesarPDF(body);
            Log.d("PedidosApp", "DEBUG_UPLOAD: Subiendo PDF a la API.");
            call.enqueue(new Callback<List<PedidoModel>>() {
                @Override
                public void onResponse(Call<List<PedidoModel>> call, Response<List<PedidoModel>> response) {
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                        Log.d("PedidosApp", "DEBUG_UPLOAD: Archivo temporal eliminado.");
                    }

                    if (response.isSuccessful() && response.body() != null) {
                        currentPedidosList = response.body();
                        currentPedidoIndex = 0;
                        currentProductoIndex = 0;

                        if (currentPedidosList.isEmpty()) {
                            nombreCliente.setText("Sin cliente");
                            textoCliente.setText("Sin productos");
                            btnNext.setEnabled(false);
                            btnPrevious.setEnabled(false);
                            speakText("No se encontraron pedidos en el documento.", UTTERANCE_ID_GENERAL_MESSAGE);
                            Log.d("PedidosApp", "DEBUG_UPLOAD: PDF procesado, pero sin pedidos. Dictando mensaje.");
                        } else {
                            btnNext.setEnabled(true);
                            btnPrevious.setEnabled(true);

                            String firstClientName = currentPedidosList.get(0).getCliente();
                            Log.d("PedidosApp", "DEBUG_CLIENTE_TTS: Primer cliente detectado: " + firstClientName);
                            // Dicta el nombre del primer cliente, el callback onDone llamará a displayAndSpeakCurrentItem
                            speakText("Pedido para el cliente: " + firstClientName + ".", UTTERANCE_ID_CLIENTE);
                        }
                    } else {
                        String errorBody = "";
                        try {
                            if (response.errorBody() != null) {
                                errorBody = response.errorBody().string();
                            }
                        } catch (Exception e) {
                            errorBody = "No se pudo leer el cuerpo de error";
                            e.printStackTrace();
                        }
                        Log.e("PedidosApp", "DEBUG_UPLOAD: Error en la respuesta del servidor: " + response.code() + " - " + errorBody);
                        nombreCliente.setText("Error al cargar");
                        textoCliente.setText("");
                        btnNext.setEnabled(false);
                        btnPrevious.setEnabled(false);
                        speakText("Error en la respuesta del servidor o al procesar el documento.", UTTERANCE_ID_ERROR);
                    }
                }

                @Override
                public void onFailure(Call<List<PedidoModel>> call, Throwable t) {
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                        Log.d("PedidosApp", "DEBUG_UPLOAD: Archivo temporal eliminado después de fallo.");
                    }
                    Log.e("PedidosApp", "DEBUG_UPLOAD: Error de red o conexión: " + t.getMessage(), t);
                    nombreCliente.setText("Error de conexión");
                    textoCliente.setText("");
                    btnNext.setEnabled(false);
                    btnPrevious.setEnabled(false);
                    speakText("Error de conexión. Verifique su red y el servidor.", UTTERANCE_ID_ERROR);
                }
            });

        } catch (Exception e) {
            Log.e("PedidosApp", "DEBUG_UPLOAD: Error al preparar el archivo para subir: " + e.getMessage(), e);
            speakText("Error al preparar el archivo para subir.", UTTERANCE_ID_ERROR);
        }
    }

    private File createTempFileFromUri(Uri uri) {
        File tempFile = null;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                tempFile = File.createTempFile("upload_pdf", ".pdf", getCacheDir());
                outputStream = new FileOutputStream(tempFile);
                byte[] buffer = new byte[1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
                Log.d("PedidosApp", "DEBUG_TEMP_FILE: Archivo temporal creado: " + tempFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e("PedidosApp", "DEBUG_TEMP_FILE: Error al crear archivo temporal: " + e.getMessage(), e);
            tempFile = null;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return tempFile;
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            Log.d("PedidosApp", "TextToSpeech liberado en onDestroy.");
        }
        super.onDestroy();
    }
}