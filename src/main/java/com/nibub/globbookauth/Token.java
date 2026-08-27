package com.nibub.globbookauth;

/**
 * The access token issued by Globbook's token endpoint in exchange for a
 * valid authorization code.
 *
 * @param accessToken bearer token to present to {@link GlobbookAuthClient#getUserInfo}. Never log this value.
 * @param tokenType   always {@code "Bearer"} for tokens issued by Globbook.
 * @param expiresIn   seconds from issuance until expiry. Globbook does not currently issue refresh tokens.
 */
public record Token(String accessToken, String tokenType, int expiresIn) {

    @Override
    public String toString() {
        // Redact accessToken so an accidental toString()/log of a Token never leaks it.
        return "Token{accessToken='[REDACTED]', tokenType='" + tokenType + "', expiresIn=" + expiresIn + "}";
    }
}
