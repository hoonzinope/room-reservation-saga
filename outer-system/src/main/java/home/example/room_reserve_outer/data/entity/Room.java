package home.example.room_reserve_outer.data.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
public class Room {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
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
