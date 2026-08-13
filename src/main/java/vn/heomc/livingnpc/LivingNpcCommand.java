package vn.heomc.livingnpc;

import java.util.Arrays;
import java.util.List;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;

final class LivingNpcCommand implements CommandExecutor, TabCompleter {
    private final LivingNpcPlugin plugin;

    LivingNpcCommand(LivingNpcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            list(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "help", "guide" -> help(sender);
            case "create" -> create(sender, args);
            case "adopt", "tiepnhan" -> adopt(sender, args);
            case "assignvillage", "ganlang" -> assignVillage(sender, args);
            case "lang", "village" -> village(sender, args);
            case "setkho", "setstorage" -> setStorage(sender, args);
            case "setdiem", "setsocial" -> setSocialPoint(sender, args);
            case "list" -> list(sender);
            case "cancel" -> cancel(sender);
            case "sethome" -> setHome(sender, args);
            case "setplot" -> setPlot(sender, args);
            case "status" -> status(sender, args);
            case "remove" -> remove(sender, args);
            case "reload" -> reload(sender);
            default -> {
                error(sender, "Lệnh không tồn tại: " + args[0]);
                help(sender);
            }
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Component.text("LivingNPC - Hướng dẫn quản lý", NamedTextColor.GOLD));
        guide(sender, "/lnpc", "Mở bảng quản lý làng và NPC.");
        sender.sendMessage(Component.text("Thêm một NPC Citizens có sẵn:", NamedTextColor.YELLOW));
        guide(sender, "/npc list", "Xem ID các NPC Citizens.");
        guide(sender, "/lnpc tiepnhan <npc-id> <làng-id>", "Đưa NPC Citizens vào LivingNPC và làng đã chọn.");
        sender.sendMessage(Component.text("Ví dụ: /lnpc tiepnhan 8 stillcliff_1", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("Tạo NPC LivingNPC mới tại vị trí đang đứng:", NamedTextColor.YELLOW));
        guide(sender, "/lnpc create <tên>", "Tạo NPC mới bằng Citizens.");
        guide(sender, "/lnpc ganlang <npc-id> <làng-id>", "Gán NPC vừa tạo vào một làng.");
        sender.sendMessage(Component.text("Thiết lập sau khi thêm:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("1. Shift + click phải NPC để mở setup.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("2. Chọn nghề, đặt Nhà và khu làm việc tương ứng.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("3. Bật NPC hoạt động khi cấu hình đã sẵn sàng.", NamedTextColor.GREEN));
        guide(sender, "/lnpc status <npc-id>", "Xem nghề, lịch và phần cấu hình còn thiếu.");
        guide(sender, "/lnpc cancel", "Hủy thao tác đang chờ chọn block.");
        sender.sendMessage(Component.text("Nếu chưa có làng:", NamedTextColor.YELLOW));
        guide(sender, "/lnpc lang tao <id> <tên hiển thị>", "Tạo làng tại vị trí đang đứng.");
    }

    private void guide(CommandSender sender, String command, String description) {
        sender.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                .append(commandLink(command, description))
                .append(Component.text(" - " + description, NamedTextColor.GRAY)));
    }

    private Component commandLink(String command, String hover) {
        return Component.text(command, NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.YELLOW)));
    }

    private void create(CommandSender sender, String[] args) {
        error(sender, "Hãy dùng /lnpc, chọn làng và Tạo NPC; hệ thống bắt buộc chọn đúng block giường.");
    }

    private void adopt(CommandSender sender, String[] args) {
        Integer id = parseId(sender, args, "/livingnpc tiepnhan <npc-id> <làng-id>");
        if (id != null && args.length >= 3) {
            result(sender, plugin.manager().adopt(id, args[2]),
                    "Đã tiếp nhận NPC Citizens " + id + " vào làng " + args[2]
                            + ". Dùng /livingnpc status " + id + " để xem còn thiếu gì.");
        } else if (id != null) {
            error(sender, "Cách dùng: /livingnpc tiepnhan <npc-id> <làng-id>");
        }
    }

    private void assignVillage(CommandSender sender, String[] args) {
        Integer id = parseId(sender, args, "/livingnpc ganlang <npc-id> <làng-id>");
        if (id != null && args.length >= 3) {
            result(sender, plugin.manager().assignVillage(id, args[2]),
                    "Đã gán NPC " + id + " vào làng " + args[2] + ".");
        } else if (id != null) {
            error(sender, "Cách dùng: /livingnpc ganlang <npc-id> <làng-id>");
        }
    }

    private void village(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || args.length < 4 || !args[1].equalsIgnoreCase("tao")) {
            error(sender, "Cách dùng: /livingnpc lang tao <id> <tên hiển thị>");
            return;
        }
        String id = args[2];
        String name = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        result(sender, plugin.villages().create(id, name, player.getLocation()),
                "Đã tạo làng " + name + " (ID: " + id + ") tại world " + player.getWorld().getName() + ".");
    }

