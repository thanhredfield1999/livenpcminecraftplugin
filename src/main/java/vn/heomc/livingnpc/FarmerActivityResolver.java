package vn.heomc.livingnpc;

final class FarmerActivityResolver {
    private FarmerActivityResolver() {
    }

    static FarmerPhase resolvePhase(
            ResidentRole activeRole,
            FarmerPhase sharedPhase,
            FarmerPhase fisherPhase,
            FarmerPhase merchantPhase,
            FarmerPhase civilPhase) {
        if (sharedPhase == FarmerPhase.GOING_TO_BED || sharedPhase == FarmerPhase.SLEEPING) {
            return sharedPhase;
        }
        if (activeRole == ResidentRole.FISHER && fisherPhase != null) return fisherPhase;
        if (activeRole == ResidentRole.MERCHANT && merchantPhase != null) return merchantPhase;
        if (CivilProfessionRuntime.zoneFor(activeRole) != null && civilPhase != null) return civilPhase;
        return sharedPhase;
    }

    static String describeActivity(FarmerPhase phase) {
        return switch (phase == null ? FarmerPhase.INACTIVE : phase) {
            case INACTIVE -> "Đang nghỉ hoặc chờ ca";
            case GOING_HOME -> "Đang về nhà";
            case GOING_TO_BED -> "Đang đi tới giường";
            case SLEEPING -> "Đang ngủ";
            case WAKING_UP -> "Đang thức dậy";
            case LEAVING_HOME -> "Đang rời khỏi nhà";
            case MORNING_ACTIVITY -> "Đang bắt đầu buổi sáng";
            case GOING_TO_PLOT -> "Đang đi tới ruộng";
            case FINDING_WORK -> "Đang tìm việc";
            case GOING_TO_CROP -> "Đang đi tới cây trồng";
            case INSPECTING -> "Đang kiểm tra cây";
            case WORKING -> "Đang làm việc";
            case GOING_TO_STORAGE -> "Đang mang hàng tới kho";
            case DEPOSITING -> "Đang giao hàng vào kho";
            case RETURNING_TO_PLOT -> "Đang quay lại khu làm việc";
            case LUNCH_BREAK -> "Đang nghỉ trưa";
            case GOING_TO_MARKET -> "Đang đi tới chợ";
            case SHOPPING -> "Đang mua sắm";
            case GOING_TO_SCENIC -> "Đang đi ngắm cảnh";
            case SOCIALIZING -> "Đang trò chuyện";
            case GOING_TO_SEAT -> "Đang đi tới ghế";
            case SITTING_REST -> "Đang ngồi nghỉ";
            case SITTING_DINING -> "Đang dùng bữa";
            case STANDING_UP -> "Đang đứng dậy";
            case RESTING -> "Đang nghỉ";
            case LOOKING_AROUND -> "Đang nhìn xung quanh";
            case WANDERING -> "Đang đi dạo";
            case WATCHING_PLAYER -> "Đang quan sát người chơi";
            case SHELTERING -> "Đang tránh nguy hiểm";
            case GOING_TO_FISHING_SPOT -> "Đang đi tới điểm câu";
            case CASTING_LINE -> "Đang thả câu";
            case WAITING_FOR_BITE -> "Đang chờ cá";
            case REELING_IN -> "Đang kéo cá";
            case GOING_TO_WORK_STATION -> "Đang đi tới trạm nghề";
            case PRODUCING -> "Đang sản xuất";
            case PATROLLING -> "Đang tuần tra";
            case ALERTING -> "Đang báo động";
            case GOING_TO_STALL -> "Đang đi tới cửa hàng";
            case OPENING_STALL -> "Đang mở quầy";
            case SERVING -> "Đang phục vụ tại quầy";
        };
    }
}
