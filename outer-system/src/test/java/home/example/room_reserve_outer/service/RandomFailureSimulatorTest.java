package home.example.room_reserve_outer.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RandomFailureSimulatorTest {

    @Test
    void maybeFail_zeroFailureRate_neverThrows() {
        RandomFailureSimulator simulator = new RandomFailureSimulator(0, 0);

        for(int i = 0; i < 20; i++) {
            assertThatCode(simulator::maybeFail).doesNotThrowAnyException();
        }
    }

    @Test
    void maybeFail_negativeFailureRate_isTreatedAsZero() {
        RandomFailureSimulator simulator = new RandomFailureSimulator(-1, 0);

        for(int i = 0; i < 20; i++) {
            assertThatCode(simulator::maybeFail).doesNotThrowAnyException();
        }
    }

    @Test
    void maybeFail_failureRateGreaterThanOne_isTreatedAsAlwaysFail() {
        RandomFailureSimulator simulator = new RandomFailureSimulator(2, 0);

        for(int i = 0; i < 20; i++) {
            assertThatThrownBy(simulator::maybeFail)
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatus()).isIn(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            HttpStatus.GATEWAY_TIMEOUT,
                            HttpStatus.SERVICE_UNAVAILABLE));
        }
    }

    @Test
    void maybeFail_fullFailureRate_throwsSimulatedApiFailure() {
        RandomFailureSimulator simulator = new RandomFailureSimulator(1, 0);

        assertThatThrownBy(simulator::maybeFail)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Simulated reservation API");
    }
}
