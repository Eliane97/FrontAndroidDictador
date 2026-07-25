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
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
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

        // ===== Cálculo de mitades de hoja con el MISMO margen para ambos remitos =====
        float pageWidth = PDRectangle.LETTER.getWidth();
        float pageHeight = PDRectangle.LETTER.getHeight();
        float margenV = 20f; // margen vertical: respecto al borde de la hoja Y respecto a la línea central

        float mitadHoja = pageHeight / 2f;
        // Cada remito ocupa exactamente la mitad de la hoja, descontando su margen arriba/abajo
        float altoRemito = mitadHoja - (2 * margenV);

        // Techo (yBase) de cada remito: a "margenV" del borde superior de su mitad de hoja
        float yBaseSuperior = pageHeight - margenV;      // margen respecto al borde superior de la hoja
        float yBaseInferior = mitadHoja - margenV;       // mismo margen, pero respecto a la línea central

        int totalPaginas = (int) Math.ceil(listaPedidos.size() / 2.0);

        try (PDDocument document = new PDDocument()) {
            int numeroPagina = 0;
            for (int i = 0; i < listaPedidos.size(); i += 2) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                numeroPagina++;

                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    // Remito Superior
                    dibujarRemito(cs, listaPedidos.get(i), yBaseSuperior, altoRemito);

                    // Remito Inferior (si hay otro)
                    if (i + 1 < listaPedidos.size()) {
                        dibujarRemito(cs, listaPedidos.get(i + 1), yBaseInferior, altoRemito);
                    }

                    // Línea de corte al medio de la hoja
                    dibujarLineaCorte(cs, pageWidth, mitadHoja);

                    // Numeración de página
                    dibujarNumeroPagina(cs, pageWidth, numeroPagina, totalPaginas);
            }}
            document.save(file);
            compartirArchivo(file);
        } catch (Exception e) {
            Log.e("PDF_ERROR", "Error:", e);
            runOnUiThread(() -> Toast.makeText(this, "Error al generar: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void dibujarRemito(PDPageContentStream cs, PedidoModel p, float yBase, float altoFijo) throws Exception {
        int margenIzq = 40;
        int anchoTotal = 520;

        float xDer = margenIzq + anchoTotal;

        // Columnas de la tabla

        float colCantX       = margenIzq + 75;
        float colDescX       = margenIzq + 115;
        float colUnitRightX  = margenIzq + 430;
        float colTotalRightX = margenIzq + 510;

        // ================= 1. MARCO EXTERIOR =================
        cs.setStrokingColor(0.85f, 0.85f, 0.85f);
        cs.setLineWidth(1f);
        drawRoundedRect(cs, margenIzq, yBase - altoFijo, anchoTotal, altoFijo, 8f);
        cs.stroke();

        // ================= 2. CABECERA =================
        // Izquierda: nombre + subtítulo
        cs.setNonStrokingColor(0.05f, 0.15f, 0.55f);
        drawLeftText(cs, PDType1Font.HELVETICA_BOLD, 16, "DISTRIBUIDORA GODOY", margenIzq + 12, yBase - 22);

        cs.setNonStrokingColor(0.45f, 0.45f, 0.45f);
        drawLeftText(cs, PDType1Font.HELVETICA, 8, "ENTREGA DE MERCADERÍA", margenIzq + 12, yBase - 34);

        // Derecha: título + datos de pedido
        cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
        drawRightText(cs, PDType1Font.HELVETICA_BOLD, 12, "REMITO / COMPROBANTE", xDer - 12, yBase - 20);

        String lineaPedido = "PEDIDO: " + p.getNumeroPedido() + "  |  Fecha: " + p.getFechayHoraPedido();
        drawRightText(cs, PDType1Font.HELVETICA_BOLD, 8, lineaPedido, xDer - 12, yBase - 32);

        // Condición
        String condicion = "Condición: Débito / Al Contado"; // <-- reemplazar por p.getCondicion() si existe

        PDFont fontCond = PDType1Font.HELVETICA_BOLD;
        float fsCond = 8f;
        float yCond = yBase - 44;

        cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
        drawRightText(cs, fontCond, fsCond, condicion, xDer - 12, yCond);



        // ================= 3. CAJA CLIENTE / DETALLE (solo contorno, sin relleno) =================
        float yCajaTop = yBase - 55;
        float yCajaBottom = yBase - 78;
        cs.setStrokingColor(0.75f, 0.75f, 0.75f);
        cs.setLineWidth(0.5f);
        drawRoundedRect(cs, margenIzq + 8, yCajaBottom, anchoTotal - 16, yCajaTop - yCajaBottom, 4f);
        cs.stroke();

        String detalle = "Entrega de mercadería correspondiente al pedido adjunto."; // <-- reemplazar por p.getDetalle() si existe

        cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
        drawLeftText(cs, PDType1Font.HELVETICA_BOLD, 8, "Cliente:", margenIzq + 18, yCajaTop - 12);
        float wLblCliente = PDType1Font.HELVETICA_BOLD.getStringWidth("Cliente:") / 1000f * 8;
        drawLeftText(cs, PDType1Font.HELVETICA, 8, " " + p.getCliente(), margenIzq + 18 + wLblCliente, yCajaTop - 12);

        drawLeftText(cs, PDType1Font.HELVETICA_BOLD, 8, "Detalle:", margenIzq + 230, yCajaTop - 12);
        float wLblDetalle = PDType1Font.HELVETICA_BOLD.getStringWidth("Detalle:") / 1000f * 8;
        drawLeftText(cs, PDType1Font.HELVETICA, 8, " " + detalle, margenIzq + 230 + wLblDetalle, yCajaTop - 12);
        // ================= 4. HEADER DE TABLA (solo contorno, sin relleno) =================
        float yTablaHeaderTop = yCajaBottom - 6;
        float alturaHeaderTabla = 16f;
        cs.setStrokingColor(0.09f, 0.16f, 0.45f);
        cs.setLineWidth(1f);
        cs.addRect(margenIzq + 8, yTablaHeaderTop - alturaHeaderTabla, anchoTotal - 16, alturaHeaderTabla);
        cs.stroke();

        float yHeaderTexto = yTablaHeaderTop - 11;
        cs.setNonStrokingColor(0.09f, 0.16f, 0.45f);
        drawLeftText(cs, PDType1Font.HELVETICA_BOLD, 8, "Cant.", colCantX, yHeaderTexto);
        drawLeftText(cs, PDType1Font.HELVETICA_BOLD, 8, "Descripción", colDescX, yHeaderTexto);
        drawRightText(cs, PDType1Font.HELVETICA_BOLD, 8, "Vlr. Unit.", colUnitRightX, yHeaderTexto);
        drawRightText(cs, PDType1Font.HELVETICA_BOLD, 8, "Vlr. Total", colTotalRightX, yHeaderTexto);

        // ================= 5. ZONA DE PRODUCTOS (alto fijo) =================
        float yItemsTop = yTablaHeaderTop - alturaHeaderTabla - 12;
        float yItemsBottom = yBase - altoFijo + 78; // reserva espacio para total + firmas + corte
        float availableHeight = yItemsTop - yItemsBottom;

        List<ProductoModel> productos = p.getProductos();
        int n = Math.max(productos.size(), 1);

        float rowHeight = Math.min(15f, availableHeight / n);
        rowHeight = Math.max(rowHeight, 6f);
        float fontSize = Math.min(8f, rowHeight - 3f);
        fontSize = Math.max(fontSize, 5f);

        float yLinea = yItemsTop;
        cs.setNonStrokingColor(0.15f, 0.15f, 0.15f);

        for (ProductoModel prod : productos) {
            // <-- requiere getCodigo() en ProductoModel
            String sCant = String.valueOf(prod.getCantidad());
            String sDesc = prod.getDescripcion();
            String sUnit = "$" + prod.getPrecioUnitario();
            String sTotal = "$" + prod.getPrecioTotaldelProducto();


            drawLeftText(cs, PDType1Font.HELVETICA, fontSize, sCant, colCantX, yLinea);
            drawLeftText(cs, PDType1Font.HELVETICA, fontSize, sDesc, colDescX, yLinea);
            drawRightText(cs, PDType1Font.HELVETICA, fontSize, sUnit, colUnitRightX, yLinea);
            drawRightText(cs, PDType1Font.HELVETICA, fontSize, sTotal, colTotalRightX, yLinea);

            // línea separadora sutil entre filas
            cs.setStrokingColor(0.93f, 0.93f, 0.93f);
            cs.setLineWidth(0.3f);
            cs.moveTo(margenIzq + 8, yLinea - rowHeight * 0.35f);
            cs.lineTo(xDer - 8, yLinea - rowHeight * 0.35f);
            cs.stroke();

            yLinea -= rowHeight;
        }

        // ================= 6. BADGE ÍTEMS TOTALES + TOTAL A PAGAR =================
        float yFooterBox = yBase - altoFijo + 55;

        // Badge "N Ítems Totales" — solo contorno, sin relleno
        String textoItems = productos.size() + " Ítems Totales";
        float fsItems = 8f;
        float wItems = PDType1Font.HELVETICA_BOLD.getStringWidth(textoItems) / 1000f * fsItems + 16;
        cs.setStrokingColor(0.6f, 0.6f, 0.6f);
        cs.setLineWidth(0.5f);
        drawRoundedRect(cs, margenIzq + 8, yFooterBox, wItems, 18f, 4f);
        cs.stroke();
        cs.setNonStrokingColor(0.2f, 0.2f, 0.2f);
        drawCenteredText(cs, PDType1Font.HELVETICA_BOLD, fsItems, textoItems, margenIzq + 8 + wItems / 2f, yFooterBox + 6);

        // Recuadro "TOTAL A PAGAR" — solo contorno verde, SIN relleno
        float anchoTotalBox = 190f;
        float xTotalBox = xDer - 8 - anchoTotalBox;
        cs.setStrokingColor(0.16f, 0.65f, 0.40f);
        cs.setLineWidth(1.3f);
        drawRoundedRect(cs, xTotalBox, yFooterBox, anchoTotalBox, 18f, 4f);
        cs.stroke();

        cs.setNonStrokingColor(0.05f, 0.1f, 0.1f);
        drawLeftText(cs, PDType1Font.HELVETICA_BOLD, 8, "MONTO TOTAL:", xTotalBox + 10, yFooterBox + 6);
        cs.setNonStrokingColor(0.1f, 0.55f, 0.3f);
        drawRightText(cs, PDType1Font.HELVETICA_BOLD, 10, "$" + p.getTotal(), xTotalBox + anchoTotalBox - 10, yFooterBox + 6);

        // ================= 7. FIRMAS (dos columnas) =================
        float yFirmaLinea = yBase - altoFijo + 30;
        float xFirma1Ini = margenIzq + 20;
        float xFirma1Fin = margenIzq + 210;

        cs.setStrokingColor(0.7f, 0.7f, 0.7f);
        cs.setLineWidth(0.5f);
        cs.moveTo(xFirma1Ini, yFirmaLinea); cs.lineTo(xFirma1Fin, yFirmaLinea); cs.stroke();


        cs.setNonStrokingColor(0.1f, 0.3f, 0.6f);
        drawCenteredText(cs, PDType1Font.HELVETICA, 7, "Firma Conforme Cliente", (xFirma1Ini + xFirma1Fin) / 2f, yFirmaLinea - 10);


        cs.setStrokingColor(0f, 0f, 0f);
        cs.setNonStrokingColor(0f, 0f, 0f);
    }

// ================= HELPERS =================

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

    private void fillRoundedRect(PDPageContentStream cs, float x, float y, float width, float height, float radius) throws IOException {
        drawRoundedRect(cs, x, y, width, height, radius);
        cs.fill();
        // el fill() consume el path; lo re-trazamos si se necesita stroke() después (ver llamadas arriba)
        drawRoundedRect(cs, x, y, width, height, radius);
    }

    private void drawLeftText(PDPageContentStream cs, PDFont font, float fontSize, String text, float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private void drawRightText(PDPageContentStream cs, PDFont font, float fontSize, String text, float rightX, float y) throws IOException {
        float width = font.getStringWidth(text) / 1000f * fontSize;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(rightX - width, y);
        cs.showText(text);
        cs.endText();
    }

    private void drawCenteredText(PDPageContentStream cs, PDFont font, float fontSize, String text, float centerX, float y) throws IOException {
        float width = font.getStringWidth(text) / 1000f * fontSize;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(centerX - width / 2f, y);
        cs.showText(text);
        cs.endText();
    }

    private void dibujarNumeroPagina(PDPageContentStream cs, float pageWidth, int numeroPagina, int totalPaginas) throws IOException {
        String texto = "Página " + numeroPagina + " de " + totalPaginas;
        cs.setNonStrokingColor(0.45f, 0.45f, 0.45f);
        drawCenteredText(cs, PDType1Font.HELVETICA, 7, texto, pageWidth / 2f, 8f);
        cs.setNonStrokingColor(0f, 0f, 0f);
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
    private void dibujarLineaCorte(PDPageContentStream cs, float pageWidth, float y) throws IOException {
        cs.setStrokingColor(0.6f, 0.6f, 0.6f);
        cs.setLineWidth(0.5f);
        cs.setLineDashPattern(new float[]{4f, 4f}, 0f);
        cs.moveTo(15f, y);
        cs.lineTo(pageWidth - 15f, y);
        cs.stroke();
        cs.setLineDashPattern(new float[]{}, 0f); // vuelve a línea sólida para lo que sigue
        cs.setStrokingColor(0f, 0f, 0f);
    }
}