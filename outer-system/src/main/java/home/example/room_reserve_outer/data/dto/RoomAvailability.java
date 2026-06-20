package home.example.room_reserve_outer.data.dto;

import lombok.Data;

@Data
public class RoomAvailability {
    private String roomNumber;
    private Boolean availability;
    private String msg;
}
