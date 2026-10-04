package edu.cit.loquillano.channel;

/** A failed call to Tiangge. retryable = timeouts, 429 and 5xx; anything else is permanent. */
class TianggeException extends RuntimeException {

    private final int status;
    private final String errorCode;
    private final boolean retryable;

    TianggeException(String message, int status, String errorCode, boolean retryable) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    int getStatus() {
        return status;
    }

    String getErrorCode() {
        return errorCode;
    }

    boolean isRetryable() {
        return retryable;
    }

    @Override
    public String toString() {
        return "TianggeException[" + status + (errorCode != null ? " " + errorCode : "") + ": " + getMessage() + "]";
    }
}
