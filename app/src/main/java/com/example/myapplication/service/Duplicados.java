package com.example.myapplication.service;


        import android.content.Intent;
        import android.net.Uri;
        import android.os.Bundle;
        import android.util.Log;
        import android.widget.TextView;
        import android.widget.Toast;
        import androidx.activity.result.ActivityResultLauncher;
        import androidx.activity.result.contract.ActivityResultContracts;
        import androidx.appcompat.app.AppCompatActivity;
        import androidx.core.content.FileProvider;


        import com.example.myapplication.R;
        import com.example.myapplication.model.PedidoModel;
        import com.example.myapplication.model.ProductoModel;
        import com.example.myapplication.service.PDFParser;
        import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
        import com.tom_roush.pdfbox.pdmodel.PDDocument;
        import com.tom_roush.pdfbox.pdmodel.PDPage;
        import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
        import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
        import java.io.File;
        import java.io.InputStream;
        import java.util.List;

public class Duplicados extends AppCompatActivity {
    private TextView txtResultado;
    private List<PedidoModel> listaPedidos;

    private final ActivityResultLauncher<String> filePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (uri != null) procesarArchivo(uri); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_duplicados);
        PDFBoxResourceLoader.init(getApplicationContext());

        txtResultado = findViewById(R.id.txtResultado);
        findViewById(R.id.btnImportar).setOnClickListener(v -> filePicker.launch("application/pdf"));
        // Ahora el botón compartirá el PDF generado
        findViewById(R.id.btnCompartir).setOnClickListener(v -> generarYCompartirPDF());
    }

    private void procesarArchivo(Uri uri) {
        new Thread(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                String textoCompleto = PDFUtils.extraerTexto(is);
                listaPedidos = PDFParser.parseEstructurado(textoCompleto);
                runOnUiThread(() -> {
                    txtResultado.setText("Procesados: " + listaPedidos.size() + " pedidos listos para exportar.");
                    findViewById(R.id.btnCompartir).setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void generarYCompartirPDF() {
        if (listaPedidos == null || listaPedidos.isEmpty()) return;

        File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        File file = new File(downloadsDir, "Remitos.pdf");

        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < listaPedidos.size(); i += 2) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    // Remito Superior
                    dibujarRemito(cs, listaPedidos.get(i), 780);

                    // Remito Inferior (si hay otro)
                    if (i + 1 < listaPedidos.size()) {
                        dibujarRemito(cs, listaPedidos.get(i + 1), 420);
                    }
                }
            }
            document.save(file);
            compartirArchivo(file);
        } catch (Exception e) {
            Log.e("PDF_ERROR", "Error:", e);
            runOnUiThread(() -> Toast.makeText(this, "Error al generar: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }private void dibujarRemito(PDPageContentStream cs, PedidoModel p, int yBase) throws Exception {
        int margenIzq = 40;
        int anchoTotal = 520;

        // 1. CABECERA
        cs.setNonStrokingColor(0.05f, 0.25f, 0.55f);
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
        cs.newLineAtOffset(margenIzq, yBase);
        cs.showText("DISTRIBUIDORA GODOY");
        cs.endText();

        cs.setNonStrokingColor(0f, 0f, 0f);
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 9);
        cs.newLineAtOffset(margenIzq, yBase - 15);
        cs.showText("Cliente: " + p.getCliente() + " | Pedido: " + p.getNumeroPedido() + " | Fecha: " + p.getFechayHoraPedido());
        cs.endText();

        // 2. ENCABEZADOS DE TABLA
        float yHeader = yBase - 40;
        cs.setLineWidth(1f);
        cs.moveTo(margenIzq, yHeader - 5); cs.lineTo(margenIzq + anchoTotal, yHeader - 5); cs.stroke();

        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 9);
        cs.newLineAtOffset(margenIzq + 5, yHeader);
        cs.showText("Cant.");
        cs.newLineAtOffset(50, 0); cs.showText("Descripción");

        // Alineación derecha: x = margenIzq + anchoTotal
        float xVlrUnit = margenIzq + 450;
        float xVlrTotal = margenIzq + 520;

        cs.newLineAtOffset(350, 0); cs.showText("Vlr. Unit.");
        cs.newLineAtOffset(70, 0); cs.showText("Vlr. Total");
        cs.endText();

        // 3. PRODUCTOS
        int yLinea = (int) (yHeader - 20);
        for (ProductoModel prod : p.getProductos()) {
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 9);
            cs.newLineAtOffset(margenIzq + 5, yLinea);
            cs.showText(String.valueOf(prod.getCantidad()));
            cs.newLineAtOffset(50, 0);
            cs.showText(prod.getDescripcion());
            cs.endText();

            // Precios alineados a la derecha usando cálculos simples
            String sUnit = "$" + prod.getPrecioUnitario();
            String sTotal = "$" + prod.getPrecioTotaldelProducto();

            cs.beginText();
            cs.newLineAtOffset(xVlrUnit - (sUnit.length() * 5), yLinea); // Ajuste manual simple
            cs.showText(sUnit);
            cs.newLineAtOffset(70, 0);
            cs.showText(sTotal);
            cs.endText();

            yLinea -= 15;
        }

        // 4. TOTAL (Rectángulo inferior)
        cs.setNonStrokingColor(0.9f, 0.9f, 0.9f); // Fondo gris claro
        cs.addRect(margenIzq + 350, yLinea - 10, 170, 20);
        cs.fill();

        cs.setNonStrokingColor(0f, 0f, 0f);
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
        cs.newLineAtOffset(margenIzq + 360, yLinea - 5);
        cs.showText("TOTAL A PAGAR: $" + p.getTotal());
        cs.endText();

        // 5. MARCO EXTERIOR
        cs.setStrokingColor(0f, 0f, 0f);
        cs.addRect(margenIzq - 5, yLinea - 30, anchoTotal + 10, (yBase - yLinea) + 40);
        cs.stroke();
    }
    private void compartirArchivo(File file) {
        // Fíjate que ahora termina en .fileprovider para coincidir con tu manifiesto
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Enviar Remitos"));
    }
}