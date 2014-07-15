package guru.nidi.ftpsync;

import java.io.IOException;
import java.util.Arrays;

/**
 *
 */
public class FtpException extends IOException {
    private final String[] replyStrings;

    public FtpException(String message, String[] replyStrings) {
        super(message);
        this.replyStrings = replyStrings;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + ": " + Arrays.toString(replyStrings);
    }
}
