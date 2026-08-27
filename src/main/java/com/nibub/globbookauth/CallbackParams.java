package com.nibub.globbookauth;

/**
 * Result of {@link GlobbookAuthClient#parseCallbackParams}.
 *
 * @param code  the authorization code to pass to {@link GlobbookAuthClient#exchangeCodeForToken}, or {@code null} if not present.
 * @param state the CSRF-protection value Globbook echoed back, if you passed one to
 *              {@link GlobbookAuthClient#getAuthorizationUrl}. {@code null} if you didn't send one, or it wasn't
 *              present in the callback. If you sent one, compare this against what you stored before redirecting
 *              and reject the callback on a mismatch — see the README's "CSRF protection (state)" section.
 */
public record CallbackParams(String code, String state) {
}
