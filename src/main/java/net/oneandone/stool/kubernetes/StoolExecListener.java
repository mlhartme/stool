package net.oneandone.stool.kubernetes;

import io.fabric8.kubernetes.client.dsl.ExecListener;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.List;

public class StoolExecListener implements ExecListener {
    public Response openResponse;
    public List<Throwable> failures;
    public Integer closeCode;
    public String closeReason;

    public StoolExecListener() {
        this.openResponse = null;
        this.failures = new ArrayList<>();
        this.closeCode = null;
        this.closeReason = null;
    }

    @Override
    public void onOpen(Response response) {
        if (openResponse != null) {
            throw new IllegalStateException(response + " vs " + openResponse);
        }
        openResponse = response;
    }

    @Override
    public void onFailure(Throwable t, Response response) {
        failures.add(t);
    }

    @Override
    public void onClose(int code, String reason) {
        if (closeCode != null) {
            throw new IllegalStateException(code + " vs " + closeCode);
        }
        closeCode = code;
        closeReason = reason;
    }
}
