package com.example.myapplication;


import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

import java.util.List;

public interface AppiService {
    @Multipart
    @POST("/api/procesar-pdf")
    Call<List<PedidoModel>> procesarPDF(@Part MultipartBody.Part file); // Espera una lista de Pedido
}