package home.example.room_reserve_outer.data.dto;

import home.example.room_reserve_outer.data.type.Operation;
import home.example.room_reserve_outer.data.type.Status;
import home.example.room_reserve_outer.util.HashUtil;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ReservationRequest {
    private String idempotency_key;
    private String room_number;
    private String guest_name;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private String operation;

    public String hash() {
        String input = String.join("|",
                String.valueOf(idempotency_key),
                String.valueOf(room_number),
                String.valueOf(guest_name),
                String.valueOf(checkIn),
                String.valueOf(checkOut),
                String.valueOf(operation));
        return HashUtil.hash(input);
    }

    public boolean hasAllParam() {
        return this.idempotency_key != null && !this.idempotency_key.trim().isEmpty()
                && this.room_number != null && !this.room_number.trim().isEmpty()
                && this.guest_name != null && !this.guest_name.trim().isEmpty()
                && this.checkIn != null && this.checkOut != null
                && this.operation != null && !this.operation.trim().isEmpty();
    }

    public boolean isValidCheckDate() {
        return this.checkIn.isBefore(this.checkOut);
    }

    public boolean isValidOperation() {
        return operation.equalsIgnoreCase(Operation.CREATE_RESERVATION.getCode()) || operation.equalsIgnoreCase(Operation.CANCEL_RESERVATION.getCode());
    }

    public boolean isCreateReservationOperation() {
        return operation.equalsIgnoreCase(Operation.CREATE_RESERVATION.getCode());
    }
}
