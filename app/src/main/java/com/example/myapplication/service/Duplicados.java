package com.example.myapplication.service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.myapplication.R;
import com.example.myapplication.model.PedidoModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Duplicados extends AppCompatActivity {
    private TextView txtResultado;
    private List<PedidoModel> listaPedidos;

    // Lanzador para seleccionar archivo
    private final ActivityResultLauncher<String> filePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) procesarArchivo(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_duplicados);
        // INICIALIZACIÓN SEGURA:
        // Al colocarlo aquí, garantizas que la librería esté lista apenas se crea la Activity
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());
        } catch (Exception e) {
            // Solo por precaución, aunque raramente fallará aquí
            Log.e("PDFBox", "Error al inicializar", e);
        }

        txtResultado = findViewById(R.id.txtResultado);
        findViewById(R.id.btnImportar).setOnClickListener(v -> filePicker.launch("application/pdf"));
        findViewById(R.id.btnCompartir).setOnClickListener(v -> compartirDatos());
    }

    private void procesarArchivo(Uri uri) {
        new Thread(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                // Aquí es donde DEBES llamar al nuevo método
                // Supongamos que PDFUtils.extraerTexto(is) convierte el PDF a un String
                String textoCompleto = PDFUtils.extraerTexto(is);

                // LLAMADA AL NUEVO MÉTODO
                listaPedidos = PDFParser.parseEstructurado(textoCompleto);

                runOnUiThread(() -> {
                    StringBuilder sb = new StringBuilder();
                    for (PedidoModel p : listaPedidos) {
                        // Ahora tu toString() podrá mostrar todos los datos nuevos
                        sb.append(p.toString()).append("\n-------------------\n");
                    }
                    txtResultado.setText(sb.toString());
                    findViewById(R.id.btnCompartir).setEnabled(true);
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void compartirDatos() {
        // Creamos un texto plano (o podrías generar un nuevo PDF aquí)
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, txtResultado.getText().toString());
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Compartir resumen"));
    }

}