package com.nibub.globbookauth.internal;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void parsesFlatObjectOfMixedTypes() {
        Map<String, Object> obj = Json.parseObject(
                "{\"sub\":\"md5hash\",\"profile_verified\":true,\"expires_in\":3600,\"picture\":null}"
        );
        assertEquals("md5hash", obj.get("sub"));
        assertEquals(Boolean.TRUE, obj.get("profile_verified"));
        assertEquals(3600.0, (Double) obj.get("expires_in"));
        assertTrue(obj.containsKey("picture"));
        assertNull(obj.get("picture"));
    }

    @Test
    void missingKeyIsAbsentNotNull() {
        Map<String, Object> obj = Json.parseObject("{\"sub\":\"md5hash\"}");
        assertFalse(obj.containsKey("gender"));
        assertNull(obj.get("gender"));
    }

    @Test
    void parsesEmptyObject() {
        Map<String, Object> obj = Json.parseObject("{}");
        assertTrue(obj.isEmpty());
    }

    @Test
    void parsesEscapedStringCharacters() {
        Map<String, Object> obj = Json.parseObject("{\"name\":\"Jane \\\"J\\\" Doe\\nLine2\"}");
        assertEquals("Jane \"J\" Doe\nLine2", obj.get("name"));
    }

    @Test
    void parsesUnicodeEscape() {
        Map<String, Object> obj = Json.parseObject("{\"emoji\":\"\\u00e9\"}");
        assertEquals("\u00e9", obj.get("emoji"));
    }

    @Test
    void parsesNegativeAndDecimalNumbers() {
        Map<String, Object> obj = Json.parseObject("{\"a\":-5,\"b\":3.14}");
        assertEquals(-5.0, (Double) obj.get("a"));
        assertEquals(3.14, (Double) obj.get("b"));
    }

    @Test
    void whitespaceAroundTokensIsIgnored() {
        Map<String, Object> obj = Json.parseObject("  {  \"a\"  :  \"b\"  ,  \"c\" : 1  }  ");
        assertEquals("b", obj.get("a"));
        assertEquals(1.0, (Double) obj.get("c"));
    }

    @Test
    void malformedJsonThrows() {
        assertThrows(Json.JsonException.class, () -> Json.parseObject("{not valid json"));
    }

    @Test
    void trailingContentThrows() {
        assertThrows(Json.JsonException.class, () -> Json.parseObject("{\"a\":1} garbage"));
    }

    @Test
    void nestedObjectIsRejected() {
        assertThrows(Json.JsonException.class, () -> Json.parseObject("{\"a\":{\"b\":1}}"));
    }
}
