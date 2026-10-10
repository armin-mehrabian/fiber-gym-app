package app.fiber.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultTargetsTest {

	@ParameterizedTest
	@ValueSource(strings = { "chest_upper", "chest_mid", "chest_lower", "delt_side", "delt_rear", "traps_mid",
		"lats_upper", "lats_lower", "quads_vasti", "glutes", "hamstrings", "calves" })
	void mainRegionsStartAt6Sets(String regionId) {
		assertThat(DefaultTargets.weeklySetsFor(regionId)).isEqualTo(6);
	}

	@ParameterizedTest
	@ValueSource(strings = { "delt_front", "traps_upper", "lower_back", "biceps_long", "biceps_short",
		"triceps_long", "triceps_lateral", "forearms", "abs_upper", "abs_lower", "obliques", "quads_rectus",
		"adductors", "glute_med" })
	void regionsWithLotsOfIndirectWorkStartAt4Sets(String regionId) {
		assertThat(DefaultTargets.weeklySetsFor(regionId)).isEqualTo(4);
	}

	@ParameterizedTest
	@ValueSource(strings = { "a_new_region", "" })
	void anyOtherRegionStartsAt4Sets(String regionId) {
		assertThat(DefaultTargets.weeklySetsFor(regionId)).isEqualTo(4);
	}

	@Test
	void thereAre12MainRegions() {
		assertThat(DefaultTargets.MAIN_REGIONS).hasSize(12);
	}

}
