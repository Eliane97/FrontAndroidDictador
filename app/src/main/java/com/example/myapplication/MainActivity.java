package com.example.myapplication; // Asegúrate de que este sea tu paquete correcto

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.AppiService;
import com.example.myapplication.PedidoModel;
import com.example.myapplication.ProductoModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

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

    // 1. Declaración de variables para cada View con un ID
    private View viewOverlay;
    private ImageButton btnBack;
    private TextView tvPlayingNow;
    private ImageButton btnImport;
    private FrameLayout artistImageContainer;
    private ImageView artistImage;
    private ImageView imageView3;

    private SeekBar songProgressBar;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private TextView nombreCliente;
    private TextView textoCliente;
    private ImageButton btnShuffle;
    private LinearLayout controlButtonsLayout;
    private ImageButton btnPrevious;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private Button btnLyricsPullUp;

    // NUEVAS DECLARACIONES
    private AppiService apiService;
    private ActivityResultLauncher<String> selectPdfLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 2. Inicialización de variables conectándolas con los IDs de tu XML
        viewOverlay = findViewById(R.id.view2);
        btnBack = findViewById(R.id.btn_back);
        tvPlayingNow = findViewById(R.id.tv_playing_now);
        btnImport = findViewById(R.id.btn_import);

        artistImageContainer = findViewById(R.id.artist_image_container);
        artistImage = findViewById(R.id.artist_image);
        imageView3 = findViewById(R.id.imageView3);

        songProgressBar = findViewById(R.id.song_progress_bar);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        nombreCliente = findViewById(R.id.nombreCliente);
        textoCliente = findViewById(R.id.textoCliente);
        btnShuffle = findViewById(R.id.btn_repetir);

        controlButtonsLayout = findViewById(R.id.control_buttons_layout);
        btnPrevious = findViewById(R.id.btn_previous);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnNext = findViewById(R.id.btn_next);

        btnLyricsPullUp = findViewById(R.id.btn_lyrics_pull_up);

        // Configuración de Retrofit
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://192.168.1.118:8080/") // Ejemplo: "http://192.168.1.105:8080/" // IP del emulador a tu localhost
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        apiService = retrofit.create(AppiService.class);

        // Inicializa el lanzador de la actividad para seleccionar el PDF
        selectPdfLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadPdfFile(uri);
                    } else {
                        Toast.makeText(this, "No se seleccionó ningún archivo.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // 3. Configuración de Listeners y lógica inicial
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Botón Volver atrás pulsado", Toast.LENGTH_SHORT).show();
                onBackPressed();
            }
        });

        // Combinado: Un solo OnClickListener para btnImport con la lógica de selección de PDF
        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Seleccionando archivo PDF...", Toast.LENGTH_SHORT).show();
                selectPdfLauncher.launch("application/pdf"); // Abre el selector de archivos para PDFs
            }
        });

        songProgressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrentTime.setText(formatTime(progress, seekBar.getMax()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(MainActivity.this, "Progreso de la canción cambiado a " + seekBar.getProgress() + "%", Toast.LENGTH_SHORT).show();
            }
        });

        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            private boolean isPlaying = false; // Variable de estado

            @Override
            public void onClick(View v) {
                if (isPlaying) {
                    btnPlayPause.setImageResource(R.drawable.ic_play);
                    Toast.makeText(MainActivity.this, "Pausado", Toast.LENGTH_SHORT).show();
                } else {
                    btnPlayPause.setImageResource(R.drawable.ic_pause);
                    Toast.makeText(MainActivity.this, "Reproduciendo", Toast.LENGTH_SHORT).show();
                }
                isPlaying = !isPlaying;
            }
        });

        btnPrevious.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Item anterior", Toast.LENGTH_SHORT).show());
        btnNext.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Item siguiente", Toast.LENGTH_SHORT).show());
        btnShuffle.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Repetir pedido", Toast.LENGTH_SHORT).show());
        btnLyricsPullUp.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Mostrar letras", Toast.LENGTH_SHORT).show());

        // Lógica de configuración inicial de textos o imágenes
    }

    private String formatTime(int progress, int max) {
        int totalSeconds = 217;
        int currentSeconds = (int) (totalSeconds * (progress / 100.0));

        int minutes = currentSeconds / 60;
        int seconds = currentSeconds % 60;

        return String.format("%d:%02d", minutes, seconds);
    }

    // MÉTODO uploadPdfFile (CORREGIDO: usando PedidoModel y ProductoModel y haciendo tempFile final)
    private void uploadPdfFile(Uri pdfUri) {
        final File tempFile;
        try {
            tempFile = createTempFileFromUri(pdfUri);
            if (tempFile == null) {
                Toast.makeText(this, "Error al crear archivo temporal.", Toast.LENGTH_SHORT).show();
                return;
            }

            RequestBody requestFile = RequestBody.create(MediaType.parse("application/pdf"), tempFile);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", tempFile.getName(), requestFile);

            Call<List<PedidoModel>> call = apiService.procesarPDF(body);
            call.enqueue(new Callback<List<PedidoModel>>() {
                @Override
                public void onResponse(Call<List<PedidoModel>> call, Response<List<PedidoModel>> response) {
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                    }

                    if (response.isSuccessful() && response.body() != null) {
                        List<PedidoModel> listaPedidos = response.body();

                        // **** INICIO DE LAS NUEVAS LÍNEAS PARA LOGCAT ****
                        Log.d("PedidosApp", "PDF Procesado - Clientes y Productos:");
                        if (listaPedidos.isEmpty()) {
                            Log.d("PedidosApp", "No se encontraron pedidos en la respuesta.");
                        } else {
                            for (PedidoModel pedido : listaPedidos) {
                                Log.d("PedidosApp", "--- Cliente: " + pedido.getCliente() + " ---");
                                if (pedido.getProductos().isEmpty()) {
                                    Log.d("PedidosApp", "  No hay productos para este cliente.");
                                } else {
                                    for (ProductoModel producto : pedido.getProductos()) {
                                        Log.d("PedidosApp", "  - Cantidad: " + producto.getCantidad() +
                                                ", Descripción: " + producto.getDescripcion());
                                    }
                                }
                            }
                        }
                        // **** FIN DE LAS NUEVAS LÍNEAS PARA LOGCAT ****


                        // ... (el resto de tu código para el Toast y los TextViews)
                        StringBuilder resultadoFinal = new StringBuilder("PDF Procesado:\n");
                        if (listaPedidos.isEmpty()) {
                            resultadoFinal.append("No se encontraron pedidos.");
                        } else {
                            for (PedidoModel pedido : listaPedidos) {
                                resultadoFinal.append("--- Cliente: ").append(pedido.getCliente()).append(" ---\n");
                                if (pedido.getProductos().isEmpty()) {
                                    resultadoFinal.append("  No hay productos para este cliente.\n");
                                } else {
                                    for (ProductoModel producto : pedido.getProductos()) {
                                        resultadoFinal.append("  - Cantidad: ").append(producto.getCantidad())
                                                .append(", Descripción: ").append(producto.getDescripcion()).append("\n");
                                    }
                                }
                            }
                        }

                        Toast.makeText(MainActivity.this, resultadoFinal.toString(), Toast.LENGTH_LONG).show();

                        if (!listaPedidos.isEmpty()) {
                            PedidoModel primerPedido = listaPedidos.get(0);
                            nombreCliente.setText(primerPedido.getCliente());

                            StringBuilder productosTxt = new StringBuilder();
                            for (ProductoModel prod : primerPedido.getProductos()) {
                                productosTxt.append(prod.getCantidad()).append(" ").append(prod.getDescripcion()).append("\n");
                            }
                            textoCliente.setText(productosTxt.toString());
                        } else {
                            nombreCliente.setText("Sin cliente");
                            textoCliente.setText("Sin productos");
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
                        Log.e("PedidosApp", "Error en la respuesta del servidor: " + response.code() + " - " + errorBody); // Log de error
                        Toast.makeText(MainActivity.this, "Error en la respuesta del servidor: " + response.code() + " - " + errorBody, Toast.LENGTH_LONG).show();
                        System.err.println("Error body: " + errorBody);
                    }
                }

                @Override
                public void onFailure(Call<List<PedidoModel>> call, Throwable t) {
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                    }
                    Log.e("PedidosApp", "Error de red o conexión: " + t.getMessage(), t); // Log de error con stack trace
                    Toast.makeText(MainActivity.this, "Error de red o conexión: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    t.printStackTrace();
                }
            });

        } catch (Exception e) {
            Log.e("PedidosApp", "Error al preparar el archivo para subir: " + e.getMessage(), e); // Log de error
            Toast.makeText(this, "Error al preparar el archivo para subir: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
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
            }
        } catch (Exception e) {
            e.printStackTrace();
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
}