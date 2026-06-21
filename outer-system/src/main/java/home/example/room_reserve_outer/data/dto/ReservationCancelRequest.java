package home.example.room_reserve_outer.data.dto;

import lombok.Data;

@Data
public class ReservationCancelRequest {
    private String idempotency_key;
    private String create_idempotency_key;
}
