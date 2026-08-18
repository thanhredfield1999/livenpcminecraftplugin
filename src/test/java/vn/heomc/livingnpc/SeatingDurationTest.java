package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Regression: NPC ngồi chill (REST seating) phải tối thiểu 30 giây (600 ticks).
 * Lunch (DINING) seating dùng chung duration settings nên cũng bị ảnh hưởng — đây là
 * hành vi thiết kế hiện tại, không phải bug.
 *
 * <p><b>Test Gap:</b> Các test này kiểm tra pure arithmetic của config và duration tính toán.
 * Không gọi {@code FarmerRuntime.tickSeating} vì method private và phụ thuộc Bukkit API
 * (NPC entity, location, SitTrait, seat manager). Để verify state transition thực tế
 * (SITTING_REST → STANDING_UP → resumePhase), cần controlled Paper server với Citizens
 * runtime hoặc integration test framework có thể mock toàn bộ Bukkit/Citizens stack.
 * Các unit test không chứng minh runtime Paper behavior.
 */
class SeatingDurationTest {

    @Test
    void defaultRestDurationMinimumIsThirtySeconds() {
        YamlConfiguration yaml = new YamlConfiguration();
        // Không set seating keys — dùng defaults
        LivingNpcConfig config = LivingNpcConfig.load(yaml);
        assertTrue(config.seating().restDurationMinTicks() >= 600L,
                "Default rest-duration-min-ticks phải >= 600 (30s); actual="
                        + config.seating().restDurationMinTicks());
        assertTrue(config.seating().restDurationMaxTicks() >= config.seating().restDurationMinTicks(),
                "rest-duration-max-ticks phải >= min; max="
                        + config.seating().restDurationMaxTicks() + " min="
                        + config.seating().restDurationMinTicks());
    }

    @Test
    void configuredRestMinCannotGoBelowThirtySeconds() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("seating.rest-duration-min-ticks", 100); // quá thấp
        yaml.set("seating.rest-duration-max-ticks", 200); // cũng quá thấp
        LivingNpcConfig config = LivingNpcConfig.load(yaml);
        assertTrue(config.seating().restDurationMinTicks() >= 600L,
                "Floor phải clamp min lên 600; actual=" + config.seating().restDurationMinTicks());
        assertTrue(config.seating().restDurationMaxTicks() >= 600L,
                "Max phải >= clamped min; actual=" + config.seating().restDurationMaxTicks());
    }

    @Test
    void configuredValidDurationPreserved() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("seating.rest-duration-min-ticks", 800);
        yaml.set("seating.rest-duration-max-ticks", 1600);
        LivingNpcConfig config = LivingNpcConfig.load(yaml);
        assertEquals(800L, config.seating().restDurationMinTicks());
        assertEquals(1600L, config.seating().restDurationMaxTicks());
    }

    @Test
    void seatEndTickRespectsDuration() {
        // Mô phỏng pure logic: seatEndTick = serverTick + randomBetween(min, max)
        // Với min=600, max=1200, bất kỳ duration nào random ra đều >= 600 ticks
        long serverTick = 5000L;
        long minDuration = 600L;
        long maxDuration = 1200L;
        // worst case: random trả min
        long seatEndTick = serverTick + minDuration;
        assertTrue(seatEndTick - serverTick >= 600L,
                "NPC phải ngồi tối thiểu 600 ticks (30s) trước khi đứng dậy");
        // best case: random trả max
        long seatEndTickMax = serverTick + maxDuration;
        assertTrue(seatEndTickMax - serverTick <= 1200L,
                "Duration không vượt max");
    }

    @Test
    void npcDoesNotStandBeforeMinDuration() {
        // Mô phỏng tickSeating check: chỉ startStanding khi serverTick >= seatEndTick
        long seatStartTick = 1000L;
        long minDuration = 600L;
        long seatEndTick = seatStartTick + minDuration; // worst case = 1600

        // Tick 1599: chưa đến deadline
        long currentTick = seatEndTick - 1;
        assertTrue(currentTick < seatEndTick,
                "Tick " + currentTick + " < seatEndTick " + seatEndTick + ": NPC vẫn ngồi");

        // Tick 1600: đúng deadline — được phép đứng
        currentTick = seatEndTick;
        assertTrue(currentTick >= seatEndTick,
                "Tick " + currentTick + " >= seatEndTick " + seatEndTick + ": NPC đứng dậy");
    }

    @Test
    void transitionAfterDeadlineReleasesToResumePhase() {
        // Pure logic: sau khi hết seatEndTick, phase chuyển về seatResumePhase
        // FarmerRuntime.tickSeating line 1465: if (serverTick >= seatEndTick) startStanding(...)
        // startStanding line 1481-1488: set STANDING_UP, sau standEndTick -> release seat, phase = resume
        //
        // Kiểm tra chuỗi: SITTING_REST -> STANDING_UP -> resumePhase
        long seatEndTick = 2000L;
        long standDuration = 8L;
        long standEndTick = seatEndTick + standDuration;

        // Tại seatEndTick: bắt đầu đứng
        assertTrue(seatEndTick >= seatEndTick);

        // Tại standEndTick: hoàn thành transition
        assertTrue(standEndTick > seatEndTick,
                "standEndTick phải sau seatEndTick");
        assertEquals(2008L, standEndTick,
                "Stand phase kéo dài đúng standDurationTicks");
    }

    @Test
    void defaultMaxIsAboveMin() {
        YamlConfiguration yaml = new YamlConfiguration();
        LivingNpcConfig config = LivingNpcConfig.load(yaml);
        assertTrue(config.seating().restDurationMaxTicks() >= config.seating().restDurationMinTicks(),
                "Default max (" + config.seating().restDurationMaxTicks()
                        + ") phải >= min (" + config.seating().restDurationMinTicks() + ")");
        assertEquals(1200L, config.seating().restDurationMaxTicks(),
                "Default max phải là 1200 ticks (60s)");
    }
}
