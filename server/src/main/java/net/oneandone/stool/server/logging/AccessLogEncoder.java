package net.oneandone.stool.server.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;

import java.io.UnsupportedEncodingException;


public class AccessLogEncoder extends EncoderBase<ILoggingEvent> {
    private static final byte[] EMPTY = new byte[0];

    @Override
    public byte[] headerBytes() {
        return EMPTY;
    }

    @Override
    public byte[] footerBytes() {
        return EMPTY;
    }

    public AccessLogEncoder() { }

    @Override
    public byte[] encode(ILoggingEvent event) {
        LogEntry entry;

        entry = LogEntry.forEvent(event);
        try {
            return entry.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException();
        }
    }
}
