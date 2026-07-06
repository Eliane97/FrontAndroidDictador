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
        import java.io.IOException;
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
        int altoFijo = 350; // Siempre la mitad de la hoja

        // ---- Columnas fijas (bordes derechos para alinear números) ----
        float colCantX       = margenIzq + 10;
        float colDescX       = margenIzq + 55;
        float colUnitRightX  = margenIzq + 440;
        float colTotalRightX = margenIzq + 510;

        // 1. MARCO EXTERIOR (posición y tamaño SIEMPRE fijos, media hoja, esquinas redondeadas)
        cs.setStrokingColor(0f, 0f, 0f);
        cs.setLineWidth(1f);
        drawRoundedRect(cs, margenIzq, yBase - altoFijo, anchoTotal, altoFijo, 10f);
        cs.stroke();

        // 2. CABECERA (posición fija)
        cs.setNonStrokingColor(0.05f, 0.25f, 0.55f);
        drawLeftText(cs, PDType1Font.HELVETICA_BOLD, 13, "DISTRIBUIDORA GODOY", margenIzq + 10, yBase - 20);

        cs.setNonStrokingColor(0f, 0f, 0f);
        drawLeftText(cs, PDType1Font.HELVETICA, 8,
                "Cliente: " + p.getCliente() + " | Pedido: " + p.getNumeroPedido() + " | Fecha: " + p.getFechayHoraPedido(),
                margenIzq + 10, yBase - 35);

        // 3. ENCABEZADOS DE TABLA (posición fija, alineados a sus columnas)
        float yHeader = yBase - 55;
        drawLeftText(cs, PDType1Font.HELVETICA_BOLD, 8, "Cant.", colCantX, yHeader);
        drawLeftText(cs, PDType1Font.HELVETICA_BOLD, 8, "Descripción", colDescX, yHeader);
        drawRightText(cs, PDType1Font.HELVETICA_BOLD, 8, "Vlr. Unit.", colUnitRightX, yHeader);
        drawRightText(cs, PDType1Font.HELVETICA_BOLD, 8, "Vlr. Total", colTotalRightX, yHeader);

        cs.setLineWidth(1f);
        cs.moveTo(margenIzq, yHeader - 5);
        cs.lineTo(margenIzq + anchoTotal, yHeader - 5);
        cs.stroke();

        // ---- Zona de items: alto disponible FIJO, sin importar cuántos productos haya ----
        float yItemsTop = yHeader - 15;
        float yItemsBottom = yBase - altoFijo + 78; // reserva espacio para total + firma
        float availableHeight = yItemsTop - yItemsBottom;

        List<ProductoModel> productos = p.getProductos();
        int n = Math.max(productos.size(), 1);

        float rowHeight = Math.min(13f, availableHeight / n);
        rowHeight = Math.max(rowHeight, 6f);
        float fontSize = Math.min(8f, rowHeight - 2f);
        fontSize = Math.max(fontSize, 5f);

        float yLinea = yItemsTop;

        // 4. PRODUCTOS (misma data/lógica original, solo re-dibujada con alineación exacta)
        for (ProductoModel prod : productos) {
            String sCant = String.valueOf(prod.getCantidad());
            String sDesc = prod.getDescripcion();
            String sUnit = "$" + prod.getPrecioUnitario();
            String sTotal = "$" + prod.getPrecioTotaldelProducto();

            drawLeftText(cs, PDType1Font.HELVETICA, fontSize, sCant, colCantX, yLinea);
            drawLeftText(cs, PDType1Font.HELVETICA, fontSize, sDesc, colDescX, yLinea);
            drawRightText(cs, PDType1Font.HELVETICA, fontSize, sUnit, colUnitRightX, yLinea);
            drawRightText(cs, PDType1Font.HELVETICA, fontSize, sTotal, colTotalRightX, yLinea);

            yLinea -= rowHeight;
        }

        // 5. TOTAL A PAGAR — recuadro verde SIN RELLENO (solo contorno), posición fija
        float yTotalBox = yBase - altoFijo + 50;
        cs.setStrokingColor(0.0f, 0.6f, 0.0f); // Verde
        cs.setLineWidth(1.5f);
        cs.addRect(margenIzq + 350, yTotalBox, 170, 20);
        cs.stroke(); // solo contorno, nunca fill()

        cs.setNonStrokingColor(0.0f, 0.4f, 0.0f);
        drawLeftText(cs, PDType1Font.HELVETICA_BOLD, 9, "MONTO TOTAL: $" + p.getTotal(), margenIzq + 360, yTotalBox + 7);

        // 6. FIRMA — línea tenue + texto "Firma: Recibí conforme" (posición fija, alineada a la izquierda)
        cs.setStrokingColor(0.6f, 0.6f, 0.6f);
        cs.setLineWidth(0.5f);
        cs.moveTo(margenIzq + 10, yBase - altoFijo + 25);
        cs.lineTo(margenIzq + 210, yBase - altoFijo + 25);
        cs.stroke();

        cs.setNonStrokingColor(0f, 0f, 0f);
        drawLeftText(cs, PDType1Font.HELVETICA, 7, "Firma: Recibí conforme", margenIzq + 50, yBase - altoFijo + 15);
        cs.setStrokingColor(0f, 0f, 0f); // Reset color a negro

    }

    private void drawLeftText(PDPageContentStream cs, PDType1Font font, float fontSize, String text, float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private void drawRightText(PDPageContentStream cs, PDType1Font font, float fontSize, String text, float rightX, float y) throws IOException {
        float width = font.getStringWidth(text) / 1000f * fontSize;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(rightX - width, y);
        cs.showText(text);
        cs.endText();
    }
    private void drawRoundedRect(PDPageContentStream cs, float x, float y, float width, float height, float radius) throws IOException {
        cs.moveTo(x + radius, y);
        cs.lineTo(x + width - radius, y);
        cs.curveTo(x + width - radius, y, x + width, y, x + width, y + radius);
        cs.lineTo(x + width, y + height - radius);
        cs.curveTo(x + width, y + height - radius, x + width, y + height, x + width - radius, y + height);
        cs.lineTo(x + radius, y + height);
        cs.curveTo(x + radius, y + height, x, y + height, x, y + height - radius);
        cs.lineTo(x, y + radius);
        cs.curveTo(x, y + radius, x, y, x + radius, y);
        cs.closePath();
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