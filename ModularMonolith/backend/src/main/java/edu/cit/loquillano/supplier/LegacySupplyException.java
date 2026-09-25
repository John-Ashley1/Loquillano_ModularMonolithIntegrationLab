package edu.cit.loquillano.supplier;

/**
 * Internal signal for a failed LegacySupply call. Never escapes this
 * package — SupplierGatewayImpl catches it and translates to a
 * ReorderResult / SupplierOrderStatus instead.
 */
class LegacySupplyException extends RuntimeException {

    final int httpStatus;
    final String errorCode;
    final boolean retryable;

    LegacySupplyException(String message, int httpStatus, String errorCode, boolean retryable) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    LegacySupplyException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.httpStatus = -1;
        this.errorCode = null;
        this.retryable = retryable;
    }
}
