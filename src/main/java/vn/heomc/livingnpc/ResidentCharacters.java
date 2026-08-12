package vn.heomc.livingnpc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ResidentCharacters {
    static final UUID THANH_UUID = UUID.fromString("46a5553d-cedc-428f-b51a-4f5ddec03c9b");
    static final UUID KEYDEN_UUID = UUID.fromString("084e73d7-7aa8-42e1-b7e4-a8dcb4bd9484");

    private ResidentCharacters() {
    }

    static ResidentProfile enrich(UUID npcUuid, ResidentProfile profile) {
        if (profile.hasCharacterDetails()) {
            return profile;
        }
        if (THANH_UUID.equals(npcUuid)) {
            return new ResidentProfile(
                    profile.id(), profile.name(), profile.gender(), "Người anh từ Redfield", profile.roles(), profile.skin(),
                    "Người anh điềm tĩnh rời làng Redfield để cùng em trai tìm một nơi đáng giúp đỡ.",
                    List.of("Bình tĩnh", "Có trách nhiệm", "Luôn bảo vệ em trai"),
                    "Cung", List.of("Kiếm tiền và chăm lo cho Keyden", "Giúp đỡ nơi hai anh em dừng chân"),
                    Map.of(KEYDEN_UUID, new ResidentRelationship("em trai", "Keyden_Redfield")));
        }
        if (KEYDEN_UUID.equals(npcUuid)) {
            return new ResidentProfile(
                    profile.id(), profile.name(), profile.gender(), "Người em từ Redfield", profile.roles(), profile.skin(),
                    "Người em gần gũi rời làng Redfield và thường đồng hành cùng anh trai Thanh.",
                    List.of("Gần gũi", "Tin tưởng anh trai", "Thích đồng hành"),
                    "Kiếm", List.of("Cùng Thanh gây dựng cuộc sống mới", "Tìm người đang cần hai anh em giúp đỡ"),
                    Map.of(THANH_UUID, new ResidentRelationship("anh trai", "ThanhRedfield")));
        }
        return profile;
    }
}