    private void setStorage(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || args.length < 2) {
            error(sender, "Cách dùng: /livingnpc setkho <làng-id>, đồng thời nhìn vào rương hoặc thùng.");
            return;
        }
        org.bukkit.block.Block target = player.getTargetBlockExact(6);
        if (target == null) {
            error(sender, "Không tìm thấy block bạn đang nhìn trong phạm vi 6 block.");
            return;
        }
        result(sender, plugin.villages().setDeliveryChest(args[1], target.getLocation()),
                "Đã đặt rương giao hàng cho làng " + args[1] + ".");
    }

    private void setSocialPoint(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || args.length < 3) {
            error(sender, "Cách dùng: /livingnpc setdiem <làng-id> <cho|ngamcanh>");
            return;
        }
        result(sender, plugin.villages().setSocialPoint(args[1], args[2].toLowerCase(), player.getLocation()),
                "Đã đặt điểm " + args[2] + " cho làng " + args[1] + ".");
    }

    private void list(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player != null) {
            plugin.residentGui().openList(player);
        }
    }

    private void cancel(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player != null) {
            plugin.residentGui().cancelPlacement(player);
        }
    }

    private void setHome(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        Integer id = parseId(sender, args, "/livingnpc sethome <npc-id>");
        if (player != null && id != null) {
            result(sender, plugin.manager().setHome(id, player.getLocation()), "Đã cập nhật vị trí nhà.");
        }
    }

    private void setPlot(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        Integer id = parseId(sender, args, "/livingnpc setplot <npc-id> [radius]");
        if (player == null || id == null) {
            return;
        }
        int radius = 4;
        if (args.length >= 3) {
            try {
                radius = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                error(sender, "Bán kính phải là một số.");
                return;
            }
        }
        if (radius < 1 || radius > plugin.config().maxPlotRadius()) {
            error(sender, "Bán kính phải từ 1 đến " + plugin.config().maxPlotRadius() + ".");
            return;
        }
        result(sender, plugin.manager().setPlot(id, player.getLocation(), radius), "Đã cập nhật ruộng với bán kính " + radius + ".");
    }

    private void status(CommandSender sender, String[] args) {
        Integer id = parseId(sender, args, "/livingnpc status <npc-id>");
        if (id == null) {
            return;
        }
        FarmerDefinition farmer = plugin.manager().get(id);
        FarmerPhase phase = plugin.manager().phase(id);
        if (farmer == null) {
            error(sender, "NPC này chưa được LivingNPC quản lý. Dùng /livingnpc tiepnhan <id>.");
            return;
        }
        if (phase != FarmerPhase.GOING_TO_BED && phase != FarmerPhase.SLEEPING) {
            if (farmer.activeRole() == ResidentRole.FISHER) {
                phase = plugin.fishers().phase(farmer.npcUuid());
            } else if (CivilProfessionRuntime.zoneFor(farmer.activeRole()) != null) {
                phase = plugin.civilProfessions().phase(farmer.npcUuid());
            }
        }
        sender.sendMessage(Component.text("Cư dân " + id + ": " + farmer.profile().name(), NamedTextColor.GOLD));
        sender.sendMessage(Component.text(
                farmer.profile().title() + " | giới tính=" + farmer.profile().gender()
                        + " | nghề đang chạy=" + farmer.activeRole().storageKey(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("UUID: " + farmer.npcUuid(), NamedTextColor.DARK_GRAY));
        org.bukkit.Location home = farmer.home().resolve();
        boolean validBed = home != null && home.getBlock().getBlockData() instanceof Bed;
        sender.sendMessage(Component.text(
                "Giường ngủ=" + (validBed ? "HỢP LỆ" : "CHƯA GÁN hoặc block giường đã mất"),
                validBed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(farmer.npcUuid());
        String worldTime = npc != null && npc.isSpawned()
                ? Long.toString(npc.getEntity().getWorld().getTime()) : "không khả dụng";
        sender.sendMessage(Component.text(
                "Ngủ: worldTime=" + worldTime + " | phase=" + phase
                        + " | quyết định=" + plugin.manager().sleepDebug(farmer.npcUuid()),
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Hồ sơ nhân vật=" + (farmer.enabled(BehaviorFlag.CHARACTER_PROFILE) ? "BẬT" : "TẮT"),
                NamedTextColor.GRAY));
        if (farmer.enabled(BehaviorFlag.CHARACTER_PROFILE)) {
            for (String line : ResidentPresentation.characterLines(farmer.profile())) {
                sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
            }
        }
        for (ResidentRole role : farmer.profile().roles().stream().sorted().toList()) {
            RoleProgress progress = farmer.progress(role);
            ResidentSchedule schedule = farmer.schedule(
                    role, new ResidentSchedule(plugin.config().workStartTick(), plugin.config().workEndTick()));
            String activity = role == farmer.activeRole()
                    ? "ĐANG CHẠY: " + activityDescription(role, phase)
                    : "đã giao, đang chờ lịch/runtime";
            sender.sendMessage(Component.text(
                    "- " + role.storageKey() + " cấp=" + progress.level()
                            + " XP=" + progress.experience() + "/" + progress.experienceForNextLevel()
                            + " lịch=" + schedule.startTick() + "-" + schedule.endTick()
                            + " trạng thái=" + activity,
                    role == farmer.activeRole() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        }
        sender.sendMessage(Component.text(
                "AI=" + (farmer.enabled(BehaviorFlag.MASTER) ? "BẬT" : "TẮT")
                        + " thu hoạch=" + (farmer.enabled(BehaviorFlag.HARVEST) ? "BẬT" : "TẮT")
                        + " trồng=" + (farmer.enabled(BehaviorFlag.PLANT) ? "BẬT" : "TẮT")
                        + " tiền làng=" + String.format(java.util.Locale.ROOT, "%.2f",
                                plugin.economy().villageAccount(farmer.villageId()).balanceMinor() / 100.0)
                        + " ruộng=" + (farmer.plot() == null ? "chưa gán" : farmer.plot().world())
                        + " bán kính=" + farmer.plotRadius(), NamedTextColor.GRAY));
        String readiness = plugin.manager().activeRoleReadiness(farmer.npcUuid());
        sender.sendMessage(Component.text("Sẵn sàng: " + readiness,
                readiness.startsWith("SẴN SÀNG") ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        ProfessionDiagnostic diagnostic = plugin.professionMonitor().diagnostic(farmer.npcUuid());
        sender.sendMessage(Component.text("Theo dõi: " + diagnostic.message(), switch (diagnostic.level()) {
            case OK -> NamedTextColor.GREEN;
            case WAITING -> NamedTextColor.YELLOW;
            case ERROR -> NamedTextColor.RED;
        }));
    }

    private String activityDescription(ResidentRole role, FarmerPhase phase) {
        if (role == ResidentRole.RESIDENT) {
            return "đang sinh hoạt quanh nhà";
        }
        if (role == ResidentRole.FISHER) {
            return switch (phase == null ? FarmerPhase.INACTIVE : phase) {
                case GOING_TO_FISHING_SPOT -> "đang đi tới điểm câu";
                case CASTING_LINE -> "đang thả câu";
                case WAITING_FOR_BITE -> "đang chờ cá cắn câu";
                case REELING_IN -> "đang kéo dây câu";
                case RESTING -> "đang nghỉ giữa các lượt câu";
                default -> "đang chờ ca hoặc điểm câu";
            };
        }
        if (CivilProfessionRuntime.zoneFor(role) != null) {
            return switch (phase == null ? FarmerPhase.INACTIVE : phase) {
                case GOING_TO_WORK_STATION -> "đang đi tới trạm nghề";
                case PRODUCING -> "đang sản xuất";
                case PATROLLING -> "đang tuần tra";
                case ALERTING -> "đang báo động";
                case RESTING -> "đang nghỉ giữa các lượt";
                default -> "đang chờ ca hoặc trạm nghề";
            };
        }
        if (role != ResidentRole.FARMER) {
            return "runtime chưa được cấu hình";
        }
        return switch (phase == null ? FarmerPhase.INACTIVE : phase) {
            case INACTIVE -> "ngoài ca hoặc không có người chơi gần";
            case GOING_TO_PLOT -> "đang đi tới ruộng";
            case FINDING_WORK -> "đang tìm cây cần chăm sóc";
            case GOING_TO_CROP -> "đang đi tới cây trồng";
            case INSPECTING -> "đang kiểm tra cây";
            case WORKING -> "đang thu hoạch hoặc trồng cây";
            case GOING_TO_STORAGE -> "đang mang nông sản tới rương kho";
            case DEPOSITING -> "đang giao nông sản vào kho làng";
            case RETURNING_TO_PLOT -> "đang quay lại ruộng";
            case LUNCH_BREAK -> "đang nghỉ trưa";
            case GOING_TO_MARKET -> "đang đi tới chợ";
            case SHOPPING -> "đang xem hàng ở chợ";
            case GOING_TO_SCENIC -> "đang đi tới điểm ngắm cảnh";
            case SOCIALIZING -> "đang trò chuyện và ngắm cảnh";
            case GOING_TO_SEAT -> "đang đi tới ghế";
            case SITTING_REST -> "đang ngồi nghỉ";
            case SITTING_DINING -> "đang ngồi ăn";
            case STANDING_UP -> "đang đứng dậy";
            case RESTING -> "đang nghỉ ngắn";
            case WATCHING_PLAYER -> "đang quan sát người chơi";
            case LOOKING_AROUND -> "đang nhìn xung quanh";
            case WANDERING -> "đang đi dạo";
            case GOING_HOME -> "đang về nhà";
            case GOING_TO_BED -> "đang đi tới giường";
            case SLEEPING -> "đang ngủ";
            case WAKING_UP -> "đang thức dậy";
            case LEAVING_HOME -> "đang rời khỏi nhà";
            case MORNING_ACTIVITY -> "đang bắt đầu buổi sáng";
            case SHELTERING -> "đang tránh nguy hiểm";
            case GOING_TO_FISHING_SPOT, CASTING_LINE, WAITING_FOR_BITE, REELING_IN -> "đang câu cá";
            case GOING_TO_WORK_STATION -> "đang đi tới trạm nghề";
            case PRODUCING -> "đang sản xuất";
            case PATROLLING -> "đang tuần tra";
            case ALERTING -> "đang báo động";
            case GOING_TO_STALL -> "đang đi tới quầy";
            case OPENING_STALL -> "đang mở quầy";
            case SERVING -> "đang phục vụ khách";
        };
    }

    private void remove(CommandSender sender, String[] args) {
        Integer id = parseId(sender, args, "/livingnpc remove <npc-id>");
        if (id != null) {
            result(sender, plugin.manager().remove(id), "Đã xóa cư dân.");
        }
    }

    private void reload(CommandSender sender) {
        plugin.reloadPluginConfig();
        success(sender, "Đã tải lại cấu hình LivingNPC.");
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        error(sender, "Lệnh này cần vị trí của người chơi.");
        return null;
    }

    private Integer parseId(CommandSender sender, String[] args, String usage) {
        if (args.length < 2) {
            error(sender, "Cách dùng: " + usage);
            sender.sendMessage(commandLink("/livingnpc help", "Mở hướng dẫn LivingNPC"));
            return null;
        }
        try {
            return Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            error(sender, "ID NPC phải là một số.");
            sender.sendMessage(commandLink("/livingnpc help", "Mở hướng dẫn LivingNPC"));
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return List.of(
                            "list", "help", "create", "tiepnhan", "ganlang",
                            "cancel", "lang", "status", "remove", "reload").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length == 3
                && (args[0].equalsIgnoreCase("tiepnhan") || args[0].equalsIgnoreCase("adopt")
                        || args[0].equalsIgnoreCase("ganlang") || args[0].equalsIgnoreCase("assignvillage"))) {
            String prefix = args[2].toLowerCase(java.util.Locale.ROOT);
            return plugin.villages().villages().stream()
                    .map(VillageDefinition::id)
                    .filter(id -> id.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .toList();
        }
        return List.of();
    }

    private void result(CommandSender sender, boolean successful, String message) {
        if (successful) {
            success(sender, message);
        } else {
            error(sender, "Không thể thực hiện. NPC có thể chưa được LivingNPC quản lý hoặc dữ liệu không lưu được.");
        }
    }

    private void success(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }
}
