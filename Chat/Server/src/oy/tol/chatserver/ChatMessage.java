package oy.tol.chatserver;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class ChatMessage {
    public LocalDateTime sent;
    public String nick;
    public String message;

    long dateAsInt() {
        return sent.toEpochSecond(ZoneOffset.UTC);
    }
}
