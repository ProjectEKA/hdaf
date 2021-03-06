package in.projecteka.consentmanager.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.projecteka.library.clients.model.Notification;
import org.apache.commons.lang3.RandomStringUtils;
import org.jeasy.random.EasyRandom;

import java.time.LocalDateTime;

public class TestBuilders {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final EasyRandom easyRandom = new EasyRandom();

    public static String string() {
        return easyRandom.nextObject(String.class);
    }

    public static String string(int size) {
        return RandomStringUtils.randomNumeric(size).strip();
    }

    public static LocalDateTime localDateTime() {
        return easyRandom.nextObject(LocalDateTime.class);
    }

    public static Notification.NotificationBuilder notificationMessage() {
        return easyRandom.nextObject(Notification.NotificationBuilder.class);
    }
}
