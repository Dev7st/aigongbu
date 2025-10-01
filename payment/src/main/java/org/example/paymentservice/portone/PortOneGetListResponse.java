package org.example.paymentservice.portone;

import lombok.Data;

import java.util.List;

@Data
public class PortOneGetListResponse {

    private int code;
    private String message;
    private Response response;

    @Data
    public static class Response {
        private int total;
        private List<PortOnePaymentResponse> list;
    }
}
