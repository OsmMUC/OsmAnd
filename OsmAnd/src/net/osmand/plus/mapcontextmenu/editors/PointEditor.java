package net.osmand.plus.mapcontextmenu.editors;

import android.app.Activity;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.helpers.AndroidUiHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public abstract class PointEditor {

	protected OsmandApplication app;
	@Nullable
	protected MapActivity mapActivity;

	protected boolean isNew;

	private boolean portraitMode;
	private boolean nightMode;

	public PointEditor(@NonNull MapActivity mapActivity) {
		this.app = mapActivity.getMyApplication();
		this.mapActivity = mapActivity;
		updateLandscapePortrait(mapActivity);
		updateNightMode();
	}

	public void setMapActivity(@Nullable MapActivity mapActivity) {
		this.mapActivity = mapActivity;
	}

	@Nullable
	public MapActivity getMapActivity() {
		return mapActivity;
	}

	public boolean isNew() {
		return isNew;
	}

	public abstract boolean isProcessingTemplate();

	public boolean isLandscapeLayout() {
		return !portraitMode;
	}

	public boolean isLight() {
		return !nightMode;
	}

	public void updateLandscapePortrait(@NonNull Activity activity) {
		portraitMode = AndroidUiHelper.isOrientationPortrait(activity);
	}

	public void updateNightMode() {
		nightMode = app.getDaynightHelper().isNightModeForMapControls();
	}

	public abstract String getFragmentTag();

	public void setCategory(String name, int color) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			Fragment fragment = mapActivity.getSupportFragmentManager().findFragmentByTag(getFragmentTag());
			if (fragment instanceof PointEditorFragmentNew) {
				PointEditorFragmentNew editorFragment = (PointEditorFragmentNew) fragment;
				editorFragment.setCategory(name, color);
			}
		}
	}
}
