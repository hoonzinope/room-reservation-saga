package home.example.room_reserve_outer.service;

import home.example.room_reserve_outer.data.dto.RoomAvailability;
import home.example.room_reserve_outer.data.dto.RoomResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "reservation.failure-rate=0",
        "spring.datasource.url=jdbc:h2:mem:outer_room_service_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@Transactional
class RoomServiceTest {

    @Autowired
    private RoomService roomService;

    @Test
    void checkRoomAvailable_availableRoom_returnsAvailableMessage() {
        RoomAvailability response = roomService.checkRoomAvailable("101");

        assertThat(response.getRoomNumber()).isEqualTo("101");
        assertThat(response.getAvailability()).isTrue();
        assertThat(response.getMsg()).isEqualTo("room is available");
    }

    @Test
    void checkRoomAvailable_maintenanceRoom_returnsMaintenanceMessage() {
        RoomAvailability response = roomService.checkRoomAvailable("202");

        assertThat(response.getRoomNumber()).isEqualTo("202");
        assertThat(response.getAvailability()).isFalse();
        assertThat(response.getMsg()).isEqualTo("room is maintenance");
    }

    @Test
    void checkRoomAvailable_unknownRoom_returnsNotExistMessage() {
        RoomAvailability response = roomService.checkRoomAvailable("999");

        assertThat(response.getRoomNumber()).isEqualTo("999");
        assertThat(response.getAvailability()).isFalse();
        assertThat(response.getMsg()).isEqualTo("room not exist");
    }

    @Test
    void findRooms_returnsRoomNumberBasedSummariesWithoutInternalIds() {
        List<RoomResponse> response = roomService.findRooms();

        assertThat(response).hasSize(5);
        assertThat(response)
                .extracting(RoomResponse::getRoom_number)
                .containsExactlyInAnyOrder("101", "102", "201", "202", "301");
        assertThat(response)
                .filteredOn(room -> "101".equals(room.getRoom_number()))
                .singleElement()
                .satisfies(room -> {
                    assertThat(room.getRoom_type()).isEqualTo("STANDARD");
                    assertThat(room.getStatus()).isEqualTo("AVAILABLE");
                    assertThat(room.getAvailability()).isTrue();
                });
        assertThat(response)
                .filteredOn(room -> "202".equals(room.getRoom_number()))
                .singleElement()
                .satisfies(room -> {
                    assertThat(room.getRoom_type()).isEqualTo("DELUXE");
                    assertThat(room.getStatus()).isEqualTo("MAINTENANCE");
                    assertThat(room.getAvailability()).isFalse();
                });
    }
}
