package com.components.metre;

public class AddressResult {
    public final String bairro;
    public final String cidade;
    public final String estado;
    public final String enderecoCompleto;
    public final double latitude;
    public final double longitude;

    public AddressResult(String bairro, String cidade, String estado,
                         String enderecoCompleto, double latitude, double longitude) {
        this.bairro = bairro;
        this.cidade = cidade;
        this.estado = estado;
        this.enderecoCompleto = enderecoCompleto;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
