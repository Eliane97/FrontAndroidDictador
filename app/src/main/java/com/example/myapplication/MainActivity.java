package com.example.myapplication; // Asegúrate de que este sea el paquete correcto de tu app

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast; // Para mostrar mensajes cortos (útil para pruebas)

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen; // Importa SplashScreen si aún la usas, aunque la API 31+ la maneja nativamente


public class MainActivity extends AppCompatActivity {

    // 1. Declaración de variables para cada View con un ID
    private View viewOverlay; // El overlay oscuro, si necesitas interactuar con él
    private ImageButton btnBack;
    private TextView tvPlayingNow;
    private ImageButton btnImport;
    private FrameLayout artistImageContainer; // El contenedor de la imagen del artista
    private ImageView artistImage; // La imagen del artista
    private ImageView imageView3; // El icono del micrófono (si lo mantienes)

    private SeekBar songProgressBar;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private TextView nombreCliente;
    private TextView tvArtistName;
    private ImageButton btnShuffle;
    private ImageButton btnRepeat;
    private LinearLayout controlButtonsLayout; // El LinearLayout que contiene los botones de control
    private ImageButton btnPrevious;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private Button btnLyricsPullUp;

    // Puedes agregar una ImageView para tu logo si quieres controlarlo desde Java
    // private ImageView imageViewLogo; // Necesitas agregar un ID a tu ImageView en XML si lo quieres conectar aquí


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Llama a installSplashScreen() ANTES de super.onCreate() o setContentView()

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Tu layout principal de la actividad

        // 2. Inicialización de variables conectándolas con los IDs de tu XML

        viewOverlay = findViewById(R.id.view2); // Usas 'view2' en tu XML
        btnBack = findViewById(R.id.btn_back);
        tvPlayingNow = findViewById(R.id.tv_playing_now);
        btnImport = findViewById(R.id.btn_heart);

        artistImageContainer = findViewById(R.id.artist_image_container);
        artistImage = findViewById(R.id.artist_image);
        imageView3 = findViewById(R.id.imageView3); // Si mantienes el mic, si no, puedes quitarlo

        songProgressBar = findViewById(R.id.song_progress_bar);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        nombreCliente = findViewById(R.id.nombreCliente);
        tvArtistName = findViewById(R.id.tv_artist_name);
        btnShuffle = findViewById(R.id.btn_shuffle);
        btnRepeat = findViewById(R.id.btn_repeat);

        controlButtonsLayout = findViewById(R.id.control_buttons_layout);
        btnPrevious = findViewById(R.id.btn_previous);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnNext = findViewById(R.id.btn_next);

        btnLyricsPullUp = findViewById(R.id.btn_lyrics_pull_up);

        // Si tu logo tiene un ID en el XML, lo inicializarías aquí:
        // imageViewLogo = findViewById(R.id.imageView_logo); // Asumiendo que le diste este ID

        // 3. Configuración de Listeners y lógica inicial

        // Ejemplo para el botón "Volver atrás"
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Aquí iría la lógica para volver a la pantalla anterior
                Toast.makeText(MainActivity.this, "Botón Volver atrás pulsado", Toast.LENGTH_SHORT).show();
                onBackPressed(); // Simula el botón de atrás del sistema
            }
        });

        // Ejemplo para el botón "Corazón" (favoritos)
        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Lógica para agregar/quitar de favoritos
                Toast.makeText(MainActivity.this, "Importar pedido", Toast.LENGTH_SHORT).show();
                // Puedes cambiar el icono aquí si quieres (ej. de vacío a relleno)
                // btnHeart.setImageResource(R.drawable.ic_heart_filled);
            }
        });

        // Ejemplo para el SeekBar
        songProgressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 'progress' es el valor actual de la barra (0-100 por defecto)
                // 'fromUser' es true si el usuario movió la barra, false si es programático
                if (fromUser) {
                    // Actualizar el tiempo actual de la canción o la posición de reproducción
                    // Aquí iría la lógica para actualizar el tiempo de la canción
                    tvCurrentTime.setText(formatTime(progress, seekBar.getMax())); // Ejemplo
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Cuando el usuario empieza a tocar la barra
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Cuando el usuario suelta la barra
                Toast.makeText(MainActivity.this, "Progreso de la canción cambiado a " + seekBar.getProgress() + "%", Toast.LENGTH_SHORT).show();
                // Aquí iría la lógica para buscar en la canción la nueva posición
            }
        });

        // Ejemplo para el botón "Play/Pause"
        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            private boolean isPlaying = false; // Variable de estado

            @Override
            public void onClick(View v) {
                if (isPlaying) {
                    // Si está reproduciendo, pausar y cambiar a icono de play
                    btnPlayPause.setImageResource(R.drawable.ic_play); // Asegúrate de tener ic_play
                    Toast.makeText(MainActivity.this, "Pausado", Toast.LENGTH_SHORT).show();
                } else {
                    // Si está pausado, reproducir y cambiar a icono de pausa
                    btnPlayPause.setImageResource(R.drawable.ic_pause); // Asegúrate de tener ic_pause
                    Toast.makeText(MainActivity.this, "Reproduciendo", Toast.LENGTH_SHORT).show();
                }
                isPlaying = !isPlaying; // Invertir el estado
                // Aquí iría la lógica real de reproducción/pausa del MediaPlayer
            }
        });

        // Otros botones de control (Previous, Next, Shuffle, Repeat)
        btnPrevious.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Item anterior", Toast.LENGTH_SHORT).show());
        btnNext.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Item siguiente", Toast.LENGTH_SHORT).show());
        btnShuffle.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Repetir pedido", Toast.LENGTH_SHORT).show());
        btnRepeat.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Modo repetir", Toast.LENGTH_SHORT).show());

        // Botón "LYRICS"
        btnLyricsPullUp.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Mostrar letras", Toast.LENGTH_SHORT).show());


        // Configuración inicial de textos (puedes cargarlos dinámicamente)
        nombreCliente.setText("Cliente");
        tvArtistName.setText("nombre del cliente");
        // artistImage.setImageResource(R.drawable.sia_profile); // Asegúrate de que esta imagen exista
        // tvCurrentTime.setText("0:00"); // Establecer tiempo inicial
        // tvTotalTime.setText("3:37"); // Establecer tiempo total

        // Lógica para cargar y mostrar la imagen del artista (si no está ya en el XML con src)
        // artistImage.setImageResource(R.drawable.sia_profile); // Necesitas tener una imagen llamada sia_profile.png o .jpg en res/drawable

    }

    // Método de ejemplo para formatear el tiempo del SeekBar
    private String formatTime(int progress, int max) {
        // Asumiendo que el progreso es de 0 a 100 y quieres mapearlo a 3:37 (217 segundos)
        int totalSeconds = 217; // Longitud total de la canción en segundos
        int currentSeconds = (int) (totalSeconds * (progress / 100.0));

        int minutes = currentSeconds / 60;
        int seconds = currentSeconds % 60;

        return String.format("%d:%02d", minutes, seconds);
    }
}