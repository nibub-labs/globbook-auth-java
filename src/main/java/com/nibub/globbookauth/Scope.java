package com.nibub.globbookauth;

/**
 * Restricted OIDC-style scopes you may pass to
 * {@link AuthorizationUrlOptions.Builder#scopes} to request restricted
 * userinfo claims. Requesting a scope only has an effect if your app has
 * been verified in the Globbook Developer Console — an unverified app's
 * consent screen never offers these regardless of what's requested, and
 * {@link GlobbookAuthClient#getUserInfo} never returns them either way
 * unless the user actually grants them at consent time.
 */
public final class Scope {
    private Scope() {
    }

    public static final String BIRTHDATE = "birthdate";
    public static final String GENDER = "gender";
    public static final String PHONE = "phone";
    public static final String ADDRESS = "address";
}
