package net.pytsing.qingcharms.manager;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CharmItemAuthTest {

    private static final byte[] SECRET =
            "test-secret".getBytes(StandardCharsets.UTF_8);

    @Test
    public void acceptsOnlyTokensSignedForTheSameCharmAndInstance() {
        String token = CharmItemAuth.createToken("example", "instance-a", SECRET);

        assertTrue(CharmItemAuth.verifyToken(token, SECRET, "instance-a"));
        assertFalse(CharmItemAuth.verifyToken(token,
                "other-secret".getBytes(StandardCharsets.UTF_8), "instance-a"));
        assertFalse(CharmItemAuth.verifyToken(token, SECRET, "instance-b"));

        String transplanted = token.replace("|example|", "|other|");
        assertFalse(CharmItemAuth.verifyToken(transplanted, SECRET, "instance-a"));
    }

    @Test
    public void rejectsMalformedAndUnsignedTokens() {
        assertFalse(CharmItemAuth.verifyToken("", SECRET, "instance-a"));
        assertFalse(CharmItemAuth.verifyToken(
                "@qc-auth|1|example", SECRET, "instance-a"));
        assertFalse(CharmItemAuth.verifyToken(
                "@qc-auth|1|example|not-a-signature", SECRET, "instance-a"));
    }

    @Test
    public void acceptsChineseCharmIdsFromQcItemsFileNames() {
        String token = CharmItemAuth.createToken("\u9634\u95f4\u62a4\u7b26",
                "instance-a", SECRET);

        assertTrue(CharmItemAuth.verifyToken(token, SECRET, "instance-a"));
        assertFalse(CharmItemAuth.verifyToken(token, SECRET, "instance-b"));
        assertFalse(CharmItemAuth.verifyToken(
                "@qc-auth|1|bad|id|signature", SECRET, "instance-a"));
    }
}
