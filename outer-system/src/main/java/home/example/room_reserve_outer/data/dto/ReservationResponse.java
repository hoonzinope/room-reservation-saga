package home.example.room_reserve_outer.data.dto;

import home.example.room_reserve_outer.data.type.ReservationError;
import home.example.room_reserve_outer.data.type.ReservationResult;
import home.example.room_reserve_outer.data.type.Status;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class ReservationResponse {
    private String idempotency_key;
    private Long reservation_id;
    private String room_number;
    private String operation;
    private Status status;
    private ReservationResult is_success;
    private ReservationError error_msg;

    public String toPayload(){
        StringBuilder sb = new StringBuilder();
        sb.append("idempotency_id=").append(this.idempotency_key).append("\n");
        sb.append("reservation_id=").append(this.reservation_id).append("\n");
        sb.append("room_number=").append(this.room_number).append("\n");
        sb.append("operation=").append(this.operation).append("\n");
        sb.append("status=").append(this.status == null ? null : this.status.getCode()).append("\n");
        sb.append("is_success=").append(this.is_success == null ? null : this.is_success.getCode()).append("\n");
        sb.append("error_msg=").append(this.error_msg == null ? null : this.error_msg.getCode()).append("\n");
        return sb.toString();
    }

    public ReservationResponse payloadToResponse(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return null;
        }

        Map<String, String> values = new HashMap<>();
        String[] lines = payload.split("\\R");
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            int delimiterIndex = line.indexOf('=');
            if (delimiterIndex < 0) {
                continue;
            }

            String key = line.substring(0, delimiterIndex).trim();
            String value = line.substring(delimiterIndex + 1).trim();
            values.put(key, value);
        }

        String idempotencyKey = values.getOrDefault("idempotency_key", values.get("idempotency_id"));
        String reservationIdValue = values.get("reservation_id");

        return ReservationResponse.builder()
                .idempotency_key(idempotencyKey)
                .reservation_id(parseLong(reservationIdValue))
                .room_number(values.get("room_number"))
                .operation(values.get("operation"))
                .status(Status.fromCode(values.get("status")))
                .is_success(ReservationResult.fromCode(values.get("is_success")))
                .error_msg(ReservationError.fromCode(values.get("error_msg")))
                .build();
    }

    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
