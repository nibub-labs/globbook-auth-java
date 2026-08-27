package com.nibub.globbookauth;

/**
 * The authenticated user's profile, as returned by
 * {@link GlobbookAuthClient#getUserInfo}. Fields mirror the OIDC top-level
 * claims Globbook's {@code /api/v2/oauth/userinfo} endpoint returns.
 *
 * <p><b>Restricted claims</b> — {@code birthdate}, {@code gender},
 * {@code phoneNumber}, and {@code address} are {@code null} unless
 * <em>both</em> are true: your app is verified in the Globbook Developer
 * Console, and the user granted the matching scope (see {@link Scope}) on
 * the consent screen. An unverified app never receives these regardless of
 * what's requested or approved. Always null-check before use.
 */
public final class UserInfo {

    private final String sub;
    private final String preferredUsername;
    private final boolean profileVerified;
    private final String email;
    private final String name;
    private final String givenName;
    private final String familyName;
    private final String bio;
    private final String picture;
    private final String coverImage;
    private final String website;
    private final String birthdate;
    private final String gender;
    private final String phoneNumber;
    private final String address;

    UserInfo(Builder b) {
        this.sub = b.sub;
        this.preferredUsername = b.preferredUsername;
        this.profileVerified = b.profileVerified;
        this.email = b.email;
        this.name = b.name;
        this.givenName = b.givenName;
        this.familyName = b.familyName;
        this.bio = b.bio;
        this.picture = b.picture;
        this.coverImage = b.coverImage;
        this.website = b.website;
        this.birthdate = b.birthdate;
        this.gender = b.gender;
        this.phoneNumber = b.phoneNumber;
        this.address = b.address;
    }

    static Builder builder() {
        return new Builder();
    }

    /** OIDC subject identifier — an MD5 hash, NOT the raw numeric user id. Stable per-user. */
    public String getSub() {
        return sub;
    }

    public String getPreferredUsername() {
        return preferredUsername;
    }

    public boolean isProfileVerified() {
        return profileVerified;
    }

    public String getEmail() {
        return email;
    }

    /** First + last name joined by a space, or just one half if the other is empty. */
    public String getName() {
        return name;
    }

    public String getGivenName() {
        return givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    /** May be an empty string if unset. */
    public String getBio() {
        return bio;
    }

    /** Signed CDN URL, or {@code null} if the user has no avatar. Time-limited — don't cache long-term. */
    public String getPicture() {
        return picture;
    }

    /** Signed CDN URL, or {@code null}. Time-limited — don't cache long-term. */
    public String getCoverImage() {
        return coverImage;
    }

    /** May be an empty string if unset. */
    public String getWebsite() {
        return website;
    }

    /** Restricted claim, {@code YYYY-MM-DD}, or {@code null} — see the class Javadoc. */
    public String getBirthdate() {
        return birthdate;
    }

    /** Restricted claim, or {@code null} — see the class Javadoc. */
    public String getGender() {
        return gender;
    }

    /** Restricted claim, or {@code null} — see the class Javadoc. */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /** Restricted claim, {@code "city country"} (this platform stores no street-level address), or {@code null} — see the class Javadoc. */
    public String getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return "UserInfo{sub='" + sub + "', preferredUsername='" + preferredUsername + "', email='" + email + "'}";
    }

    static final class Builder {
        String sub = "";
        String preferredUsername = "";
        boolean profileVerified;
        String email = "";
        String name = "";
        String givenName = "";
        String familyName = "";
        String bio = "";
        String picture;
        String coverImage;
        String website = "";
        String birthdate;
        String gender;
        String phoneNumber;
        String address;

        UserInfo build() {
            return new UserInfo(this);
        }
    }
}
