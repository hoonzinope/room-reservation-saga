package home.example.room_reserve_outer.service;

import home.example.room_reserve_outer.data.dto.RoomAvailability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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
}
