package home.example.room_reserve_outer.service;

import home.example.room_reserve_outer.data.dto.RoomAvailability;
import home.example.room_reserve_outer.data.entity.Room;
import home.example.room_reserve_outer.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public RoomAvailability checkRoomAvailable(String room_number) {
        Optional<Room> optionalRoom = roomRepository.findForAvailabilityCheck(room_number);
        RoomAvailability roomAvailability = new RoomAvailability();
        roomAvailability.setRoomNumber(room_number);

        if(optionalRoom.isPresent()) {
            Room room = optionalRoom.get();
            if(room.isAvailable()){
                roomAvailability.setAvailability(true);
                roomAvailability.setMsg("room is available");
            } else {
                roomAvailability.setAvailability(false);
                roomAvailability.setMsg("room is maintenance");
            }
        } else {
            roomAvailability.setAvailability(false);
            roomAvailability.setMsg("room not exist");
        }
        return roomAvailability;
    }

}
