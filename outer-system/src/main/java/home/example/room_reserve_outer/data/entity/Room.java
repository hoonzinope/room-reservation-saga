package home.example.room_reserve_outer.data.entity;

import lombok.Getter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "rooms")
public class Room {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String roomNumber;
    private String roomType;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isAvailable(){
        return this.status.equalsIgnoreCase("available");
    }
}
