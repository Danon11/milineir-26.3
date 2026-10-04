package org.millenaire.fabric.content;

import java.util.List;

public record VillagerTypeDefinition(String culture, String id, String name, String gender,
                                    String familyNameList, String firstNameList, List<String> textures,
                                    List<String> clothes, List<String> goals, LegacyDocument source) {
    public VillagerTypeDefinition {
        textures = List.copyOf(textures); clothes = List.copyOf(clothes); goals = List.copyOf(goals);
    }
    public static VillagerTypeDefinition from(String culture, String id, LegacyDocument source) {
        return new VillagerTypeDefinition(culture, id, source.first("native_name", source.first("nativename", id)),
                source.first("gender", ""), source.first("familynamelist", ""), source.first("firstnamelist", ""),
                source.values("texture"), source.values("clothes"), source.values("goal"), source);
    }
}
