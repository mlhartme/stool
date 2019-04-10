package net.oneandone.stool.server.util;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.Map;


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
        Map<String, String> mdc;

        mdc = event.getMDCPropertyMap();
        entry = new LogEntry(LocalDateTime.now(), mdc.get("client-invocation"), "COMMAND", mdc.get("user"), mdc.get("stage"), mdc.get("client-command"));
        try {
            return entry.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException();
        }
    }
}
