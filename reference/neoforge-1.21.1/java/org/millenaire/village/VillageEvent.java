package org.millenaire.village;

import javax.annotation.Nullable;

public record VillageEvent(long gameTime, VillageEventType type, String param1, @Nullable String param2) {
}
