package com.example.myapplication; // <--- ¡Importante! Asegúrate de que este sea tu paquete base

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper; // Importa Looper para Handler más moderno
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_SCREEN_TIMEOUT = 3000; // 3 segundos

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Opcional: Ocultar la barra de estado para una experiencia de pantalla completa
        // getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        //         WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_splash); // Asegúrate de que este es el layout de tu splash

        // Usa el constructor de Handler que especifica el Looper principal
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                // Crea un Intent para iniciar tu MainActivity
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent); // Inicia MainActivity
                finish(); // Cierra SplashActivity para que el usuario no pueda volver a ella
            }
        }, SPLASH_SCREEN_TIMEOUT);
    }
}