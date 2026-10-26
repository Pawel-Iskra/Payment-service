package priv.home.paymentservice.outbox.model;

import lombok.Getter;

import java.util.UUID;


@Getter
public class PublishResult {

    private static final int MAX_ERROR_INFO_CHARS = 255; // TODO: configs

    private UUID eventId;
    private boolean published;
    private String errorInfo;


    public static PublishResult buildNotPublished(UUID eventId, Throwable exception) {
        PublishResult publishResult = new PublishResult();
        publishResult.eventId = eventId;
        publishResult.published = false;
        publishResult.errorInfo = buildErrorInfo(exception);
        return publishResult;
    }

    public static PublishResult buildPublished(UUID eventId) {
        PublishResult publishResult = new PublishResult();
        publishResult.eventId = eventId;
        publishResult.published = true;
        publishResult.errorInfo = null;
        return publishResult;
    }


    private static String buildErrorInfo(Throwable exception) {
        String errorInfo = "ErrorType = %s, Cause = %s, Message = %s"
                .formatted(exception.toString(), exception.getCause(), exception.getMessage());
        return errorInfo.length() > MAX_ERROR_INFO_CHARS ? errorInfo.substring(0, MAX_ERROR_INFO_CHARS) : errorInfo;
    }

    private PublishResult() {
    }
}
