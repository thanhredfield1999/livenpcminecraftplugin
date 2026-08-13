package vn.heomc.livingnpc;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;

final class RanchDialogue {
    private static final double OBSERVATION_RANGE = 8.0;

    private RanchDialogue() {
    }

    static String nearbyAnimalLine(Location speaker, NpcAccount villageInventory) {
        if (speaker == null || speaker.getWorld() == null) return null;
        List<Animals> nearby = speaker.getWorld().getNearbyEntitiesByType(
                Animals.class, speaker, OBSERVATION_RANGE,
                animal -> species(animal) != null).stream().toList();
        Animals nearest = nearby.stream().min(Comparator.comparingDouble(
                animal -> speaker.distanceSquared(animal.getLocation()))).orElse(null);
        if (nearest == null) return null;
        Species species = species(nearest);
        List<Animals> group = nearby.stream().filter(species.type()::isInstance).toList();
        long babies = group.stream().filter(animal -> !animal.isAdult()).count();
        long ready = group.stream().filter(Animals::isAdult)
                .filter(Animals::canBreed).filter(animal -> !animal.isLoveMode()).count();
        long inLove = group.stream().filter(Animals::isLoveMode).count();
        int food = villageInventory == null ? 0 : villageInventory.quantity(species.foodKey());
        return line(new Context(species, group.size(), babies, ready, inLove, food));
    }

    static String line(Context context) {
        Species species = context.species();
        if (context.babies() > 0) {
            return choose(
                    "Có " + context.babies() + " " + species.babyName()
                            + " trong đàn. Tôi đang để ý để chúng không lạc khỏi mẹ.",
                    species.babyObservation(),
                    "Đàn " + species.displayName() + " có thêm con non rồi. Chuồng đông vui hơn hẳn.");
        }
        if (context.inLove() >= 2) {
            return choose(
                    "Hai " + species.displayName() + " đã ăn rồi. Giờ cứ để chúng ở gần nhau một lát.",
                    "Cặp " + species.displayName() + " này đã sẵn sàng sinh sản, tôi không nên làm chúng giật mình.");
        }
        if (context.ready() >= 2 && context.food() >= 2) {
            return choose(
                    "Cặp " + species.displayName() + " này đã trưởng thành và kho còn " + context.food() + " "
                            + species.foodName() + ". Tôi có đủ thức ăn để chuẩn bị cho chúng sinh sản.",
                    species.readyObservation(),
                    "Tôi đang chọn hai con " + species.displayName() + " đã trưởng thành và sẵn sàng sinh sản.");
        }
        if (context.ready() >= 2) {
            return choose(
                    "Có một cặp " + species.displayName() + " sẵn sàng, nhưng kho chưa đủ hai "
                            + species.foodName() + ".",
                    "Đàn " + species.displayName() + " đang chờ ăn. Trong kho hiện chỉ còn " + context.food()
                            + " " + species.foodName() + ".");
        }
        if (context.total() == 1) {
            return choose(
                    "Chuồng này mới có một " + species.displayName() + ". Một mình nó thì chưa thể gây đàn được.",
                    "Con " + species.displayName() + " này vẫn thiếu bạn cùng đàn. Có thêm một con nữa sẽ tốt hơn.");
        }
        if (context.total() >= 6) {
            return choose(
                    "Đàn " + species.displayName() + " đã có " + context.total()
                            + " con. Tôi phải để ý mật độ trong chuồng.",
                    species.crowdedObservation());
        }
        return choose(
                species.calmObservation(),
                "Tôi đang xem tình trạng của " + context.total() + " con " + species.displayName() + " quanh đây.",
                "Đàn " + species.displayName() + " hôm nay khá yên. Tôi sẽ kiểm tra từng con một.");
    }

    private static Species species(Animals animal) {
        if (animal instanceof Cow) return Species.COW;
        if (animal instanceof Chicken) return Species.CHICKEN;
        if (animal instanceof Sheep) return Species.SHEEP;
        if (animal instanceof Pig) return Species.PIG;
        if (animal instanceof Rabbit) return Species.RABBIT;
        return null;
    }

    private static String choose(String... lines) {
        return lines[ThreadLocalRandom.current().nextInt(lines.length)];
    }

