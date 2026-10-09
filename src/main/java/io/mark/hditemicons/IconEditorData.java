package io.mark.hditemicons;

import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

public final class IconEditorData {
	@Nullable
	public Map<Integer, CustomRotation> rotations;
	@Nullable
	public Map<String, Set<Integer>> groups;
	@Nullable
	public Map<String, CustomRotation> presets;

	public IconEditorData() {
	}

	public IconEditorData(@Nullable Map<Integer, CustomRotation> rotations,
						@Nullable Map<String, Set<Integer>> groups,
						@Nullable Map<String, CustomRotation> presets) {
		this.rotations = rotations;
		this.groups = groups;
		this.presets = presets;
	}
}
