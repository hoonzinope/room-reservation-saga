package home.example.room_reserve_outer.service;

import home.example.room_reserve_outer.data.dto.RoomAvailability;
import home.example.room_reserve_outer.data.dto.RoomResponse;
import home.example.room_reserve_outer.data.entity.Room;
import home.example.room_reserve_outer.repository.RoomRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public RoomAvailability checkRoomAvailable(String room_number) {
        log.info("room availability check started roomNumber={}", room_number);
        Optional<Room> optionalRoom = roomRepository.findForAvailabilityCheck(room_number);
        RoomAvailability roomAvailability = new RoomAvailability();
        roomAvailability.setRoomNumber(room_number);

        if(optionalRoom.isPresent()) {
            Room room = optionalRoom.get();
            if(room.isAvailable()){
                roomAvailability.setAvailability(true);
                roomAvailability.setMsg("room is available");
                log.info("room availability check completed roomNumber={} availability=true status={}",
                        room_number,
                        room.getStatus());
            } else {
                roomAvailability.setAvailability(false);
                roomAvailability.setMsg("room is maintenance");
                log.info("room availability check completed roomNumber={} availability=false status={}",
                        room_number,
                        room.getStatus());
            }
        } else {
            roomAvailability.setAvailability(false);
            roomAvailability.setMsg("room not exist");
            log.warn("room availability check failed roomNumber={} reason=not-found", room_number);
        }
        return roomAvailability;
    }

    public List<RoomResponse> findRooms() {
        log.info("room list started");
        List<RoomResponse> rooms = roomRepository.findAll().stream()
                .map(this::toRoomResponse)
                .collect(Collectors.toList());
        log.info("room list completed count={}", rooms.size());
        return rooms;
    }

    private RoomResponse toRoomResponse(Room room) {
        return RoomResponse.builder()
                .room_number(room.getRoomNumber())
                .room_type(room.getRoomType())
                .status(room.getStatus())
                .availability(room.isAvailable())
                .build();
    }

}