    record Context(Species species, int total, long babies, long ready, long inLove, int food) {
    }

    enum Species {
        COW(Cow.class, "bò", "bê con", "wheat", "lúa mì",
                "Bê con thường bám khá sát bò mẹ. Tôi phải giữ lối trong chuồng thật thoáng.",
                "Hai con bò này đã trưởng thành và sẵn sàng sinh sản. Cho chúng ăn lúa mì là đàn sẽ sớm có bê con.",
                "Bò đông quá dễ chắn cổng chuồng. Tôi phải giữ lối ra vào thông thoáng.",
                "Mấy con bò đang nhai lại rất yên. Đó thường là dấu hiệu chúng không bị căng thẳng."),
        CHICKEN(Chicken.class, "gà", "gà con", "wheat_seeds", "hạt lúa mì",
                "Gà con rất dễ lọt qua chỗ hở, nên tôi vẫn thường nhìn kỹ chân hàng rào.",
                "Cặp gà này đã trưởng thành và sẵn sàng sinh sản. Một ít hạt lúa mì sẽ giúp đàn sớm có thêm gà con.",
                "Đàn gà đông thì trứng dễ nằm khuất trong góc. Tôi phải kiểm tra chuồng thường xuyên.",
                "Gà đang bới đất khá đều. Có vẻ chuồng hôm nay khô ráo và ổn định."),
        SHEEP(Sheep.class, "cừu", "cừu con", "wheat", "lúa mì",
                "Cừu con còn nhỏ nên lớp lông chưa dày. Tôi không muốn nó đứng ngoài mưa lâu.",
                "Hai con cừu này đã sẵn sàng. Kho còn lúa mì thì có thể gây thêm đàn.",
                "Cừu đứng quá sát nhau thì khó kiểm tra lông và sức khỏe từng con.",
                "Lông cừu trông sạch và đều. Đàn này đang được chăm khá tốt."),
        PIG(Pig.class, "lợn", "lợn con", "carrot", "cà rốt",
                "Lợn con chạy nhanh hơn vẻ ngoài của nó. Cổng chuồng phải luôn được đóng kỹ.",
                "Cặp lợn này đã trưởng thành và sẵn sàng sinh sản. Có đủ cà rốt là tôi có thể cho chúng ăn.",
                "Chuồng lợn đông quá sẽ chật rất nhanh. Tôi phải theo dõi số lượng cẩn thận.",
                "Mấy con lợn đang đi lại bình thường. Có vẻ chúng ăn ngủ khá tốt."),
        RABBIT(Rabbit.class, "thỏ", "thỏ con", "carrot", "cà rốt",
                "Thỏ con rất nhỏ và hay nép vào góc. Phải nhìn kỹ mới thấy được chúng.",
                "Hai con thỏ này đã trưởng thành. Chỉ cần đủ cà rốt là có thể gây thêm đàn.",
                "Thỏ sinh đàn nhanh, nên chuồng đông là phải tính chỗ rộng hơn ngay.",
                "Tai chúng dựng lên nhưng không bỏ chạy. Có vẻ đàn thỏ đã quen với nơi này.");

        private final Class<? extends Animals> type;
        private final String displayName;
        private final String babyName;
        private final String foodKey;
        private final String foodName;
        private final String babyObservation;
        private final String readyObservation;
        private final String crowdedObservation;
        private final String calmObservation;

        Species(
                Class<? extends Animals> type, String displayName, String babyName, String foodKey, String foodName,
                String babyObservation, String readyObservation,
                String crowdedObservation, String calmObservation) {
            this.type = type;
            this.displayName = displayName;
            this.babyName = babyName;
            this.foodKey = foodKey;
            this.foodName = foodName;
            this.babyObservation = babyObservation;
            this.readyObservation = readyObservation;
            this.crowdedObservation = crowdedObservation;
            this.calmObservation = calmObservation;
        }

        Class<? extends Animals> type() { return type; }
        String displayName() { return displayName; }
        String babyName() { return babyName; }
        String foodKey() { return foodKey; }
        String foodName() { return foodName; }
        String babyObservation() { return babyObservation; }
        String readyObservation() { return readyObservation; }
        String crowdedObservation() { return crowdedObservation; }
        String calmObservation() { return calmObservation; }
    }
}
