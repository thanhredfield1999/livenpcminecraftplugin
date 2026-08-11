package vn.heomc.livingnpc;

import java.util.Arrays;
import java.util.List;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class LivingNpcCommand implements CommandExecutor, TabCompleter {
    private final LivingNpcPlugin plugin;

    LivingNpcCommand(LivingNpcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, 1);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "help", "guide" -> help(sender, parsePage(args));
            case "create" -> create(sender, args);
            case "adopt", "tiepnhan" -> adopt(sender, args);
            case "lang", "village" -> village(sender, args);
            case "setkho", "setstorage" -> setStorage(sender, args);
            case "setdiem", "setsocial" -> setSocialPoint(sender, args);
            case "list" -> list(sender);
            case "cancel" -> cancel(sender);
            case "sethome" -> setHome(sender, args);
            case "setplot" -> setPlot(sender, args);
            case "status" -> status(sender, args);
            case "combat", "chien" -> combat(sender, args);
            case "remove" -> remove(sender, args);
            case "reload" -> reload(sender);
            default -> {
                error(sender, "Lệnh không tồn tại: " + args[0]);
                help(sender, 1);
            }
        }
        return true;
    }

    private void help(CommandSender sender, int page) {
        int selected = Math.clamp(page, 1, 3);
        sender.sendMessage(Component.text("Hướng dẫn LivingNPC ", NamedTextColor.GOLD)
                .append(Component.text("[" + selected + "/3]", NamedTextColor.YELLOW)));
        switch (selected) {
            case 1 -> {
                guide(sender, "/livingnpc list", "Mở bảng quản lý NPC và kho tổng.");
                guide(sender, "/livingnpc lang tao <id> <tên>", "Tạo một làng mới tại vị trí đang đứng.");
                guide(sender, "/livingnpc tiepnhan <npc-id> <làng-id>", "Đưa NPC Citizens có sẵn vào một làng.");
                guide(sender, "/livingnpc setkho <làng-id>", "Nhìn vào rương hoặc thùng rồi đặt kho giao hàng.");
                guide(sender, "/livingnpc setdiem <làng-id> <cho|ngamcanh>", "Đặt điểm sinh hoạt tại vị trí đứng.");
                guide(sender, "/livingnpc create <tên>", "Tạo nông dân mới tại vị trí hiện tại.");
                guide(sender, "/livingnpc sethome <id>", "Đặt nhà NPC tại vị trí hiện tại.");
                guide(sender, "/livingnpc setplot <id> [bán-kính]", "Gán vùng ruộng có giới hạn.");
                guide(sender, "/livingnpc cancel", "Hủy thao tác đang chờ chọn vị trí.");
            }
            case 2 -> {
                guide(sender, "/livingnpc status <id>", "Xem nghề, lịch, hoạt động và khu làm việc.");
                guide(sender, "/livingnpc combat", "Thiết lập và điều khiển vùng ải Zombie riêng.");
                guide(sender, "/livingnpc reload", "Đọc lại các file cấu hình.");
                guide(sender, "/livingnpc remove <id>", "Xóa vĩnh viễn NPC do LivingNPC quản lý.");
                sender.sendMessage(Component.text("Thu hoạch, trồng cây và bán kho đều TẮT mặc định để an toàn.", NamedTextColor.RED));
                sender.sendMessage(Component.text("Tắt Trí tuệ NPC để dừng toàn bộ di chuyển và hành vi.", NamedTextColor.GRAY));
            }
            case 3 -> {
                sender.sendMessage(Component.text("Kho tổng: 512 vật phẩm, tối đa 32 sản phẩm mỗi NPC trong một ca.", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("Tiền thị trấn tách khỏi Vault/Essentials; giá nằm trong prices.yml.", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("NPC chỉ tìm mục tiêu trong khu được giao, không quét toàn thế giới.", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("Nhấp phải NPC để nói chuyện bằng câu thoại tiếng Việt.", NamedTextColor.GRAY));
            }
            default -> throw new IllegalStateException("Unexpected guide page");
        }
        sender.sendMessage(Component.text("Dùng ", NamedTextColor.DARK_GRAY)
                .append(commandLink("/livingnpc help " + (selected == 3 ? 1 : selected + 1), "Trang tiếp theo")));
    }

    private int parsePage(String[] args) {
        if (args.length < 2) {
            return 1;
        }
        try {
            return Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            return 1;
        }
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
        Player player = requirePlayer(sender);
        if (player == null || args.length < 2) {
            error(sender, "Cách dùng: /livingnpc create <tên>");
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        NPC npc = plugin.manager().create(ResidentProfile.custom(name), player.getLocation());
        if (npc == null) {
            error(sender, "Citizens không thể tạo nông dân tại vị trí này.");
            return;
        }
        success(sender, "Đã tạo nông dân " + npc.getId() + " (" + npc.getName() + "). Hãy gán ruộng bằng /livingnpc setplot " + npc.getId() + " [bán-kính].");
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
        sender.sendMessage(Component.text("Cư dân " + id + ": " + farmer.profile().name(), NamedTextColor.GOLD));
        sender.sendMessage(Component.text(
                farmer.profile().title() + " | giới tính=" + farmer.profile().gender()
                        + " | nghề đang chạy=" + farmer.activeRole().storageKey(), NamedTextColor.GRAY));
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
        sender.sendMessage(Component.text("Sẵn sàng: " + plugin.manager().readiness(farmer.npcUuid()),
                plugin.manager().ready(farmer.npcUuid()) ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
    }

    private void combat(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            error(sender, "Dùng: /livingnpc combat tao|goc1|goc2|rutlui|bat|tat|status ...");
            return;
        }
        String action = args[1].toLowerCase();
        if (action.equals("tao")) {
            if (args.length < 6) {
                error(sender, "Cách dùng: /livingnpc combat tao <ải-id> <làng-id> <cung-id> <kiếm-id>");
                return;
            }
            try {
                result(sender, plugin.combat().create(args[2], args[3], Integer.parseInt(args[4]),
                                Integer.parseInt(args[5]), player.getLocation()),
                        "Đã tạo ải " + args[2] + "; vị trí hiện tại là điểm rút lui.");
            } catch (NumberFormatException exception) {
                error(sender, "ID NPC phải là số.");
            }
            return;
        }
        if (args.length < 3) {
            error(sender, "Thiếu ID ải.");
            return;
        }
        if (action.equals("status")) {
            CombatArena arena = plugin.combat().arena(args[2]);
            if (arena == null) {
                error(sender, "Không tìm thấy ải " + args[2] + ".");
                return;
            }
            sender.sendMessage(Component.text("Ải " + arena.id()
                    + " | làng=" + arena.villageId()
                    + " | cấu hình=" + (arena.configured() ? "ĐỦ" : "THIẾU")
                    + " | hoạt động=" + (arena.active() ? "BẬT" : "TẮT")
                    + " | kill lượt này=" + arena.killsThisRun() + "/32", NamedTextColor.GOLD));
            return;
        }
        boolean successful = switch (action) {
            case "goc1" -> plugin.combat().setCorner(args[2], 1, player.getLocation());
            case "goc2" -> plugin.combat().setCorner(args[2], 2, player.getLocation());
            case "rutlui" -> plugin.combat().setRetreat(args[2], player.getLocation());
            case "bat" -> plugin.combat().start(args[2]);
            case "tat" -> plugin.combat().stop(args[2]);
            default -> false;
        };
        result(sender, successful, "Đã cập nhật ải " + args[2] + " (" + action + ").");
    }

    private String activityDescription(ResidentRole role, FarmerPhase phase) {
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
            case GOING_TO_MARKET -> "đang đi tới chợ";
            case SHOPPING -> "đang xem hàng ở chợ";
            case GOING_TO_SCENIC -> "đang đi tới điểm ngắm cảnh";
            case SOCIALIZING -> "đang trò chuyện và ngắm cảnh";
            case RESTING -> "đang nghỉ ngắn";
            case WATCHING_PLAYER -> "đang quan sát người chơi";
            case LOOKING_AROUND -> "đang nhìn xung quanh";
            case WANDERING -> "đang đi dạo";
            case GOING_HOME -> "đang về nhà";
            case SHELTERING -> "đang tránh nguy hiểm";
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
            return List.of("help", "guide", "list", "cancel", "create", "lang", "tiepnhan", "setkho", "setdiem", "sethome", "setplot", "status", "combat", "remove", "reload").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("guide"))) {
            return List.of("1", "2", "3").stream()
                    .filter(value -> value.startsWith(args[1]))
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
