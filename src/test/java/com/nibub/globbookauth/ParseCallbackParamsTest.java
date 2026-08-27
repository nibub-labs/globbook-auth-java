package com.nibub.globbookauth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ParseCallbackParamsTest {

    @Test
    void parsesCodeAndStateFromFullUrl() {
        CallbackParams params = GlobbookAuthClient.parseCallbackParams("https://example.com/callback?code=abc123&state=xyz789");
        assertEquals("abc123", params.code());
        assertEquals("xyz789", params.state());
    }

    @Test
    void parsesFromPathAndQuery() {
        CallbackParams params = GlobbookAuthClient.parseCallbackParams("/callback?code=abc123");
        assertEquals("abc123", params.code());
        assertNull(params.state());
    }

    @Test
    void parsesFromBareQueryString() {
        CallbackParams params = GlobbookAuthClient.parseCallbackParams("code=abc123&state=xyz789");
        assertEquals("abc123", params.code());
        assertEquals("xyz789", params.state());
    }

    @Test
    void missingCodeIsNull() {
        CallbackParams params = GlobbookAuthClient.parseCallbackParams("https://example.com/callback?state=xyz789");
        assertNull(params.code());
        assertEquals("xyz789", params.state());
    }

    @Test
    void emptyInputYieldsAllNull() {
        CallbackParams params = GlobbookAuthClient.parseCallbackParams("");
        assertNull(params.code());
        assertNull(params.state());
    }
}
