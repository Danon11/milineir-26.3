package org.millenaire.fabric.paint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaintBucketItemTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void recognizesBothWhiteBucketIdsAndSilver() {
        assertEquals("white", PaintBucketItem.colorFromName("paintbucketwhite").orElseThrow());
        assertEquals("white", PaintBucketItem.colorFromName("millenaire:paint_bucket_white").orElseThrow());
        assertEquals("silver", PaintBucketItem.colorFromName("paint_bucket_silver").orElseThrow());
        assertTrue(PaintBucketItem.colorFromName("paint_bucket_light_gray").isEmpty());
    }
}
