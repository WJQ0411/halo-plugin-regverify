package run.halo.regverify;

/**
 * Data exposed to the verification script for a single attempt.
 */
public record VerifyInput(
    String answer,
    String username,
    String email,
    String displayName,
    String ip
) {

    public VerifyInput {
        answer = answer == null ? "" : answer;
        username = username == null ? "" : username;
        email = email == null ? "" : email;
        displayName = displayName == null ? "" : displayName;
        ip = ip == null ? "" : ip;
    }

    @Override
    public String toString() {
        // Never log the answer itself.
        return "VerifyInput[username=%s, ip=%s]".formatted(username, ip);
    }
}
