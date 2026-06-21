package home.example.room_reserve_outer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class RandomFailureSimulator {
    private final double failureRate;
    private final long timeoutMillis;

    public RandomFailureSimulator(@Value("${reservation.failure-rate:0}") double failureRate,
                                  @Value("${reservation.failure-timeout-millis:3000}") long timeoutMillis) {
        this.failureRate = normalizeFailureRate(failureRate);
        this.timeoutMillis = Math.max(0, timeoutMillis);
        log.info("random failure simulator configured failureRate={} timeoutMillis={}",
                this.failureRate,
                this.timeoutMillis);
    }

    public void maybeFail() {
        if(failureRate <= 0 || ThreadLocalRandom.current().nextDouble() >= failureRate) {
            return;
        }

        int failureType = ThreadLocalRandom.current().nextInt(3);
        if(failureType == 0) {
            log.warn("random failure simulator triggered type=server-error");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Simulated reservation API failure");
        }
        if(failureType == 1) {
            log.warn("random failure simulator triggered type=timeout timeoutMillis={}", timeoutMillis);
            sleepBeforeTimeout();
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Simulated reservation API timeout");
        }

        log.warn("random failure simulator triggered type=connection-reset");
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Simulated reservation API connection reset");
    }

    private void sleepBeforeTimeout() {
        if(timeoutMillis == 0) {
            return;
        }

        try {
            Thread.sleep(timeoutMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("random failure simulator timeout interrupted");
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Simulated reservation API timeout", e);
        }
    }

    private double normalizeFailureRate(double configuredFailureRate) {
        if(configuredFailureRate < 0) {
            return 0;
        }
        if(configuredFailureRate > 1) {
            return 1;
        }
        return configuredFailureRate;
    }
}
